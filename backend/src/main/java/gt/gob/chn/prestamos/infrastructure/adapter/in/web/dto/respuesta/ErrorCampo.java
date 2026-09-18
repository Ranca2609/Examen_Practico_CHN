package gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ErrorCampo", description = "Error de validacion de un campo")
public record ErrorCampo(

        @Schema(description = "Nombre del campo con error", example = "numeroIdentificacion")
        String campo,

        @Schema(description = "Mensaje de validacion",
                example = "El numero de identificacion (DPI) debe tener exactamente 13 digitos")
        String mensaje) {
}
