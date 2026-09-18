package gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(name = "ClienteResponse", description = "Datos de un cliente")
public record ClienteResponse(

        @Schema(description = "Identificador del cliente", example = "1")
        Long id,

        @Schema(description = "Nombres", example = "Maria Jose")
        String nombre,

        @Schema(description = "Apellidos", example = "Ramirez Lopez")
        String apellido,

        @Schema(description = "Nombre y apellido concatenados", example = "Maria Jose Ramirez Lopez")
        String nombreCompleto,

        @Schema(description = "DPI del cliente", example = "2547896320101")
        String numeroIdentificacion,

        @Schema(description = "Fecha de nacimiento", example = "1992-04-18")
        LocalDate fechaNacimiento,

        @Schema(description = "Edad cumplida en anios", example = "34")
        int edad,

        @Schema(description = "Direccion de residencia",
                example = "5a Avenida 12-45 Zona 10, Ciudad de Guatemala")
        String direccion,

        @Schema(description = "Correo electronico", example = "maria.ramirez@correo.com.gt")
        String correoElectronico,

        @Schema(description = "Telefono de contacto", example = "55123456")
        String telefono,

        @Schema(description = "Indica si el cliente esta activo", example = "true")
        boolean activo,

        @Schema(description = "Fecha de creacion del registro", example = "2026-01-15T09:30:00")
        LocalDateTime fechaCreacion,

        @Schema(description = "Fecha de la ultima modificacion", example = "2026-02-02T14:10:00")
        LocalDateTime fechaModificacion) {
}
