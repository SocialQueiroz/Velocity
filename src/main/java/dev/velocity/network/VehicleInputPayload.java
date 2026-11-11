package dev.velocity.network;

import dev.velocity.core.VelocityMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Cliente → servidor: entrada do motorista, enviada a cada tick enquanto dirige. ~13 bytes. */
public record VehicleInputPayload(float throttle, float brake, float steer, boolean handbrake) implements CustomPayload {
    public static final CustomPayload.Id<VehicleInputPayload> ID = new CustomPayload.Id<>(VelocityMod.id("vehicle_input"));
    public static final PacketCodec<RegistryByteBuf, VehicleInputPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.FLOAT, VehicleInputPayload::throttle,
            PacketCodecs.FLOAT, VehicleInputPayload::brake,
            PacketCodecs.FLOAT, VehicleInputPayload::steer,
            PacketCodecs.BOOL, VehicleInputPayload::handbrake,
            VehicleInputPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
