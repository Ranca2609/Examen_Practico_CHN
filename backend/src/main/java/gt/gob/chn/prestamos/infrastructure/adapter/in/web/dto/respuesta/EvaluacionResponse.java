package gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(name = "EvaluacionResponse", description = "Evaluacion de capacidad de pago del cliente")
public record EvaluacionResponse(

        @Schema(description = "Porcentaje del ingreso mensual que compromete la cuota",
                example = "25.06")
        BigDecimal porcentajeComprometido,

        @Schema(description = "Indica si el credito es recomendable segun la politica",
                example = "true")
        boolean recomendado,

        @Schema(description = "Explicacion del resultado",
                example = "La cuota compromete 25.06% del ingreso declarado, dentro del limite de 40%")
        String observacion,

        @Schema(description = "Prestamos vigentes que ya tiene el cliente", example = "1")
        long prestamosVigentes) {
}
