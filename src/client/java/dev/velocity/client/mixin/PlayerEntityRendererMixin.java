package dev.velocity.client.mixin;

import dev.velocity.vehicle.VehicleEntity;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Jogadores sentados num veículo são desenhados em 80 % da escala: o boneco do Minecraft tem
 * tronco+cabeça de ~1,25 m acima do quadril e atravessaria o teto de um carro em escala real.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Inject(method = "scale(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("TAIL"))
    private void velocity$scaleInVehicle(AbstractClientPlayerEntity player, MatrixStack matrices, float amount, CallbackInfo ci) {
        if (player.getVehicle() instanceof VehicleEntity) {
            matrices.scale(VehicleEntity.OCCUPANT_RENDER_SCALE, VehicleEntity.OCCUPANT_RENDER_SCALE, VehicleEntity.OCCUPANT_RENDER_SCALE);
        }
    }
}
