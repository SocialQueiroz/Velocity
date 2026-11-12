package dev.velocity.client.model;

import dev.velocity.vehicle.VehicleEntity;
import net.minecraft.client.MinecraftClient;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Malhas deformadas por veículo danificado. A deformação só é recalculada quando o dano muda
 * (versão); veículos intactos não ocupam memória aqui. Entradas de veículos removidos são limpas.
 */
public final class DamageMeshCache {
    private record Entry(int version, BakedVehicleModel model, Map<BakedVehicleModel.Batch, float[]> data) {}

    private static final Map<Integer, Entry> cache = new HashMap<>();

    private DamageMeshCache() {}

    public static float[] get(VehicleEntity v, BakedVehicleModel model, BakedVehicleModel.Batch batch) {
        Entry e = cache.get(v.getId());
        if (e == null || e.version != v.damageVersion || e.model != model) {
            e = new Entry(v.damageVersion, model, new IdentityHashMap<>());
            cache.put(v.getId(), e);
        }
        return e.data.computeIfAbsent(batch, b -> MeshDeformer.deform(b.data(), v.damage.dents()));
    }

    /** Chamado periodicamente: descarta veículos que não existem mais ou foram consertados. */
    public static void cleanup(MinecraftClient client) {
        if (client.world == null) { cache.clear(); return; }
        for (Iterator<Map.Entry<Integer, Entry>> it = cache.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Entry> en = it.next();
            if (!(client.world.getEntityById(en.getKey()) instanceof VehicleEntity v) || v.isRemoved() || v.damage.dents().isEmpty()) it.remove();
        }
    }
}
