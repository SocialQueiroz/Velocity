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
                       float[] anim, boolean turnLeftOn, boolean turnRightOn, boolean reverse,
                       float rpmFraction, float speedFraction) {}

    /** Dano visível: veículo (para o cache de malha deformada), peças soltas e carcaça queimada. */
    public record Damage(dev.velocity.vehicle.VehicleEntity vehicle, java.util.Set<String> detached, boolean wrecked) {}

    public static void render(BakedVehicleModel model, Pose pose, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        render(model, pose, null, matrices, consumers, light);
    }

    public static void render(BakedVehicleModel model, Pose pose, Damage damage, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        RenderLayer opaque = RenderLayer.getEntityCutoutNoCull(model.texture());
        // Passe 1: opacos, pintura e emissivos. Passe 2: translúcidos (vidros) depois, para mesclar corretamente.
        renderPart(model, model.root(), pose, damage, matrices, consumers.getBuffer(opaque), light, false);
        if (damage == null || !damage.wrecked())   // carcaça: vidros estourados
            renderPart(model, model.root(), pose, damage, matrices, consumers.getBuffer(RenderLayer.getEntityTranslucent(model.texture())), light, true);
    }

    private static int mul(int argb, int tint) {
        int r = ((argb >> 16) & 255) * ((tint >> 16) & 255) / 255, g = ((argb >> 8) & 255) * ((tint >> 8) & 255) / 255, b = (argb & 255) * (tint & 255) / 255;
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    private static void renderPart(BakedVehicleModel model, BakedVehicleModel.Part part, Pose pose, Damage damage, MatrixStack m,
                                   VertexConsumer vc, int light, boolean translucentPass) {
        if (damage != null && damage.detached().contains(part.name())) return;   // peça solta (e suas filhas)
        boolean wrecked = damage != null && damage.wrecked();
        boolean deform = damage != null && !part.rigid() && !damage.vehicle().damage.dents().isEmpty();
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
            case "rpm" -> pose.rpmFraction();
            case "speed" -> pose.speedFraction();
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
                    if (on && !wrecked) l = LightmapTextureManager.MAX_LIGHT_COORDINATE;
                    else color = b.offColor(); // lente/tela apagada
                }
                default -> { }
            }
            if (wrecked) color = mul(color, 0xFF3A3632);   // fuligem
            emit(deform ? DamageMeshCache.get(damage.vehicle(), model, b) : b.data(), entry, vc, color, l);
        }
        for (BakedVehicleModel.Part child : part.children()) renderPart(model, child, pose, damage, m, vc, light, translucentPass);
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
