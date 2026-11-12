package dev.velocity.client.model;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;

/**
 * Desenha um {@link BakedVehicleModel} percorrendo a árvore de partes e aplicando a animação
 * procedural de cada parte em torno do seu pivô. Zero alocação por quadro.
 */
public final class VehicleMeshRenderer {
    /** Reutilizado: renderização de entidades ocorre só na thread de render. */
    private static final Quaternionf ROT = new Quaternionf();

    private VehicleMeshRenderer() {}

    /** Estado visual do veículo neste quadro. */
    /**
     * @param anim frações 0..1 suavizadas indexadas por VehicleEntity.ANIM_*
     */
    public record Pose(float wheelSpinRad, float steerRad, int paintArgb, boolean headlights, boolean brake, boolean engineOn,
                       float[] anim, boolean turnLeftOn, boolean turnRightOn, boolean reverse) {}

    public static void render(BakedVehicleModel model, Pose pose, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        RenderLayer opaque = RenderLayer.getEntityCutoutNoCull(model.texture());
        // Passe 1: opacos, pintura e emissivos. Passe 2: translúcidos (vidros) depois, para mesclar corretamente.
        renderPart(model.root(), pose, matrices, consumers.getBuffer(opaque), light, false);
        renderPart(model.root(), pose, matrices, consumers.getBuffer(RenderLayer.getEntityTranslucent(model.texture())), light, true);
    }

    private static void renderPart(BakedVehicleModel.Part part, Pose pose, MatrixStack m, VertexConsumer vc, int light, boolean translucentPass) {
        m.push();
        float px = part.pivot().x(), py = part.pivot().y(), pz = part.pivot().z();
        float angle = switch (part.animation()) {
            case "wheel_spin" -> pose.wheelSpinRad();
            case "steer", "steering_wheel" -> pose.steerRad();
            case "door_l" -> pose.anim()[dev.velocity.vehicle.VehicleEntity.ANIM_DOOR_L];
            case "door_r" -> pose.anim()[dev.velocity.vehicle.VehicleEntity.ANIM_DOOR_R];
            case "hood" -> pose.anim()[dev.velocity.vehicle.VehicleEntity.ANIM_HOOD];
            case "trunk" -> pose.anim()[dev.velocity.vehicle.VehicleEntity.ANIM_TRUNK];
            case "spoiler" -> pose.anim()[dev.velocity.vehicle.VehicleEntity.ANIM_SPOILER];
            default -> 0f;
        } * part.factor();
        if (angle != 0f) {
            m.translate(px, py, pz);
            m.multiply(ROT.rotationAxis(angle, part.axis()));
            m.translate(-px, -py, -pz);
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
                            : "engine".equals(b.lightChannel()) ? pose.engineOn()
                            : "headlights".equals(b.lightChannel()) ? pose.headlights()
                            : "brake".equals(b.lightChannel()) ? pose.brake()
                            : "tail".equals(b.lightChannel()) ? (pose.brake() || pose.headlights())
                            : "turn_l".equals(b.lightChannel()) ? pose.turnLeftOn()
                            : "turn_r".equals(b.lightChannel()) ? pose.turnRightOn()
                            : "reverse".equals(b.lightChannel()) ? pose.reverse()
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
