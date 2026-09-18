package gt.gob.chn.prestamos.application.usecase;

import gt.gob.chn.prestamos.domain.exception.RecursoNoEncontradoException;
import gt.gob.chn.prestamos.domain.exception.ReglaNegocioException;
import gt.gob.chn.prestamos.domain.model.Prestamo;
import gt.gob.chn.prestamos.domain.model.SolicitudPrestamo;
import gt.gob.chn.prestamos.domain.model.Validaciones;
import gt.gob.chn.prestamos.domain.model.consulta.FiltroSolicitud;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.domain.model.consulta.SolicitudDetalle;
import gt.gob.chn.prestamos.domain.port.in.GestionarSolicitudesUseCase;
import gt.gob.chn.prestamos.domain.port.in.command.AprobarSolicitudCommand;
import gt.gob.chn.prestamos.domain.port.in.command.ContextoOperacion;
import gt.gob.chn.prestamos.domain.port.in.command.CrearSolicitudCommand;
import gt.gob.chn.prestamos.domain.port.in.command.RechazarSolicitudCommand;
import gt.gob.chn.prestamos.domain.port.in.command.SimularSolicitudCommand;
import gt.gob.chn.prestamos.domain.port.out.AuditoriaPort;
import gt.gob.chn.prestamos.domain.port.out.ClienteRepositorio;
import gt.gob.chn.prestamos.domain.port.out.CorrelativoPort;
import gt.gob.chn.prestamos.domain.port.out.PrestamoRepositorio;
import gt.gob.chn.prestamos.domain.port.out.RelojPort;
import gt.gob.chn.prestamos.domain.port.out.SolicitudPrestamoRepositorio;
import gt.gob.chn.prestamos.domain.service.CalculadoraAmortizacion;
import gt.gob.chn.prestamos.domain.service.EvaluadorCapacidadPago;
import gt.gob.chn.prestamos.domain.service.PlanAmortizacion;
import gt.gob.chn.prestamos.domain.service.ResultadoEvaluacion;
import gt.gob.chn.prestamos.domain.service.ResultadoSimulacion;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GestionarSolicitudesService implements GestionarSolicitudesUseCase {

    private final ClienteRepositorio clienteRepositorio;
    private final SolicitudPrestamoRepositorio solicitudRepositorio;
    private final PrestamoRepositorio prestamoRepositorio;
    private final CorrelativoPort correlativo;
    private final AuditoriaPort auditoria;
    private final RelojPort reloj;
    private final CalculadoraAmortizacion calculadora;
    private final EvaluadorCapacidadPago evaluador;

    public GestionarSolicitudesService(ClienteRepositorio clienteRepositorio,
                                       SolicitudPrestamoRepositorio solicitudRepositorio,
                                       PrestamoRepositorio prestamoRepositorio,
                                       CorrelativoPort correlativo,
                                       AuditoriaPort auditoria,
                                       RelojPort reloj,
                                       CalculadoraAmortizacion calculadora,
                                       EvaluadorCapacidadPago evaluador) {
        this.clienteRepositorio = clienteRepositorio;
        this.solicitudRepositorio = solicitudRepositorio;
        this.prestamoRepositorio = prestamoRepositorio;
        this.correlativo = correlativo;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.calculadora = calculadora;
        this.evaluador = evaluador;
    }

    @Override
    public SolicitudDetalle crear(CrearSolicitudCommand cmd, ContextoOperacion ctx) {
        exigirClienteExistente(cmd.clienteId());
        int plazo = Validaciones.exigirNoNulo(cmd.plazoMeses(), "plazo en meses");

        // Secuencia de la base, no MAX(id) + 1: segura ante solicitudes concurrentes.
        SolicitudPrestamo solicitud = SolicitudPrestamo.nueva(
                correlativo.siguienteNumeroSolicitud(), cmd.clienteId(), cmd.montoSolicitado(),
                plazo, cmd.tasaInteresAnual(), cmd.tipoPrestamo(), cmd.destino(),
                cmd.ingresoMensualDeclarado(), cmd.observaciones(), reloj.ahora());

        SolicitudPrestamo guardada = solicitudRepositorio.guardar(solicitud);

        auditoria.registrar(ctx.usuario(), AccionesAuditoria.SOLICITUD_CREADA,
                AccionesAuditoria.ENTIDAD_SOLICITUD, String.valueOf(guardada.getId()),
                "Solicitud " + guardada.getNumeroSolicitud() + " por "
                        + guardada.getMontoSolicitado() + " a " + guardada.getPlazoMeses()
                        + " meses (cliente " + guardada.getClienteId() + ")",
                ctx.direccionIp());

        return detalle(guardada.getId());
    }

    @Override
    public SolicitudDetalle aprobar(Long id, AprobarSolicitudCommand cmd, ContextoOperacion ctx) {
        SolicitudPrestamo solicitud = exigirSolicitud(id);

        // El UNIQUE de prestamos.solicitud_id ya lo impide; aquí se traduce a un mensaje de negocio.
        if (prestamoRepositorio.buscarPorSolicitudId(id).isPresent()) {
            throw new ReglaNegocioException(
                    "La solicitud " + solicitud.getNumeroSolicitud() + " ya generó un préstamo");
        }

        // Sin ajustes del analista se aprueba en las condiciones solicitadas.
        BigDecimal monto = cmd.montoAprobado() != null
                ? cmd.montoAprobado() : solicitud.getMontoSolicitado();
        int plazo = cmd.plazoAprobadoMeses() != null
                ? cmd.plazoAprobadoMeses() : solicitud.getPlazoMeses();
        BigDecimal tasa = cmd.tasaAprobada() != null
                ? cmd.tasaAprobada() : solicitud.getTasaInteresAnual();

        LocalDateTime ahora = reloj.ahora();
        solicitud.aprobar(monto, plazo, tasa, ctx.usuario(), cmd.motivo(), ahora);
        solicitudRepositorio.guardar(solicitud);

        // La cuota queda congelada en el préstamo: no cambia si luego se ajusta la política de tasas.
        PlanAmortizacion plan = calculadora.calcular(monto, plazo, tasa);
        Prestamo prestamo = Prestamo.nuevo(correlativo.siguienteNumeroPrestamo(), solicitud.getId(),
                solicitud.getClienteId(), monto, plazo, tasa, plan.cuotaMensual(),
                plan.montoTotal(), reloj.hoy(), ahora);
        Prestamo prestamoGuardado = prestamoRepositorio.guardar(prestamo);

        auditoria.registrar(ctx.usuario(), AccionesAuditoria.SOLICITUD_APROBADA,
                AccionesAuditoria.ENTIDAD_SOLICITUD, String.valueOf(id),
                "Aprobada " + solicitud.getNumeroSolicitud() + " por " + monto + " a " + plazo
                        + " meses al " + tasa + "% anual",
                ctx.direccionIp());
        auditoria.registrar(ctx.usuario(), AccionesAuditoria.PRESTAMO_CREADO,
                AccionesAuditoria.ENTIDAD_PRESTAMO, String.valueOf(prestamoGuardado.getId()),
                "Préstamo " + prestamoGuardado.getNumeroPrestamo() + " con cuota mensual de "
                        + plan.cuotaMensual() + " y total a pagar de " + plan.montoTotal(),
                ctx.direccionIp());

        return detalle(id);
    }

    @Override
    public SolicitudDetalle rechazar(Long id, RechazarSolicitudCommand cmd, ContextoOperacion ctx) {
        SolicitudPrestamo solicitud = exigirSolicitud(id);

        solicitud.rechazar(ctx.usuario(), cmd.motivo(), reloj.ahora());
        solicitudRepositorio.guardar(solicitud);

        auditoria.registrar(ctx.usuario(), AccionesAuditoria.SOLICITUD_RECHAZADA,
                AccionesAuditoria.ENTIDAD_SOLICITUD, String.valueOf(id),
                "Rechazada " + solicitud.getNumeroSolicitud() + ". Motivo: " + cmd.motivo(),
                ctx.direccionIp());

        return detalle(id);
    }

    @Override
    @Transactional(readOnly = true)
    public SolicitudDetalle obtener(Long id) {
        return detalle(id);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaDominio<SolicitudDetalle> listar(FiltroSolicitud filtro) {
        return solicitudRepositorio.listarDetalle(filtro);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SolicitudDetalle> listarPorCliente(Long clienteId) {
        exigirClienteExistente(clienteId);
        return solicitudRepositorio.listarDetallePorCliente(clienteId);
    }

    @Override
    @Transactional(readOnly = true)
    public ResultadoSimulacion simular(SimularSolicitudCommand cmd) {
        int plazo = Validaciones.exigirNoNulo(cmd.plazoMeses(), "plazo en meses");

        // No persiste ni audita; los préstamos vigentes entran en la política de riesgo.
        long prestamosVigentes = cmd.clienteId() == null
                ? 0L : prestamoRepositorio.contarVigentesPorCliente(cmd.clienteId());

        PlanAmortizacion plan = calculadora.calcular(cmd.monto(), plazo, cmd.tasaInteresAnual());
        ResultadoEvaluacion evaluacion =
                evaluador.evaluar(cmd.ingresoMensual(), plan.cuotaMensual(), prestamosVigentes);

        return new ResultadoSimulacion(evaluacion, plan);
    }

    private SolicitudPrestamo exigirSolicitud(Long id) {
        return solicitudRepositorio.buscarPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud de préstamo", id));
    }

    private SolicitudDetalle detalle(Long id) {
        return solicitudRepositorio.buscarDetallePorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud de préstamo", id));
    }

    private void exigirClienteExistente(Long clienteId) {
        if (clienteRepositorio.buscarPorId(clienteId).isEmpty()) {
            throw new RecursoNoEncontradoException("Cliente", clienteId);
        }
    }
}
