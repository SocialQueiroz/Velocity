package dev.velocity.client.audio;

import dev.velocity.vehicle.VehicleEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mantém um {@link VehicleSoundSet} para os veículos audíveis mais próximos. Limite de veículos
 * simultâneos para não esgotar os canais de áudio do Minecraft (cada veículo usa até 9 loops).
 */
public final class VehicleAudioController {
    private static final double RANGE = 48.0;
    private static final int MAX_VEHICLES = 4;
    private static final Map<Integer, VehicleSoundSet> sets = new HashMap<>();

    private VehicleAudioController() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(VehicleAudioController::tick);
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null || client.isPaused()) {
            if (client.world == null) { sets.values().forEach(VehicleSoundSet::stop); sets.clear(); }
            return;
        }
        if (client.world.getTime() % 200 == 0) dev.velocity.client.model.DamageMeshCache.cleanup(client);
        List<VehicleEntity> near = client.world.getEntitiesByClass(VehicleEntity.class,
                        client.player.getBoundingBox().expand(RANGE), v -> !v.isRemoved())
                .stream().sorted(Comparator.comparingDouble(v -> v.squaredDistanceTo(client.player)))
                .limit(MAX_VEHICLES).toList();
        Map<Integer, VehicleEntity> keep = new HashMap<>();
        for (VehicleEntity v : near) keep.put(v.getId(), v);
        sets.entrySet().removeIf(e -> {
            if (keep.containsKey(e.getKey())) return false;
            e.getValue().stop();
            return true;
        });
        for (VehicleEntity v : near) {
            VehicleSoundSet set = sets.computeIfAbsent(v.getId(), id -> new VehicleSoundSet(v));
            set.tick(client, 1.0f);
        }
    }
}
