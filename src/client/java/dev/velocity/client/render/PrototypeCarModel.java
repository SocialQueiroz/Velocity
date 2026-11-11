package dev.velocity.client.render;

import dev.velocity.core.VelocityMod;
import dev.velocity.vehicle.VehicleEntity;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;

/**
 * MODELO PROVISÓRIO DE DESENVOLVIMENTO — NÃO É O ASSET FINAL.
 *
 * Serve apenas para validar hierarquia de partes, pivôs, rotação das rodas, esterçamento e
 * luzes na Fase 1. O modelo definitivo do Aurion VX (Fase 2) será produzido no Blockbench/Blender
 * conforme docs/ARCHITECTURE.md §C/§E e está PENDENTE.
 *
 * Unidades em pixels (16 px = 1 m). Frente = +Z. Solo em y = 24 (convenção de modelos de entidade).
 */
public class PrototypeCarModel extends EntityModel<VehicleEntity> {
    public static final EntityModelLayer LAYER = new EntityModelLayer(VelocityMod.id("prototype_car"), "main");

    private final ModelPart root;
    private final ModelPart wheelFL, wheelFR, wheelRL, wheelRR;
    private final ModelPart steerFL, steerFR;
    public final ModelPart headlights;
    public final ModelPart taillights;

    public PrototypeCarModel(ModelPart root) {
        this.root = root;
        ModelPart chassis = root.getChild("chassis");
        this.steerFL = chassis.getChild("steer_fl");
        this.steerFR = chassis.getChild("steer_fr");
        this.wheelFL = steerFL.getChild("wheel");
        this.wheelFR = steerFR.getChild("wheel");
        this.wheelRL = chassis.getChild("wheel_rl");
        this.wheelRR = chassis.getChild("wheel_rr");
        this.headlights = root.getChild("headlights");
        this.taillights = root.getChild("taillights");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData r = data.getRoot();

        // Hierarquia: root → chassis → {body, cabin, steer_fl → wheel, steer_fr → wheel, wheel_rl, wheel_rr}
        ModelPartData chassis = r.addChild("chassis", ModelPartBuilder.create(), ModelTransform.pivot(0, 24, 0));

        // Carroceria inferior (pintura): 30 × 8 × 70 px ≈ 1,9 × 0,5 × 4,4 m
        chassis.addChild("body", ModelPartBuilder.create()
                .uv(0, 0).cuboid(-15, -13, -35, 30, 8, 70)
                // capô em cunha (aproximação em degrau)
                .uv(0, 0).cuboid(-14, -15, 6, 28, 2, 26)
                // para-choques
                .uv(0, 200).cuboid(-15, -9, 35, 30, 4, 2)
                .uv(0, 200).cuboid(-15, -9, -37, 30, 4, 2),
                ModelTransform.NONE);

        // Cabine / vidros
        chassis.addChild("cabin", ModelPartBuilder.create()
                .uv(0, 100).cuboid(-12, -22, -16, 24, 9, 22),
                ModelTransform.NONE);

        // Rodas: raio 5,5 px (~0,34 m), largura 4 px. Pivô no centro do cubo.
        ModelPartBuilder wheel = ModelPartBuilder.create().uv(0, 150).cuboid(-2, -5.5f, -5.5f, 4, 11, 11);
        ModelPartData steerFl = chassis.addChild("steer_fl", ModelPartBuilder.create(), ModelTransform.pivot(13, -5.5f, 22));
        steerFl.addChild("wheel", wheel, ModelTransform.NONE);
        ModelPartData steerFr = chassis.addChild("steer_fr", ModelPartBuilder.create(), ModelTransform.pivot(-13, -5.5f, 22));
        steerFr.addChild("wheel", wheel, ModelTransform.NONE);
        chassis.addChild("wheel_rl", wheel, ModelTransform.pivot(13, -5.5f, -22));
        chassis.addChild("wheel_rr", wheel, ModelTransform.pivot(-13, -5.5f, -22));

        // Luzes (renderizadas em passe separado com brilho total quando acesas)
        r.addChild("headlights", ModelPartBuilder.create()
                .uv(100, 200).cuboid(-13, -12, 35, 7, 2, 1)
                .uv(100, 200).cuboid(6, -12, 35, 7, 2, 1),
                ModelTransform.pivot(0, 24, 0));
        r.addChild("taillights", ModelPartBuilder.create()
                .uv(150, 200).cuboid(-14, -12, -36, 8, 2, 1)
                .uv(150, 200).cuboid(6, -12, -36, 8, 2, 1),
                ModelTransform.pivot(0, 24, 0));

        return TexturedModelData.of(data, 512, 256);
    }

    @Override
    public void setAngles(VehicleEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        // Ver animate(): precisa de tickDelta para interpolar.
    }

    public void animate(VehicleEntity v, float tickDelta) {
        float spin = v.prevWheelSpin + (v.wheelSpin - v.prevWheelSpin) * tickDelta;
        wheelFL.pitch = wheelFR.pitch = wheelRL.pitch = wheelRR.pitch = spin;
        float steer = (float) Math.toRadians(v.getSteerDeg());
        steerFL.yaw = steerFR.yaw = -steer;
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        headlights.visible = false;
        taillights.visible = false;
        root.render(matrices, vertices, light, overlay, color);
    }
}
