package gt.gob.chn.prestamos.domain.model;

import gt.gob.chn.prestamos.domain.exception.ReglaNegocioException;
import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class SolicitudPrestamo {

    public static final BigDecimal MONTO_MINIMO = new BigDecimal("1000.00");
    public static final BigDecimal MONTO_MAXIMO = new BigDecimal("5000000.00");
    public static final int PLAZO_MINIMO_MESES = 6;
    public static final int PLAZO_MAXIMO_MESES = 360;
    public static final BigDecimal TASA_MINIMA = new BigDecimal("0.01");
    public static final BigDecimal TASA_MAXIMA = new BigDecimal("100.00");

    private static final int DESTINO_MINIMO = 5;
    private static final int DESTINO_MAXIMO = 200;
    private static final int OBSERVACIONES_MAXIMO = 500;
    private static final int MOTIVO_MINIMO = 10;
    private static final int MOTIVO_MAXIMO = 500;

    private final Long id;
    private final String numeroSolicitud;
    private final Long clienteId;
    private final BigDecimal montoSolicitado;
    private final int plazoMeses;
    private final BigDecimal tasaInteresAnual;
    private final TipoPrestamo tipoPrestamo;
    private final String destino;
    private final BigDecimal ingresoMensualDeclarado;
    private EstadoSolicitud estado;
    private final LocalDateTime fechaSolicitud;
    private final String observaciones;
    private ResolucionSolicitud resolucion;

    private SolicitudPrestamo(Long id, String numeroSolicitud, Long clienteId, BigDecimal montoSolicitado,
                              int plazoMeses, BigDecimal tasaInteresAnual, TipoPrestamo tipoPrestamo,
                              String destino, BigDecimal ingresoMensualDeclarado, EstadoSolicitud estado,
                              LocalDateTime fechaSolicitud, String observaciones, ResolucionSolicitud resolucion) {
        this.id = id;
        this.numeroSolicitud = NumeroDocumento.exigir(numeroSolicitud, TipoDocumento.SOLICITUD_CREDITO);
        this.clienteId = Validaciones.exigirNoNulo(clienteId, "cliente");
        this.montoSolicitado = Montos.normalizar(
                Validaciones.exigirRango(montoSolicitado, "monto solicitado", MONTO_MINIMO, MONTO_MAXIMO));
        this.plazoMeses = Validaciones.exigirRango(
                plazoMeses, "plazo en meses", PLAZO_MINIMO_MESES, PLAZO_MAXIMO_MESES);
        this.tasaInteresAnual = Montos.normalizar(
                Validaciones.exigirRango(tasaInteresAnual, "tasa de interes anual", TASA_MINIMA, TASA_MAXIMA));
        this.tipoPrestamo = Validaciones.exigirNoNulo(tipoPrestamo, "tipo de prestamo");
        this.destino = Validaciones.exigirTexto(destino, "destino del prestamo", DESTINO_MINIMO, DESTINO_MAXIMO);
        this.ingresoMensualDeclarado = Montos.normalizar(
                Validaciones.exigirPositivo(ingresoMensualDeclarado, "ingreso mensual declarado"));
        this.estado = Validaciones.exigirNoNulo(estado, "estado de la solicitud");
        this.fechaSolicitud = Validaciones.exigirNoNulo(fechaSolicitud, "fecha de solicitud");
        this.observaciones = Validaciones.exigirTextoOpcional(observaciones, "observaciones", OBSERVACIONES_MAXIMO);
        this.resolucion = resolucion;
    }

    public static SolicitudPrestamo nueva(String numeroSolicitud, Long clienteId, BigDecimal montoSolicitado,
                                          int plazoMeses, BigDecimal tasaInteresAnual, TipoPrestamo tipoPrestamo,
                                          String destino, BigDecimal ingresoMensualDeclarado, String observaciones,
                                          LocalDateTime ahora) {
        Validaciones.exigirNoNulo(ahora, "fecha de operacion");
        return new SolicitudPrestamo(null, numeroSolicitud, clienteId, montoSolicitado, plazoMeses,
                tasaInteresAnual, tipoPrestamo, destino, ingresoMensualDeclarado,
                EstadoSolicitud.EN_PROCESO, ahora, observaciones, null);
    }

    public static SolicitudPrestamo reconstituir(Long id, String numeroSolicitud, Long clienteId,
                                                 BigDecimal montoSolicitado, int plazoMeses,
                                                 BigDecimal tasaInteresAnual, TipoPrestamo tipoPrestamo,
                                                 String destino, BigDecimal ingresoMensualDeclarado,
                                                 EstadoSolicitud estado, LocalDateTime fechaSolicitud,
                                                 String observaciones, ResolucionSolicitud resolucion) {
        Validaciones.exigirNoNulo(id, "identificador de la solicitud");
        return new SolicitudPrestamo(id, numeroSolicitud, clienteId, montoSolicitado, plazoMeses, tasaInteresAnual,
                tipoPrestamo, destino, ingresoMensualDeclarado, estado, fechaSolicitud, observaciones, resolucion);
    }

    /** Admite aprobacion parcial: el monto aprobado puede ser menor al solicitado, nunca mayor. */
    public void aprobar(BigDecimal montoAprobado, int plazoAprobadoMeses, BigDecimal tasaAprobada,
                        String usuario, String motivo, LocalDateTime ahora) {
        exigirEnProceso();
        Validaciones.exigirNoNulo(ahora, "fecha de operacion");
        Validaciones.exigirPositivo(montoAprobado, "monto aprobado");
        if (montoAprobado.compareTo(montoSolicitado) > 0) {
            throw new ValidacionDominioException("El monto aprobado no puede exceder el monto solicitado ("
                    + montoSolicitado.toPlainString() + ").");
        }
        Validaciones.exigirRango(plazoAprobadoMeses, "plazo aprobado en meses",
                PLAZO_MINIMO_MESES, PLAZO_MAXIMO_MESES);
        Validaciones.exigirRango(tasaAprobada, "tasa aprobada", TASA_MINIMA, TASA_MAXIMA);
        String motivoLimpio = Validaciones.exigirTextoOpcional(motivo, "motivo de la resolucion", MOTIVO_MAXIMO);

        this.estado = EstadoSolicitud.APROBADA;
        this.resolucion = ResolucionSolicitud.aprobacion(ahora, usuario, montoAprobado, plazoAprobadoMeses,
                tasaAprobada, motivoLimpio);
    }

    /** El motivo es obligatorio: queda como constancia para el cliente y respaldo ante auditoria. */
    public void rechazar(String usuario, String motivo, LocalDateTime ahora) {
        exigirEnProceso();
        Validaciones.exigirNoNulo(ahora, "fecha de operacion");
        String motivoLimpio = Validaciones.exigirTexto(
                motivo, "motivo del rechazo", MOTIVO_MINIMO, MOTIVO_MAXIMO);

        this.estado = EstadoSolicitud.RECHAZADA;
        this.resolucion = ResolucionSolicitud.rechazo(ahora, usuario, motivoLimpio);
    }

    public boolean estaEnProceso() {
        return estado == EstadoSolicitud.EN_PROCESO;
    }

    private void exigirEnProceso() {
        if (!estaEnProceso()) {
            throw new ReglaNegocioException("La solicitud ya fue resuelta y no admite cambios de estado");
        }
    }

    public Long getId() {
        return id;
    }

    public String getNumeroSolicitud() {
        return numeroSolicitud;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public BigDecimal getMontoSolicitado() {
        return montoSolicitado;
    }

    public int getPlazoMeses() {
        return plazoMeses;
    }

    public BigDecimal getTasaInteresAnual() {
        return tasaInteresAnual;
    }

    public TipoPrestamo getTipoPrestamo() {
        return tipoPrestamo;
    }

    public String getDestino() {
        return destino;
    }

    public BigDecimal getIngresoMensualDeclarado() {
        return ingresoMensualDeclarado;
    }

    public EstadoSolicitud getEstado() {
        return estado;
    }

    public LocalDateTime getFechaSolicitud() {
        return fechaSolicitud;
    }

    public String getObservaciones() {
        return observaciones;
    }

    /** Null mientras la solicitud sigue EN_PROCESO. */
    public ResolucionSolicitud getResolucion() {
        return resolucion;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SolicitudPrestamo otra)) {
            return false;
        }
        return id != null && id.equals(otra.id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }

    @Override
    public String toString() {
        return "SolicitudPrestamo{id=" + id + ", numeroSolicitud=" + numeroSolicitud
                + ", clienteId=" + clienteId + ", estado=" + estado + "}";
    }
}
