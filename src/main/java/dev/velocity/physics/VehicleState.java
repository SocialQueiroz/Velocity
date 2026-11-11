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
    public double odometerM;
}
