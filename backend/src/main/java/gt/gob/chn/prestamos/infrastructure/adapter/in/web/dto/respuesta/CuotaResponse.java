package gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(name = "CuotaResponse", description = "Fila del plan de amortizacion")
public record CuotaResponse(

        @Schema(description = "Numero de cuota", example = "1")
        int numero,

        @Schema(description = "Saldo al inicio del periodo", example = "120000.00")
        BigDecimal saldoInicial,

        @Schema(description = "Cuota total del periodo", example = "3133.01")
        BigDecimal cuota,

        @Schema(description = "Porcion de la cuota aplicada a capital", example = "1958.01")
        BigDecimal abonoCapital,

        @Schema(description = "Porcion de la cuota aplicada a intereses", example = "1175.00")
        BigDecimal abonoInteres,

        @Schema(description = "Saldo al cierre del periodo", example = "118041.99")
        BigDecimal saldoFinal) {
}
