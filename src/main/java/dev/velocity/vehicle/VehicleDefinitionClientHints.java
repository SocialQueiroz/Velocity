package dev.velocity.vehicle;

/**
 * Parâmetros visuais mínimos necessários no cliente. Na Fase 1 são fixos; na Fase 2 serão
 * sincronizados do servidor junto com a definição (pacote de sincronização no login/reload).
 */
public record VehicleDefinitionClientHints(float wheelRadius, float redlineRpm, float fuelCapacity,
                                           float gaugeMaxRpm, float gaugeMaxKmh) {
    public static final VehicleDefinitionClientHints DEFAULT = new VehicleDefinitionClientHints(0.34f, 7800f, 62f, 8000f, 340f);
}
