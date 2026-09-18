package gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.peticion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "ActualizarClienteRequest", description = "Datos modificables de un cliente")
public record ActualizarClienteRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(min = 2, max = 60, message = "El nombre debe tener entre 2 y 60 caracteres")
        @Schema(description = "Nombres del cliente", example = "Maria Jose")
        String nombre,

        @NotBlank(message = "El apellido es obligatorio")
        @Size(min = 2, max = 60, message = "El apellido debe tener entre 2 y 60 caracteres")
        @Schema(description = "Apellidos del cliente", example = "Ramirez de Leon")
        String apellido,

        @NotBlank(message = "La direccion es obligatoria")
        @Size(min = 5, max = 200, message = "La direccion debe tener entre 5 y 200 caracteres")
        @Schema(description = "Direccion de residencia",
                example = "8a Calle 3-22 Zona 1, Mixco")
        String direccion,

        @NotBlank(message = "El correo electronico es obligatorio")
        @Email(message = "El correo electronico no tiene un formato valido")
        @Size(max = 120, message = "El correo electronico no puede exceder 120 caracteres")
        @Schema(description = "Correo electronico unico del cliente",
                example = "maria.deleon@correo.com.gt")
        String correoElectronico,

        @NotBlank(message = "El telefono es obligatorio")
        @Pattern(regexp = "\\d{8}", message = "El telefono debe tener exactamente 8 digitos")
        @Schema(description = "Telefono de contacto (8 digitos)", example = "44887766")
        String telefono) {
}
