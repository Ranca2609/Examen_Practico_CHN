package gt.gob.chn.prestamos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("NumeroDocumento - numeracion oficial con digito verificador")
class NumeroDocumentoTest {

    @ParameterizedTest(name = "{0} #{1} -> {2}")
    @CsvSource({
            "SOLICITUD_CREDITO, 1, SC-001-2026-000001-3",
            "PRESTAMO,          4, PR-001-2026-000004-3",
            "RECIBO_CAJA,       7, RC-001-2026-000007-1"
    })
    void compone_el_numero_oficial(TipoDocumento tipo, long correlativo, String esperado) {
        assertThat(new NumeroDocumento(tipo, "001", 2026, correlativo).valor()).isEqualTo(esperado);
    }

    @Test
    void el_digito_verificador_es_luhn() {
        // Vector de control clasico del algoritmo de Luhn.
        assertThat(NumeroDocumento.luhn("7992739871")).isEqualTo(3);
    }

    @Test
    void el_correlativo_crece_sin_truncarse_y_se_puede_releer() {
        NumeroDocumento grande = new NumeroDocumento(TipoDocumento.RECIBO_CAJA, "001", 2031, 12_345_678L);

        assertThat(grande.valor()).startsWith("RC-001-2031-12345678-");
        assertThat(NumeroDocumento.interpretar(grande.valor(), TipoDocumento.RECIBO_CAJA)).isEqualTo(grande);
    }

    @Test
    void acepta_un_numero_valido_y_lo_devuelve_sin_espacios() {
        assertThat(NumeroDocumento.exigir("  PR-001-2026-000004-3 ", TipoDocumento.PRESTAMO))
                .isEqualTo("PR-001-2026-000004-3");
    }

    @Test
    void detecta_cualquier_digito_mal_copiado() {
        String valido = "PR-001-2026-000004-3";
        for (int i = 0; i < valido.length(); i++) {
            char original = valido.charAt(i);
            if (!Character.isDigit(original)) {
                continue;
            }
            char otro = original == '9' ? '0' : (char) (original + 1);
            String alterado = valido.substring(0, i) + otro + valido.substring(i + 1);
            assertThatThrownBy(() -> NumeroDocumento.exigir(alterado, TipoDocumento.PRESTAMO))
                    .as("posicion %d: %s", i, alterado)
                    .isInstanceOf(ValidacionDominioException.class);
        }
    }

    @Test
    void detecta_digitos_vecinos_intercambiados() {
        // 000012 -> 000021: el error de digitacion mas comun en ventanilla.
        String valido = new NumeroDocumento(TipoDocumento.RECIBO_CAJA, "001", 2026, 12).valor();
        String transpuesto = valido.replace("000012", "000021");

        assertThatThrownBy(() -> NumeroDocumento.exigir(transpuesto, TipoDocumento.RECIBO_CAJA))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("digito verificador");
    }

    @Test
    void cambiar_solo_el_prefijo_invalida_el_numero() {
        // Mismos digitos que PR-001-2026-000004-3, pero presentado como recibo.
        assertThatThrownBy(() -> NumeroDocumento.exigir("RC-001-2026-000004-3", TipoDocumento.RECIBO_CAJA))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("digito verificador");
    }

    @Test
    void rechaza_el_numero_de_otro_tipo_de_documento() {
        assertThatThrownBy(() -> NumeroDocumento.exigir("SC-001-2026-000001-3", TipoDocumento.PRESTAMO))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("debe iniciar con PR");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "PRE-2026-000004",          // nomenclatura anterior
            "pr-001-2026-000004-3",     // minusculas
            "PR-01-2026-000004-3",      // agencia de dos digitos
            "PR-001-26-000004-3",       // anio abreviado
            "PR-001-2026-00004-3",      // correlativo de cinco digitos
            "PR-001-2026-0000004-3",    // ceros de relleno de mas
            "PR-001-2026-000004",       // sin verificador
            "PR 001 2026 000004 3"      // separadores distintos
    })
    void rechaza_estructuras_invalidas(String texto) {
        assertThatThrownBy(() -> NumeroDocumento.exigir(texto, TipoDocumento.PRESTAMO))
                .isInstanceOf(ValidacionDominioException.class);
    }

    @Test
    void exige_el_numero() {
        assertThatThrownBy(() -> NumeroDocumento.exigir("  ", TipoDocumento.RECIBO_CAJA))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("obligatorio");
    }

    @Test
    void valida_sus_componentes_al_construirse() {
        assertThatThrownBy(() -> new NumeroDocumento(TipoDocumento.PRESTAMO, "1", 2026, 1))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("agencia");
        assertThatThrownBy(() -> new NumeroDocumento(TipoDocumento.PRESTAMO, "001", 2026, 0))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("correlativo");
        assertThatThrownBy(() -> new NumeroDocumento(null, "001", 2026, 1))
                .isInstanceOf(ValidacionDominioException.class);
    }

    @Test
    void las_entidades_no_aceptan_numeros_invalidos() {
        // Un numero de prestamo valido no sirve como recibo.
        assertThatThrownBy(() -> Pago.nuevo("PR-001-2026-000004-3", 1L, new BigDecimal("100.00"),
                FormaPago.EFECTIVO, new BigDecimal("500.00"), new BigDecimal("400.00"), "cajero", null,
                LocalDateTime.of(2026, 3, 10, 9, 0)))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("recibo de caja");
    }
}
