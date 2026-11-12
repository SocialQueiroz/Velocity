package dev.velocity.vehicle;

import dev.velocity.core.VelocityMod;
import dev.velocity.core.VelocityRegistries;
import dev.velocity.core.VelocitySounds;
import dev.velocity.network.VehicleActionPayload;
import dev.velocity.physics.SurfaceGrip;
import dev.velocity.physics.VehicleInput;
import dev.velocity.physics.VehiclePhysics;
import dev.velocity.physics.VehicleState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Entidade veicular genérica. Toda característica específica vem de {@link VehicleDefinition}.
 *
 * Autoridade: o servidor simula ({@link VehiclePhysics}) e o rastreamento vanilla de entidades
 * replica posição/rotação. getControllingPassenger() fica nulo de propósito, para que o cliente
 * NÃO envie VehicleMoveC2SPacket (o que tornaria o cliente autoritativo).
 */
public class VehicleEntity extends Entity {
    private static final TrackedData<String> DEFINITION = DataTracker.registerData(VehicleEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Float> SPEED = DataTracker.registerData(VehicleEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> RPM = DataTracker.registerData(VehicleEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> GEAR = DataTracker.registerData(VehicleEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> STEER = DataTracker.registerData(VehicleEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> FUEL = DataTracker.registerData(VehicleEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> ENGINE_ON = DataTracker.registerData(VehicleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> LIGHTS_ON = DataTracker.registerData(VehicleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> BRAKING = DataTracker.registerData(VehicleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> HEALTH = DataTracker.registerData(VehicleEntity.class, TrackedDataHandlerRegistry.FLOAT);
    /** Bits: portas, capô, porta-malas, setas, pisca-alerta (ver FLAG_*). */
    private static final TrackedData<Integer> FLAGS = DataTracker.registerData(VehicleEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** Assento de cada ocupante: 2 bits por índice na lista de passageiros (até 4). */
    private static final TrackedData<Float> LOAD = DataTracker.registerData(VehicleEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> SLIP = DataTracker.registerData(VehicleEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> SEAT_MAP = DataTracker.registerData(VehicleEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public static final int FLAG_DOOR_L = 1, FLAG_DOOR_R = 1 << 1, FLAG_HOOD = 1 << 2, FLAG_TRUNK = 1 << 3,
            FLAG_TURN_L = 1 << 4, FLAG_TURN_R = 1 << 5, FLAG_HAZARD = 1 << 6;
    /** Índices de animação no cliente. */
    public static final int ANIM_DOOR_L = 0, ANIM_DOOR_R = 1, ANIM_HOOD = 2, ANIM_TRUNK = 3, ANIM_SPOILER = 4, ANIM_COUNT = 5;

    public static final String DEFAULT_DEFINITION = "velocity:aurion_vx";
    /** Ids renomeados entre versões: id antigo → id atual (mantém veículos de saves antigos). */
    private static final java.util.Map<String, String> LEGACY_IDS = java.util.Map.of("velocity:aurion_vx_prototype", "velocity:aurion_vx");
    /** Escala visual dos ocupantes (ver PlayerEntityRendererMixin). */
    public static final float OCCUPANT_RENDER_SCALE = 0.80f;
    /** Altura do olho do motorista sentado acima do solo, em metros (câmera interna). */
    public static final double SEAT_EYE_HEIGHT = 1.05;
    /** Posição do quadril do motorista (assento 0) no espaço do veículo: x à esquerda, y, z à frente. */
    private static final double SEAT_X = 0.40, SEAT_HIP_Y = 0.27, SEAT_Z = -0.30;
    private static final int INPUT_TIMEOUT_TICKS = 10;
    private static final float TICK_DT = 0.05f;
    private static final float GRAVITY_PER_TICK = 0.08f;

    private final VehicleState state = new VehicleState();
    private final VehiclePhysics physics = new VehiclePhysics(state);
    private VehicleInput input = VehicleInput.NONE;
    private int ticksSinceInput = Integer.MAX_VALUE / 2;
    private boolean fuelInitialised;
    /** Somente testes automatizados: aceita entrada sem motorista. Não é salvo nem sincronizado. */
    public boolean acceptInputWithoutDriver;

    // Servidor: assento de cada ocupante, assento pedido pela porta clicada, fechamento automático das portas.
    private final java.util.Map<java.util.UUID, Integer> seats = new java.util.HashMap<>();
    private int requestedSeat = -1;
    private final int[] doorCloseTimer = new int[2];
    private boolean turnArmed;

    // Cliente: interpolação da posição recebida do servidor (sem isto o veículo "pula" a 20 Hz e a
    // câmera do ocupante fica para trás em alta velocidade).
    private static final int CLIENT_LERP_STEPS = 2;
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;
    private float lerpYaw, lerpPitch;

    // Cliente: frações de abertura (0..1) para animação, com valor anterior para interpolar.
    public final float[] anim = new float[ANIM_COUNT];
    public final float[] prevAnim = new float[ANIM_COUNT];

    // Somente cliente: rotação acumulada das rodas (rad) para animação.
    public float wheelSpin;
    public float prevWheelSpin;

    public VehicleEntity(EntityType<? extends VehicleEntity> type, World world) {
        super(type, world);
        this.intersectionChecked = true;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(DEFINITION, DEFAULT_DEFINITION);
        builder.add(SPEED, 0f);
        builder.add(RPM, 0f);
        builder.add(GEAR, 0);
        builder.add(STEER, 0f);
        builder.add(FUEL, 0f);
        builder.add(ENGINE_ON, false);
        builder.add(LIGHTS_ON, false);
        builder.add(BRAKING, false);
        builder.add(HEALTH, 100f);
        builder.add(FLAGS, 0);
        builder.add(SEAT_MAP, 0);
        builder.add(LOAD, 0f);
        builder.add(SLIP, 0f);
    }

    // ------------------------------------------------------------------ definição

    public void setDefinitionId(String id) {
        dataTracker.set(DEFINITION, id);
    }

    public String getDefinitionId() {
        return dataTracker.get(DEFINITION);
    }

    /** Pode ser nulo no cliente (definições vivem no data pack do servidor) ou se o JSON sumiu. */
    @Nullable
    public VehicleDefinition definition() {
        return VehicleDefinitions.get(getDefinitionId());
    }

    // ------------------------------------------------------------------ ocupantes

    @Nullable
    public PlayerEntity getDriver() {
        List<Entity> list = getPassengerList();
        for (int i = 0; i < list.size(); i++) {
            if (seatOfIndex(i) == 0 && list.get(i) instanceof PlayerEntity p) return p;
        }
        return null;
    }

    private int seatCount() {
        VehicleDefinition def = definition();
        return def != null ? def.seats() : 2;
    }

    /** Assento do ocupante na posição 'index' da lista de passageiros (válido nos dois lados). */
    public int seatOfIndex(int index) {
        return index < 0 || index > 3 ? 0 : (dataTracker.get(SEAT_MAP) >> (index * 2)) & 3;
    }

    private boolean seatFree(int seat) {
        return seat >= 0 && seat < seatCount() && !seats.containsValue(seat);
    }

    private int firstFreeSeat() {
        for (int i = 0; i < seatCount(); i++) if (seatFree(i)) return i;
        return -1;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengerList().size() < seatCount() && (getWorld().isClient || firstFreeSeat() >= 0);
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (getWorld().isClient) return;
        int seat = seatFree(requestedSeat) ? requestedSeat : firstFreeSeat();
        requestedSeat = -1;
        seats.put(passenger.getUuid(), Math.max(0, seat));
        syncSeatMap();
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (getWorld().isClient) return;
        Integer seat = seats.remove(passenger.getUuid());
        syncSeatMap();
        if (seat != null && seat < 2) {        // a porta do lado abre para a saída e fecha sozinha
            setVehicleFlag(seat == 0 ? FLAG_DOOR_L : FLAG_DOOR_R, true);
            doorCloseTimer[seat] = 25;
        }
    }

    private void syncSeatMap() {
        int map = 0;
        List<Entity> list = getPassengerList();
        for (int i = 0; i < Math.min(4, list.size()); i++) map |= (seats.getOrDefault(list.get(i).getUuid(), 0) & 3) << (i * 2);
        dataTracker.set(SEAT_MAP, map);
    }

    @Override
    public Vec3d updatePassengerForDismount(LivingEntity passenger) {
        // sai pelo lado do próprio assento, ao lado da porta
        int seat = seats.getOrDefault(passenger.getUuid(), 0);
        Vec3d local = new Vec3d(seat % 2 == 0 ? 1.45 : -1.45, 0.05, SEAT_Z);
        Vec3d pos = getPos().add(local.rotateY(-getYaw() * MathHelper.RADIANS_PER_DEGREE));
        if (getWorld().isSpaceEmpty(passenger, passenger.getDimensions(passenger.getPose()).getBoxAt(pos))) return pos;
        return super.updatePassengerForDismount(passenger);
    }

    // ------------------------------------------------------------------ interação por zona

    public int getVehicleFlags() { return dataTracker.get(FLAGS); }

    public boolean hasVehicleFlag(int flag) { return (getVehicleFlags() & flag) != 0; }

    private void setVehicleFlag(int flag, boolean on) {
        int f = getVehicleFlags();
        dataTracker.set(FLAGS, on ? f | flag : f & ~flag);
    }

    /**
     * Clique com o ponto exato (relativo à posição da entidade, em coordenadas do mundo).
     * O cliente sempre aceita para que só este pacote seja enviado; o servidor decide pela zona.
     */
    @Override
    public ActionResult interactAt(PlayerEntity player, Vec3d hitPos, Hand hand) {
        if (getWorld().isClient) return ActionResult.SUCCESS;
        if (player.getVehicle() == this) return ActionResult.PASS;
        Vec3d local = hitPos.rotateY(getYaw() * MathHelper.RADIANS_PER_DEGREE);
        VehicleDefinition def = definition();
        if (def != null) {
            for (VehicleDefinition.InteractionZone zone : def.interactionZones()) {
                if (zone.contains(local.x, local.y, local.z, 0.12)) return useZone(player, zone);
            }
        }
        if (player.shouldCancelInteraction()) return ActionResult.PASS;
        return player.startRiding(this) ? ActionResult.CONSUME : ActionResult.PASS;
    }

    private ActionResult useZone(PlayerEntity player, VehicleDefinition.InteractionZone zone) {
        switch (zone.action()) {
            case "door" -> {
                int seat = zone.seat();
                int flag = seat % 2 == 0 ? FLAG_DOOR_L : FLAG_DOOR_R;
                boolean open = hasVehicleFlag(flag);
                if (!open || player.shouldCancelInteraction()) {
                    toggle(flag, open ? VelocitySounds.DOOR_CLOSE : VelocitySounds.DOOR_OPEN);
                    doorCloseTimer[seat % 2] = 0;
                    return ActionResult.CONSUME;
                }
                if (!seatFree(seat)) return ActionResult.PASS;
                requestedSeat = seat;
                if (player.startRiding(this)) {
                    doorCloseTimer[seat % 2] = 15;  // fecha depois de sentar
                    return ActionResult.CONSUME;
                }
                requestedSeat = -1;
                return ActionResult.PASS;
            }
            case "hood" -> { toggle(FLAG_HOOD, hasVehicleFlag(FLAG_HOOD) ? VelocitySounds.HOOD_CLOSE : VelocitySounds.HOOD_OPEN); return ActionResult.CONSUME; }
            case "trunk" -> { toggle(FLAG_TRUNK, hasVehicleFlag(FLAG_TRUNK) ? VelocitySounds.HOOD_CLOSE : VelocitySounds.HOOD_OPEN); return ActionResult.CONSUME; }
            default -> { return ActionResult.PASS; }
        }
    }

    private void toggle(int flag, net.minecraft.sound.SoundEvent sound) {
        boolean now = !hasVehicleFlag(flag);
        setVehicleFlag(flag, now);
        getWorld().playSound(null, getBlockPos(), sound, SoundCategory.NEUTRAL, 0.8f, 0.95f + random.nextFloat() * 0.1f);
    }

    @Override
    protected Vec3d getPassengerAttachmentPos(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
        // Assento 0 = motorista (esquerda, +X), 1 = passageiro. O ponto de fixação do jogador (0,6 m) fica
        // abaixo do quadril do modelo sentado em 0,75 × 0,9375 × escala-dos-ocupantes.
        int seat = seatOfIndex(getPassengerList().indexOf(passenger));
        double side = (seat % 2 == 0) ? SEAT_X : -SEAT_X;
        double forward = seat < 2 ? SEAT_Z : SEAT_Z - 0.8;
        double hipAboveFeet = 0.75 * 0.9375 * OCCUPANT_RENDER_SCALE;
        double attachY = SEAT_HIP_Y - hipAboveFeet + 0.6;
        return new Vec3d(side, attachY, forward).rotateY(-getYaw() * MathHelper.RADIANS_PER_DEGREE);
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
        super.updatePassengerPosition(passenger, positionUpdater);
        // O corpo do ocupante acompanha a guinada do veículo.
        float dYaw = getYaw() - prevYaw;
        passenger.setYaw(passenger.getYaw() + dYaw);
        passenger.setHeadYaw(passenger.getHeadYaw() + dYaw);
    }

    // ------------------------------------------------------------------ entrada (servidor)

    public void receiveInput(VehicleInput in) {
        this.input = in.sanitized();
        this.ticksSinceInput = 0;
    }

    public void handleAction(int action) {
        switch (action) {
            case VehicleActionPayload.TOGGLE_ENGINE -> {
                if (!state.engineOn && state.fuelLiters <= 0f) return;
                state.engineOn = !state.engineOn;
                getWorld().playSound(null, getBlockPos(), state.engineOn ? VelocitySounds.V6_START : VelocitySounds.V6_STOP,
                        SoundCategory.NEUTRAL, 1.0f, 1.0f);
            }
            case VehicleActionPayload.TOGGLE_LIGHTS -> dataTracker.set(LIGHTS_ON, !dataTracker.get(LIGHTS_ON));
            case VehicleActionPayload.HORN -> getWorld().playSound(null, getBlockPos(), VelocitySounds.HORN, SoundCategory.NEUTRAL, 2.0f, 1.0f);
            case VehicleActionPayload.TURN_LEFT -> { setVehicleFlag(FLAG_TURN_L, !hasVehicleFlag(FLAG_TURN_L)); setVehicleFlag(FLAG_TURN_R, false); turnArmed = false; }
            case VehicleActionPayload.TURN_RIGHT -> { setVehicleFlag(FLAG_TURN_R, !hasVehicleFlag(FLAG_TURN_R)); setVehicleFlag(FLAG_TURN_L, false); turnArmed = false; }
            case VehicleActionPayload.HAZARD -> setVehicleFlag(FLAG_HAZARD, !hasVehicleFlag(FLAG_HAZARD));
            default -> VelocityMod.LOGGER.debug("Ação de veículo desconhecida: {}", action);
        }
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            clientTick();
            return;
        }
        VehicleDefinition def = definition();
        if (def == null) { // definição ausente: veículo fica inerte, mas não quebra o mundo
            applyGravityAndMove(Vec3d.ZERO);
            return;
        }
        if (!fuelInitialised) {
            state.fuelLiters = Math.min(state.fuelLiters, def.fuel().capacityLiters());
            fuelInitialised = true;
        }

        ticksSinceInput++;
        for (int d = 0; d < 2; d++) {
            if (doorCloseTimer[d] > 0 && --doorCloseTimer[d] == 0) toggle(d == 0 ? FLAG_DOOR_L : FLAG_DOOR_R, VelocitySounds.DOOR_CLOSE);
        }
        // seta se desliga sozinha quando o volante volta ao centro depois de esterçar para o mesmo lado
        boolean left = hasVehicleFlag(FLAG_TURN_L), right = hasVehicleFlag(FLAG_TURN_R);
        if (left || right) {
            if ((left && state.steerDeg < -10f) || (right && state.steerDeg > 10f)) turnArmed = true;
            else if (turnArmed && Math.abs(state.steerDeg) < 3f) { setVehicleFlag(FLAG_TURN_L | FLAG_TURN_R, false); turnArmed = false; }
        }
        boolean controlled = getDriver() != null || acceptInputWithoutDriver;
        VehicleInput in = (controlled && ticksSinceInput <= INPUT_TIMEOUT_TICKS) ? input : VehicleInput.NONE;
        if (!controlled) state.engineOn = false; // desliga ao sair (TODO: opção "deixar ligado")

        BlockPos below = BlockPos.ofFloored(getX(), getY() - 0.2, getZ());
        float grip = isOnGround() ? SurfaceGrip.of(getWorld().getBlockState(below)) : 0f;
        float slopeSin = 0f; // TODO Fase 3: inclinação a partir de raycasts por roda

        physics.step(def, in, TICK_DT, grip, slopeSin);
        setYaw(getYaw() + state.yawRateDeg * TICK_DT);

        float yawRad = getYaw() * MathHelper.RADIANS_PER_DEGREE;
        double perTick = state.speed * TICK_DT; // 1 bloco = 1 metro
        Vec3d horizontal = new Vec3d(-MathHelper.sin(yawRad) * perTick, 0, MathHelper.cos(yawRad) * perTick);
        applyGravityAndMove(horizontal);

        if (horizontalCollision && Math.abs(state.speed) > 1f) {
            float impact = Math.abs(state.speed);
            dataTracker.set(HEALTH, Math.max(0f, dataTracker.get(HEALTH) - impact * 1.5f));
            state.speed *= -0.15f; // pequeno ricochete
            getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.NEUTRAL,
                    Math.min(1.5f, impact / 10f), 0.7f);
        }

        dataTracker.set(SPEED, state.speed);
        dataTracker.set(RPM, state.rpm);
        dataTracker.set(GEAR, state.gear);
        dataTracker.set(STEER, state.steerDeg);
        dataTracker.set(FUEL, state.fuelLiters);
        dataTracker.set(ENGINE_ON, state.engineOn);
        dataTracker.set(BRAKING, state.braking);
        dataTracker.set(LOAD, state.load);
        dataTracker.set(SLIP, state.tireSlip);
    }

    private void applyGravityAndMove(Vec3d horizontal) {
        double vy = isOnGround() ? -0.04 : getVelocity().y - GRAVITY_PER_TICK;
        vy = Math.max(vy, -3.0);
        Vec3d motion = new Vec3d(horizontal.x, vy, horizontal.z);
        setVelocity(motion);
        move(MovementType.SELF, motion);
        if (isOnGround()) setVelocity(getVelocity().x, 0, getVelocity().z);
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
        lerpX = x; lerpY = y; lerpZ = z;
        lerpYaw = yaw; lerpPitch = pitch;
        lerpSteps = CLIENT_LERP_STEPS;
    }

    @Override public double getLerpTargetX() { return lerpSteps > 0 ? lerpX : getX(); }
    @Override public double getLerpTargetY() { return lerpSteps > 0 ? lerpY : getY(); }
    @Override public double getLerpTargetZ() { return lerpSteps > 0 ? lerpZ : getZ(); }
    @Override public float getLerpTargetYaw() { return lerpSteps > 0 ? lerpYaw : getYaw(); }
    @Override public float getLerpTargetPitch() { return lerpSteps > 0 ? lerpPitch : getPitch(); }

    private void clientTick() {
        if (lerpSteps > 0) {
            double f = 1.0 / lerpSteps;
            setPosition(getX() + (lerpX - getX()) * f, getY() + (lerpY - getY()) * f, getZ() + (lerpZ - getZ()) * f);
            setRotation(getYaw() + MathHelper.wrapDegrees(lerpYaw - getYaw()) * (float) f,
                    getPitch() + (lerpPitch - getPitch()) * (float) f);
            lerpSteps--;
        }
        System.arraycopy(anim, 0, prevAnim, 0, ANIM_COUNT);
        approach(ANIM_DOOR_L, hasVehicleFlag(FLAG_DOOR_L), 0.09f);
        approach(ANIM_DOOR_R, hasVehicleFlag(FLAG_DOOR_R), 0.09f);
        approach(ANIM_HOOD, hasVehicleFlag(FLAG_HOOD), 0.06f);
        approach(ANIM_TRUNK, hasVehicleFlag(FLAG_TRUNK), 0.06f);
        // spoiler ativo: sobe acima de 90 km/h, recolhe abaixo de 65 km/h
        float kmh = Math.abs(getSpeed()) * 3.6f;
        boolean spoilerUp = anim[ANIM_SPOILER] > 0.5f ? kmh > 65f : kmh > 90f;
        approach(ANIM_SPOILER, spoilerUp, 0.04f);
        prevWheelSpin = wheelSpin;
        VehicleDefinitionClientHints hints = VehicleDefinitionClientHints.DEFAULT;
        wheelSpin += getSpeed() * TICK_DT / hints.wheelRadius();
    }

    private void approach(int i, boolean open, float speed) {
        anim[i] = open ? Math.min(1f, anim[i] + speed) : Math.max(0f, anim[i] - speed);
    }

    /** Fração de abertura suavizada (ease in-out) e interpolada para o quadro atual. */
    public float animFraction(int i, float tickDelta) {
        float t = prevAnim[i] + (anim[i] - prevAnim[i]) * tickDelta;
        return t * t * (3f - 2f * t);
    }

    /** Pisca: 1,25 Hz em fase com o relógio do mundo (todos os jogadores veem igual). */
    public boolean blinkOn(float tickDelta) {
        return ((getWorld().getTime() + tickDelta) % 16f) < 8f;
    }

    @Override
    public float getStepHeight() {
        return 0.6f; // sobe meio-bloco/degrau; bloco inteiro exige rampa
    }

    // ------------------------------------------------------------------ dano / item

    @Override
    public boolean canHit() {
        return !isRemoved();
    }

    @Override
    public boolean isCollidable() {
        return true;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient || isRemoved()) return !getWorld().isClient;
        if (isInvulnerableTo(source)) return false;
        boolean creative = source.getAttacker() instanceof PlayerEntity p && p.getAbilities().creativeMode;
        if (creative || source.getAttacker() instanceof PlayerEntity p2 && p2.isSneaking()) {
            // Recolher veículo: shift + ataque. Fase 5 substituirá por guincho/garagem.
            if (!creative) dropStack(new ItemStack(VelocityRegistries.VEHICLE_SPAWNER));
            discard();
            return true;
        }
        dataTracker.set(HEALTH, Math.max(0f, dataTracker.get(HEALTH) - amount));
        return true;
    }

    // ------------------------------------------------------------------ NBT

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.contains("Definition")) {
            String id = nbt.getString("Definition");
            setDefinitionId(LEGACY_IDS.getOrDefault(id, id));
        }
        state.fuelLiters = nbt.getFloat("Fuel");
        state.odometerM = nbt.getDouble("Odometer");
        dataTracker.set(HEALTH, nbt.contains("Health") ? nbt.getFloat("Health") : 100f);
        dataTracker.set(FLAGS, nbt.getInt("Flags"));
        fuelInitialised = false;
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putString("Definition", getDefinitionId());
        nbt.putFloat("Fuel", state.fuelLiters);
        nbt.putDouble("Odometer", state.odometerM);
        nbt.putFloat("Health", dataTracker.get(HEALTH));
        nbt.putInt("Flags", getVehicleFlags() & (FLAG_DOOR_L | FLAG_DOOR_R | FLAG_HOOD | FLAG_TRUNK | FLAG_HAZARD));
    }

    /** Liga/desliga o motor de forma explícita (garagem, comandos, testes). Toca o som de partida/parada. */
    public void setEngineOn(boolean on) {
        if (state.engineOn != on) handleAction(VehicleActionPayload.TOGGLE_ENGINE);
    }

    /** Para o veículo imediatamente (teletransporte, garagem, reboque, testes): zera velocidade e entrada. */
    public void halt() {
        state.speed = 0f;
        state.steerDeg = 0f;
        state.yawRateDeg = 0f;
        input = VehicleInput.NONE;
        ticksSinceInput = Integer.MAX_VALUE / 2;
        setVelocity(Vec3d.ZERO);
    }

    public void fillTank() {
        VehicleDefinition def = definition();
        state.fuelLiters = def != null ? def.fuel().capacityLiters() : 50f;
        dataTracker.set(FUEL, state.fuelLiters);
    }

    // ------------------------------------------------------------------ leitura (cliente/HUD)

    public float getSpeed() { return dataTracker.get(SPEED); }
    public float getRpm() { return dataTracker.get(RPM); }
    public int getGear() { return dataTracker.get(GEAR); }
    public float getSteerDeg() { return dataTracker.get(STEER); }
    public float getFuel() { return dataTracker.get(FUEL); }
    public boolean isEngineOn() { return dataTracker.get(ENGINE_ON); }
    public boolean areLightsOn() { return dataTracker.get(LIGHTS_ON); }
    public boolean isBraking() { return dataTracker.get(BRAKING); }
    public float getHealth() { return dataTracker.get(HEALTH); }
    public boolean isReversing() { return getGear() < 0; }
    public float getLoad() { return dataTracker.get(LOAD); }
    public float getTireSlip() { return dataTracker.get(SLIP); }
}
