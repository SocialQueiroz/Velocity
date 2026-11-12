package dev.velocity.vehicle;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Definição técnica imutável de um veículo, carregada de JSON (data pack:
 * data/&lt;namespace&gt;/velocity_vehicles/*.json). Toda a lógica de física lê
 * apenas daqui, para que novos veículos não exijam código novo.
 *
 * Unidades SI: kg, m, N·m, rpm, segundos.
 */
public record VehicleDefinition(
        String id,
        String displayName,
        String manufacturer,
        String category,
        int seats,
        Dimensions dimensions,
        Engine engine,
        Transmission transmission,
        Chassis chassis,
        Fuel fuel,
        java.util.List<InteractionZone> interactionZones) {

    /**
     * Região clicável no espaço do veículo (metros; +X esquerda, +Y cima, +Z frente).
     * action: "door" (abre/fecha; com a porta aberta, entra no assento 'seat'), "hood", "trunk".
     */
    public record InteractionZone(String id, String action, float[] min, float[] max, int seat) {
        public boolean contains(double x, double y, double z, double tolerance) {
            return x >= min[0] - tolerance && x <= max[0] + tolerance && y >= min[1] - tolerance && y <= max[1] + tolerance
                    && z >= min[2] - tolerance && z <= max[2] + tolerance;
        }
    }

    public record Dimensions(float length, float width, float height, float wheelbase, float wheelRadius) {}

    /** Curva de torque simplificada: sobe de idle até peakTorqueRpm, platô, cai até redline. */
    public record Engine(String layout, float idleRpm, float peakTorqueNm, float peakTorqueRpm,
                         float peakPowerRpm, float redlineRpm, float engineBrakeNm) {
        public float torqueAt(float rpm) {
            if (rpm <= idleRpm) return peakTorqueNm * 0.55f;
            if (rpm < peakTorqueRpm) {
                float t = (rpm - idleRpm) / (peakTorqueRpm - idleRpm);
                return peakTorqueNm * (0.55f + 0.45f * t);
            }
            if (rpm <= peakPowerRpm) return peakTorqueNm;
            if (rpm >= redlineRpm) return 0f; // corte de giro
            float t = (rpm - peakPowerRpm) / (redlineRpm - peakPowerRpm);
            return peakTorqueNm * (1f - 0.35f * t);
        }
    }

    public record Transmission(float[] gearRatios, float reverseRatio, float finalDrive,
                               float efficiency, float upshiftRpm, float downshiftRpm) {
        public int forwardGears() { return gearRatios.length; }
        /** gear: -1 ré, 0 neutro, 1..n marchas à frente. */
        public float ratio(int gear) {
            if (gear < 0) return -reverseRatio;
            if (gear == 0) return 0f;
            return gearRatios[Math.min(gear, gearRatios.length) - 1];
        }
    }

    public record Chassis(float massKg, float dragCoefficient, float frontalAreaM2, float rollingResistance,
                          float tireGrip, float maxBrakeForceN, float maxSteerDeg, float steerSpeedFalloff) {}

    public record Fuel(float capacityLiters, float consumptionPerKwh) {}

    // ---------------------------------------------------------------- JSON

    public static VehicleDefinition fromJson(String id, JsonObject o) {
        JsonObject d = o.getAsJsonObject("dimensions");
        JsonObject e = o.getAsJsonObject("engine");
        JsonObject t = o.getAsJsonObject("transmission");
        JsonObject c = o.getAsJsonObject("chassis");
        JsonObject f = o.getAsJsonObject("fuel");

        JsonArray gears = t.getAsJsonArray("gear_ratios");
        float[] ratios = new float[gears.size()];
        for (int i = 0; i < ratios.length; i++) ratios[i] = gears.get(i).getAsFloat();
        if (ratios.length == 0) throw new IllegalArgumentException("gear_ratios vazio em " + id);

        VehicleDefinition def = new VehicleDefinition(
                id,
                o.get("display_name").getAsString(),
                o.get("manufacturer").getAsString(),
                o.get("category").getAsString(),
                o.get("seats").getAsInt(),
                new Dimensions(d.get("length").getAsFloat(), d.get("width").getAsFloat(), d.get("height").getAsFloat(),
                        d.get("wheelbase").getAsFloat(), d.get("wheel_radius").getAsFloat()),
                new Engine(e.get("layout").getAsString(), e.get("idle_rpm").getAsFloat(),
                        e.get("peak_torque_nm").getAsFloat(), e.get("peak_torque_rpm").getAsFloat(),
                        e.get("peak_power_rpm").getAsFloat(), e.get("redline_rpm").getAsFloat(),
                        e.get("engine_brake_nm").getAsFloat()),
                new Transmission(ratios, t.get("reverse_ratio").getAsFloat(), t.get("final_drive").getAsFloat(),
                        t.get("efficiency").getAsFloat(), t.get("upshift_rpm").getAsFloat(),
                        t.get("downshift_rpm").getAsFloat()),
                new Chassis(c.get("mass_kg").getAsFloat(), c.get("drag_coefficient").getAsFloat(),
                        c.get("frontal_area_m2").getAsFloat(), c.get("rolling_resistance").getAsFloat(),
                        c.get("tire_grip").getAsFloat(), c.get("max_brake_force_n").getAsFloat(),
                        c.get("max_steer_deg").getAsFloat(), c.get("steer_speed_falloff").getAsFloat()),
                new Fuel(f.get("capacity_liters").getAsFloat(), f.get("consumption_l_per_kwh").getAsFloat()),
                parseZones(o));
        def.validate();
        return def;
    }

    private static java.util.List<InteractionZone> parseZones(JsonObject o) {
        if (!o.has("interaction_zones")) return java.util.List.of();
        java.util.List<InteractionZone> list = new java.util.ArrayList<>();
        for (var el : o.getAsJsonArray("interaction_zones")) {
            JsonObject z = el.getAsJsonObject();
            list.add(new InteractionZone(z.get("id").getAsString(), z.get("action").getAsString(),
                    vec3(z.getAsJsonArray("min")), vec3(z.getAsJsonArray("max")), z.has("seat") ? z.get("seat").getAsInt() : -1));
        }
        return java.util.List.copyOf(list);
    }

    private static float[] vec3(JsonArray a) {
        if (a.size() != 3) throw new IllegalArgumentException("vetor precisa de 3 valores");
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    public void validate() {
        if (seats < 1) throw new IllegalArgumentException(id + ": seats < 1");
        if (chassis.massKg() <= 0) throw new IllegalArgumentException(id + ": massa inválida");
        if (dimensions.wheelRadius() <= 0 || dimensions.wheelbase() <= 0)
            throw new IllegalArgumentException(id + ": dimensões inválidas");
        if (engine.redlineRpm() <= engine.idleRpm())
            throw new IllegalArgumentException(id + ": redline <= idle");
    }
}
