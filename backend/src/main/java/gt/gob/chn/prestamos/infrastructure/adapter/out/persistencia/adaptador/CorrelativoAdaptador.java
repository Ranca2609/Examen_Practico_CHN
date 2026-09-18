package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.adaptador;

import gt.gob.chn.prestamos.domain.model.NumeroDocumento;
import gt.gob.chn.prestamos.domain.model.TipoDocumento;
import gt.gob.chn.prestamos.domain.port.out.CorrelativoPort;
import gt.gob.chn.prestamos.domain.port.out.RelojPort;
import gt.gob.chn.prestamos.infrastructure.config.PropiedadesAplicacion;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

@Component
public class CorrelativoAdaptador implements CorrelativoPort {

    // Secuencias y no MAX(id)+1: son atomicas ante registros concurrentes.
    // NEXT VALUE FOR no admite parametros, por eso las sentencias son literales.
    private static final String SQL_SECUENCIA_SOLICITUD = "SELECT NEXT VALUE FOR dbo.seq_solicitud";
    private static final String SQL_SECUENCIA_PRESTAMO = "SELECT NEXT VALUE FOR dbo.seq_prestamo";
    private static final String SQL_SECUENCIA_RECIBO = "SELECT NEXT VALUE FOR dbo.seq_recibo";

    private final EntityManager gestorEntidades;
    private final RelojPort reloj;
    private final String agencia;

    public CorrelativoAdaptador(EntityManager gestorEntidades, RelojPort reloj, PropiedadesAplicacion propiedades) {
        this.gestorEntidades = gestorEntidades;
        this.reloj = reloj;
        this.agencia = propiedades.correlativos().agencia();
    }

    @Override
    public String siguienteNumeroSolicitud() {
        return emitir(TipoDocumento.SOLICITUD_CREDITO, SQL_SECUENCIA_SOLICITUD);
    }

    @Override
    public String siguienteNumeroPrestamo() {
        return emitir(TipoDocumento.PRESTAMO, SQL_SECUENCIA_PRESTAMO);
    }

    @Override
    public String siguienteNumeroRecibo() {
        return emitir(TipoDocumento.RECIBO_CAJA, SQL_SECUENCIA_RECIBO);
    }

    private String emitir(TipoDocumento tipo, String sentencia) {
        long consecutivo = siguienteValor(sentencia);
        return new NumeroDocumento(tipo, agencia, reloj.hoy().getYear(), consecutivo).valor();
    }

    // El driver puede devolver Integer, Long o BigDecimal segun el tipo de la secuencia.
    private long siguienteValor(String sentencia) {
        Number valor = (Number) gestorEntidades.createNativeQuery(sentencia).getSingleResult();
        return valor.longValue();
    }
}
