package gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.peticion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "RechazarSolicitudRequest", description = "Motivo del rechazo de la solicitud")
public record RechazarSolicitudRequest(

        @NotBlank(message = "El motivo del rechazo es obligatorio")
        @Size(min = 10, max = 500, message = "El motivo debe tener entre 10 y 500 caracteres")
        @Schema(description = "Detalle del motivo de rechazo",
                example = "La cuota mensual excede el 40 por ciento del ingreso declarado")
        String motivo) {
}
