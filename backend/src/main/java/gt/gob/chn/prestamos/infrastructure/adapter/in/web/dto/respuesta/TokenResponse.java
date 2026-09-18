package gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TokenResponse", description = "Token JWT y datos del usuario autenticado")
public record TokenResponse(

        @Schema(description = "Token JWT firmado (HS256)",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.firma")
        String token,

        @Schema(description = "Tipo de token", example = "Bearer")
        String tipo,

        @Schema(description = "Vigencia del token en segundos", example = "28800")
        long expiraEnSegundos,

        @Schema(description = "Usuario propietario del token")
        UsuarioResponse usuario) {
}
