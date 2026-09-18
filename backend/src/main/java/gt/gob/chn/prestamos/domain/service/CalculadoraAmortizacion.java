package gt.gob.chn.prestamos.domain.service;

import gt.gob.chn.prestamos.domain.model.Montos;
import gt.gob.chn.prestamos.domain.model.Validaciones;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

public class CalculadoraAmortizacion {

    /** Precision de los pasos intermedios; el resultado siempre se redondea a 2 decimales. */
    private static final MathContext PRECISION = MathContext.DECIMAL64;

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal MESES_DEL_ANIO = new BigDecimal("12");
    private static final int PLAZO_MAXIMO_SOPORTADO = 600;

    public PlanAmortizacion calcular(BigDecimal monto, int plazoMeses, BigDecimal tasaAnual) {
        BigDecimal cuotaTeorica = calcularCuotaMensual(monto, plazoMeses, tasaAnual);
        BigDecimal tasaMensual = tasaMensual(tasaAnual);

        List<CuotaAmortizacion> cuotas = new ArrayList<>(plazoMeses);
        BigDecimal saldo = Montos.normalizar(monto);
        BigDecimal totalIntereses = Montos.CERO;
        BigDecimal montoTotal = Montos.CERO;

        for (int numero = 1; numero <= plazoMeses; numero++) {
            BigDecimal saldoInicial = saldo;
            BigDecimal abonoInteres = Montos.normalizar(saldoInicial.multiply(tasaMensual, PRECISION));
            BigDecimal abonoCapital = Montos.normalizar(cuotaTeorica.subtract(abonoInteres));
            BigDecimal cuotaPeriodo = cuotaTeorica;

            // La ultima cuota absorbe el residuo de redondeo: los capitales suman el monto al centavo.
            if (numero == plazoMeses || abonoCapital.compareTo(saldoInicial) >= 0) {
                abonoCapital = saldoInicial;
                cuotaPeriodo = Montos.normalizar(abonoCapital.add(abonoInteres));
            }

            saldo = Montos.normalizar(saldoInicial.subtract(abonoCapital));
            totalIntereses = Montos.normalizar(totalIntereses.add(abonoInteres));
            montoTotal = Montos.normalizar(montoTotal.add(cuotaPeriodo));
            cuotas.add(new CuotaAmortizacion(
                    numero, saldoInicial, cuotaPeriodo, abonoCapital, abonoInteres, saldo));
        }

        return new PlanAmortizacion(cuotaTeorica, totalIntereses, montoTotal, cuotas);
    }

    /** Sistema frances: cuota = monto * i / (1 - (1 + i)^-n), con i = tasa anual / 100 / 12. */
    public BigDecimal calcularCuotaMensual(BigDecimal monto, int plazoMeses, BigDecimal tasaAnual) {
        validarParametros(monto, plazoMeses, tasaAnual);
        BigDecimal tasaMensual = tasaMensual(tasaAnual);
        if (tasaMensual.signum() == 0) {
            return Montos.normalizar(monto.divide(new BigDecimal(plazoMeses), PRECISION));
        }
        BigDecimal factor = BigDecimal.ONE.add(tasaMensual).pow(plazoMeses, PRECISION);
        BigDecimal denominador = BigDecimal.ONE.subtract(BigDecimal.ONE.divide(factor, PRECISION), PRECISION);
        return Montos.normalizar(monto.multiply(tasaMensual, PRECISION).divide(denominador, PRECISION));
    }

    private BigDecimal tasaMensual(BigDecimal tasaAnual) {
        return tasaAnual.divide(CIEN, PRECISION).divide(MESES_DEL_ANIO, PRECISION);
    }

    private void validarParametros(BigDecimal monto, int plazoMeses, BigDecimal tasaAnual) {
        Validaciones.exigirPositivo(monto, "monto a financiar");
        Validaciones.exigirRango(plazoMeses, "plazo en meses", 1, PLAZO_MAXIMO_SOPORTADO);
        Validaciones.exigirNoNegativo(tasaAnual, "tasa de interes anual");
    }
}
