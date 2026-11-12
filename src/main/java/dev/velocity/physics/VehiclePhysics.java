package dev.velocity.physics;

import dev.velocity.vehicle.VehicleDefinition;

/**
 * Modelo longitudinal + modelo de bicicleta para esterçamento. Independente do Minecraft
 * (testável em JUnit). Executado apenas no servidor (autoridade).
 *
 * Forças: tração (curva de torque × relação × diferencial × eficiência / raio da roda),
 * limitada pela aderência μ·m·g; arrasto ½·ρ·Cd·A·v²; resistência ao rolamento Crr·m·g;
 * freio; freio-motor; componente da gravidade na rampa.
 */
public final class VehiclePhysics {
    public static final float G = 9.81f;
    public static final float AIR_DENSITY = 1.225f;
    private static final float RAD_S_TO_RPM = 60f / (2f * (float) Math.PI);

    private final VehicleState state;

    public VehiclePhysics(VehicleState state) {
        this.state = state;
    }

    /**
     * @param surfaceGrip multiplicador de aderência do bloco sob o veículo (1 = asfalto)
     * @param slopeSin seno da inclinação na direção do movimento (positivo = subida)
     */
    public void step(VehicleDefinition def, VehicleInput rawIn, float dt, float surfaceGrip, float slopeSin) {
        VehicleInput in = rawIn.sanitized();
        VehicleDefinition.Chassis ch = def.chassis();
        VehicleDefinition.Transmission tr = def.transmission();
        VehicleDefinition.Engine en = def.engine();
        float m = ch.massKg();
        float v = state.speed;
        float wheelR = def.dimensions().wheelRadius();

        boolean hasFuel = state.fuelLiters > 0f;
        boolean running = state.engineOn && hasFuel;
        if (!hasFuel) state.engineOn = false;

        // --- Seleção de marcha: freio parado engata ré; acelerador parado engata 1ª.
        if (running && Math.abs(v) < 0.3f) {
            if (in.brake() > 0.1f && in.throttle() < 0.05f && state.gear >= 0) state.gear = -1;
            else if (in.throttle() > 0.1f && state.gear <= 0) state.gear = 1;
        }
        // Em ré, "freio" funciona como acelerador para trás e "acelerador" como freio.
        float drive = state.gear < 0 ? in.brake() : in.throttle();
        float brake = state.gear < 0 ? in.throttle() : in.brake();
        state.braking = brake > 0.05f || in.handbrake();

        // --- RPM a partir da velocidade das rodas.
        float ratio = tr.ratio(state.gear) * tr.finalDrive();
        float wheelRpm = Math.abs(v) / wheelR * RAD_S_TO_RPM;
        float coupledRpm = wheelRpm * Math.abs(ratio);
        float targetRpm;
        if (!running) targetRpm = 0f;
        else if (state.gear == 0) targetRpm = en.idleRpm() + drive * (en.redlineRpm() - en.idleRpm());
        else targetRpm = Math.max(en.idleRpm(), coupledRpm);
        state.rpm += (targetRpm - state.rpm) * Math.min(1f, dt * 12f);

        // --- Câmbio automático (Fase 1). Manual previsto na Fase 3.
        if (running && state.gear > 0) {
            if (state.rpm > tr.upshiftRpm() && state.gear < tr.forwardGears()) state.gear++;
            else if (state.rpm < tr.downshiftRpm() && state.gear > 1) state.gear--;
        }

        // --- Forças longitudinais (positivo = para frente do veículo).
        float maxTraction = ch.tireGrip() * surfaceGrip * m * G;
        float fDrive = 0f;
        if (running && state.gear != 0) {
            float torque = en.torqueAt(state.rpm) * drive;
            fDrive = torque * ratio * tr.efficiency() / wheelR;
            if (drive < 0.05f && Math.abs(v) > 0.5f) // freio-motor
                fDrive -= Math.signum(v) * en.engineBrakeNm() * Math.abs(ratio) / wheelR;
        }
        float slipLong = maxTraction > 0 ? Math.abs(fDrive) / maxTraction - 1f : (Math.abs(fDrive) > 0 ? 1f : 0f);
        fDrive = clamp(fDrive, -maxTraction, maxTraction);

        float fDrag = 0.5f * AIR_DENSITY * ch.dragCoefficient() * ch.frontalAreaM2() * v * Math.abs(v);
        float fRoll = Math.abs(v) > 0.05f ? Math.signum(v) * ch.rollingResistance() * m * G : 0f;
        float fSlope = m * G * slopeSin;
        float brakeWanted = ch.maxBrakeForceN() * brake + (in.handbrake() ? ch.maxBrakeForceN() * 0.6f : 0f);
        float brakeTotal = Math.min(brakeWanted, maxTraction);
        float slipBrake = Math.abs(v) > 3f && maxTraction > 0 ? (brakeWanted / maxTraction - 0.9f) * 4f : 0f;

        float fNet = fDrive - fDrag - fRoll - fSlope;
        float newV = v + fNet / m * dt;
        // Freio nunca inverte o sentido do movimento.
        float brakeDv = brakeTotal / m * dt;
        if (Math.abs(newV) <= brakeDv) newV = 0f;
        else newV -= Math.signum(newV) * brakeDv;
        if (!running && Math.abs(newV) < 0.05f && slopeSin == 0f) newV = 0f;
        state.speed = newV;

        // --- Esterçamento (modelo de bicicleta) com redução em alta velocidade e limite lateral μg/v.
        float steerTarget = in.steer() * ch.maxSteerDeg() / (1f + Math.abs(newV) * ch.steerSpeedFalloff());
        state.steerDeg += (steerTarget - state.steerDeg) * Math.min(1f, dt * 8f);
        float yawRate = 0f;
        float slipLat = 0f;
        if (Math.abs(newV) > 0.05f) {
            yawRate = newV / def.dimensions().wheelbase() * (float) Math.tan(Math.toRadians(state.steerDeg));
            float maxYaw = maxTraction / m / Math.max(1f, Math.abs(newV));
            if (in.handbrake()) maxYaw *= 1.6f; // eixo traseiro travado facilita rotação
            slipLat = Math.abs(yawRate) > maxYaw * 0.85f ? (Math.abs(yawRate) / maxYaw - 0.85f) * 3f : 0f;
            yawRate = clamp(yawRate, -maxYaw, maxYaw);
        }
        state.yawRateDeg = (float) Math.toDegrees(yawRate);
        float slipHand = in.handbrake() && Math.abs(newV) > 4f ? 1f : 0f;
        float slip = Math.max(Math.max(slipLong * (Math.abs(newV) > 0.5f || drive > 0.5f ? 1f : 0f), slipBrake), Math.max(slipLat, slipHand));
        state.tireSlip = clamp(slip, 0f, 1f);
        state.load = running ? drive : 0f;

        // --- Combustível: potência entregue (kW) × consumo específico + marcha lenta.
        if (running) {
            float powerKw = Math.max(0f, en.torqueAt(state.rpm) * drive * state.rpm / RAD_S_TO_RPM / 1000f);
            float idleKw = 2f;
            state.fuelLiters = Math.max(0f, state.fuelLiters - (powerKw + idleKw) * def.fuel().consumptionPerKwh() * dt / 3600f);
        }
        state.odometerM += Math.abs(newV) * dt;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
