package dev.velocity.client.mixin;

import dev.velocity.vehicle.VehicleEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Câmera interna: em primeira pessoa dentro de um veículo, o olho fica na altura real de um
 * motorista sentado (definida pelo veículo), em vez de 1,62 m acima dos pés do jogador.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPos(double x, double y, double z);
    @Shadow public abstract Vec3d getPos();

    @Inject(method = "update", at = @At("TAIL"))
    private void velocity$seatEye(BlockView area, Entity focused, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (thirdPerson || !(focused.getVehicle() instanceof VehicleEntity vehicle)) return;
        Vec3d p = getPos();
        double vehicleY = vehicle.getLerpedPos(tickDelta).y;
        setPos(p.x, vehicleY + VehicleEntity.SEAT_EYE_HEIGHT, p.z);
    }
}
