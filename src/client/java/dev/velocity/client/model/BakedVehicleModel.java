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
public record BakedVehicleModel(Identifier id, Identifier texture, int defaultPaint, Part root, int triangleCount,
                                Vector3f boundsMin, Vector3f boundsMax) {

    public static final int FLOATS_PER_VERTEX = 8;
    public static final int FLOATS_PER_QUAD = FLOATS_PER_VERTEX * 4;

    /** @param offColor cor (ARGB) multiplicada quando um material emissivo está apagado */
    public record Batch(String material, MaterialRole role, String lightChannel, int offColor, float[] data) {
        public int quadCount() { return data.length / FLOATS_PER_QUAD; }
    }

    /**
     * @param animation fonte da animação procedural: "none", "wheel_spin" (rad), "steer" (rad das rodas),
     *                  "steering_wheel" (rad das rodas × fator), "door_l/door_r/hood/trunk/spoiler" (0..1),
     *                  "rpm" e "speed" (fração 0..1 do fundo de escala do mostrador — ponteiros)
     * @param axis      eixo de rotação no espaço do modelo (normalizado)
     * @param factor    multiplicador aplicado ao valor da fonte
     */
    public record Part(String name, Vector3f pivot, String animation, Vector3f axis, float factor,
                       List<Batch> batches, List<Part> children) {}
}
