package gt.gob.chn.prestamos.application.usecase;

import gt.gob.chn.prestamos.domain.exception.RecursoNoEncontradoException;
import gt.gob.chn.prestamos.domain.model.FormaPago;
import gt.gob.chn.prestamos.domain.model.Pago;
import gt.gob.chn.prestamos.domain.model.Prestamo;
import gt.gob.chn.prestamos.domain.model.consulta.FiltroPago;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.domain.model.consulta.PagoDetalle;
import gt.gob.chn.prestamos.domain.port.in.RegistrarPagosUseCase;
import gt.gob.chn.prestamos.domain.port.in.command.ContextoOperacion;
import gt.gob.chn.prestamos.domain.port.in.command.RegistrarPagoCommand;
import gt.gob.chn.prestamos.domain.port.out.AuditoriaPort;
import gt.gob.chn.prestamos.domain.port.out.CorrelativoPort;
import gt.gob.chn.prestamos.domain.port.out.PagoRepositorio;
import gt.gob.chn.prestamos.domain.port.out.PrestamoRepositorio;
import gt.gob.chn.prestamos.domain.port.out.RelojPort;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RegistrarPagosService implements RegistrarPagosUseCase {

    private final PrestamoRepositorio prestamoRepositorio;
    private final PagoRepositorio pagoRepositorio;
    private final CorrelativoPort correlativo;
    private final AuditoriaPort auditoria;
    private final RelojPort reloj;

    public RegistrarPagosService(PrestamoRepositorio prestamoRepositorio,
                                 PagoRepositorio pagoRepositorio,
                                 CorrelativoPort correlativo,
                                 AuditoriaPort auditoria,
                                 RelojPort reloj) {
        this.prestamoRepositorio = prestamoRepositorio;
        this.pagoRepositorio = pagoRepositorio;
        this.correlativo = correlativo;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Override
    public PagoDetalle registrar(RegistrarPagoCommand cmd, ContextoOperacion ctx) {
        Prestamo prestamo = prestamoRepositorio.buscarPorId(cmd.prestamoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Préstamo", cmd.prestamoId()));

        BigDecimal saldoAnterior = prestamo.getSaldoPendiente();

        prestamo.aplicarPago(cmd.monto());
        BigDecimal saldoPosterior = prestamo.getSaldoPendiente();

        Pago pago = Pago.nuevo(correlativo.siguienteNumeroRecibo(), prestamo.getId(), cmd.monto(),
                FormaPago.EFECTIVO, saldoAnterior, saldoPosterior, ctx.usuario(),
                cmd.observaciones(), reloj.ahora());

        prestamoRepositorio.guardar(prestamo);
        Pago guardado = pagoRepositorio.guardar(pago);

        auditoria.registrar(ctx.usuario(), AccionesAuditoria.PAGO_REGISTRADO,
                AccionesAuditoria.ENTIDAD_PAGO, String.valueOf(guardado.getId()),
                "Recibo " + guardado.getNumeroRecibo() + " por " + cmd.monto() + " sobre "
                        + prestamo.getNumeroPrestamo() + ". Saldo: " + saldoAnterior + " -> "
                        + saldoPosterior,
                ctx.direccionIp());

        if (prestamo.estaLiquidado()) {
            auditoria.registrar(ctx.usuario(), AccionesAuditoria.PRESTAMO_LIQUIDADO,
                    AccionesAuditoria.ENTIDAD_PRESTAMO, String.valueOf(prestamo.getId()),
                    "El préstamo " + prestamo.getNumeroPrestamo()
                            + " quedó liquidado con el recibo " + guardado.getNumeroRecibo(),
                    ctx.direccionIp());
        }

        return pagoRepositorio.buscarDetallePorId(guardado.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Pago", guardado.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PagoDetalle> listarPorPrestamo(Long prestamoId) {
        if (prestamoRepositorio.buscarPorId(prestamoId).isEmpty()) {
            throw new RecursoNoEncontradoException("Préstamo", prestamoId);
        }
        return pagoRepositorio.listarDetallePorPrestamo(prestamoId);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaDominio<PagoDetalle> listar(FiltroPago filtro) {
        return pagoRepositorio.listarDetalle(filtro);
    }
}
