package gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.peticion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(name = "ClienteRequest", description = "Datos para el registro de un nuevo cliente")
public record ClienteRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(min = 2, max = 60, message = "El nombre debe tener entre 2 y 60 caracteres")
        @Schema(description = "Nombres del cliente", example = "Maria Jose")
        String nombre,

        @NotBlank(message = "El apellido es obligatorio")
        @Size(min = 2, max = 60, message = "El apellido debe tener entre 2 y 60 caracteres")
        @Schema(description = "Apellidos del cliente", example = "Ramirez Lopez")
        String apellido,

        @NotBlank(message = "El numero de identificacion (DPI) es obligatorio")
        @Pattern(regexp = "\\d{13}",
                message = "El numero de identificacion (DPI) debe tener exactamente 13 digitos")
        @Schema(description = "DPI del cliente (13 digitos)", example = "2547896320101")
        String numeroIdentificacion,

        @NotNull(message = "La fecha de nacimiento es obligatoria")
        @Past(message = "La fecha de nacimiento debe ser anterior a la fecha actual")
        @Schema(description = "Fecha de nacimiento (ISO-8601). El cliente debe ser mayor de edad",
                example = "1992-04-18")
        LocalDate fechaNacimiento,

        @NotBlank(message = "La direccion es obligatoria")
        @Size(min = 5, max = 200, message = "La direccion debe tener entre 5 y 200 caracteres")
        @Schema(description = "Direccion de residencia",
                example = "5a Avenida 12-45 Zona 10, Ciudad de Guatemala")
        String direccion,

        @NotBlank(message = "El correo electronico es obligatorio")
        @Email(message = "El correo electronico no tiene un formato valido")
        @Size(max = 120, message = "El correo electronico no puede exceder 120 caracteres")
        @Schema(description = "Correo electronico unico del cliente",
                example = "maria.ramirez@correo.com.gt")
        String correoElectronico,

        @NotBlank(message = "El telefono es obligatorio")
        @Pattern(regexp = "\\d{8}", message = "El telefono debe tener exactamente 8 digitos")
        @Schema(description = "Telefono de contacto (8 digitos)", example = "55123456")
        String telefono) {
}
