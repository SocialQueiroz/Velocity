package dev.velocity.physics;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;

/** Aderência por superfície. Valores relativos ao asfalto (1.0). Primeira versão; virá de tags/data na Fase 3. */
public final class SurfaceGrip {
    private SurfaceGrip() {}

    public static float of(BlockState s) {
        if (s.isAir()) return 0f;
        if (s.isIn(BlockTags.ICE)) return 0.12f;
        if (s.isOf(Blocks.SNOW_BLOCK) || s.isOf(Blocks.SNOW) || s.isOf(Blocks.POWDER_SNOW)) return 0.35f;
        if (s.isIn(BlockTags.SAND)) return 0.55f;
        if (s.isOf(Blocks.MUD) || s.isOf(Blocks.SOUL_SAND) || s.isOf(Blocks.SOUL_SOIL)) return 0.45f;
        if (s.isOf(Blocks.GRAVEL)) return 0.6f;
        if (s.isOf(Blocks.GRASS_BLOCK) || s.isIn(BlockTags.DIRT)) return 0.7f;
        return 1.0f; // pedra, concreto, madeira etc.
    }
}
