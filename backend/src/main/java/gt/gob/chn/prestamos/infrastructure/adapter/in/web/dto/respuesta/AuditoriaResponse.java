package gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(name = "AuditoriaResponse", description = "Registro de auditoria del sistema")
public record AuditoriaResponse(

        @Schema(description = "Identificador del registro", example = "1")
        Long id,

        @Schema(description = "Usuario que ejecuto la operacion", example = "analista")
        String usuario,

        @Schema(description = "Accion ejecutada", example = "APROBAR_SOLICITUD")
        String accion,

        @Schema(description = "Entidad afectada", example = "SolicitudPrestamo")
        String entidad,

        @Schema(description = "Identificador de la entidad afectada", example = "1")
        String entidadId,

        @Schema(description = "Detalle de la operacion",
                example = "Solicitud SC-001-2026-000001-3 aprobada por 120000.00 a 48 meses")
        String detalle,

        @Schema(description = "Direccion IP de origen", example = "192.168.1.25")
        String direccionIp,

        @Schema(description = "Fecha y hora del registro", example = "2026-02-10T11:05:00")
        LocalDateTime fecha) {
}
