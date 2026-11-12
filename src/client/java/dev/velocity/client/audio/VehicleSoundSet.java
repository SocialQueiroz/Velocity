package dev.velocity.client.audio;

import dev.velocity.core.VelocitySounds;
import dev.velocity.vehicle.VehicleEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;

/**
 * Mixer de áudio de um veículo: camadas de motor gravadas em RPMs fixos (850/2500/4500/7000),
 * misturadas por proximidade de RPM (escala logarítmica) e por carga (acelerando × desacelerando),
 * com afinação = RPM atual / RPM da gravação (limitada a 0,5–2,0 pelo Minecraft).
 */
public final class VehicleSoundSet {
    private static final float[] CENTERS = {850f, 2500f, 4500f, 7000f};
    private static final SoundEvent[] ON = {VelocitySounds.V6_IDLE_ON, VelocitySounds.V6_LOW_ON, VelocitySounds.V6_MID_ON, VelocitySounds.V6_HIGH_ON};
    private static final SoundEvent[] OFF = {null, VelocitySounds.V6_LOW_OFF, VelocitySounds.V6_MID_OFF, VelocitySounds.V6_HIGH_OFF};
    /** Partida dura ~1,35 s: as camadas entram depois que o motor "pega". */
    private static final int START_DELAY_TICKS = 22;

    private final VehicleEntity vehicle;
    private final VehicleLoopSound[] on = new VehicleLoopSound[4];
    private final VehicleLoopSound[] off = new VehicleLoopSound[4];
    private VehicleLoopSound squeal, wind;
    private boolean stopped;
    private int engineTicks;
    private float rpm, load;
    private int lastGear;
    private boolean lastBlink;

    VehicleSoundSet(VehicleEntity vehicle) {
        this.vehicle = vehicle;
        this.lastGear = vehicle.getGear();
    }

    boolean isStopped() { return stopped; }

    void stop() { stopped = true; }

    /** Pesos triangulares em log(RPM): cada camada é máxima no seu RPM e some no RPM da vizinha. */
    static float layerWeight(int i, float rpm) {
        float r = Math.max(rpm, 1f);
        if (i == 0 && r <= CENTERS[0]) return 1f;
        if (i == CENTERS.length - 1 && r >= CENTERS[i]) return 1f;
        float lr = (float) Math.log(r), lc = (float) Math.log(CENTERS[i]);
        if (r < CENTERS[i]) {
            if (i == 0) return 1f;
            float span = lc - (float) Math.log(CENTERS[i - 1]);
            return MathHelper.clamp(1f - (lc - lr) / span, 0f, 1f);
        }
        float span = (float) Math.log(CENTERS[i + 1]) - lc;
        return MathHelper.clamp(1f - (lr - lc) / span, 0f, 1f);
    }

    void tick(MinecraftClient client, float distanceVolume) {
        SoundManager sm = client.getSoundManager();
        boolean inside = client.player != null && client.player.getVehicle() == vehicle;
        boolean engine = vehicle.isEngineOn();
        engineTicks = engine ? engineTicks + 1 : 0;
        rpm += (vehicle.getRpm() - rpm) * 0.35f;
        load += (vehicle.getLoad() - load) * 0.25f;

        boolean running = engine && engineTicks > START_DELAY_TICKS;
        for (int i = 0; i < 4; i++) {
            float w = running ? layerWeight(i, rpm) : 0f;
            float pitch = MathHelper.clamp(rpm / CENTERS[i], 0.5f, 2.0f);
            float onVol = i == 0 ? w * 0.7f : w * (0.35f + 0.65f * load);
            float offVol = i == 0 ? 0f : w * (1f - load) * 0.75f;
            float muffle = inside ? 0.8f : 1f;
            on[i] = drive(sm, on[i], ON[i], onVol * muffle * distanceVolume, pitch);
            if (OFF[i] != null) off[i] = drive(sm, off[i], OFF[i], offVol * muffle * distanceVolume, pitch);
        }
        float speed = Math.abs(vehicle.getSpeed());
        float slip = vehicle.getTireSlip();
        squeal = drive(sm, squeal, VelocitySounds.TIRE_SQUEAL,
                slip > 0.05f ? slip * MathHelper.clamp(speed / 8f, 0.2f, 1f) * distanceVolume : 0f, 0.9f + 0.2f * slip);
        wind = drive(sm, wind, VelocitySounds.WIND, inside ? MathHelper.clamp((speed - 12f) / 40f, 0f, 0.8f) : 0f,
                0.8f + MathHelper.clamp(speed / 80f, 0f, 0.6f));

        // troca de marcha (para cima ou para baixo, andando para frente)
        int gear = vehicle.getGear();
        if (gear != lastGear && gear > 0 && lastGear > 0) {
            client.getSoundManager().play(new PositionedSoundInstance(VelocitySounds.GEAR_SHIFT, SoundCategory.NEUTRAL,
                    0.5f * distanceVolume, 1f, net.minecraft.client.sound.SoundInstance.createRandom(),
                    vehicle.getX(), vehicle.getY() + 0.5, vehicle.getZ()));
        }
        lastGear = gear;

        // relé da seta: só para quem está dentro
        boolean signal = vehicle.hasVehicleFlag(VehicleEntity.FLAG_TURN_L) || vehicle.hasVehicleFlag(VehicleEntity.FLAG_TURN_R)
                || vehicle.hasVehicleFlag(VehicleEntity.FLAG_HAZARD);
        boolean blink = signal && vehicle.blinkOn(0f);
        if (inside && signal && blink != lastBlink) {
            sm.play(PositionedSoundInstance.master(blink ? VelocitySounds.TURN_TICK : VelocitySounds.TURN_TOCK, 1f, 0.45f));
        }
        lastBlink = blink;
    }

    /** Cria o loop quando ele fica audível; enquanto existe, só atualiza alvos. */
    private VehicleLoopSound drive(SoundManager sm, VehicleLoopSound s, SoundEvent ev, float volume, float pitch) {
        if (s == null || s.isDone()) {
            if (volume < 0.01f) return s;
            s = new VehicleLoopSound(ev, vehicle, this);
            sm.play(s);
        }
        s.targetVolume = volume;
        s.targetPitch = pitch;
        return s;
    }
}
