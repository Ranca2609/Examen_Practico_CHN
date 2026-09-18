package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.mapeador;

import gt.gob.chn.prestamos.domain.model.Prestamo;
import gt.gob.chn.prestamos.domain.model.consulta.PrestamoDetalle;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.entidad.PrestamoEntidad;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion.PrestamoProyeccion;
import org.springframework.stereotype.Component;

@Component
public class MapeadorPrestamo {

    public Prestamo aDominio(PrestamoEntidad entidad) {
        if (entidad == null) {
            return null;
        }
        return Prestamo.reconstituir(
                entidad.getId(),
                entidad.getNumeroPrestamo(),
                entidad.getSolicitudId(),
                entidad.getClienteId(),
                entidad.getMontoAprobado(),
                entidad.getPlazoMeses(),
                entidad.getTasaInteresAnual(),
                entidad.getCuotaMensual(),
                entidad.getMontoTotalAPagar(),
                entidad.getTotalPagado(),
                entidad.getEstado(),
                entidad.getFechaDesembolso(),
                entidad.getFechaVencimiento(),
                entidad.getFechaCreacion());
    }

    public PrestamoEntidad aEntidad(Prestamo prestamo) {
        return new PrestamoEntidad(
                prestamo.getId(),
                prestamo.getNumeroPrestamo(),
                prestamo.getSolicitudId(),
                prestamo.getClienteId(),
                prestamo.getMontoAprobado(),
                prestamo.getPlazoMeses(),
                prestamo.getTasaInteresAnual(),
                prestamo.getCuotaMensual(),
                prestamo.getMontoTotalAPagar(),
                prestamo.getTotalPagado(),
                prestamo.getEstado(),
                prestamo.getFechaDesembolso(),
                prestamo.getFechaVencimiento(),
                prestamo.getFechaCreacion());
    }

    public PrestamoDetalle aDetalle(PrestamoProyeccion proyeccion) {
        return new PrestamoDetalle(
                aDominio(proyeccion.prestamo()),
                proyeccion.numeroSolicitud(),
                nombreCompleto(proyeccion.nombreCliente(), proyeccion.apellidoCliente()),
                proyeccion.identificacionCliente());
    }

    private String nombreCompleto(String nombre, String apellido) {
        return (nombre + " " + apellido).trim();
    }
}
