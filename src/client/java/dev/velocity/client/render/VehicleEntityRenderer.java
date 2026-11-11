package dev.velocity.client.render;

import dev.velocity.client.model.BakedVehicleModel;
import dev.velocity.client.model.VehicleMeshRenderer;
import dev.velocity.client.model.VehicleModelManager;
import dev.velocity.core.VelocityMod;
import dev.velocity.vehicle.VehicleEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * Renderiza o veículo pela malha 3D do id da definição (assets/&lt;ns&gt;/vehicle_models/&lt;id&gt;.json).
 * Se a malha não existir/estiver inválida, usa o modelo de cubos de fallback para o veículo
 * continuar visível e o problema aparecer no log.
 */
public class VehicleEntityRenderer extends EntityRenderer<VehicleEntity> {
    private static final Identifier FALLBACK_TEXTURE = VelocityMod.id("textures/entity/prototype_car.png");
    private final PrototypeCarModel fallback;

    public VehicleEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.fallback = new PrototypeCarModel(ctx.getPart(PrototypeCarModel.LAYER));
        this.shadowRadius = 1.4f;
    }

    @Override
    public void render(VehicleEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider consumers, int light) {
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));

        Identifier modelId = Identifier.tryParse(entity.getDefinitionId());
        BakedVehicleModel model = modelId != null ? VehicleModelManager.get(modelId) : null;
        if (model != null) {
            float spin = entity.prevWheelSpin + (entity.wheelSpin - entity.prevWheelSpin) * tickDelta;
            var pose = new VehicleMeshRenderer.Pose(spin, (float) Math.toRadians(entity.getSteerDeg()),
                    model.defaultPaint(), entity.areLightsOn(), entity.isBraking(), entity.isEngineOn());
            VehicleMeshRenderer.render(model, pose, matrices, consumers, light);
        } else {
            renderFallback(entity, tickDelta, matrices, consumers, light);
        }
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
    }

    private void renderFallback(VehicleEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        matrices.push();
        matrices.scale(-1f, -1f, 1f);
        matrices.translate(0f, -1.501f, 0f);
        fallback.animate(entity, tickDelta);
        VertexConsumer vc = consumers.getBuffer(fallback.getLayer(FALLBACK_TEXTURE));
        fallback.render(matrices, vc, light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
        matrices.pop();
    }

    @Override
    public Identifier getTexture(VehicleEntity entity) {
        return FALLBACK_TEXTURE;
    }
}
