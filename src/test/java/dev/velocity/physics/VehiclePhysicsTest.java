package dev.velocity.physics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.velocity.vehicle.VehicleDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class VehiclePhysicsTest {
    private static final float DT = 0.05f;
    private VehicleDefinition def;
    private VehicleState state;
    private VehiclePhysics physics;

    @BeforeEach
    void setUp() throws Exception {
        try (var in = getClass().getResourceAsStream("/data/velocity/velocity_vehicles/aurion_vx.json")) {
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            def = VehicleDefinition.fromJson("velocity:aurion_vx", json);
        }
        state = new VehicleState();
        state.engineOn = true;
        state.fuelLiters = 62f;
        physics = new VehiclePhysics(state);
    }

    private float run(VehicleInput in, float seconds, float grip) {
        for (int i = 0; i < (int) (seconds / DT); i++) physics.step(def, in, DT, grip, 0f);
        return state.speed;
    }

    @Test
    void zeroTo100IsPlausibleForSportsCar() {
        float t = 0;
        VehicleInput full = new VehicleInput(1, 0, 0, false);
        while (state.speed < 100 / 3.6f && t < 30) { physics.step(def, full, DT, 1f, 0f); t += DT; }
        System.out.printf("0-100 km/h: %.2f s, marcha %d%n", t, state.gear);
        assertTrue(t > 3.0f && t < 8.0f, "0-100 fora da faixa plausível: " + t);
    }

    @Test
    void topSpeedIsBoundedByDragAndRedline() {
        float v = run(new VehicleInput(1, 0, 0, false), 120, 1f);
        System.out.printf("Vel. máx.: %.1f km/h, marcha %d, rpm %.0f%n", v * 3.6f, state.gear, state.rpm);
        assertTrue(v * 3.6f > 200 && v * 3.6f < 330, "velocidade máxima implausível: " + v * 3.6f);
        assertTrue(state.rpm <= def.engine().redlineRpm() + 1);
    }

    @Test
    void brakingStopsAndNeverReverses() {
        run(new VehicleInput(1, 0, 0, false), 6, 1f);
        float t = 0;
        while (state.speed > 0 && t < 20) { physics.step(def, new VehicleInput(0, 1, 0, false), DT, 1f, 0f); t += DT; }
        assertEquals(0f, state.speed, 1e-4);
        assertTrue(t < 6f, "frenagem lenta demais: " + t);
    }

    @Test
    void iceGivesLessAccelerationThanAsphalt() {
        float asphalt = run(new VehicleInput(1, 0, 0, false), 2, 1f);
        setUp0();
        float ice = run(new VehicleInput(1, 0, 0, false), 2, SurfaceGripConstants.ICE);
        assertTrue(ice < asphalt * 0.5f, "gelo=" + ice + " asfalto=" + asphalt);
    }

    @Test
    void reverseEngagesFromStandstill() {
        run(new VehicleInput(0, 1, 0, false), 3, 1f);
        assertEquals(-1, state.gear);
        assertTrue(state.speed < -1f);
    }

    @Test
    void brakePedalWithEngineOffLightsBrakeAndDoesNotEngageReverse() {
        state.engineOn = false;
        physics.step(def, new VehicleInput(0, 1, 0, false), DT, 1f, 0f);
        assertTrue(state.braking);
        assertEquals(0, state.gear);
    }

    @Test
    void engineOffProducesNoThrust() {
        state.engineOn = false;
        assertEquals(0f, run(new VehicleInput(1, 0, 0, false), 3, 1f), 1e-4);
    }

    @Test
    void consumesFuelAndStallsWhenEmpty() {
        state.fuelLiters = 0.01f;
        run(new VehicleInput(1, 0, 0, false), 30, 1f);
        assertEquals(0f, state.fuelLiters, 1e-6);
        assertFalse(state.engineOn);
    }

    @Test
    void steeringTurnsAndIsLimitedByGrip() {
        run(new VehicleInput(1, 0, 0, false), 5, 1f);
        physics.step(def, new VehicleInput(0, 0, 1, false), DT, 1f, 0f);
        for (int i = 0; i < 20; i++) physics.step(def, new VehicleInput(0, 0, 1, false), DT, 1f, 0f);
        assertTrue(state.yawRateDeg > 0);
        float maxYawDeg = (float) Math.toDegrees(def.chassis().tireGrip() * 9.81f / Math.abs(state.speed));
        assertTrue(state.yawRateDeg <= maxYawDeg + 1e-3);
    }

    private void setUp0() {
        state.speed = 0; state.rpm = 0; state.gear = 0; state.steerDeg = 0;
    }

    /** Evita carregar classes do Minecraft (BlockState) em teste unitário. */
    static final class SurfaceGripConstants { static final float ICE = 0.12f; }
}
