package gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(name = "SolicitudResponse", description = "Datos de una solicitud de prestamo")
public record SolicitudResponse(

        @Schema(description = "Identificador de la solicitud", example = "1")
        Long id,

        @Schema(description = "Numero correlativo de la solicitud", example = "SC-001-2026-000001-3")
        String numeroSolicitud,

        @Schema(description = "Identificador del cliente solicitante", example = "1")
        Long clienteId,

        @Schema(description = "Nombre completo del cliente", example = "Maria Jose Ramirez Lopez")
        String nombreCliente,

        @Schema(description = "DPI del cliente", example = "2547896320101")
        String identificacionCliente,

        @Schema(description = "Monto solicitado", example = "150000.00")
        BigDecimal montoSolicitado,

        @Schema(description = "Plazo solicitado en meses", example = "60")
        int plazoMeses,

        @Schema(description = "Tasa de interes anual solicitada", example = "12.50")
        BigDecimal tasaInteresAnual,

        @Schema(description = "Tipo de prestamo",
                allowableValues = {"PERSONAL", "HIPOTECARIO", "VEHICULAR", "EMPRESARIAL", "EDUCATIVO"},
                example = "PERSONAL")
        String tipoPrestamo,

        @Schema(description = "Destino del financiamiento",
                example = "Remodelacion de vivienda familiar")
        String destino,

        @Schema(description = "Ingreso mensual declarado", example = "12500.00")
        BigDecimal ingresoMensualDeclarado,

        @Schema(description = "Estado de la solicitud",
                allowableValues = {"EN_PROCESO", "APROBADA", "RECHAZADA"}, example = "EN_PROCESO")
        String estado,

        @Schema(description = "Fecha de registro de la solicitud", example = "2026-02-05T08:45:00")
        LocalDateTime fechaSolicitud,

        @Schema(description = "Observaciones del asesor",
                example = "Cliente con historial crediticio favorable")
        String observaciones,

        @Schema(description = "Resolucion; nula mientras la solicitud este EN_PROCESO")
        ResolucionResponse resolucion) {
}
