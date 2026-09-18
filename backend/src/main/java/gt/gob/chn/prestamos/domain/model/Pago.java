package gt.gob.chn.prestamos.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class Pago {

    private static final int OBSERVACIONES_MAXIMO = 300;

    private final Long id;
    private final String numeroRecibo;
    private final Long prestamoId;
    private final BigDecimal monto;
    private final LocalDateTime fechaPago;
    private final FormaPago formaPago;
    // Saldos congelados al registrar: el recibo se audita sin recalcular.
    private final BigDecimal saldoAnterior;
    private final BigDecimal saldoPosterior;
    private final String usuarioRegistro;
    private final String observaciones;

    private Pago(Long id, String numeroRecibo, Long prestamoId, BigDecimal monto, LocalDateTime fechaPago,
                 FormaPago formaPago, BigDecimal saldoAnterior, BigDecimal saldoPosterior,
                 String usuarioRegistro, String observaciones) {
        this.id = id;
        this.numeroRecibo = NumeroDocumento.exigir(numeroRecibo, TipoDocumento.RECIBO_CAJA);
        this.prestamoId = Validaciones.exigirNoNulo(prestamoId, "prestamo");
        this.monto = Montos.normalizar(Validaciones.exigirPositivo(monto, "monto del pago"));
        this.fechaPago = Validaciones.exigirNoNulo(fechaPago, "fecha del pago");
        this.formaPago = Validaciones.exigirNoNulo(formaPago, "forma de pago");
        this.saldoAnterior = Montos.normalizar(Validaciones.exigirNoNegativo(saldoAnterior, "saldo anterior"));
        this.saldoPosterior = Montos.normalizar(Validaciones.exigirNoNegativo(saldoPosterior, "saldo posterior"));
        this.usuarioRegistro = Validaciones.exigirTexto(usuarioRegistro, "usuario que registra el pago", 3, 50);
        this.observaciones = Validaciones.exigirTextoOpcional(observaciones, "observaciones", OBSERVACIONES_MAXIMO);
    }

    public static Pago nuevo(String numeroRecibo, Long prestamoId, BigDecimal monto, FormaPago formaPago,
                             BigDecimal saldoAnterior, BigDecimal saldoPosterior, String usuarioRegistro,
                             String observaciones, LocalDateTime fechaPago) {
        return new Pago(null, numeroRecibo, prestamoId, monto, fechaPago, formaPago, saldoAnterior,
                saldoPosterior, usuarioRegistro, observaciones);
    }

    public static Pago reconstituir(Long id, String numeroRecibo, Long prestamoId, BigDecimal monto,
                                    LocalDateTime fechaPago, FormaPago formaPago, BigDecimal saldoAnterior,
                                    BigDecimal saldoPosterior, String usuarioRegistro, String observaciones) {
        Validaciones.exigirNoNulo(id, "identificador del pago");
        return new Pago(id, numeroRecibo, prestamoId, monto, fechaPago, formaPago, saldoAnterior, saldoPosterior,
                usuarioRegistro, observaciones);
    }

    public Long getId() {
        return id;
    }

    public String getNumeroRecibo() {
        return numeroRecibo;
    }

    public Long getPrestamoId() {
        return prestamoId;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public LocalDateTime getFechaPago() {
        return fechaPago;
    }

    public FormaPago getFormaPago() {
        return formaPago;
    }

    public BigDecimal getSaldoAnterior() {
        return saldoAnterior;
    }

    public BigDecimal getSaldoPosterior() {
        return saldoPosterior;
    }

    public String getUsuarioRegistro() {
        return usuarioRegistro;
    }

    public String getObservaciones() {
        return observaciones;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Pago otro)) {
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
        return "Pago{id=" + id + ", numeroRecibo=" + numeroRecibo + ", prestamoId=" + prestamoId
                + ", monto=" + monto.toPlainString() + "}";
    }
}
