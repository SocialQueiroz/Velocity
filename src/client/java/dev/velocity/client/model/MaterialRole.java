package dev.velocity.client.model;

/** Como um material da malha é renderizado. */
public enum MaterialRole {
    /** Opaco com a cor da textura. */
    OPAQUE,
    /** Opaco tingido pela cor de pintura do veículo (a textura deve ser clara/neutra). */
    PAINT,
    /** Translúcido (vidros). Renderizado depois dos opacos. */
    TRANSLUCENT,
    /** Opaco com brilho total quando o canal de luz associado está ativo. */
    EMISSIVE
}
