package dev.velocity.network;

import dev.velocity.physics.VehicleInput;
import dev.velocity.vehicle.VehicleEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public final class VelocityNetwork {
    private VelocityNetwork() {}

    public static void init() {
        PayloadTypeRegistry.playC2S().register(VehicleInputPayload.ID, VehicleInputPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(VehicleActionPayload.ID, VehicleActionPayload.CODEC);

        // Handlers rodam na thread do servidor. Só o motorista atual pode controlar o veículo.
        ServerPlayNetworking.registerGlobalReceiver(VehicleInputPayload.ID, (payload, ctx) -> {
            VehicleEntity v = drivenBy(ctx.player());
            if (v != null) v.receiveInput(new VehicleInput(payload.throttle(), payload.brake(), payload.steer(), payload.handbrake()));
        });
        ServerPlayNetworking.registerGlobalReceiver(VehicleActionPayload.ID, (payload, ctx) -> {
            VehicleEntity v = drivenBy(ctx.player());
            if (v != null) v.handleAction(payload.action());
        });
    }

    private static VehicleEntity drivenBy(ServerPlayerEntity player) {
        if (player.getVehicle() instanceof VehicleEntity v && v.getDriver() == player) return v;
        return null;
    }
}
