package gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.peticion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "LoginRequest", description = "Credenciales para obtener un token de acceso JWT")
public record LoginRequest(

        @NotBlank(message = "El usuario es obligatorio")
        @Size(max = 50, message = "El usuario no puede exceder 50 caracteres")
        @Schema(description = "Nombre de usuario", example = "admin")
        String username,

        @NotBlank(message = "La contrasena es obligatoria")
        @Size(min = 8, max = 100, message = "La contrasena debe tener entre 8 y 100 caracteres")
        @Schema(description = "Contrasena del usuario", example = "Chn2026*Demo")
        String contrasena) {
}
