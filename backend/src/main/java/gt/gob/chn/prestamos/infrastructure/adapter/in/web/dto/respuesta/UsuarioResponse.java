package gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UsuarioResponse", description = "Datos del usuario autenticado")
public record UsuarioResponse(

        @Schema(description = "Identificador del usuario", example = "1")
        Long id,

        @Schema(description = "Nombre de usuario", example = "admin")
        String username,

        @Schema(description = "Nombre completo", example = "Administrador del Sistema")
        String nombreCompleto,

        @Schema(description = "Correo institucional", example = "admin@chn.com.gt")
        String correo,

        @Schema(description = "Rol asignado",
                allowableValues = {"ADMIN", "ANALISTA", "CAJERO", "CONSULTA"}, example = "ADMIN")
        String rol) {
}
