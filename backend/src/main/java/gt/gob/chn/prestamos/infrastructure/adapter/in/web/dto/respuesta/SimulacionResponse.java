package gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SimulacionResponse", description = "Resultado de la simulacion de un prestamo")
public record SimulacionResponse(

        @Schema(description = "Evaluacion de capacidad de pago")
        EvaluacionResponse evaluacion,

        @Schema(description = "Plan de amortizacion proyectado")
        PlanAmortizacionResponse plan) {
}
