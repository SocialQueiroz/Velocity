package dev.velocity.vehicle;

import dev.velocity.core.VelocityMod;
import dev.velocity.core.VelocityRegistries;
import dev.velocity.network.VehicleActionPayload;
import dev.velocity.physics.SurfaceGrip;
import dev.velocity.physics.VehicleInput;
import dev.velocity.physics.VehiclePhysics;
import dev.velocity.physics.VehicleState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
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
        Entity first = getFirstPassenger();
        return first instanceof PlayerEntity p ? p : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        VehicleDefinition def = definition();
        int seats = def != null ? def.seats() : 2;
        return getPassengerList().size() < seats;
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (player.shouldCancelInteraction()) return ActionResult.PASS;
        if (getWorld().isClient) return ActionResult.SUCCESS;
        return player.startRiding(this) ? ActionResult.CONSUME : ActionResult.PASS;
    }

    @Override
    protected Vec3d getPassengerAttachmentPos(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
        // Assento 0 = motorista (esquerda, +X), 1 = passageiro. O ponto de fixação do jogador (0,6 m) fica
        // abaixo do quadril do modelo sentado em 0,75 × 0,9375 × escala-dos-ocupantes.
        int seat = Math.max(0, getPassengerList().indexOf(passenger));
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
                getWorld().playSound(null, getBlockPos(),
                        state.engineOn ? SoundEvents.BLOCK_PISTON_EXTEND : SoundEvents.BLOCK_PISTON_CONTRACT,
                        SoundCategory.NEUTRAL, 0.6f, 0.6f); // TODO Fase 2: sons próprios de partida/desligamento
            }
            case VehicleActionPayload.TOGGLE_LIGHTS -> dataTracker.set(LIGHTS_ON, !dataTracker.get(LIGHTS_ON));
            case VehicleActionPayload.HORN -> getWorld().playSound(null, getBlockPos(),
                    SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO.value(), SoundCategory.NEUTRAL, 1.5f, 1.4f); // TODO buzina própria
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
    }

    private void applyGravityAndMove(Vec3d horizontal) {
        double vy = isOnGround() ? -0.04 : getVelocity().y - GRAVITY_PER_TICK;
        vy = Math.max(vy, -3.0);
        Vec3d motion = new Vec3d(horizontal.x, vy, horizontal.z);
        setVelocity(motion);
        move(MovementType.SELF, motion);
        if (isOnGround()) setVelocity(getVelocity().x, 0, getVelocity().z);
    }

    private void clientTick() {
        prevWheelSpin = wheelSpin;
        VehicleDefinitionClientHints hints = VehicleDefinitionClientHints.DEFAULT;
        wheelSpin += getSpeed() * TICK_DT / hints.wheelRadius();
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
        fuelInitialised = false;
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putString("Definition", getDefinitionId());
        nbt.putFloat("Fuel", state.fuelLiters);
        nbt.putDouble("Odometer", state.odometerM);
        nbt.putFloat("Health", dataTracker.get(HEALTH));
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
}
