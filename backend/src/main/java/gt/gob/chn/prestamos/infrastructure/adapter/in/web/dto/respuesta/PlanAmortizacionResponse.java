package gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(name = "PlanAmortizacionResponse", description = "Tabla de amortizacion del prestamo")
public record PlanAmortizacionResponse(

        @Schema(description = "Cuota mensual fija", example = "3133.01")
        BigDecimal cuotaMensual,

        @Schema(description = "Total de intereses del plazo completo", example = "30384.48")
        BigDecimal totalIntereses,

        @Schema(description = "Monto total a pagar (capital mas intereses)", example = "150384.48")
        BigDecimal montoTotal,

        @Schema(description = "Detalle cuota por cuota")
        List<CuotaResponse> cuotas) {
}
