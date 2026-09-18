package gt.gob.chn.prestamos.domain.port.in.command;

import java.time.LocalDate;

public record RegistrarClienteCommand(
        String nombre,
        String apellido,
        String numeroIdentificacion,
        LocalDate fechaNacimiento,
        String direccion,
        String correoElectronico,
        String telefono) {
}
