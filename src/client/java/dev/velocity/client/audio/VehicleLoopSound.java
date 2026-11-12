package dev.velocity.client.audio;

import dev.velocity.vehicle.VehicleEntity;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

/**
 * Loop preso a um veículo. Volume e afinação são definidos a cada tick pelo {@link VehicleSoundSet}
 * (mixer); esta classe só acompanha a posição e termina quando o conjunto é encerrado.
 */
public final class VehicleLoopSound extends MovingSoundInstance {
    private final VehicleEntity vehicle;
    private final VehicleSoundSet owner;
    float targetVolume;
    float targetPitch = 1f;

    VehicleLoopSound(SoundEvent sound, VehicleEntity vehicle, VehicleSoundSet owner) {
        super(sound, SoundCategory.NEUTRAL, SoundInstance.createRandom());
        this.vehicle = vehicle;
        this.owner = owner;
        this.repeat = true;
        this.repeatDelay = 0;
        this.attenuationType = AttenuationType.LINEAR;
        this.volume = 0f;
        this.pitch = 1f;
        this.x = vehicle.getX();
        this.y = vehicle.getY() + 0.5;
        this.z = vehicle.getZ();
    }

    @Override
    public boolean shouldAlwaysPlay() {
        return true;   // começa em volume 0 e sobe pelo mixer; sem isto o Minecraft descartaria o som
    }

    @Override
    public void tick() {
        if (vehicle.isRemoved() || owner.isStopped()) {
            setDone();
            return;
        }
        x = vehicle.getX();
        y = vehicle.getY() + 0.5;
        z = vehicle.getZ();
        // suavização curta evita degraus audíveis entre ticks
        volume += (targetVolume - volume) * 0.5f;
        pitch += (targetPitch - pitch) * 0.5f;
    }
}
