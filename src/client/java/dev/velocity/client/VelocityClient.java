package dev.velocity.client;

import dev.velocity.client.hud.VehicleHud;
import dev.velocity.client.input.VehicleControls;
import dev.velocity.client.model.VehicleModelManager;
import dev.velocity.client.render.PrototypeCarModel;
import dev.velocity.client.render.VehicleEntityRenderer;
import dev.velocity.core.VelocityRegistries;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;

public final class VelocityClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(PrototypeCarModel.LAYER, PrototypeCarModel::getTexturedModelData);
        EntityRendererRegistry.register(VelocityRegistries.VEHICLE, VehicleEntityRenderer::new);
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new VehicleModelManager());
        VehicleControls.init();
        HudRenderCallback.EVENT.register(VehicleHud::render);
        dev.velocity.client.dev.VisualTestHarness.initIfEnabled();
    }
}
