package gt.gob.chn.prestamos.application.usecase;

import gt.gob.chn.prestamos.domain.exception.RecursoNoEncontradoException;
import gt.gob.chn.prestamos.domain.model.EstadoPrestamo;
import gt.gob.chn.prestamos.domain.model.FormaPago;
import gt.gob.chn.prestamos.domain.model.Montos;
import gt.gob.chn.prestamos.domain.model.Pago;
import gt.gob.chn.prestamos.domain.model.Prestamo;
import gt.gob.chn.prestamos.domain.model.Validaciones;
import gt.gob.chn.prestamos.domain.model.consulta.PagoDetalle;
import gt.gob.chn.prestamos.domain.model.consulta.PrestamoDetalle;
import gt.gob.chn.prestamos.domain.model.reporte.ArchivoGenerado;
import gt.gob.chn.prestamos.domain.model.reporte.ColumnaReporte;
import gt.gob.chn.prestamos.domain.model.reporte.DocumentoReporte;
import gt.gob.chn.prestamos.domain.model.reporte.FilaReporte;
import gt.gob.chn.prestamos.domain.model.reporte.FormatoReporte;
import gt.gob.chn.prestamos.domain.model.reporte.ParDato;
import gt.gob.chn.prestamos.domain.model.reporte.ValorCelda;
import gt.gob.chn.prestamos.domain.port.in.GenerarReportesUseCase;
import gt.gob.chn.prestamos.domain.port.in.command.ContextoOperacion;
import gt.gob.chn.prestamos.domain.port.out.AuditoriaPort;
import gt.gob.chn.prestamos.domain.port.out.GeneradorReportePort;
import gt.gob.chn.prestamos.domain.port.out.PagoRepositorio;
import gt.gob.chn.prestamos.domain.port.out.PrestamoRepositorio;
import gt.gob.chn.prestamos.domain.port.out.RelojPort;
import gt.gob.chn.prestamos.domain.service.CalculadoraAmortizacion;
import gt.gob.chn.prestamos.domain.service.CuotaAmortizacion;
import gt.gob.chn.prestamos.domain.service.PlanAmortizacion;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GenerarReportesService implements GenerarReportesUseCase {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Separadores fijos: el documento no debe variar con el Locale del servidor.
    private static final DecimalFormatSymbols SIMBOLOS_GT = simbolosDeGuatemala();

    private static final String TITULO_AMORTIZACION = "Plan de amortización";
    private static final String TITULO_PAGOS = "Historial de pagos";
    private static final String CELDA_VACIA = "";

    private final PrestamoRepositorio prestamoRepositorio;
    private final PagoRepositorio pagoRepositorio;
    private final CalculadoraAmortizacion calculadora;
    private final RelojPort reloj;
    private final AuditoriaPort auditoria;
    private final Map<FormatoReporte, GeneradorReportePort> generadores;

    public GenerarReportesService(PrestamoRepositorio prestamoRepositorio,
                                  PagoRepositorio pagoRepositorio,
                                  CalculadoraAmortizacion calculadora,
                                  RelojPort reloj,
                                  AuditoriaPort auditoria,
                                  List<GeneradorReportePort> generadores) {
        this.prestamoRepositorio = prestamoRepositorio;
        this.pagoRepositorio = pagoRepositorio;
        this.calculadora = calculadora;
        this.reloj = reloj;
        this.auditoria = auditoria;
        this.generadores = indexarPorFormato(generadores);
    }

    // Falla al arrancar si falta o sobra un generador, en vez de devolver un 500 al descargar.
    private static Map<FormatoReporte, GeneradorReportePort> indexarPorFormato(
            List<GeneradorReportePort> disponibles) {
        Map<FormatoReporte, GeneradorReportePort> porFormato = new EnumMap<>(FormatoReporte.class);
        for (GeneradorReportePort generador : disponibles) {
            GeneradorReportePort anterior = porFormato.put(generador.formato(), generador);
            if (anterior != null) {
                throw new IllegalStateException("Hay dos generadores de reportes para el formato "
                        + generador.formato() + ": " + anterior.getClass().getName() + " y "
                        + generador.getClass().getName() + ". Debe haber exactamente uno por formato.");
            }
        }
        for (FormatoReporte formato : FormatoReporte.values()) {
            if (!porFormato.containsKey(formato)) {
                throw new IllegalStateException("No hay ningún generador de reportes para el formato "
                        + formato + ". Falta una implementación de GeneradorReportePort que lo atienda.");
            }
        }
        return Collections.unmodifiableMap(porFormato);
    }

    // No readOnly: con FlushMode.MANUAL el INSERT de auditoría de la descarga se perdería.
    @Override
    @Transactional
    public ArchivoGenerado planAmortizacion(Long prestamoId, FormatoReporte formato, ContextoOperacion ctx) {
        validarPeticion(formato, ctx);
        PrestamoDetalle detalle = buscarDetalle(prestamoId);
        Prestamo prestamo = detalle.prestamo();

        PlanAmortizacion plan = calculadora.calcular(prestamo.getMontoAprobado(),
                prestamo.getPlazoMeses(), prestamo.getTasaInteresAnual());

        List<FilaReporte> filas = plan.cuotas().stream()
                .map(cuota -> FilaReporte.de(
                        new ValorCelda.Entero(cuota.numero()),
                        new ValorCelda.Moneda(cuota.saldoInicial()),
                        new ValorCelda.Moneda(cuota.cuota()),
                        new ValorCelda.Moneda(cuota.abonoCapital()),
                        new ValorCelda.Moneda(cuota.abonoInteres()),
                        new ValorCelda.Moneda(cuota.saldoFinal())))
                .toList();

        // Se suman las cuotas impresas (no los acumulados del plan) para que cuadre al centavo.
        List<ValorCelda> totales = List.of(
                new ValorCelda.Texto("Totales"),
                new ValorCelda.Texto(CELDA_VACIA),
                new ValorCelda.Moneda(sumar(plan.cuotas(), CuotaAmortizacion::cuota)),
                new ValorCelda.Moneda(sumar(plan.cuotas(), CuotaAmortizacion::abonoCapital)),
                new ValorCelda.Moneda(sumar(plan.cuotas(), CuotaAmortizacion::abonoInteres)),
                new ValorCelda.Texto(CELDA_VACIA));

        DocumentoReporte documento = new DocumentoReporte(
                TITULO_AMORTIZACION,
                "Préstamo " + prestamo.getNumeroPrestamo(),
                datosEncabezado(detalle),
                List.of(ColumnaReporte.centro("No."),
                        ColumnaReporte.derecha("Saldo inicial"),
                        ColumnaReporte.derecha("Cuota"),
                        ColumnaReporte.derecha("Abono a capital"),
                        ColumnaReporte.derecha("Abono a intereses"),
                        ColumnaReporte.derecha("Saldo final")),
                filas,
                totales,
                "Cuotas niveladas sobre saldos (sistema francés). Es el plan pactado al "
                        + "desembolsar, por lo que no cambia con los abonos realizados.",
                ctx.usuario(),
                reloj.ahora(),
                "plan-amortizacion-" + prestamo.getNumeroPrestamo());

        return generar(documento, formato, prestamo, ctx);
    }

    @Override
    @Transactional
    public ArchivoGenerado historialPagos(Long prestamoId, FormatoReporte formato, ContextoOperacion ctx) {
        validarPeticion(formato, ctx);
        PrestamoDetalle detalle = buscarDetalle(prestamoId);
        Prestamo prestamo = detalle.prestamo();

        // El repositorio entrega del más reciente al más antiguo; el reporte va cronológico.
        List<Pago> pagos = pagoRepositorio.listarDetallePorPrestamo(prestamoId).stream()
                .map(PagoDetalle::pago)
                .sorted(Comparator.<Pago, LocalDateTime>comparing(Pago::getFechaPago)
                        .thenComparing(Pago::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<FilaReporte> filas = pagos.stream()
                .map(pago -> FilaReporte.de(
                        new ValorCelda.Texto(pago.getNumeroRecibo()),
                        new ValorCelda.FechaHora(pago.getFechaPago()),
                        new ValorCelda.Moneda(pago.getMonto()),
                        new ValorCelda.Moneda(pago.getSaldoAnterior()),
                        new ValorCelda.Moneda(pago.getSaldoPosterior()),
                        new ValorCelda.Texto(etiquetaDe(pago.getFormaPago())),
                        new ValorCelda.Texto(pago.getUsuarioRegistro()),
                        new ValorCelda.Texto(pago.getObservaciones())))
                .toList();

        // Sin pagos se genera igual: el encabezado sirve de constancia de que no hay movimientos.
        List<ValorCelda> totales = pagos.isEmpty()
                ? List.of()
                : List.of(new ValorCelda.Texto("Total pagado"),
                        new ValorCelda.Texto(CELDA_VACIA),
                        new ValorCelda.Moneda(sumar(pagos, Pago::getMonto)),
                        new ValorCelda.Texto(CELDA_VACIA),
                        new ValorCelda.Texto(CELDA_VACIA),
                        new ValorCelda.Texto(CELDA_VACIA),
                        new ValorCelda.Texto(CELDA_VACIA),
                        new ValorCelda.Texto(CELDA_VACIA));

        DocumentoReporte documento = new DocumentoReporte(
                TITULO_PAGOS,
                "Préstamo " + prestamo.getNumeroPrestamo(),
                datosEncabezado(detalle),
                List.of(ColumnaReporte.izquierda("No. de recibo"),
                        ColumnaReporte.izquierda("Fecha y hora"),
                        ColumnaReporte.derecha("Monto"),
                        ColumnaReporte.derecha("Saldo anterior"),
                        ColumnaReporte.derecha("Saldo posterior"),
                        ColumnaReporte.izquierda("Forma de pago"),
                        ColumnaReporte.izquierda("Registrado por"),
                        ColumnaReporte.izquierda("Observaciones")),
                filas,
                totales,
                pagos.isEmpty()
                        ? "Este préstamo aún no registra pagos."
                        : "Documento informativo. Los saldos corresponden al momento de cada pago.",
                ctx.usuario(),
                reloj.ahora(),
                "historial-pagos-" + prestamo.getNumeroPrestamo());

        return generar(documento, formato, prestamo, ctx);
    }

    private ArchivoGenerado generar(DocumentoReporte documento, FormatoReporte formato,
                                    Prestamo prestamo, ContextoOperacion ctx) {
        ArchivoGenerado archivo = generadores.get(formato).generar(documento);

        auditoria.registrar(ctx.usuario(), AccionesAuditoria.REPORTE_DESCARGADO,
                AccionesAuditoria.ENTIDAD_PRESTAMO, String.valueOf(prestamo.getId()),
                "Descarga de " + documento.titulo() + " del préstamo " + prestamo.getNumeroPrestamo()
                        + " en formato " + formato.name() + " (" + archivo.nombre() + ", "
                        + archivo.tamanoEnBytes() + " bytes)",
                ctx.direccionIp());

        return archivo;
    }

    private static void validarPeticion(FormatoReporte formato, ContextoOperacion ctx) {
        Validaciones.exigirNoNulo(formato, "formato del reporte");
        Validaciones.exigirNoNulo(ctx, "contexto de la operación");
    }

    private PrestamoDetalle buscarDetalle(Long prestamoId) {
        return prestamoRepositorio.buscarDetallePorId(prestamoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Préstamo", prestamoId));
    }

    private List<ParDato> datosEncabezado(PrestamoDetalle detalle) {
        Prestamo prestamo = detalle.prestamo();
        return List.of(
                new ParDato("Préstamo", prestamo.getNumeroPrestamo()),
                new ParDato("Solicitud de origen", detalle.numeroSolicitud()),
                new ParDato("Cliente", detalle.nombreCliente()),
                new ParDato("DPI", detalle.identificacionCliente()),
                new ParDato("Estado", etiquetaDe(prestamo.getEstado())),
                new ParDato("Monto aprobado", moneda(prestamo.getMontoAprobado())),
                new ParDato("Plazo", prestamo.getPlazoMeses() + " meses"),
                new ParDato("Tasa de interés anual", porcentaje(prestamo.getTasaInteresAnual())),
                new ParDato("Cuota mensual", moneda(prestamo.getCuotaMensual())),
                new ParDato("Total a pagar", moneda(prestamo.getMontoTotalAPagar())),
                new ParDato("Total pagado", moneda(prestamo.getTotalPagado())),
                new ParDato("Saldo pendiente", moneda(prestamo.getSaldoPendiente())),
                new ParDato("Fecha de desembolso", fecha(prestamo.getFechaDesembolso())),
                new ParDato("Fecha de vencimiento", fecha(prestamo.getFechaVencimiento())));
    }

    private static <T> BigDecimal sumar(List<T> filas, Function<T, BigDecimal> columna) {
        return Montos.normalizar(filas.stream()
                .map(columna)
                .reduce(Montos.CERO, BigDecimal::add));
    }

    private static String moneda(BigDecimal valor) {
        return "Q " + formatoDecimal().format(Montos.ceroSiNulo(valor));
    }

    private static String porcentaje(BigDecimal valor) {
        return formatoDecimal().format(Montos.ceroSiNulo(valor)) + " %";
    }

    private static String fecha(LocalDate valor) {
        return valor == null ? CELDA_VACIA : FORMATO_FECHA.format(valor);
    }

    // DecimalFormat no es thread-safe: una instancia por llamada mantiene el servicio sin estado.
    private static DecimalFormat formatoDecimal() {
        return new DecimalFormat("#,##0.00", SIMBOLOS_GT);
    }

    private static DecimalFormatSymbols simbolosDeGuatemala() {
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols(Locale.ROOT);
        simbolos.setDecimalSeparator('.');
        simbolos.setGroupingSeparator(',');
        return simbolos;
    }

    private static String etiquetaDe(EstadoPrestamo estado) {
        return switch (estado) {
            case VIGENTE -> "Vigente";
            case LIQUIDADO -> "Liquidado";
        };
    }

    private static String etiquetaDe(FormaPago formaPago) {
        return switch (formaPago) {
            case EFECTIVO -> "Efectivo";
        };
    }
}
