package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.entidad;

import gt.gob.chn.prestamos.domain.model.EstadoSolicitud;
import gt.gob.chn.prestamos.domain.model.TipoPrestamo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "solicitudes_prestamo")
public class SolicitudPrestamoEntidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "numero_solicitud", nullable = false, length = 25, unique = true)
    private String numeroSolicitud;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(name = "monto_solicitado", nullable = false, precision = 15, scale = 2)
    private BigDecimal montoSolicitado;

    @Column(name = "plazo_meses", nullable = false)
    private int plazoMeses;

    @Column(name = "tasa_interes_anual", nullable = false, precision = 5, scale = 2)
    private BigDecimal tasaInteresAnual;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_prestamo", nullable = false, length = 20)
    private TipoPrestamo tipoPrestamo;

    @Column(name = "destino", nullable = false, length = 200)
    private String destino;

    @Column(name = "ingreso_mensual_declarado", nullable = false, precision = 15, scale = 2)
    private BigDecimal ingresoMensualDeclarado;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 15)
    private EstadoSolicitud estado;

    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDateTime fechaSolicitud;

    @Column(name = "observaciones", length = 500)
    private String observaciones;

    // Resolucion aplanada: nula mientras la solicitud sigue EN_PROCESO.

    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;

    @Column(name = "usuario_resolucion", length = 50)
    private String usuarioResolucion;

    @Column(name = "monto_aprobado", precision = 15, scale = 2)
    private BigDecimal montoAprobado;

    @Column(name = "plazo_aprobado_meses")
    private Integer plazoAprobadoMeses;

    @Column(name = "tasa_aprobada", precision = 5, scale = 2)
    private BigDecimal tasaAprobada;

    @Column(name = "motivo_resolucion", length = 500)
    private String motivoResolucion;

    protected SolicitudPrestamoEntidad() {
    }

    public SolicitudPrestamoEntidad(Long id, String numeroSolicitud, Long clienteId,
                                    BigDecimal montoSolicitado, int plazoMeses,
                                    BigDecimal tasaInteresAnual, TipoPrestamo tipoPrestamo,
                                    String destino, BigDecimal ingresoMensualDeclarado,
                                    EstadoSolicitud estado, LocalDateTime fechaSolicitud,
                                    String observaciones, LocalDateTime fechaResolucion,
                                    String usuarioResolucion, BigDecimal montoAprobado,
                                    Integer plazoAprobadoMeses, BigDecimal tasaAprobada,
                                    String motivoResolucion) {
        this.id = id;
        this.numeroSolicitud = numeroSolicitud;
        this.clienteId = clienteId;
        this.montoSolicitado = montoSolicitado;
        this.plazoMeses = plazoMeses;
        this.tasaInteresAnual = tasaInteresAnual;
        this.tipoPrestamo = tipoPrestamo;
        this.destino = destino;
        this.ingresoMensualDeclarado = ingresoMensualDeclarado;
        this.estado = estado;
        this.fechaSolicitud = fechaSolicitud;
        this.observaciones = observaciones;
        this.fechaResolucion = fechaResolucion;
        this.usuarioResolucion = usuarioResolucion;
        this.montoAprobado = montoAprobado;
        this.plazoAprobadoMeses = plazoAprobadoMeses;
        this.tasaAprobada = tasaAprobada;
        this.motivoResolucion = motivoResolucion;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNumeroSolicitud() {
        return numeroSolicitud;
    }

    public void setNumeroSolicitud(String numeroSolicitud) {
        this.numeroSolicitud = numeroSolicitud;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public void setClienteId(Long clienteId) {
        this.clienteId = clienteId;
    }

    public BigDecimal getMontoSolicitado() {
        return montoSolicitado;
    }

    public void setMontoSolicitado(BigDecimal montoSolicitado) {
        this.montoSolicitado = montoSolicitado;
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

    public TipoPrestamo getTipoPrestamo() {
        return tipoPrestamo;
    }

    public void setTipoPrestamo(TipoPrestamo tipoPrestamo) {
        this.tipoPrestamo = tipoPrestamo;
    }

    public String getDestino() {
        return destino;
    }

    public void setDestino(String destino) {
        this.destino = destino;
    }

    public BigDecimal getIngresoMensualDeclarado() {
        return ingresoMensualDeclarado;
    }

    public void setIngresoMensualDeclarado(BigDecimal ingresoMensualDeclarado) {
        this.ingresoMensualDeclarado = ingresoMensualDeclarado;
    }

    public EstadoSolicitud getEstado() {
        return estado;
    }

    public void setEstado(EstadoSolicitud estado) {
        this.estado = estado;
    }

    public LocalDateTime getFechaSolicitud() {
        return fechaSolicitud;
    }

    public void setFechaSolicitud(LocalDateTime fechaSolicitud) {
        this.fechaSolicitud = fechaSolicitud;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public LocalDateTime getFechaResolucion() {
        return fechaResolucion;
    }

    public void setFechaResolucion(LocalDateTime fechaResolucion) {
        this.fechaResolucion = fechaResolucion;
    }

    public String getUsuarioResolucion() {
        return usuarioResolucion;
    }

    public void setUsuarioResolucion(String usuarioResolucion) {
        this.usuarioResolucion = usuarioResolucion;
    }

    public BigDecimal getMontoAprobado() {
        return montoAprobado;
    }

    public void setMontoAprobado(BigDecimal montoAprobado) {
        this.montoAprobado = montoAprobado;
    }

    public Integer getPlazoAprobadoMeses() {
        return plazoAprobadoMeses;
    }

    public void setPlazoAprobadoMeses(Integer plazoAprobadoMeses) {
        this.plazoAprobadoMeses = plazoAprobadoMeses;
    }

    public BigDecimal getTasaAprobada() {
        return tasaAprobada;
    }

    public void setTasaAprobada(BigDecimal tasaAprobada) {
        this.tasaAprobada = tasaAprobada;
    }

    public String getMotivoResolucion() {
        return motivoResolucion;
    }

    public void setMotivoResolucion(String motivoResolucion) {
        this.motivoResolucion = motivoResolucion;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof SolicitudPrestamoEntidad entidad)) {
            return false;
        }
        return id != null && id.equals(entidad.id);
    }

    @Override
    public int hashCode() {
        return SolicitudPrestamoEntidad.class.hashCode();
    }
}
