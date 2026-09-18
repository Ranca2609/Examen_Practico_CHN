package gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "PrestamoResponse", description = "Datos de un prestamo y su avance de pago")
public record PrestamoResponse(

        @Schema(description = "Identificador del prestamo", example = "1")
        Long id,

        @Schema(description = "Numero correlativo del prestamo", example = "PR-001-2026-000001-9")
        String numeroPrestamo,

        @Schema(description = "Identificador de la solicitud que lo origino", example = "1")
        Long solicitudId,

        @Schema(description = "Numero de la solicitud que lo origino", example = "SC-001-2026-000001-3")
        String numeroSolicitud,

        @Schema(description = "Identificador del cliente", example = "1")
        Long clienteId,

        @Schema(description = "Nombre completo del cliente", example = "Maria Jose Ramirez Lopez")
        String nombreCliente,

        @Schema(description = "DPI del cliente", example = "2547896320101")
        String identificacionCliente,

        @Schema(description = "Monto aprobado y desembolsado", example = "120000.00")
        BigDecimal montoAprobado,

        @Schema(description = "Plazo en meses", example = "48")
        int plazoMeses,

        @Schema(description = "Tasa de interes anual aplicada", example = "11.75")
        BigDecimal tasaInteresAnual,

        @Schema(description = "Cuota mensual calculada por el sistema frances", example = "3133.01")
        BigDecimal cuotaMensual,

        @Schema(description = "Monto total a pagar (capital mas intereses)", example = "150384.48")
        BigDecimal montoTotalAPagar,

        @Schema(description = "Suma de los pagos aplicados", example = "9399.03")
        BigDecimal totalPagado,

        @Schema(description = "Saldo pendiente de pago", example = "140985.45")
        BigDecimal saldoPendiente,

        @Schema(description = "Porcentaje del total ya pagado", example = "6.25")
        BigDecimal porcentajePagado,

        @Schema(description = "Estado del prestamo",
                allowableValues = {"VIGENTE", "LIQUIDADO"}, example = "VIGENTE")
        String estado,

        @Schema(description = "Fecha de desembolso", example = "2026-02-10")
        LocalDate fechaDesembolso,

        @Schema(description = "Fecha de vencimiento del ultimo pago", example = "2030-02-10")
        LocalDate fechaVencimiento) {
}
