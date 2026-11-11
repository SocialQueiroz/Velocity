package dev.velocity.client.render;

import dev.velocity.core.VelocityMod;
import dev.velocity.vehicle.VehicleEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public class VehicleEntityRenderer extends EntityRenderer<VehicleEntity> {
    private static final Identifier TEXTURE = VelocityMod.id("textures/entity/prototype_car.png");
    private final PrototypeCarModel model;

    public VehicleEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.model = new PrototypeCarModel(ctx.getPart(PrototypeCarModel.LAYER));
        this.shadowRadius = 1.4f;
    }

    @Override
    public void render(VehicleEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider consumers, int light) {
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
        matrices.scale(-1f, -1f, 1f);
        matrices.translate(0f, -1.501f, 0f);

        model.animate(entity, tickDelta);
        VertexConsumer vc = consumers.getBuffer(model.getLayer(TEXTURE));
        model.render(matrices, vc, light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);

        // Passe emissivo: faróis e luzes de freio com iluminação máxima.
        VertexConsumer emissive = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
        int full = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        model.headlights.visible = true;
        model.headlights.render(matrices, emissive, entity.areLightsOn() ? full : light, OverlayTexture.DEFAULT_UV);
        model.headlights.visible = false;
        boolean tailOn = entity.isBraking() || entity.areLightsOn();
        int tailColor = entity.isBraking() ? 0xFFFFFFFF : 0xFF993333; // posição = mais escura; freio = cheia
        model.taillights.visible = true;
        model.taillights.render(matrices, emissive, tailOn ? full : light, OverlayTexture.DEFAULT_UV, tailOn ? tailColor : 0xFF552222);
        model.taillights.visible = false;

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
    }

    @Override
    public Identifier getTexture(VehicleEntity entity) {
        return TEXTURE;
    }
}
