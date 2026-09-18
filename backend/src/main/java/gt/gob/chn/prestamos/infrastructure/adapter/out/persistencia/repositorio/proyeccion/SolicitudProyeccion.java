package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion;

import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.entidad.SolicitudPrestamoEntidad;

public record SolicitudProyeccion(
        SolicitudPrestamoEntidad solicitud,
        String nombreCliente,
        String apellidoCliente,
        String identificacionCliente) {
}
