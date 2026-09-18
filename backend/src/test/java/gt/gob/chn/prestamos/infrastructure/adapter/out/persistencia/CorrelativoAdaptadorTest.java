package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gt.gob.chn.prestamos.domain.port.out.RelojPort;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.adaptador.CorrelativoAdaptador;
import gt.gob.chn.prestamos.infrastructure.config.PropiedadesAplicacion;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CorrelativoAdaptador - emision de numeros oficiales")
class CorrelativoAdaptadorTest {

    private final EntityManager gestorEntidades = mock(EntityManager.class);
    private final RelojPort reloj = mock(RelojPort.class);

    @BeforeEach
    void anioEnCurso() {
        when(reloj.hoy()).thenReturn(LocalDate.of(2026, 9, 18));
    }

    @Test
    void cada_documento_usa_su_secuencia_y_su_prefijo() {
        secuenciaDevuelve("seq_solicitud", 11L);
        secuenciaDevuelve("seq_prestamo", new BigDecimal("5"));
        secuenciaDevuelve("seq_recibo", 8);
        CorrelativoAdaptador adaptador = adaptadorDeAgencia("001");

        assertThat(adaptador.siguienteNumeroSolicitud()).isEqualTo("SC-001-2026-000011-2");
        assertThat(adaptador.siguienteNumeroPrestamo()).isEqualTo("PR-001-2026-000005-0");
        assertThat(adaptador.siguienteNumeroRecibo()).isEqualTo("RC-001-2026-000008-9");
    }

    @Test
    void la_agencia_configurada_forma_parte_del_numero() {
        secuenciaDevuelve("seq_recibo", 1L);

        assertThat(adaptadorDeAgencia("015").siguienteNumeroRecibo()).startsWith("RC-015-2026-000001-");
    }

    private CorrelativoAdaptador adaptadorDeAgencia(String agencia) {
        // Solo el bloque de correlativos le interesa al adaptador.
        PropiedadesAplicacion propiedades = new PropiedadesAplicacion(
                null, null, false, null, new PropiedadesAplicacion.Correlativos(agencia));
        return new CorrelativoAdaptador(gestorEntidades, reloj, propiedades);
    }

    private void secuenciaDevuelve(String secuencia, Number valor) {
        Query consulta = mock(Query.class);
        when(consulta.getSingleResult()).thenReturn(valor);
        when(gestorEntidades.createNativeQuery("SELECT NEXT VALUE FOR dbo." + secuencia)).thenReturn(consulta);
    }
}
