package gt.gob.chn.prestamos.infrastructure.adapter.out.reporte;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import gt.gob.chn.prestamos.domain.model.reporte.ValorCelda;

final class FormatoValores {

    static final Locale GUATEMALA = Locale.of("es", "GT");

    // El símbolo se antepone aparte para dejar el espacio de "Q 1,234.56".
    private static final String PATRON_IMPORTE = "#,##0.00";

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy", GUATEMALA);
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", GUATEMALA);

    private FormatoValores() {
    }

    // Sin default a propósito: un tipo nuevo de ValorCelda debe romper la compilación aquí.
    static String comoTexto(ValorCelda celda) {
        if (celda == null) {
            return "";
        }
        return switch (celda) {
            case ValorCelda.Texto(String valor) -> valor == null ? "" : valor;
            case ValorCelda.Entero(int valor) -> Integer.toString(valor);
            case ValorCelda.Moneda(BigDecimal valor) -> moneda(valor);
            case ValorCelda.Porcentaje(BigDecimal valor) -> porcentaje(valor);
            case ValorCelda.Fecha(LocalDate valor) -> fecha(valor);
            case ValorCelda.FechaHora(LocalDateTime valor) -> fechaHora(valor);
        };
    }

    static String moneda(BigDecimal valor) {
        return valor == null ? "" : "Q " + formateador(PATRON_IMPORTE).format(valor);
    }

    static String porcentaje(BigDecimal valor) {
        return valor == null ? "" : formateador(PATRON_IMPORTE).format(valor) + " %";
    }

    static String fecha(LocalDate valor) {
        return valor == null ? "" : FECHA.format(valor);
    }

    static String fechaHora(LocalDateTime valor) {
        return valor == null ? "" : FECHA_HORA.format(valor);
    }

    // DecimalFormat no es thread-safe: instancia nueva por llamada. Los separadores se fijan
    // a mano para que una actualización del CLDR de es-GT no cambie el formato exigido.
    private static DecimalFormat formateador(String patron) {
        DecimalFormatSymbols simbolos = DecimalFormatSymbols.getInstance(GUATEMALA);
        simbolos.setGroupingSeparator(',');
        simbolos.setDecimalSeparator('.');
        return new DecimalFormat(patron, simbolos);
    }
}
