package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion;

import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.entidad.PrestamoEntidad;

public record PrestamoProyeccion(
        PrestamoEntidad prestamo,
        String numeroSolicitud,
        String nombreCliente,
        String apellidoCliente,
        String identificacionCliente) {
}
