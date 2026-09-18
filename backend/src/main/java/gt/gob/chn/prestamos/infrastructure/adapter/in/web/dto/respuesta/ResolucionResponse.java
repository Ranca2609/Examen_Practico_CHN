package gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(name = "ResolucionResponse", description = "Detalle de la resolucion de una solicitud")
public record ResolucionResponse(

        @Schema(description = "Momento en que se resolvio la solicitud",
                example = "2026-02-10T11:05:00")
        LocalDateTime fechaResolucion,

        @Schema(description = "Usuario que resolvio la solicitud", example = "analista")
        String usuarioResolucion,

        @Schema(description = "Monto aprobado; nulo si fue rechazada", example = "120000.00")
        BigDecimal montoAprobado,

        @Schema(description = "Plazo aprobado en meses; nulo si fue rechazada", example = "48")
        Integer plazoAprobadoMeses,

        @Schema(description = "Tasa anual aprobada; nula si fue rechazada", example = "11.75")
        BigDecimal tasaAprobada,

        @Schema(description = "Motivo registrado por el analista",
                example = "Capacidad de pago suficiente y garantia verificada")
        String motivo) {
}
