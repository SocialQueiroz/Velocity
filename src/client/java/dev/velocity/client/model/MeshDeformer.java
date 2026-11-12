package dev.velocity.client.model;

import dev.velocity.damage.VehicleDamage;

import java.util.List;

/**
 * Deforma a malha em torno dos pontos de impacto. Cada vértice dentro do raio de um amassado é
 * empurrado na direção do impacto (queda suave até a borda) com um enrugamento pseudoaleatório
 * determinístico; as normais dos quads afetados são recalculadas para a luz mostrar a lataria amassada.
 */
public final class MeshDeformer {
    private MeshDeformer() {}

    public static float[] deform(float[] src, List<VehicleDamage.Dent> dents) {
        final int V = BakedVehicleModel.FLOATS_PER_VERTEX, Q = BakedVehicleModel.FLOATS_PER_QUAD;
        float[] out = src.clone();
        boolean any = false;
        for (int q = 0; q < out.length; q += Q) {
            boolean moved = false;
            for (int v = q; v < q + Q; v += V) {
                float x = src[v], y = src[v + 1], z = src[v + 2];
                float ox = 0, oy = 0, oz = 0;
                for (int i = 0; i < dents.size(); i++) {
                    VehicleDamage.Dent d = dents.get(i);
                    float dx = x - d.x(), dy = y - d.y(), dz = z - d.z();
                    float dist2 = dx * dx + dy * dy + dz * dz;
                    float r = d.radius();
                    if (dist2 >= r * r) continue;
                    float w = 1f - (float) Math.sqrt(dist2) / r;
                    w = w * w * (3f - 2f * w);
                    float push = d.depth() * w;
                    // enrugamento: ruído estável por posição (mesmo resultado em todos os clientes)
                    float n1 = hash(x, y, z, 1) - 0.5f, n2 = hash(x, y, z, 2) - 0.5f, n3 = hash(x, y, z, 3) - 0.5f;
                    float wr = push * 0.45f;
                    ox += d.dx() * push + n1 * wr;
                    oy += d.dy() * push + n2 * wr;
                    oz += d.dz() * push + n3 * wr;
                }
                if (ox != 0 || oy != 0 || oz != 0) {
                    out[v] = x + ox; out[v + 1] = y + oy; out[v + 2] = z + oz;
                    moved = true;
                }
            }
            if (moved) {
                any = true;
                float ax = out[q + V] - out[q], ay = out[q + V + 1] - out[q + 1], az = out[q + V + 2] - out[q + 2];
                float bx = out[q + 2 * V] - out[q], by = out[q + 2 * V + 1] - out[q + 1], bz = out[q + 2 * V + 2] - out[q + 2];
                float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len > 1e-9f) {
                    nx /= len; ny /= len; nz /= len;
                    // mantém o lado original da face (a normal antiga pode apontar para o outro lado do enrolamento)
                    if (nx * src[q + 5] + ny * src[q + 6] + nz * src[q + 7] < 0) { nx = -nx; ny = -ny; nz = -nz; }
                    for (int v = q; v < q + Q; v += V) { out[v + 5] = nx; out[v + 6] = ny; out[v + 7] = nz; }
                }
            }
        }
        return any ? out : src;
    }

    private static float hash(float x, float y, float z, int salt) {
        int h = Float.floatToIntBits(x) * 73856093 ^ Float.floatToIntBits(y) * 19349663 ^ Float.floatToIntBits(z) * 83492791 ^ salt * 0x9E3779B9;
        h ^= h >>> 15; h *= 0x2C1B3C6D; h ^= h >>> 12;
        return (h & 0xFFFF) / 65535f;
    }
}
