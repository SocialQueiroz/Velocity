package dev.velocity.client.mixin;

import dev.velocity.client.model.BakedVehicleModel;
import dev.velocity.client.model.VehicleModelManager;
import dev.velocity.vehicle.VehicleEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Mira em veículos pela caixa ORIENTADA do modelo 3D (comprimento real do carro), não pela caixa
 * quadrada de colisão da entidade. Assim é possível clicar no capô, nas portas e no porta-malas.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow @Final MinecraftClient client;

    @Inject(method = "updateCrosshairTarget", at = @At("TAIL"))
    private void velocity$vehicleTarget(float tickDelta, CallbackInfo ci) {
        Entity camera = client.getCameraEntity();
        if (camera == null || client.world == null || client.player == null) return;
        double reach = client.player.getEntityInteractionRange();
        Vec3d start = camera.getCameraPosVec(tickDelta);
        Vec3d end = start.add(camera.getRotationVec(tickDelta).multiply(reach));
        double best = client.crosshairTarget != null && client.crosshairTarget.getType() != HitResult.Type.MISS
                ? client.crosshairTarget.getPos().squaredDistanceTo(start) : reach * reach;
        EntityHitResult found = null;
        Box search = new Box(start, end).expand(5.0);
        for (VehicleEntity v : client.world.getEntitiesByClass(VehicleEntity.class, search, e -> !e.isRemoved())) {
            if (client.player.getVehicle() == v) continue;
            Identifier id = Identifier.tryParse(v.getDefinitionId());
            BakedVehicleModel model = id != null ? VehicleModelManager.get(id) : null;
            if (model == null) continue;   // sem modelo: fica a caixa vanilla
            Vec3d origin = v.getLerpedPos(tickDelta);
            double yawRad = v.getYaw(tickDelta) * MathHelper.RADIANS_PER_DEGREE;
            Vec3d ls = start.subtract(origin).rotateY((float) yawRad);
            Vec3d le = end.subtract(origin).rotateY((float) yawRad);
            Box local = new Box(model.boundsMin().x(), model.boundsMin().y(), model.boundsMin().z(),
                    model.boundsMax().x(), model.boundsMax().y(), model.boundsMax().z());
            Optional<Vec3d> hit = local.raycast(ls, le);
            if (hit.isEmpty()) continue;
            Vec3d world = hit.get().rotateY((float) -yawRad).add(origin);
            double d = world.squaredDistanceTo(start);
            if (d < best) { best = d; found = new EntityHitResult(v, world); }
        }
        if (found != null) {
            client.crosshairTarget = found;
            client.targetedEntity = found.getEntity();
        }
    }
}
