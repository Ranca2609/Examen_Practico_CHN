package gt.gob.chn.prestamos.domain.service;

import java.math.BigDecimal;

public record ResultadoEvaluacion(
        BigDecimal porcentajeComprometido,
        boolean recomendado,
        String observacion,
        long prestamosVigentes) {
}
