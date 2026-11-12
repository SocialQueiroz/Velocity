package dev.velocity.physics;

/** Estado mutável da simulação (servidor). Campos sincronizados ao cliente via DataTracker. */
public final class VehicleState {
    public float speed;       // m/s ao longo da direção do veículo (negativo = ré)
    public float rpm;
    public int gear;          // -1 ré, 0 neutro, 1..n
    public float steerDeg;    // ângulo das rodas dianteiras
    public float yawRateDeg;  // graus/s
    public float fuelLiters;
    public boolean engineOn;
    public boolean braking;
    public float powerFactor = 1f; // 0..1: potência disponível (dano no motor)
    public float load;        // carga do motor 0..1 (pedal efetivo com motor ligado) — áudio
    public float tireSlip;    // 0..1, saturação de aderência (longitudinal ou lateral) — áudio/efeitos   // pedal de freio efetivo (para a luz de freio)
    public double odometerM;
}
