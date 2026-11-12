package dev.velocity.damage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Estado de dano de um veículo e regras de impacto. Java puro (testável sem Minecraft).
 *
 * Um impacto é descrito no espaço do veículo (metros; +X esquerda, +Y cima, +Z frente): ponto de
 * contato, direção em que a lataria é empurrada e velocidade de impacto (m/s). As consequências
 * crescem com a energia (∝ v²) e diminuem com a robustez do veículo ("toughness").
 */
public final class VehicleDamage {
    public static final int MAX_DENTS = 24;
    public static final int FLOATS_PER_DENT = 8;

    /** Amassado: centro, raio de influência, profundidade e direção (unitária) do afundamento. */
    public record Dent(float x, float y, float z, float radius, float depth, float dx, float dy, float dz) {}

    public enum Zone { FRONT, REAR, LEFT, RIGHT }

    /** Peça que se solta quando a saúde da sua zona cai abaixo do limite. */
    public record Breakable(String part, Zone zone, float detachBelow) {}

    /** Perfil de dano do veículo (vem do JSON de definição). */
    public record Profile(float toughness, Zone engineZone, float halfLength, float halfWidth, List<Breakable> breakables) {
        public static final Profile DEFAULT = new Profile(1f, Zone.FRONT, 2.2f, 0.96f, List.of());
    }

    /** O que um impacto causou — usado pela entidade para sons, partículas, ocupantes e explosão. */
    public record ImpactResult(float dentDepth, float zoneDamage, float engineDamage, List<String> detachedNow,
                               float occupantDamage, boolean ignited, float instantExplosionPower) {
        public static final ImpactResult NONE = new ImpactResult(0, 0, 0, List.of(), 0, false, 0);
        public boolean any() { return dentDepth > 0 || zoneDamage > 0; }
    }

    // limiares (m/s)
    public static final float MIN_DAMAGE_SPEED = 2.0f;        // ~7 km/h: abaixo disso é só encostar
    public static final float ENGINE_DAMAGE_SPEED = 10.0f;    // 36 km/h
    public static final float OCCUPANT_DAMAGE_SPEED = 10.0f;
    public static final float INSTANT_EXPLOSION_SPEED = 36.0f; // ~130 km/h (× √toughness)

    private final List<Dent> dents = new ArrayList<>();
    private final float[] zoneHealth = {100f, 100f, 100f, 100f};
    private float engineHealth = 100f;
    private final Set<String> detached = new LinkedHashSet<>();
    private int burnTicks = -1;      // >= 0: pegando fogo, explode ao chegar a 0
    private boolean wrecked;

    public List<Dent> dents() { return dents; }
    public float zoneHealth(Zone z) { return zoneHealth[z.ordinal()]; }
    public float engineHealth() { return engineHealth; }
    public Set<String> detached() { return detached; }
    public boolean burning() { return burnTicks >= 0 && !wrecked; }
    public int burnTicks() { return burnTicks; }
    public boolean wrecked() { return wrecked; }
    public boolean pristine() { return dents.isEmpty() && engineHealth >= 100f && detached.isEmpty() && !wrecked && burnTicks < 0; }

    /** Fator de potência do motor: 1 até 70 % de saúde, caindo linearmente até 0. */
    public float powerFactor() {
        if (wrecked || engineHealth <= 0f) return 0f;
        return engineHealth >= 70f ? 1f : Math.max(0.15f, engineHealth / 70f);
    }

    public boolean engineUsable() { return !wrecked && engineHealth > 0f; }

    public static Zone zoneOf(Profile p, float lx, float lz) {
        if (lz > p.halfLength() - 0.55f) return Zone.FRONT;
        if (lz < -p.halfLength() + 0.55f) return Zone.REAR;
        return lx >= 0 ? Zone.LEFT : Zone.RIGHT;
    }

    /**
     * Aplica um impacto.
     * @param nx,ny,nz direção em que a lataria é empurrada (para dentro do carro), não precisa ser unitária
     */
    public ImpactResult applyImpact(Profile p, float lx, float ly, float lz, float nx, float ny, float nz, float speed) {
        if (wrecked || speed < MIN_DAMAGE_SPEED) return ImpactResult.NONE;
        float t = Math.max(0.1f, p.toughness());
        float over = speed - MIN_DAMAGE_SPEED;

        // amassado: profundidade cresce um pouco mais que linear com a velocidade
        float depth = Math.min(0.45f, 0.012f * (float) Math.pow(over, 1.25) / t);
        float radius = 0.35f + depth * 1.6f;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-5f) { nx = 0; ny = 0; nz = lz >= 0 ? -1 : 1; len = 1; }
        addDent(new Dent(lx, ly, lz, radius, depth, nx / len, ny / len, nz / len));

        // saúde da zona ∝ energia
        Zone zone = zoneOf(p, lx, lz);
        float zoneDamage = over * over * 0.22f / t;
        zoneHealth[zone.ordinal()] = Math.max(0f, zoneHealth[zone.ordinal()] - zoneDamage);

        float engineDamage = 0f;
        if (zone == p.engineZone() && speed > ENGINE_DAMAGE_SPEED) {
            float e = speed - ENGINE_DAMAGE_SPEED;
            engineDamage = e * e * 0.35f / t;
            engineHealth = Math.max(0f, engineHealth - engineDamage);
        }

