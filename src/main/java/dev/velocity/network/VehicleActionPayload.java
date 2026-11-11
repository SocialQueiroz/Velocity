package dev.velocity.network;

import dev.velocity.core.VelocityMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Cliente → servidor: ações discretas (ligar motor, faróis, buzina). */
public record VehicleActionPayload(int action) implements CustomPayload {
    public static final int TOGGLE_ENGINE = 0;
    public static final int TOGGLE_LIGHTS = 1;
    public static final int HORN = 2;

    public static final CustomPayload.Id<VehicleActionPayload> ID = new CustomPayload.Id<>(VelocityMod.id("vehicle_action"));
    public static final PacketCodec<RegistryByteBuf, VehicleActionPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.VAR_INT, VehicleActionPayload::action, VehicleActionPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
