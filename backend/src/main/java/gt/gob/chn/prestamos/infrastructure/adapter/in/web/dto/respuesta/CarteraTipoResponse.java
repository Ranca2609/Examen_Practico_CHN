package gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(name = "CarteraTipoResponse", description = "Indicadores de la cartera de un tipo de prestamo")
public record CarteraTipoResponse(

        @Schema(description = "Tipo de prestamo",
                allowableValues = {"PERSONAL", "HIPOTECARIO", "VEHICULAR", "EMPRESARIAL", "EDUCATIVO"},
                example = "HIPOTECARIO")
        String tipoPrestamo,

        @Schema(description = "Prestamos desembolsados de este tipo (0 si aun no hay)", example = "1")
        long cantidadPrestamos,

        @Schema(description = "Suma de los montos aprobados", example = "850000.00")
        BigDecimal montoAprobado,

        @Schema(description = "Saldo pendiente de cobro (capital mas intereses)", example = "1334009.98")
        BigDecimal saldoPendiente,

        @Schema(description = "Total recuperado mediante pagos", example = "0.00")
        BigDecimal totalRecuperado) {
}
