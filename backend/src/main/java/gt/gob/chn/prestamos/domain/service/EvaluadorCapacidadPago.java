package gt.gob.chn.prestamos.domain.service;

import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;
import gt.gob.chn.prestamos.domain.model.Montos;
import gt.gob.chn.prestamos.domain.model.Validaciones;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class EvaluadorCapacidadPago {

    public static final BigDecimal PORCENTAJE_MAXIMO_ENDEUDAMIENTO = new BigDecimal("40");

    /** Limite exclusivo: al alcanzarlo ya no se recomienda el credito. */
    public static final int MAXIMO_PRESTAMOS_VIGENTES = 3;

    private static final BigDecimal CIEN = new BigDecimal("100");

    // Es una recomendacion, no un bloqueo: la decision final la toma el analista.
    public ResultadoEvaluacion evaluar(BigDecimal ingresoMensual, BigDecimal cuotaMensual, long prestamosVigentes) {
        Validaciones.exigirPositivo(ingresoMensual, "ingreso mensual");
        Validaciones.exigirPositivo(cuotaMensual, "cuota mensual");
        if (prestamosVigentes < 0) {
            throw new ValidacionDominioException("La cantidad de prestamos vigentes no puede ser negativa.");
        }

        BigDecimal porcentajeComprometido = Montos.normalizar(cuotaMensual)
                .multiply(CIEN)
                .divide(Montos.normalizar(ingresoMensual), Montos.ESCALA, RoundingMode.HALF_UP);

        boolean dentroDeCapacidad =
                porcentajeComprometido.compareTo(PORCENTAJE_MAXIMO_ENDEUDAMIENTO) <= 0;
        boolean dentroDelLimiteDePrestamos = prestamosVigentes < MAXIMO_PRESTAMOS_VIGENTES;
        boolean recomendado = dentroDeCapacidad && dentroDelLimiteDePrestamos;

        return new ResultadoEvaluacion(porcentajeComprometido, recomendado,
                describir(porcentajeComprometido, prestamosVigentes, dentroDeCapacidad,
                        dentroDelLimiteDePrestamos),
                prestamosVigentes);
    }

    private String describir(BigDecimal porcentaje, long prestamosVigentes, boolean dentroDeCapacidad,
                             boolean dentroDelLimiteDePrestamos) {
        if (dentroDeCapacidad && dentroDelLimiteDePrestamos) {
            return "La cuota compromete el " + porcentaje.toPlainString()
                    + "% del ingreso declarado, dentro del limite institucional del "
                    + PORCENTAJE_MAXIMO_ENDEUDAMIENTO.toPlainString() + "%. El cliente registra "
                    + prestamosVigentes + " prestamo(s) vigente(s).";
        }

        StringBuilder motivo = new StringBuilder("No se recomienda el otorgamiento: ");
        if (!dentroDeCapacidad) {
            motivo.append("la cuota compromete el ").append(porcentaje.toPlainString())
                    .append("% del ingreso declarado y supera el limite del ")
                    .append(PORCENTAJE_MAXIMO_ENDEUDAMIENTO.toPlainString()).append("%");
        }
        if (!dentroDeCapacidad && !dentroDelLimiteDePrestamos) {
            motivo.append("; ademas, ");
        }
        if (!dentroDelLimiteDePrestamos) {
            motivo.append("el cliente ya registra ").append(prestamosVigentes)
                    .append(" prestamo(s) vigente(s) y el maximo permitido es ")
                    .append(MAXIMO_PRESTAMOS_VIGENTES);
        }
        return motivo.append(".").toString();
    }
}
