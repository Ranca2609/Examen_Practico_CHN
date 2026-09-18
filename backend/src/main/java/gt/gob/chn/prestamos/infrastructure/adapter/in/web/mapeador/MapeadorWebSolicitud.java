package gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador;

import gt.gob.chn.prestamos.domain.model.ResolucionSolicitud;
import gt.gob.chn.prestamos.domain.model.SolicitudPrestamo;
import gt.gob.chn.prestamos.domain.model.consulta.SolicitudDetalle;
import gt.gob.chn.prestamos.domain.port.in.command.AprobarSolicitudCommand;
import gt.gob.chn.prestamos.domain.port.in.command.CrearSolicitudCommand;
import gt.gob.chn.prestamos.domain.port.in.command.RechazarSolicitudCommand;
import gt.gob.chn.prestamos.domain.port.in.command.SimularSolicitudCommand;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.peticion.AprobarSolicitudRequest;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.peticion.RechazarSolicitudRequest;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.peticion.SimulacionRequest;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.peticion.SolicitudRequest;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.ResolucionResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.SolicitudResponse;

public final class MapeadorWebSolicitud {

    private MapeadorWebSolicitud() {
    }

    public static CrearSolicitudCommand aComando(SolicitudRequest peticion) {
        return new CrearSolicitudCommand(
                peticion.clienteId(),
                peticion.montoSolicitado(),
                peticion.plazoMeses(),
                peticion.tasaInteresAnual(),
                ConversorEnumWeb.aTipoPrestamo(peticion.tipoPrestamo()),
                peticion.destino(),
                peticion.ingresoMensualDeclarado(),
                peticion.observaciones());
    }

    // Los nulos se propagan: el caso de uso los completa con las condiciones solicitadas.
    public static AprobarSolicitudCommand aComando(AprobarSolicitudRequest peticion) {
        return new AprobarSolicitudCommand(
                peticion.montoAprobado(),
                peticion.plazoAprobadoMeses(),
                peticion.tasaAprobada(),
                peticion.motivo());
    }

    public static RechazarSolicitudCommand aComando(RechazarSolicitudRequest peticion) {
        return new RechazarSolicitudCommand(peticion.motivo());
    }

    public static SimularSolicitudCommand aComando(SimulacionRequest peticion) {
        return new SimularSolicitudCommand(
                peticion.clienteId(),
                peticion.monto(),
                peticion.plazoMeses(),
                peticion.tasaInteresAnual(),
                peticion.ingresoMensual());
    }

    public static SolicitudResponse aRespuesta(SolicitudDetalle detalle) {
        SolicitudPrestamo solicitud = detalle.solicitud();
        return new SolicitudResponse(
                solicitud.getId(),
                solicitud.getNumeroSolicitud(),
                solicitud.getClienteId(),
                detalle.nombreCliente(),
                detalle.identificacionCliente(),
                solicitud.getMontoSolicitado(),
                solicitud.getPlazoMeses(),
                solicitud.getTasaInteresAnual(),
                solicitud.getTipoPrestamo().name(),
                solicitud.getDestino(),
                solicitud.getIngresoMensualDeclarado(),
                solicitud.getEstado().name(),
                solicitud.getFechaSolicitud(),
                solicitud.getObservaciones(),
                aRespuesta(solicitud.getResolucion()));
    }

    private static ResolucionResponse aRespuesta(ResolucionSolicitud resolucion) {
        if (resolucion == null) {
            return null;
        }
        return new ResolucionResponse(
                resolucion.fechaResolucion(),
                resolucion.usuarioResolucion(),
                resolucion.montoAprobado(),
                resolucion.plazoAprobadoMeses(),
                resolucion.tasaAprobada(),
                resolucion.motivo());
    }
}
