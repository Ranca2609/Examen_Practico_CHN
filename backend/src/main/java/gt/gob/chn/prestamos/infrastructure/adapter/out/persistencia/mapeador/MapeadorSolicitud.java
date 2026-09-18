package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.mapeador;

import gt.gob.chn.prestamos.domain.model.ResolucionSolicitud;
import gt.gob.chn.prestamos.domain.model.SolicitudPrestamo;
import gt.gob.chn.prestamos.domain.model.consulta.SolicitudDetalle;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.entidad.SolicitudPrestamoEntidad;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion.SolicitudProyeccion;
import org.springframework.stereotype.Component;

@Component
public class MapeadorSolicitud {

    public SolicitudPrestamo aDominio(SolicitudPrestamoEntidad entidad) {
        if (entidad == null) {
            return null;
        }
        ResolucionSolicitud resolucion = null;
        // fecha_resolucion es la marca de que la solicitud ya fue resuelta.
        if (entidad.getFechaResolucion() != null) {
            resolucion = new ResolucionSolicitud(
                    entidad.getFechaResolucion(),
                    entidad.getUsuarioResolucion(),
                    entidad.getMontoAprobado(),
                    entidad.getPlazoAprobadoMeses(),
                    entidad.getTasaAprobada(),
                    entidad.getMotivoResolucion());
        }
        return SolicitudPrestamo.reconstituir(
                entidad.getId(),
                entidad.getNumeroSolicitud(),
                entidad.getClienteId(),
                entidad.getMontoSolicitado(),
                entidad.getPlazoMeses(),
                entidad.getTasaInteresAnual(),
                entidad.getTipoPrestamo(),
                entidad.getDestino(),
                entidad.getIngresoMensualDeclarado(),
                entidad.getEstado(),
                entidad.getFechaSolicitud(),
                entidad.getObservaciones(),
                resolucion);
    }

    public SolicitudPrestamoEntidad aEntidad(SolicitudPrestamo solicitud) {
        ResolucionSolicitud resolucion = solicitud.getResolucion();
        return new SolicitudPrestamoEntidad(
                solicitud.getId(),
                solicitud.getNumeroSolicitud(),
                solicitud.getClienteId(),
                solicitud.getMontoSolicitado(),
                solicitud.getPlazoMeses(),
                solicitud.getTasaInteresAnual(),
                solicitud.getTipoPrestamo(),
                solicitud.getDestino(),
                solicitud.getIngresoMensualDeclarado(),
                solicitud.getEstado(),
                solicitud.getFechaSolicitud(),
                solicitud.getObservaciones(),
                resolucion == null ? null : resolucion.fechaResolucion(),
                resolucion == null ? null : resolucion.usuarioResolucion(),
                resolucion == null ? null : resolucion.montoAprobado(),
                resolucion == null ? null : resolucion.plazoAprobadoMeses(),
                resolucion == null ? null : resolucion.tasaAprobada(),
                resolucion == null ? null : resolucion.motivo());
    }

    public SolicitudDetalle aDetalle(SolicitudProyeccion proyeccion) {
        return new SolicitudDetalle(
                aDominio(proyeccion.solicitud()),
                nombreCompleto(proyeccion.nombreCliente(), proyeccion.apellidoCliente()),
                proyeccion.identificacionCliente());
    }

    private String nombreCompleto(String nombre, String apellido) {
        return (nombre + " " + apellido).trim();
    }
}
