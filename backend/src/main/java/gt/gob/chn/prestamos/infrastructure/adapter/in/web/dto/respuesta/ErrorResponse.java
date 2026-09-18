package gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(name = "ErrorResponse", description = "Respuesta de error uniforme de la API")
public record ErrorResponse(

        @Schema(description = "Momento en que se genero el error", example = "2026-02-10T11:05:00")
        LocalDateTime timestamp,

        @Schema(description = "Codigo de estado HTTP", example = "400")
        int estado,

        @Schema(description = "Codigo de negocio del error", example = "VALIDACION")
        String codigo,

        @Schema(description = "Mensaje legible para el usuario",
                example = "La peticion contiene campos invalidos")
        String mensaje,

        @Schema(description = "Ruta solicitada", example = "/api/v1/clientes")
        String ruta,

        @Schema(description = "Errores por campo; presente solo en fallos de validacion")
        List<ErrorCampo> errores) {

    public static ErrorResponse de(int estado, String codigo, String mensaje, String ruta) {
        return new ErrorResponse(LocalDateTime.now(), estado, codigo, mensaje, ruta, List.of());
    }

    public static ErrorResponse de(int estado, String codigo, String mensaje, String ruta,
                                   List<ErrorCampo> errores) {
        return new ErrorResponse(LocalDateTime.now(), estado, codigo, mensaje, ruta,
                errores == null ? List.of() : List.copyOf(errores));
    }
}
