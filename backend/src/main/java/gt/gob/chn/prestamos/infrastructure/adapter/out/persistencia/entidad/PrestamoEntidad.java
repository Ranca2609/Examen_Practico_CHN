package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.entidad;

import gt.gob.chn.prestamos.domain.model.EstadoPrestamo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "prestamos")
public class PrestamoEntidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "numero_prestamo", nullable = false, length = 25, unique = true)
    private String numeroPrestamo;

    @Column(name = "solicitud_id", nullable = false, unique = true)
    private Long solicitudId;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(name = "monto_aprobado", nullable = false, precision = 15, scale = 2)
    private BigDecimal montoAprobado;

    @Column(name = "plazo_meses", nullable = false)
    private int plazoMeses;

    @Column(name = "tasa_interes_anual", nullable = false, precision = 5, scale = 2)
    private BigDecimal tasaInteresAnual;

    @Column(name = "cuota_mensual", nullable = false, precision = 15, scale = 2)
    private BigDecimal cuotaMensual;

    @Column(name = "monto_total_a_pagar", nullable = false, precision = 15, scale = 2)
    private BigDecimal montoTotalAPagar;

    @Column(name = "total_pagado", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalPagado;

    // saldo_pendiente no se mapea: es columna calculada PERSISTED y SQL Server rechazaria
    // el INSERT/UPDATE que JPA generaria sobre ella.

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 15)
    private EstadoPrestamo estado;

    @Column(name = "fecha_desembolso", nullable = false)
    private LocalDate fechaDesembolso;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    protected PrestamoEntidad() {
    }

    public PrestamoEntidad(Long id, String numeroPrestamo, Long solicitudId, Long clienteId,
                           BigDecimal montoAprobado, int plazoMeses, BigDecimal tasaInteresAnual,
                           BigDecimal cuotaMensual, BigDecimal montoTotalAPagar,
                           BigDecimal totalPagado, EstadoPrestamo estado, LocalDate fechaDesembolso,
                           LocalDate fechaVencimiento, LocalDateTime fechaCreacion) {
        this.id = id;
        this.numeroPrestamo = numeroPrestamo;
        this.solicitudId = solicitudId;
        this.clienteId = clienteId;
        this.montoAprobado = montoAprobado;
        this.plazoMeses = plazoMeses;
        this.tasaInteresAnual = tasaInteresAnual;
        this.cuotaMensual = cuotaMensual;
        this.montoTotalAPagar = montoTotalAPagar;
        this.totalPagado = totalPagado;
        this.estado = estado;
        this.fechaDesembolso = fechaDesembolso;
        this.fechaVencimiento = fechaVencimiento;
        this.fechaCreacion = fechaCreacion;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNumeroPrestamo() {
        return numeroPrestamo;
    }

    public void setNumeroPrestamo(String numeroPrestamo) {
        this.numeroPrestamo = numeroPrestamo;
    }

    public Long getSolicitudId() {
        return solicitudId;
    }

    public void setSolicitudId(Long solicitudId) {
        this.solicitudId = solicitudId;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public void setClienteId(Long clienteId) {
        this.clienteId = clienteId;
    }

    public BigDecimal getMontoAprobado() {
        return montoAprobado;
    }

    public void setMontoAprobado(BigDecimal montoAprobado) {
        this.montoAprobado = montoAprobado;
    }

    public int getPlazoMeses() {
        return plazoMeses;
    }

    public void setPlazoMeses(int plazoMeses) {
        this.plazoMeses = plazoMeses;
    }

    public BigDecimal getTasaInteresAnual() {
        return tasaInteresAnual;
    }

    public void setTasaInteresAnual(BigDecimal tasaInteresAnual) {
        this.tasaInteresAnual = tasaInteresAnual;
    }

    public BigDecimal getCuotaMensual() {
        return cuotaMensual;
    }

    public void setCuotaMensual(BigDecimal cuotaMensual) {
        this.cuotaMensual = cuotaMensual;
    }

    public BigDecimal getMontoTotalAPagar() {
        return montoTotalAPagar;
    }

    public void setMontoTotalAPagar(BigDecimal montoTotalAPagar) {
        this.montoTotalAPagar = montoTotalAPagar;
    }

    public BigDecimal getTotalPagado() {
        return totalPagado;
    }

    public void setTotalPagado(BigDecimal totalPagado) {
        this.totalPagado = totalPagado;
    }

    public EstadoPrestamo getEstado() {
        return estado;
    }

    public void setEstado(EstadoPrestamo estado) {
        this.estado = estado;
    }

    public LocalDate getFechaDesembolso() {
        return fechaDesembolso;
    }

    public void setFechaDesembolso(LocalDate fechaDesembolso) {
        this.fechaDesembolso = fechaDesembolso;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public void setFechaVencimiento(LocalDate fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof PrestamoEntidad entidad)) {
            return false;
        }
        return id != null && id.equals(entidad.id);
    }

    @Override
    public int hashCode() {
        return PrestamoEntidad.class.hashCode();
    }
}
