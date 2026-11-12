package dev.velocity.core;

import dev.velocity.network.VelocityNetwork;
import dev.velocity.vehicle.VehicleDefinitions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VelocityMod implements ModInitializer {
    public static final String MOD_ID = "velocity";
    public static final Logger LOGGER = LoggerFactory.getLogger("VELOCITY");

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        VelocitySounds.init();
        VelocityRegistries.init();
        VelocityNetwork.init();
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new VehicleDefinitions());
        LOGGER.info("VELOCITY inicializado.");
    }
}
