package dev.velocity.client.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.velocity.core.VelocityMod;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * Carrega todos os assets/&lt;ns&gt;/vehicle_models/*.json a cada recarga de resource pack (F3+T).
 * Um modelo inválido é registrado no log e ignorado; o renderer usa o modelo de fallback.
 * O id do modelo é igual ao id da definição do veículo (ex.: velocity:aurion_vx).
 */
public final class VehicleModelManager implements SimpleSynchronousResourceReloadListener, IdentifiableResourceReloadListener {
    public static final String FOLDER = "vehicle_models";
    private static volatile Map<Identifier, BakedVehicleModel> models = Map.of();

    @Nullable
    public static BakedVehicleModel get(Identifier id) {
        return models.get(id);
    }

    @Override
    public Identifier getFabricId() {
        return VelocityMod.id("vehicle_models");
    }

    @Override
    public void reload(ResourceManager manager) {
        Map<Identifier, BakedVehicleModel> loaded = new HashMap<>();
        long start = System.nanoTime();
        for (Map.Entry<Identifier, Resource> e : manager.findResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet()) {
            Identifier file = e.getKey();
            String path = file.getPath();
            Identifier id = Identifier.of(file.getNamespace(), path.substring(FOLDER.length() + 1, path.length() - 5));
            try (Reader reader = e.getValue().getReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                BakedVehicleModel model = VehicleModelLoader.load(id, json, manager);
                loaded.put(id, model);
                VelocityMod.LOGGER.info("Modelo de veículo {}: {} triângulos", id, model.triangleCount());
            } catch (Exception ex) {
                VelocityMod.LOGGER.error("Falha ao carregar modelo de veículo {}: {}", file, ex.toString());
            }
        }
        models = Map.copyOf(loaded);
        VelocityMod.LOGGER.info("VELOCITY: {} modelo(s) de veículo carregado(s) em {} ms", loaded.size(),
                (System.nanoTime() - start) / 1_000_000);
    }
}
