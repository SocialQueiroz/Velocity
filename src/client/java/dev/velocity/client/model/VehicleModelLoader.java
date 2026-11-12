package dev.velocity.client.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lê a descrição do modelo (JSON) + malha (OBJ) e produz um {@link BakedVehicleModel}.
 *
 * Formato do JSON (assets/&lt;ns&gt;/vehicle_models/&lt;nome&gt;.json):
 * <pre>
 * {
 *   "mesh": "velocity:vehicle_models/x.obj",
 *   "texture": "velocity:textures/vehicle/x.png",
 *   "default_paint": "#B3181F",
 *   "parts": { "steer_fl": { "parent": "root", "pivot": [0.8, 0.34, 1.3], "animation": "steer" }, ... },
 *   "materials": { "paint": { "role": "paint" }, "glass": { "role": "translucent" },
 *                  "headlight": { "role": "emissive", "light": "headlights" } }
 * }
 * </pre>
 * Coordenadas em metros, Y para cima, frente do veículo em +Z, origem no solo, no centro.
 * Partes presentes no OBJ e ausentes do JSON viram filhas de "root" com pivô na origem.
 */
public final class VehicleModelLoader {
    private VehicleModelLoader() {}

    private record Decl(String parent, Vector3f pivot, String animation, Vector3f axis, float factor) {}

    /** Eixo e fator padrão por fonte de animação. */
    private static Vector3f defaultAxis(String anim) {
        return switch (anim) { case "steer" -> new Vector3f(0, 1, 0); case "steering_wheel" -> new Vector3f(0, 0, 1); default -> new Vector3f(1, 0, 0); };
    }

    private static float defaultFactor(String anim) {
        // esterço positivo = direita = rodas apontando para -X → rotação negativa em Y
        return switch (anim) { case "steer" -> -1f; case "steering_wheel" -> 12f; default -> 1f; };
    }

