package dev.velocity.vehicle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.velocity.core.VelocityMod;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Registro orientado a dados: lê data/&lt;ns&gt;/velocity_vehicles/*.json em cada /reload.
 * Erros num arquivo são registrados em log e o arquivo é ignorado (os demais continuam válidos).
 */
public final class VehicleDefinitions implements SimpleSynchronousResourceReloadListener, IdentifiableResourceReloadListener {
    public static final String FOLDER = "velocity_vehicles";
    private static volatile Map<String, VehicleDefinition> definitions = Map.of();

    public static VehicleDefinition get(String id) {
        return definitions.get(id);
    }

    public static Map<String, VehicleDefinition> all() {
        return definitions;
    }

    @Override
    public Identifier getFabricId() {
        return VelocityMod.id("vehicle_definitions");
    }

    @Override
    public void reload(ResourceManager manager) {
        Map<String, VehicleDefinition> loaded = new HashMap<>();
        for (Map.Entry<Identifier, Resource> entry :
                manager.findResources(FOLDER, path -> path.getPath().endsWith(".json")).entrySet()) {
            Identifier file = entry.getKey();
            String name = file.getPath().substring(FOLDER.length() + 1, file.getPath().length() - ".json".length());
            String id = file.getNamespace() + ":" + name;
            try (Reader reader = entry.getValue().getReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                loaded.put(id, VehicleDefinition.fromJson(id, json));
            } catch (Exception e) {
                VelocityMod.LOGGER.error("Definição de veículo inválida {}: {}", file, e.toString());
            }
        }
        definitions = Collections.unmodifiableMap(loaded);
        VelocityMod.LOGGER.info("VELOCITY: {} definição(ões) de veículo carregada(s): {}", loaded.size(), loaded.keySet());
    }
}
