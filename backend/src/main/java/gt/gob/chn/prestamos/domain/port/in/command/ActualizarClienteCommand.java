package gt.gob.chn.prestamos.domain.port.in.command;

public record ActualizarClienteCommand(
        String nombre,
        String apellido,
        String direccion,
        String correoElectronico,
        String telefono) {
}
