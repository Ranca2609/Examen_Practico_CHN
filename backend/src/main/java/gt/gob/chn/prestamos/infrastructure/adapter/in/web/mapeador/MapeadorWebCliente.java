package gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador;

import gt.gob.chn.prestamos.domain.model.Cliente;
import gt.gob.chn.prestamos.domain.port.in.command.ActualizarClienteCommand;
import gt.gob.chn.prestamos.domain.port.in.command.RegistrarClienteCommand;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.peticion.ActualizarClienteRequest;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.peticion.ClienteRequest;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.ClienteResponse;
import java.time.LocalDate;
import java.time.ZoneId;

public final class MapeadorWebCliente {

    // La edad se calcula con la fecha de Guatemala, no con la zona del servidor.
    private static final ZoneId ZONA_GUATEMALA = ZoneId.of("America/Guatemala");

    private MapeadorWebCliente() {
    }

    public static RegistrarClienteCommand aComando(ClienteRequest peticion) {
        return new RegistrarClienteCommand(
                peticion.nombre(),
                peticion.apellido(),
                peticion.numeroIdentificacion(),
                peticion.fechaNacimiento(),
                peticion.direccion(),
                peticion.correoElectronico(),
                peticion.telefono());
    }

    public static ActualizarClienteCommand aComando(ActualizarClienteRequest peticion) {
        return new ActualizarClienteCommand(
                peticion.nombre(),
                peticion.apellido(),
                peticion.direccion(),
                peticion.correoElectronico(),
                peticion.telefono());
    }

    public static ClienteResponse aRespuesta(Cliente cliente) {
        return new ClienteResponse(
                cliente.getId(),
                cliente.getNombre(),
                cliente.getApellido(),
                cliente.nombreCompleto(),
                cliente.getNumeroIdentificacion(),
                cliente.getFechaNacimiento(),
                cliente.edad(LocalDate.now(ZONA_GUATEMALA)),
                cliente.getDireccion(),
                cliente.getCorreoElectronico(),
                cliente.getTelefono(),
                cliente.isActivo(),
                cliente.getFechaCreacion(),
                cliente.getFechaModificacion());
    }
}
