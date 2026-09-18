package gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(name = "RecaudacionMensualResponse", description = "Recaudacion de un mes calendario")
public record RecaudacionMensualResponse(

        @Schema(description = "Mes en formato AAAA-MM", example = "2026-04")
        String periodo,

        @Schema(description = "Anio del periodo", example = "2026")
        int anio,

        @Schema(description = "Mes del periodo, de 1 a 12", example = "4")
        int mes,

        @Schema(description = "Pagos registrados en el mes (0 si no hubo)", example = "2")
        long cantidadPagos,

        @Schema(description = "Suma de los pagos del mes", example = "12450.75")
        BigDecimal monto) {
}
