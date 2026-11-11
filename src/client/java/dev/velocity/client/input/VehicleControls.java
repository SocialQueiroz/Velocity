package dev.velocity.client.input;

import dev.velocity.network.VehicleActionPayload;
import dev.velocity.network.VehicleInputPayload;
import dev.velocity.vehicle.VehicleEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Controles. Acelerar/frear/esterçar reutilizam W/S/A/D e Espaço (freio de mão) do próprio
 * Minecraft, que já são remapeáveis; teclas extras ficam numa categoria própria e evitam
 * atalhos vanilla (N, J, H não têm função padrão).
 */
public final class VehicleControls {
    private static final String CATEGORY = "category.velocity";
    public static KeyBinding engine;
    public static KeyBinding lights;
    public static KeyBinding horn;

    private VehicleControls() {}

    public static void init() {
        engine = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.velocity.engine", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY));
        lights = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.velocity.lights", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY));
        horn = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.velocity.horn", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(VehicleControls::tick);
    }

    private static void tick(MinecraftClient client) {
        if (client.player == null || !(client.player.getVehicle() instanceof VehicleEntity vehicle)) {
            drain();
            return;
        }
        if (vehicle.getDriver() != client.player) {
            drain();
            return;
        }
        var o = client.options;
        float throttle = o.forwardKey.isPressed() ? 1f : 0f;
        float brake = o.backKey.isPressed() ? 1f : 0f;
        float steer = (o.rightKey.isPressed() ? 1f : 0f) - (o.leftKey.isPressed() ? 1f : 0f);
        boolean handbrake = o.jumpKey.isPressed();
        ClientPlayNetworking.send(new VehicleInputPayload(throttle, brake, steer, handbrake));

        while (engine.wasPressed()) ClientPlayNetworking.send(new VehicleActionPayload(VehicleActionPayload.TOGGLE_ENGINE));
        while (lights.wasPressed()) ClientPlayNetworking.send(new VehicleActionPayload(VehicleActionPayload.TOGGLE_LIGHTS));
        while (horn.wasPressed()) ClientPlayNetworking.send(new VehicleActionPayload(VehicleActionPayload.HORN));
    }

    private static void drain() {
        if (engine == null) return;
        while (engine.wasPressed()) { }
        while (lights.wasPressed()) { }
        while (horn.wasPressed()) { }
    }
}
