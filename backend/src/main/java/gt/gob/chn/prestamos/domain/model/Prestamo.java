package gt.gob.chn.prestamos.domain.model;

import gt.gob.chn.prestamos.domain.exception.ReglaNegocioException;
import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class Prestamo {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final Long id;
    private final String numeroPrestamo;
    private final Long solicitudId;
    private final Long clienteId;
    private final BigDecimal montoAprobado;
    private final int plazoMeses;
    private final BigDecimal tasaInteresAnual;
    private final BigDecimal cuotaMensual;
    private final BigDecimal montoTotalAPagar;
    private BigDecimal totalPagado;
    private EstadoPrestamo estado;
    private final LocalDate fechaDesembolso;
    private final LocalDate fechaVencimiento;
    private final LocalDateTime fechaCreacion;

    private Prestamo(Long id, String numeroPrestamo, Long solicitudId, Long clienteId, BigDecimal montoAprobado,
                     int plazoMeses, BigDecimal tasaInteresAnual, BigDecimal cuotaMensual,
                     BigDecimal montoTotalAPagar, BigDecimal totalPagado, EstadoPrestamo estado,
                     LocalDate fechaDesembolso, LocalDate fechaVencimiento, LocalDateTime fechaCreacion) {
        this.id = id;
        this.numeroPrestamo = NumeroDocumento.exigir(numeroPrestamo, TipoDocumento.PRESTAMO);
        this.solicitudId = Validaciones.exigirNoNulo(solicitudId, "solicitud de origen");
        this.clienteId = Validaciones.exigirNoNulo(clienteId, "cliente");
        this.montoAprobado = Montos.normalizar(Validaciones.exigirPositivo(montoAprobado, "monto aprobado"));
        this.plazoMeses = Validaciones.exigirRango(plazoMeses, "plazo en meses",
                SolicitudPrestamo.PLAZO_MINIMO_MESES, SolicitudPrestamo.PLAZO_MAXIMO_MESES);
        this.tasaInteresAnual = Montos.normalizar(Validaciones.exigirRango(tasaInteresAnual,
                "tasa de interes anual", SolicitudPrestamo.TASA_MINIMA, SolicitudPrestamo.TASA_MAXIMA));
        this.cuotaMensual = Montos.normalizar(Validaciones.exigirPositivo(cuotaMensual, "cuota mensual"));
        this.montoTotalAPagar = Montos.normalizar(
                Validaciones.exigirPositivo(montoTotalAPagar, "monto total a pagar"));
        this.totalPagado = Montos.normalizar(Validaciones.exigirNoNegativo(totalPagado, "total pagado"));
        if (this.totalPagado.compareTo(this.montoTotalAPagar) > 0) {
            throw new ValidacionDominioException(
                    "El total pagado no puede exceder el monto total a pagar del prestamo.");
        }
        this.estado = Validaciones.exigirNoNulo(estado, "estado del prestamo");
        this.fechaDesembolso = Validaciones.exigirNoNulo(fechaDesembolso, "fecha de desembolso");
        this.fechaVencimiento = Validaciones.exigirNoNulo(fechaVencimiento, "fecha de vencimiento");
        this.fechaCreacion = Validaciones.exigirNoNulo(fechaCreacion, "fecha de creacion");
    }

    public static Prestamo nuevo(String numeroPrestamo, Long solicitudId, Long clienteId, BigDecimal montoAprobado,
                                 int plazoMeses, BigDecimal tasaInteresAnual, BigDecimal cuotaMensual,
                                 BigDecimal montoTotalAPagar, LocalDate fechaDesembolso, LocalDateTime ahora) {
        Validaciones.exigirNoNulo(ahora, "fecha de operacion");
        Validaciones.exigirNoNulo(fechaDesembolso, "fecha de desembolso");
        return new Prestamo(null, numeroPrestamo, solicitudId, clienteId, montoAprobado, plazoMeses,
                tasaInteresAnual, cuotaMensual, montoTotalAPagar, Montos.CERO, EstadoPrestamo.VIGENTE,
                fechaDesembolso, fechaDesembolso.plusMonths(plazoMeses), ahora);
    }

    public static Prestamo reconstituir(Long id, String numeroPrestamo, Long solicitudId, Long clienteId,
                                        BigDecimal montoAprobado, int plazoMeses, BigDecimal tasaInteresAnual,
                                        BigDecimal cuotaMensual, BigDecimal montoTotalAPagar,
                                        BigDecimal totalPagado, EstadoPrestamo estado, LocalDate fechaDesembolso,
                                        LocalDate fechaVencimiento, LocalDateTime fechaCreacion) {
        Validaciones.exigirNoNulo(id, "identificador del prestamo");
        return new Prestamo(id, numeroPrestamo, solicitudId, clienteId, montoAprobado, plazoMeses, tasaInteresAnual,
                cuotaMensual, montoTotalAPagar, totalPagado, estado, fechaDesembolso, fechaVencimiento,
                fechaCreacion);
    }

    public BigDecimal getSaldoPendiente() {
        return Montos.normalizar(montoTotalAPagar.subtract(totalPagado));
    }

    public BigDecimal getPorcentajePagado() {
        if (montoTotalAPagar.signum() == 0) {
            return Montos.CERO;
        }
        return totalPagado.multiply(CIEN).divide(montoTotalAPagar, Montos.ESCALA, RoundingMode.HALF_UP);
    }

    /** Rechaza el sobrepago para que el saldo nunca quede negativo. */
    public void aplicarPago(BigDecimal monto) {
        Validaciones.exigirPositivo(monto, "monto del pago");
        if (estaLiquidado()) {
            throw new ReglaNegocioException("El prestamo ya esta liquidado");
        }
        BigDecimal saldoPendiente = getSaldoPendiente();
        BigDecimal montoNormalizado = Montos.normalizar(monto);
        if (montoNormalizado.compareTo(saldoPendiente) > 0) {
            throw new ReglaNegocioException("El monto del pago excede el saldo pendiente del prestamo. "
                    + "Saldo disponible: " + saldoPendiente.toPlainString() + ".");
        }
        this.totalPagado = Montos.normalizar(totalPagado.add(montoNormalizado));
        if (getSaldoPendiente().signum() == 0) {
            this.estado = EstadoPrestamo.LIQUIDADO;
        }
    }

    public boolean estaLiquidado() {
        return estado == EstadoPrestamo.LIQUIDADO;
    }

    public Long getId() {
        return id;
    }

    public String getNumeroPrestamo() {
        return numeroPrestamo;
    }

    public Long getSolicitudId() {
        return solicitudId;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public BigDecimal getMontoAprobado() {
        return montoAprobado;
    }

    public int getPlazoMeses() {
        return plazoMeses;
    }

    public BigDecimal getTasaInteresAnual() {
        return tasaInteresAnual;
    }

    public BigDecimal getCuotaMensual() {
        return cuotaMensual;
    }

    public BigDecimal getMontoTotalAPagar() {
        return montoTotalAPagar;
    }

    public BigDecimal getTotalPagado() {
        return totalPagado;
    }

    public EstadoPrestamo getEstado() {
        return estado;
    }

    public LocalDate getFechaDesembolso() {
        return fechaDesembolso;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Prestamo otro)) {
            return false;
        }
        return id != null && id.equals(otro.id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }

    @Override
    public String toString() {
        return "Prestamo{id=" + id + ", numeroPrestamo=" + numeroPrestamo + ", clienteId=" + clienteId
                + ", estado=" + estado + ", saldoPendiente=" + getSaldoPendiente().toPlainString() + "}";
    }
}
