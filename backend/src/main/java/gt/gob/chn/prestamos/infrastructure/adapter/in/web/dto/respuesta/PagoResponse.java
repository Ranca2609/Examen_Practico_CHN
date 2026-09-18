package gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(name = "PagoResponse", description = "Datos de un pago registrado")
public record PagoResponse(

        @Schema(description = "Identificador del pago", example = "1")
        Long id,

        @Schema(description = "Numero correlativo del recibo", example = "RC-001-2026-000001-4")
        String numeroRecibo,

        @Schema(description = "Identificador del prestamo abonado", example = "1")
        Long prestamoId,

        @Schema(description = "Numero del prestamo abonado", example = "PR-001-2026-000001-9")
        String numeroPrestamo,

        @Schema(description = "Identificador del cliente", example = "1")
        Long clienteId,

        @Schema(description = "Nombre completo del cliente", example = "Maria Jose Ramirez Lopez")
        String nombreCliente,

        @Schema(description = "Monto del pago", example = "3133.01")
        BigDecimal monto,

        @Schema(description = "Fecha y hora del pago", example = "2026-03-10T10:22:00")
        LocalDateTime fechaPago,

        @Schema(description = "Forma de pago", allowableValues = {"EFECTIVO"}, example = "EFECTIVO")
        String formaPago,

        @Schema(description = "Saldo antes de aplicar el pago", example = "150384.48")
        BigDecimal saldoAnterior,

        @Schema(description = "Saldo despues de aplicar el pago", example = "147251.47")
        BigDecimal saldoPosterior,

        @Schema(description = "Usuario que registro el pago", example = "cajero")
        String usuarioRegistro,

        @Schema(description = "Observaciones del cajero",
                example = "Pago de cuota correspondiente a enero")
        String observaciones) {
}
