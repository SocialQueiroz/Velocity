package dev.velocity.client.dev;

import dev.velocity.core.VelocityMod;
import dev.velocity.core.VelocityRegistries;
import dev.velocity.physics.VehicleInput;
import dev.velocity.vehicle.VehicleEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Teste visual automatizado (somente desenvolvimento). Ativado por -Dvelocity.visualtest=true
 * (tarefa Gradle runVisualTest). Carrega um mundo, coloca um veículo, posiciona a câmera em
 * ângulos fixos, salva screenshots em run/screenshots/vt_*.png e fecha o jogo.
 * Sem a propriedade, esta classe não registra nada.
 */
public final class VisualTestHarness {
    private record Step(int atTick, Consumer<MinecraftClient> action) {}

    private static final List<Step> steps = new ArrayList<>();
    private static int tick = -1;
    private static VehicleEntity vehicle;
    private static final Vec3d CAR = new Vec3d(0.5, -60, 0.5); // superfície do mundo plano (y = -60)

    private VisualTestHarness() {}

    public static void initIfEnabled() {
        if (!Boolean.getBoolean("velocity.visualtest")) return;
        VelocityMod.LOGGER.warn("VELOCITY: modo de teste visual ATIVO");
        int t = 60;
        // vistas estáticas: (nome, distância, altura, ângulo em graus em torno do carro; 0 = frente)
        String[][] views = {
                {"front34", "5.5", "1.6", "35"}, {"rear34", "5.5", "1.8", "215"}, {"side", "6.5", "1.0", "90"},
                {"front", "6", "1.2", "0"}, {"top", "0.01", "9", "0"}, {"low_wheel", "2.6", "1.75", "62"}};
        for (String[] v : views) {
            final String name = v[0];
            final double dist = Double.parseDouble(v[1]), h = Double.parseDouble(v[2]), ang = Double.parseDouble(v[3]);
            steps.add(new Step(t, c -> placeCamera(c, dist, h, ang)));
            steps.add(new Step(t + 25, c -> shot(c, name)));
            t += 35;
        }
        // luzes acesas à noite
        steps.add(new Step(t, c -> server(c, s -> { s.getOverworld().setTimeOfDay(18000); vehicle.handleAction(1); })));
        steps.add(new Step(t + 2, c -> placeCamera(c, 6, 1.4, 25)));
        steps.add(new Step(t + 30, c -> shot(c, "night_lights")));
        steps.add(new Step(t + 32, c -> placeCamera(c, 6, 1.6, 200)));
        for (int i = 30; i < 62; i += 2) steps.add(new Step(t + i, c -> server(c, s -> vehicle.receiveInput(new VehicleInput(0, 1, 0, false)))));
        steps.add(new Step(t + 60, c -> shot(c, "night_brake")));
        t += 70;
        // esterçamento: liga motor, esterça à direita parado, vista de cima
        steps.add(new Step(t, c -> server(c, s -> { s.getOverworld().setTimeOfDay(6000); vehicle.handleAction(1); vehicle.handleAction(0); })));
        for (int i = 0; i < 40; i += 2) steps.add(new Step(t + i, c -> server(c, s -> vehicle.receiveInput(new VehicleInput(0, 0, 1, false)))));
        steps.add(new Step(t + 4, c -> placeCamera(c, 0.01, 8, 0)));
        steps.add(new Step(t + 38, c -> shot(c, "steer_right_top")));
        t += 45;
        // direção: anda com esterço à direita e verifica sentido da guinada pelo log
        final float[] yaw0 = new float[1];
        steps.add(new Step(t, c -> server(c, s -> yaw0[0] = vehicle.getYaw())));
        for (int i = 0; i < 60; i += 2) steps.add(new Step(t + i, c -> server(c, s -> vehicle.receiveInput(new VehicleInput(0.6f, 0, 1, false)))));
        steps.add(new Step(t + 62, c -> server(c, s -> VelocityMod.LOGGER.info(
                "VISUALTEST guinada com esterço à direita: Δyaw = {} (positivo = horário visto de cima = direita)",
                vehicle.getYaw() - yaw0[0]))));
        steps.add(new Step(t + 70, MinecraftClient::scheduleStop));

        ClientTickEvents.END_CLIENT_TICK.register(VisualTestHarness::onTick);
    }

    private static void onTick(MinecraftClient c) {
        if (c.world == null || c.player == null || c.getServer() == null) return;
        tick++;
        if (tick == 1) {
            c.options.hudHidden = true;
            c.options.setPerspective(Perspective.FIRST_PERSON);
            server(c, s -> {
                ServerWorld w = s.getOverworld();
                w.setTimeOfDay(6000);
                s.getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(false, s);
                // remove veículos de execuções anteriores
                for (VehicleEntity old : w.getEntitiesByType(VelocityRegistries.VEHICLE, e -> true)) old.discard();
                vehicle = VelocityRegistries.VEHICLE.create(w);
                vehicle.refreshPositionAndAngles(CAR.x, CAR.y, CAR.z, 0f, 0f);
                vehicle.fillTank();
                vehicle.acceptInputWithoutDriver = true;
                w.spawnEntity(vehicle);
                ServerPlayerEntity p = s.getPlayerManager().getPlayerList().get(0);
                p.getAbilities().flying = true;
                p.sendAbilitiesUpdate();
            });
        }
        for (Step st : steps) if (st.atTick() == tick) st.action().accept(c);
    }

    private static void placeCamera(MinecraftClient c, double dist, double height, double angleDeg) {
        server(c, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayerList().get(0);
            // ângulo 0 = à frente do carro (+Z), crescente no sentido anti-horário visto de cima
            double a = Math.toRadians(angleDeg);
            double x = CAR.x + Math.sin(a) * dist, z = CAR.z + Math.cos(a) * dist;
            double y = CAR.y + height - p.getStandingEyeHeight();
            double tx = CAR.x - x, tz = CAR.z - z, ty = (CAR.y + 0.6) - (y + p.getStandingEyeHeight());
            float yaw = (float) (MathHelper.atan2(tz, tx) * 180 / Math.PI) - 90f;
            float pitch = (float) -(MathHelper.atan2(ty, Math.sqrt(tx * tx + tz * tz)) * 180 / Math.PI);
            p.networkHandler.requestTeleport(x, y, z, yaw, pitch);
        });
    }

    private static void shot(MinecraftClient c, String name) {
        ScreenshotRecorder.saveScreenshot(c.runDirectory, "vt_" + name + ".png", c.getFramebuffer(),
                msg -> VelocityMod.LOGGER.info("VISUALTEST screenshot {}", name));
    }

    private static void server(MinecraftClient c, Consumer<IntegratedServer> action) {
        IntegratedServer s = c.getServer();
        if (s != null) s.execute(() -> action.accept(s));
    }
}
