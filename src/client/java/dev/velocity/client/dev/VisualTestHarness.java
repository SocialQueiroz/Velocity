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
    private static final Vec3d CAR = new Vec3d(0.5, -58, 0.5); // topo da plataforma (mundo plano: chão em y = -60)
    private static final Vec3d DEFAULT_TARGET = new Vec3d(0, 0.6, 0);

    private VisualTestHarness() {}

    public static void initIfEnabled() {
        if (!Boolean.getBoolean("velocity.visualtest")) return;
        VelocityMod.LOGGER.warn("VELOCITY: modo de teste visual ATIVO");
        int t = 60;
        // vistas: nome, distância, altura do olho (relativa à base do carro), ângulo (0 = frente, anti-horário
        // visto de cima), alvo x/y/z (relativo ao carro). O carro fica sobre uma plataforma de 2 blocos,
        // então o olho pode ir até ~0.4 m abaixo da base quando a câmera está fora da plataforma.
        double[][] v = {
                {5.5, 1.6, 35, 0, 0.6, 0}, {5.5, 1.8, 215, 0, 0.6, 0}, {6.5, 1.0, 90, 0, 0.6, 0}, {6, 1.2, 0, 0, 0.6, 0},
                {0.01, 9, 0, 0, 0.6, 0}, {2.6, 1.75, 62, 0, 0.6, 0},
                {1.0, 0.50, 80, 0.81, 0.34, 1.28}, {1.0, 0.50, 100, 0.82, 0.34, -1.34}, {0.9, 0.75, 35, 0.81, 0.34, 1.28},
                {1.03, 1.08, 223, 0.35, 0.85, 0.30}, {0.92, 1.06, 180, 0.40, 0.95, 0.62},
                {0.75, 0.98, 0, 0, 0.60, -0.60}, {1.0, 0.80, 270, 0.83, 0.55, -0.20}};
        String[] names = {"front34", "rear34", "side", "front", "top", "low_wheel", "brake_fl", "brake_rl", "brake_fl_front", "interior", "cockpit", "seats", "door_panel"};
        for (int i = 0; i < v.length; i++) {
            final String name = names[i];
            final double[] q = v[i];
            steps.add(new Step(t, c -> placeCamera(c, q[0], q[1], q[2], new Vec3d(q[3], q[4], q[5]))));
            steps.add(new Step(t + 25, c -> shot(c, name)));
            t += 35;
        }
        // luzes acesas à noite
        steps.add(new Step(t, c -> server(c, s -> { s.getOverworld().setTimeOfDay(18000); vehicle.handleAction(1); })));
        steps.add(new Step(t + 2, c -> placeCamera(c, 6, 1.4, 25, DEFAULT_TARGET)));
        steps.add(new Step(t + 30, c -> shot(c, "night_lights")));
        steps.add(new Step(t + 32, c -> placeCamera(c, 6, 1.6, 200, DEFAULT_TARGET)));
        for (int i = 30; i < 62; i += 2) steps.add(new Step(t + i, c -> server(c, s -> vehicle.receiveInput(new VehicleInput(0, 1, 0, false)))));
        steps.add(new Step(t + 60, c -> shot(c, "night_brake")));
        t += 70;
        // esterçamento: liga motor, esterça à direita parado, vista de cima
        steps.add(new Step(t, c -> server(c, s -> { s.getOverworld().setTimeOfDay(6000); vehicle.handleAction(1); vehicle.setEngineOn(true); })));
        for (int i = 0; i < 40; i += 2) steps.add(new Step(t + i, c -> server(c, s -> vehicle.receiveInput(new VehicleInput(0, 0, 1, false)))));
        steps.add(new Step(t + 4, c -> placeCamera(c, 0.92, 1.06, 180, new Vec3d(0.40, 0.95, 0.62))));
        steps.add(new Step(t + 38, c -> shot(c, "cockpit_steer_right")));
        t += 45;
        // direção: anda com esterço à direita e verifica sentido da guinada pelo log
        final float[] yaw0 = new float[1];
        steps.add(new Step(t, c -> server(c, s -> yaw0[0] = vehicle.getYaw())));
        for (int i = 0; i < 60; i += 2) steps.add(new Step(t + i, c -> server(c, s -> vehicle.receiveInput(new VehicleInput(0.6f, 0, 1, false)))));
        steps.add(new Step(t + 62, c -> server(c, s -> VelocityMod.LOGGER.info(
                "VISUALTEST guinada com esterço à direita: Δyaw = {} (positivo = horário visto de cima = direita)",
                vehicle.getYaw() - yaw0[0]))));
        t += 70;
        steps.add(new Step(t - 2, c -> server(c, s -> { vehicle.halt(); vehicle.refreshPositionAndAngles(CAR.x, CAR.y, CAR.z, 0f, 0f); })));
        // ré: motor ligado + "freio" parado engata ré → luz de ré; depois volta à posição
        steps.add(new Step(t, c -> server(c, s -> { s.getOverworld().setTimeOfDay(18000); vehicle.setEngineOn(true); })));
        for (int i = 2; i < 12; i += 2) steps.add(new Step(t + i, c -> server(c, s -> vehicle.receiveInput(new VehicleInput(0, 0.05f, 0, false)))));
        steps.add(new Step(t + 3, c -> placeCamera(c, 6, 1.6, 200, DEFAULT_TARGET)));
        steps.add(new Step(t + 12, c -> shot(c, "night_reverse")));
        steps.add(new Step(t + 14, c -> server(c, s -> {
            vehicle.setEngineOn(false);
            vehicle.halt();
            vehicle.refreshPositionAndAngles(CAR.x, CAR.y, CAR.z, 0f, 0f);
            s.getOverworld().setTimeOfDay(6000);
        })));
        t += 30;
        // zonas de interação: clique na porta esquerda, capô e porta-malas (pontos no espaço do veículo)
        steps.add(new Step(t, c -> server(c, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayerList().get(0);
            VelocityMod.LOGGER.info("VISUALTEST zona porta: {}", vehicle.interactAt(p, new Vec3d(1.0, 0.7, 0.0), net.minecraft.util.Hand.MAIN_HAND));
            VelocityMod.LOGGER.info("VISUALTEST zona capô: {}", vehicle.interactAt(p, new Vec3d(0, 0.85, 1.4), net.minecraft.util.Hand.MAIN_HAND));
            VelocityMod.LOGGER.info("VISUALTEST zona porta-malas: {}", vehicle.interactAt(p, new Vec3d(0, 0.95, -1.9), net.minecraft.util.Hand.MAIN_HAND));
            VelocityMod.LOGGER.info("VISUALTEST flags após cliques: {}", Integer.toBinaryString(vehicle.getVehicleFlags()));
            vehicle.handleAction(3);   // seta esquerda
        })));
        steps.add(new Step(t + 2, c -> placeCamera(c, 5.0, 1.8, 40, DEFAULT_TARGET)));
        steps.add(new Step(t + 40, c -> shot(c, "open_front34")));
        steps.add(new Step(t + 42, c -> {
            placeCamera(c, 5.0, 2.0, 210, DEFAULT_TARGET);
            if (c.world.getEntityById(vehicle.getId()) instanceof VehicleEntity cv) {   // força o spoiler (só visual)
                cv.anim[VehicleEntity.ANIM_SPOILER] = 1f; cv.prevAnim[VehicleEntity.ANIM_SPOILER] = 1f;
            }
        }));
        steps.add(new Step(t + 44, c -> server(c, s -> vehicle.handleAction(5))));   // pisca-alerta
        for (int k = 0; k < 4; k++) { final int kk = k; steps.add(new Step(t + 66 + k * 4, c -> {
            if (c.world.getEntityById(vehicle.getId()) instanceof VehicleEntity cv) { cv.anim[VehicleEntity.ANIM_SPOILER] = 1f; cv.prevAnim[VehicleEntity.ANIM_SPOILER] = 1f; }
            shot(c, "open_rear34_" + kk);
        })); }
        // mira: olhando o capô de frente, fora da caixa quadrada de colisão (1,9 m)
        steps.add(new Step(t + 84, c -> server(c, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayerList().get(0);
            p.setSneaking(true);   // Shift + clique na porta aberta = fechar (sem Shift = entrar)
            vehicle.interactAt(p, new Vec3d(1.0, 0.7, 0.0), net.minecraft.util.Hand.MAIN_HAND);
            p.setSneaking(false);
            vehicle.interactAt(p, new Vec3d(0, 0.85, 1.4), net.minecraft.util.Hand.MAIN_HAND);
            vehicle.interactAt(p, new Vec3d(0, 0.95, -1.9), net.minecraft.util.Hand.MAIN_HAND);
            vehicle.handleAction(5); vehicle.handleAction(3);
        })));
        steps.add(new Step(t + 86, c -> placeCamera(c, 2.0, 1.1, 0, new Vec3d(0, 0.7, 2.0))));
        steps.add(new Step(t + 110, c -> VelocityMod.LOGGER.info("VISUALTEST mira no capô: {} {}",
                c.crosshairTarget == null ? "null" : c.crosshairTarget.getType(),
                c.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult eh ? eh.getEntity().getType().toString() + " em " + eh.getPos().subtract(vehicle.getPos()) : "")));
        t += 120;
        // jogador sentado: escala do ocupante (3ª pessoa) e câmera interna (1ª pessoa)
        steps.add(new Step(t, c -> server(c, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayerList().get(0);
            p.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            vehicle.setYaw(0f);
            vehicle.refreshPositionAndAngles(CAR.x, CAR.y, CAR.z, 0f, 0f);
            vehicle.setEngineOn(false);   // estado conhecido antes do teste de áudio
            p.startRiding(vehicle, true);
            p.setYaw(0f); p.setPitch(5f);
        })));
        steps.add(new Step(t + 20, c -> { c.player.setYaw(0f); c.player.setPitch(8f); }));
        steps.add(new Step(t + 30, c -> shot(c, "ride_first_person")));
        steps.add(new Step(t + 32, c -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT)));
        steps.add(new Step(t + 50, c -> shot(c, "ride_third_person")));
        t += 60;
        // ÁUDIO: motorista real, teclas reais. Partida → 7 s acelerando → 2 s soltando → frenagem forte →
        // buzina → desliga. O OpenAL grava a mixagem em run/audio_test.wav (ver art/tools/visual_test.sh).
        steps.add(new Step(t, c -> {
            c.options.setPerspective(Perspective.FIRST_PERSON);
            server(c, s -> { vehicle.acceptInputWithoutDriver = false; vehicle.halt();
                vehicle.refreshPositionAndAngles(CAR.x, CAR.y, CAR.z, 0f, 0f); });
            VelocityMod.LOGGER.info("AUDIOTEST marco partida tick={}", c.world.getTime());
            server(c, s -> VelocityMod.LOGGER.info("AUDIOTEST servidor: motorista={} passageiros={} veículo do jogador={}",
                    vehicle.getDriver(), vehicle.getPassengerList(), s.getPlayerManager().getPlayerList().get(0).getVehicle()));
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new dev.velocity.network.VehicleActionPayload(dev.velocity.network.VehicleActionPayload.TOGGLE_ENGINE));
        }));
        steps.add(new Step(t + 40, c -> { c.options.forwardKey.setPressed(true);
            VelocityMod.LOGGER.info("AUDIOTEST marco acelera tick={}", c.world.getTime()); }));
        for (int i = 40; i < 260; i += 10) {
            steps.add(new Step(t + i, c -> { if (c.player.getVehicle() instanceof VehicleEntity cv)
                VelocityMod.LOGGER.info("AUDIOTEST tick={} rpm={} marcha={} kmh={} carga={} slip={}", c.world.getTime(),
                        Math.round(cv.getRpm()), cv.getGear(), Math.round(Math.abs(cv.getSpeed()) * 3.6f), cv.getLoad(), cv.getTireSlip()); }));
        }
        steps.add(new Step(t + 150, c -> shot(c, "driving_cockpit")));   // ponteiros em movimento
        steps.add(new Step(t + 180, c -> { c.options.forwardKey.setPressed(false);
            VelocityMod.LOGGER.info("AUDIOTEST marco solta tick={}", c.world.getTime()); }));
        steps.add(new Step(t + 220, c -> { c.options.backKey.setPressed(true);
            VelocityMod.LOGGER.info("AUDIOTEST marco freia tick={}", c.world.getTime()); }));
        steps.add(new Step(t + 260, c -> { c.options.backKey.setPressed(false);
            VelocityMod.LOGGER.info("AUDIOTEST marco buzina tick={}", c.world.getTime());
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new dev.velocity.network.VehicleActionPayload(dev.velocity.network.VehicleActionPayload.HORN)); }));
        steps.add(new Step(t + 290, c -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new dev.velocity.network.VehicleActionPayload(dev.velocity.network.VehicleActionPayload.TOGGLE_ENGINE))));
        steps.add(new Step(t + 330, MinecraftClient::scheduleStop));

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
                // plataforma de 2 blocos sob o carro (permite câmeras baixas e por baixo)
                for (int x = -1; x <= 1; x++) for (int z = -2; z <= 2; z++) for (int y = -60; y <= -59; y++)
                    w.setBlockState(new net.minecraft.util.math.BlockPos(x, y, z), net.minecraft.block.Blocks.SMOOTH_STONE.getDefaultState());
                // remove veículos de execuções anteriores
                for (VehicleEntity old : w.getEntitiesByType(VelocityRegistries.VEHICLE, e -> true)) old.discard();
                vehicle = VelocityRegistries.VEHICLE.create(w);
                vehicle.refreshPositionAndAngles(CAR.x, CAR.y, CAR.z, 0f, 0f);
                vehicle.fillTank();
                vehicle.acceptInputWithoutDriver = true;
                w.spawnEntity(vehicle);
                ServerPlayerEntity p = s.getPlayerManager().getPlayerList().get(0);
                p.changeGameMode(net.minecraft.world.GameMode.SPECTATOR); // sem gravidade/colisão: câmera livre
            });
        }
        for (Step st : steps) if (st.atTick() == tick) st.action().accept(c);
    }

    private static void placeCamera(MinecraftClient c, double dist, double eyeHeight, double angleDeg, Vec3d target) {
        server(c, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayerList().get(0);
            Vec3d tgt = CAR.add(target);
            double a = Math.toRadians(angleDeg);
            double ex = tgt.x + Math.sin(a) * dist, ez = tgt.z + Math.cos(a) * dist;
            double ey = CAR.y + eyeHeight;
            double dx = tgt.x - ex, dy = tgt.y - ey, dz = tgt.z - ez;
            float yaw = (float) (MathHelper.atan2(dz, dx) * 180 / Math.PI) - 90f;
            float pitch = (float) -(MathHelper.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180 / Math.PI);
            p.networkHandler.requestTeleport(ex, ey - p.getStandingEyeHeight(), ez, yaw, pitch);
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
