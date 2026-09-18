package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.mapeador;

import gt.gob.chn.prestamos.domain.model.Cliente;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.entidad.ClienteEntidad;
import org.springframework.stereotype.Component;

@Component
public class MapeadorCliente {

    public Cliente aDominio(ClienteEntidad entidad) {
        if (entidad == null) {
            return null;
        }
        return Cliente.reconstituir(
                entidad.getId(),
                entidad.getNombre(),
                entidad.getApellido(),
                entidad.getNumeroIdentificacion(),
                entidad.getFechaNacimiento(),
                entidad.getDireccion(),
                entidad.getCorreoElectronico(),
                entidad.getTelefono(),
                entidad.isActivo(),
                entidad.getFechaCreacion(),
                entidad.getFechaModificacion());
    }

    public ClienteEntidad aEntidad(Cliente cliente) {
        return new ClienteEntidad(
                cliente.getId(),
                cliente.getNombre(),
                cliente.getApellido(),
                cliente.getNumeroIdentificacion(),
                cliente.getFechaNacimiento(),
                cliente.getDireccion(),
                cliente.getCorreoElectronico(),
                cliente.getTelefono(),
                cliente.isActivo(),
                cliente.getFechaCreacion(),
                cliente.getFechaModificacion());
    }
}
