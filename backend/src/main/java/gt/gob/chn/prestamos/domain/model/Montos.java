package gt.gob.chn.prestamos.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Montos {

    // Importes en GTQ a 2 decimales con HALF_UP para que los saldos cuadren al centavo.
    public static final int ESCALA = 2;

    public static final BigDecimal CERO = BigDecimal.ZERO.setScale(ESCALA, RoundingMode.HALF_UP);

    private Montos() {
    }

    /** Propaga null para no ocultar ausencias; ceroSiNulo es la variante para acumulados. */
    public static BigDecimal normalizar(BigDecimal valor) {
        return valor == null ? null : valor.setScale(ESCALA, RoundingMode.HALF_UP);
    }

    public static BigDecimal ceroSiNulo(BigDecimal valor) {
        return valor == null ? CERO : normalizar(valor);
    }
}
