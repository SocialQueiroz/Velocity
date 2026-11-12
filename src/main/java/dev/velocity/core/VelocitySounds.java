package dev.velocity.core;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;

/** Sons do VELOCITY — todos sintetizados por art/tools/gen_sounds.py (sem gravações de terceiros). */
public final class VelocitySounds {
    private VelocitySounds() {}

    public static final SoundEvent V6_IDLE_ON = register("engine.v6.idle_on");
    public static final SoundEvent V6_LOW_ON = register("engine.v6.low_on");
    public static final SoundEvent V6_LOW_OFF = register("engine.v6.low_off");
    public static final SoundEvent V6_MID_ON = register("engine.v6.mid_on");
    public static final SoundEvent V6_MID_OFF = register("engine.v6.mid_off");
    public static final SoundEvent V6_HIGH_ON = register("engine.v6.high_on");
    public static final SoundEvent V6_HIGH_OFF = register("engine.v6.high_off");
    public static final SoundEvent V6_START = register("engine.v6.start");
    public static final SoundEvent V6_STOP = register("engine.v6.stop");
    public static final SoundEvent HORN = register("vehicle.horn");
    public static final SoundEvent DOOR_OPEN = register("vehicle.door_open");
    public static final SoundEvent DOOR_CLOSE = register("vehicle.door_close");
    public static final SoundEvent HOOD_OPEN = register("vehicle.hood_open");
    public static final SoundEvent HOOD_CLOSE = register("vehicle.hood_close");
    public static final SoundEvent TURN_TICK = register("vehicle.turn_tick");
    public static final SoundEvent TURN_TOCK = register("vehicle.turn_tock");
    public static final SoundEvent GEAR_SHIFT = register("vehicle.gear_shift");
    public static final SoundEvent TIRE_SQUEAL = register("vehicle.tire_squeal");
    public static final SoundEvent WIND = register("vehicle.wind");

    private static SoundEvent register(String path) {
        return Registry.register(Registries.SOUND_EVENT, VelocityMod.id(path), SoundEvent.of(VelocityMod.id(path)));
    }

    public static void init() {
        // carregar a classe registra os sons
    }
}
