package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import gt.gob.chn.prestamos.domain.model.NumeroDocumento;
import gt.gob.chn.prestamos.domain.model.TipoDocumento;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Datos demo - los numeros oficiales cumplen la regla del dominio")
class NumeracionDatosDemoTest {

    private static final String SCRIPT = "/db/demo/V900__datos_demo.sql";
    private static final Pattern NUMERO = Pattern.compile("\\b(SC|PR|RC)-[0-9]{3}-[0-9]{4}-[0-9]{6,9}-[0-9]\\b");

    @Test
    void cada_numero_del_script_es_valido_para_su_tipo() throws IOException {
        Map<TipoDocumento, List<String>> encontrados = new EnumMap<>(TipoDocumento.class);
        Matcher coincidencia = NUMERO.matcher(leerScript());
        while (coincidencia.find()) {
            TipoDocumento tipo = TipoDocumento.desdeCodigo(coincidencia.group(1));
            encontrados.computeIfAbsent(tipo, t -> new ArrayList<>()).add(coincidencia.group());
        }

        // Sin esta guardia, un cambio de formato dejaria la prueba en verde sin revisar nada.
        assertThat(encontrados).as("el script debe contener numeros de los tres tipos")
                .containsOnlyKeys(TipoDocumento.values());

        // El CHECK de la base solo valida la estructura: un verificador errado solo lo detecta el dominio.
        encontrados.forEach((tipo, numeros) -> numeros.forEach(numero ->
                assertThat(NumeroDocumento.exigir(numero, tipo)).as(numero).isEqualTo(numero)));
    }

    private String leerScript() throws IOException {
        try (InputStream entrada = getClass().getResourceAsStream(SCRIPT)) {
            assertThat(entrada).as("el script de datos demo debe estar en el classpath").isNotNull();
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
