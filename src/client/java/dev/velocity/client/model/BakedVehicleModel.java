package dev.velocity.client.model;

import net.minecraft.util.Identifier;
import org.joml.Vector3f;

import java.util.List;

/**
 * Malha de veículo pronta para renderizar: árvore de partes com pivôs, cada parte com
 * lotes por material em arrays planos (sem alocação por quadro).
 *
 * Layout de cada quad em {@link Batch#data}: 4 vértices × (x, y, z, u, v, nx, ny, nz).
 * Triângulos são armazenados como quads degenerados (4º vértice = 3º).
 */
public record BakedVehicleModel(Identifier id, Identifier texture, int defaultPaint, Part root, int triangleCount) {

    public static final int FLOATS_PER_VERTEX = 8;
    public static final int FLOATS_PER_QUAD = FLOATS_PER_VERTEX * 4;

    public record Batch(String material, MaterialRole role, String lightChannel, float[] data) {
        public int quadCount() { return data.length / FLOATS_PER_QUAD; }
    }

    /**
     * @param animation ligação de animação procedural: "none", "wheel_spin", "steer"
     */
    public record Part(String name, Vector3f pivot, String animation, List<Batch> batches, List<Part> children) {}
}