        List<String> detachedNow = new ArrayList<>();
        for (Breakable b : p.breakables()) {
            if (zoneHealth[b.zone().ordinal()] < b.detachBelow() && detached.add(b.part())) detachedNow.add(b.part());
        }

        float occupant = speed > OCCUPANT_DAMAGE_SPEED ? (speed - OCCUPANT_DAMAGE_SPEED) * 0.7f : 0f;

        float instant = 0f;
        float explodeAt = INSTANT_EXPLOSION_SPEED * (float) Math.sqrt(t);
        boolean ignited = false;
        if (speed >= explodeAt) {
            instant = Math.min(5f, 2f + (speed - explodeAt) / 6f);
        } else if (engineHealth <= 25f && burnTicks < 0) {
            burnTicks = 60 + (int) (engineHealth * 2);   // 3 a 5,5 s para sair do carro
            ignited = true;
        }
        return new ImpactResult(depth, zoneDamage, engineDamage, List.copyOf(detachedNow), occupant, ignited, instant);
    }

    private void addDent(Dent d) {
        if (d.depth() <= 0.004f) return;
        for (int i = 0; i < dents.size(); i++) {
            Dent o = dents.get(i);
            float ddx = o.x() - d.x(), ddy = o.y() - d.y(), ddz = o.z() - d.z();
            if (ddx * ddx + ddy * ddy + ddz * ddz < 0.30f * 0.30f) {   // mesmo lugar: aprofunda em vez de duplicar
                float depth = Math.min(0.5f, (float) Math.sqrt(o.depth() * o.depth() + d.depth() * d.depth()));
                dents.set(i, new Dent(o.x(), o.y(), o.z(), Math.max(o.radius(), d.radius()), depth, o.dx(), o.dy(), o.dz()));
                return;
            }
        }
        if (dents.size() >= MAX_DENTS) {            // descarta o amassado mais raso
            int shallow = 0;
            for (int i = 1; i < dents.size(); i++) if (dents.get(i).depth() < dents.get(shallow).depth()) shallow = i;
            if (dents.get(shallow).depth() >= d.depth()) return;
            dents.remove(shallow);
        }
        dents.add(d);
    }

    /** Avança o fogo. Retorna a potência da explosão quando o tempo acaba (0 = nada). */
    public float tickBurn(float fuelFraction) {
        if (wrecked || burnTicks < 0) return 0f;
        if (burnTicks-- > 0) return 0f;
        return 2.5f + 1.5f * Math.max(0f, Math.min(1f, fuelFraction));
    }

    /** Depois da explosão: carcaça. Solta todas as peças quebráveis e deforma a carroceria. */
    public void wreck(Profile p, long seed) {
        wrecked = true;
        burnTicks = -1;
        engineHealth = 0f;
        java.util.Random r = new java.util.Random(seed);
        for (int i = 0; i < zoneHealth.length; i++) zoneHealth[i] = 0f;
        for (Breakable b : p.breakables()) detached.add(b.part());
        for (int i = 0; i < 8; i++) {
            float lx = (r.nextFloat() * 2 - 1) * p.halfWidth();
            float lz = (r.nextFloat() * 2 - 1) * p.halfLength();
            addDent(new Dent(lx, 0.5f + r.nextFloat() * 0.6f, lz, 0.6f + r.nextFloat() * 0.4f, 0.12f + r.nextFloat() * 0.2f,
                    -Math.signum(lx) * 0.5f, -0.7f, -Math.signum(lz) * 0.5f));
        }
    }

    public void repairAll() {
        dents.clear();
        java.util.Arrays.fill(zoneHealth, 100f);
        engineHealth = 100f;
        detached.clear();
        burnTicks = -1;
        wrecked = false;
    }

    // ------------------------------------------------------------- (de)serialização neutra
    public float[] encodeDents() {
        float[] out = new float[dents.size() * FLOATS_PER_DENT];
        int o = 0;
        for (Dent d : dents) {
            out[o++] = d.x(); out[o++] = d.y(); out[o++] = d.z(); out[o++] = d.radius(); out[o++] = d.depth();
            out[o++] = d.dx(); out[o++] = d.dy(); out[o++] = d.dz();
        }
        return out;
    }

    public void load(float[] dentData, float[] zones, float engine, java.util.Collection<String> detachedParts, int burn, boolean isWrecked) {
        dents.clear();
        for (int i = 0; i + FLOATS_PER_DENT <= dentData.length && dents.size() < MAX_DENTS; i += FLOATS_PER_DENT) {
            dents.add(new Dent(dentData[i], dentData[i + 1], dentData[i + 2], dentData[i + 3], dentData[i + 4],
                    dentData[i + 5], dentData[i + 6], dentData[i + 7]));
        }
        for (int i = 0; i < zoneHealth.length; i++) zoneHealth[i] = i < zones.length ? zones[i] : 100f;
        engineHealth = engine;
        detached.clear();
        detached.addAll(detachedParts);
        burnTicks = burn;
        wrecked = isWrecked;
    }

    public float[] zoneHealthArray() { return zoneHealth.clone(); }
}
