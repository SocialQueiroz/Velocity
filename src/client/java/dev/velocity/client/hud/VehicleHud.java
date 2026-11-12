package dev.velocity.client.hud;

import dev.velocity.vehicle.VehicleDefinitionClientHints;
import dev.velocity.vehicle.VehicleEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/** HUD mínima da Fase 1 (velocidade, RPM, marcha, combustível, estado). Versão gráfica completa na Fase 3. */
public final class VehicleHud {
    private VehicleHud() {}

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || !(mc.player.getVehicle() instanceof VehicleEntity v)) return;

        VehicleDefinitionClientHints hints = VehicleDefinitionClientHints.DEFAULT;
        TextRenderer tr = mc.textRenderer;
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        int x = w - 150;
        int y = h - 74;

        ctx.fill(x - 6, y - 6, w - 6, h - 6, 0xAA0E1116);

        int kmh = Math.round(Math.abs(v.getSpeed()) * 3.6f);
        ctx.drawText(tr, String.format("%3d km/h", kmh), x, y, 0xFFFFFFFF, false);
        int gear = v.getGear();
        String g = gear < 0 ? "R" : gear == 0 ? "N" : Integer.toString(gear);
        ctx.drawText(tr, g, w - 24, y, gear < 0 ? 0xFFFF5555 : 0xFF55FFFF, false);

        // Barra de RPM com zona vermelha
        float rpmFrac = Math.min(1f, v.getRpm() / hints.redlineRpm());
        int barW = 130;
        ctx.fill(x, y + 14, x + barW, y + 20, 0xFF2A2F38);
        ctx.fill(x, y + 14, x + (int) (barW * rpmFrac), y + 20, rpmFrac > 0.88f ? 0xFFE53935 : 0xFF4FC3F7);
        ctx.drawText(tr, String.format("%5.0f rpm", v.getRpm()), x, y + 23, 0xFFB0BEC5, false);

        float fuelFrac = Math.min(1f, v.getFuel() / hints.fuelCapacity());
        ctx.fill(x, y + 36, x + barW, y + 40, 0xFF2A2F38);
        ctx.fill(x, y + 36, x + (int) (barW * fuelFrac), y + 40, fuelFrac < 0.15f ? 0xFFFFA000 : 0xFF66BB6A);
        ctx.drawText(tr, String.format("Comb. %.1f L", v.getFuel()), x, y + 43, 0xFFB0BEC5, false);

        // indicadores de seta / pisca-alerta (piscam em fase com as lâmpadas)
        boolean blink = v.blinkOn(tickCounter.getTickDelta(true));
        boolean hz = v.hasVehicleFlag(VehicleEntity.FLAG_HAZARD);
        if (blink && (hz || v.hasVehicleFlag(VehicleEntity.FLAG_TURN_L))) ctx.drawText(tr, "◀", w - 52, y, 0xFF4CAF50, false);
        if (blink && (hz || v.hasVehicleFlag(VehicleEntity.FLAG_TURN_R))) ctx.drawText(tr, "▶", w - 40, y, 0xFF4CAF50, false);

        String status = (v.isEngineOn() ? "MOTOR ON" : "MOTOR OFF [N]") + (v.areLightsOn() ? "  FARÓIS" : "");
        ctx.drawText(tr, status, x, y + 55, v.isEngineOn() ? 0xFF66BB6A : 0xFFFFA000, false);
    }
}
