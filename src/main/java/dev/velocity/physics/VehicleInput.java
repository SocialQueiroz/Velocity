package dev.velocity.physics;

/** Entrada normalizada do motorista. throttle/brake em [0,1], steer em [-1,1] (positivo = direita). */
public record VehicleInput(float throttle, float brake, float steer, boolean handbrake) {
    public static final VehicleInput NONE = new VehicleInput(0, 0, 0, false);

    public VehicleInput sanitized() {
        return new VehicleInput(clamp(throttle, 0, 1), clamp(brake, 0, 1), clamp(steer, -1, 1), handbrake);
    }

    private static float clamp(float v, float lo, float hi) {
        return Float.isNaN(v) ? 0f : Math.max(lo, Math.min(hi, v));
    }
}