    public static BakedVehicleModel load(Identifier id, JsonObject json, ResourceManager manager) throws IOException {
        Identifier meshId = Identifier.of(json.get("mesh").getAsString());
        Identifier texture = Identifier.of(json.get("texture").getAsString());
        int paint = json.has("default_paint") ? parseColor(json.get("default_paint").getAsString()) : 0xFFFFFFFF;

        Optional<Resource> meshRes = manager.getResource(meshId);
        if (meshRes.isEmpty()) throw new IOException("malha não encontrada: " + meshId);
        ObjParser.Mesh mesh;
        try (Reader reader = meshRes.get().getReader()) {
            mesh = ObjParser.parse(reader, meshId.toString());
        }

        // Materiais
        Map<String, MaterialRole> roles = new HashMap<>();
        Map<String, String> lights = new HashMap<>();
        Map<String, Integer> offColors = new HashMap<>();
        if (json.has("materials")) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("materials").entrySet()) {
                JsonObject m = e.getValue().getAsJsonObject();
                roles.put(e.getKey(), MaterialRole.valueOf(m.get("role").getAsString().toUpperCase(java.util.Locale.ROOT)));
                if (m.has("light")) lights.put(e.getKey(), m.get("light").getAsString());
                if (m.has("off")) offColors.put(e.getKey(), parseColor(m.get("off").getAsString()));
            }
        }

        // Declarações de partes (pai, pivô, animação)
        Map<String, Decl> decls = new LinkedHashMap<>();
        decls.put("root", new Decl(null, new Vector3f(), "none", new Vector3f(1, 0, 0), 1f));
        if (json.has("parts")) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("parts").entrySet()) {
                JsonObject p = e.getValue().getAsJsonObject();
                Vector3f pivot = new Vector3f();
                if (p.has("pivot")) {
                    var a = p.getAsJsonArray("pivot");
                    pivot.set(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
                }
                String anim = p.has("animation") ? p.get("animation").getAsString() : "none";
                Vector3f axis = defaultAxis(anim);
                if (p.has("axis")) {
                    var a = p.getAsJsonArray("axis");
                    axis.set(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
                    if (axis.lengthSquared() < 1e-8f) throw new IOException("eixo nulo na parte '" + e.getKey() + "'");
                    axis.normalize();
                }
                float factor = p.has("factor") ? p.get("factor").getAsFloat() : defaultFactor(anim);
                decls.put(e.getKey(), new Decl(p.has("parent") ? p.get("parent").getAsString() : "root", pivot, anim, axis, factor));
            }
        }
        for (String partName : mesh.parts().keySet()) decls.putIfAbsent(partName, new Decl("root", new Vector3f(), "none", new Vector3f(1, 0, 0), 1f));

        // Valida pais e ciclos
        for (Map.Entry<String, Decl> e : decls.entrySet()) {
            String cur = e.getKey();
            int guard = 0;
            while (decls.get(cur).parent() != null) {
                String parent = decls.get(cur).parent();
                if (!decls.containsKey(parent)) throw new IOException("parte '" + cur + "' tem pai inexistente '" + parent + "'");
                cur = parent;
                if (++guard > decls.size()) throw new IOException("ciclo na hierarquia envolvendo '" + e.getKey() + "'");
            }
        }

        // Monta árvore
        Map<String, List<String>> children = new HashMap<>();
        decls.forEach((name, d) -> { if (d.parent() != null) children.computeIfAbsent(d.parent(), k -> new ArrayList<>()).add(name); });
        int[] tris = {0};
        BakedVehicleModel.Part root = build("root", decls.get("root"), decls, children, mesh, roles, lights, offColors, tris);
        // limites da malha em repouso (usados para o raycast de interação no cliente)
        Vector3f min = new Vector3f(Float.MAX_VALUE), max = new Vector3f(-Float.MAX_VALUE);
        for (Map<String, List<ObjParser.Face>> mats : mesh.parts().values())
            for (List<ObjParser.Face> faces : mats.values())
                for (ObjParser.Face f : faces)
                    for (ObjParser.Vertex v : f.vertices()) { min.min(new Vector3f(v.x(), v.y(), v.z())); max.max(new Vector3f(v.x(), v.y(), v.z())); }
        if (tris[0] == 0) throw new IOException("malha vazia: " + meshId);
        return new BakedVehicleModel(id, texture, paint, root, tris[0], min, max);
    }

    private static BakedVehicleModel.Part build(String name, Decl decl,
            Map<String, Decl> decls, Map<String, List<String>> children, ObjParser.Mesh mesh,
            Map<String, MaterialRole> roles, Map<String, String> lights, Map<String, Integer> offColors, int[] tris) {
        List<BakedVehicleModel.Batch> batches = new ArrayList<>();
        Map<String, List<ObjParser.Face>> byMaterial = mesh.parts().getOrDefault(name, Map.of());
        for (Map.Entry<String, List<ObjParser.Face>> e : byMaterial.entrySet()) {
            String mat = e.getKey();
            MaterialRole role = roles.getOrDefault(mat, MaterialRole.OPAQUE);
            batches.add(new BakedVehicleModel.Batch(mat, role, lights.get(mat), offColors.getOrDefault(mat, 0xFF7A7A7A), bake(e.getValue(), tris)));
        }
        List<BakedVehicleModel.Part> kids = new ArrayList<>();
        for (String child : children.getOrDefault(name, List.of())) {
            kids.add(build(child, decls.get(child), decls, children, mesh, roles, lights, offColors, tris));
        }
        return new BakedVehicleModel.Part(name, decl.pivot(), decl.animation(), decl.axis(), decl.factor(),
                List.copyOf(batches), List.copyOf(kids));
    }

    private static float[] bake(List<ObjParser.Face> faces, int[] tris) {
        float[] out = new float[faces.size() * BakedVehicleModel.FLOATS_PER_QUAD];
        int o = 0;
        for (ObjParser.Face face : faces) {
            ObjParser.Vertex[] v = face.vertices();
            tris[0] += v.length == 4 ? 2 : 1;
            // normal da face para vértices sem 'vn'
            float ax = v[1].x() - v[0].x(), ay = v[1].y() - v[0].y(), az = v[1].z() - v[0].z();
            float bx = v[2].x() - v[0].x(), by = v[2].y() - v[0].y(), bz = v[2].z() - v[0].z();
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1e-12f) { nx /= len; ny /= len; nz /= len; } else { nx = 0; ny = 1; nz = 0; }
            for (int i = 0; i < 4; i++) {
                ObjParser.Vertex x = v[Math.min(i, v.length - 1)];
                out[o++] = x.x(); out[o++] = x.y(); out[o++] = x.z();
                out[o++] = x.u(); out[o++] = x.v();
                boolean has = !Float.isNaN(x.nx());
                out[o++] = has ? x.nx() : nx; out[o++] = has ? x.ny() : ny; out[o++] = has ? x.nz() : nz;
            }
        }
        return out;
    }

    static int parseColor(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        int rgb = (int) Long.parseLong(h, 16);
        return h.length() <= 6 ? 0xFF000000 | rgb : rgb;
    }
}
