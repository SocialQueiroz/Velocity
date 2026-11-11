package dev.velocity.client.model;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

/**
 * Desenha um {@link BakedVehicleModel} percorrendo a árvore de partes e aplicando a animação
 * procedural de cada parte em torno do seu pivô. Zero alocação por quadro.
 */
public final class VehicleMeshRenderer {
    private VehicleMeshRenderer() {}

    /** Estado visual do veículo neste quadro. */
    public record Pose(float wheelSpinRad, float steerRad, int paintArgb, boolean headlights, boolean brake) {}

    public static void render(BakedVehicleModel model, Pose pose, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        RenderLayer opaque = RenderLayer.getEntityCutoutNoCull(model.texture());
        // Passe 1: opacos, pintura e emissivos. Passe 2: translúcidos (vidros) depois, para mesclar corretamente.
        renderPart(model.root(), pose, matrices, consumers.getBuffer(opaque), light, false);
        renderPart(model.root(), pose, matrices, consumers.getBuffer(RenderLayer.getEntityTranslucent(model.texture())), light, true);
    }

    private static void renderPart(BakedVehicleModel.Part part, Pose pose, MatrixStack m, VertexConsumer vc, int light, boolean translucentPass) {
        m.push();
        float px = part.pivot().x(), py = part.pivot().y(), pz = part.pivot().z();
        switch (part.animation()) {
            case "wheel_spin" -> {
                m.translate(px, py, pz);
                m.multiply(RotationAxis.POSITIVE_X.rotation(pose.wheelSpinRad()));
                m.translate(-px, -py, -pz);
            }
            case "steer" -> {
                // Esterço positivo = direita = rodas apontando para -X (frente em +Z).
                m.translate(px, py, pz);
                m.multiply(RotationAxis.POSITIVE_Y.rotation(-pose.steerRad()));
                m.translate(-px, -py, -pz);
            }
            default -> { }
        }
        MatrixStack.Entry entry = m.peek();
        for (BakedVehicleModel.Batch b : part.batches()) {
            boolean translucent = b.role() == MaterialRole.TRANSLUCENT;
            if (translucent != translucentPass) continue;
            int color = 0xFFFFFFFF;
            int l = light;
            switch (b.role()) {
                case PAINT -> color = pose.paintArgb();
                case EMISSIVE -> {
                    boolean on = "always".equals(b.lightChannel()) ? true
                            : "headlights".equals(b.lightChannel()) ? pose.headlights()
                            : "brake".equals(b.lightChannel()) ? pose.brake()
                            : "tail".equals(b.lightChannel()) ? (pose.brake() || pose.headlights())
                            : false;
                    if (on) l = LightmapTextureManager.MAX_LIGHT_COORDINATE;
                    else color = 0xFF7A7A7A; // lente apagada mais escura
                }
                default -> { }
            }
            emit(b.data(), entry, vc, color, l);
        }
        for (BakedVehicleModel.Part child : part.children()) renderPart(child, pose, m, vc, light, translucentPass);
        m.pop();
    }

    private static void emit(float[] d, MatrixStack.Entry entry, VertexConsumer vc, int color, int light) {
        for (int i = 0; i < d.length; i += BakedVehicleModel.FLOATS_PER_VERTEX) {
            vc.vertex(entry, d[i], d[i + 1], d[i + 2])
                    .color(color)
                    .texture(d[i + 3], d[i + 4])
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(light)
                    .normal(entry, d[i + 5], d[i + 6], d[i + 7]);
        }
    }
}
