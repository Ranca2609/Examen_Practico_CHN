package gt.gob.chn.prestamos.domain.port.in;

import gt.gob.chn.prestamos.domain.model.consulta.FiltroSolicitud;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.domain.model.consulta.SolicitudDetalle;
import gt.gob.chn.prestamos.domain.port.in.command.AprobarSolicitudCommand;
import gt.gob.chn.prestamos.domain.port.in.command.ContextoOperacion;
import gt.gob.chn.prestamos.domain.port.in.command.CrearSolicitudCommand;
import gt.gob.chn.prestamos.domain.port.in.command.RechazarSolicitudCommand;
import gt.gob.chn.prestamos.domain.port.in.command.SimularSolicitudCommand;
import gt.gob.chn.prestamos.domain.service.ResultadoSimulacion;
import java.util.List;

public interface GestionarSolicitudesUseCase {

    SolicitudDetalle crear(CrearSolicitudCommand cmd, ContextoOperacion ctx);

    /** Ademas crea y desembolsa el prestamo con las condiciones aprobadas. */
    SolicitudDetalle aprobar(Long id, AprobarSolicitudCommand cmd, ContextoOperacion ctx);

    SolicitudDetalle rechazar(Long id, RechazarSolicitudCommand cmd, ContextoOperacion ctx);

    SolicitudDetalle obtener(Long id);

    PaginaDominio<SolicitudDetalle> listar(FiltroSolicitud filtro);

    List<SolicitudDetalle> listarPorCliente(Long clienteId);

    /** No persiste nada. */
    ResultadoSimulacion simular(SimularSolicitudCommand cmd);
}
