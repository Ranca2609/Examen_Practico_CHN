const ESCALA = 2;

// HALF_UP como BigDecimal.setScale(2, HALF_UP) del backend. toFixed(6) evita que 1.005
// (guardado como 1.00499...) se redondee hacia abajo.
export function redondear(valor, decimales = ESCALA) {
  if (!Number.isFinite(Number(valor))) return 0;

  const factor = 10 ** decimales;
  const escalado = Number((Number(valor) * factor).toFixed(6));
  const signo = escalado < 0 ? -1 : 1;

  return (signo * Math.round(Math.abs(escalado))) / factor;
}

function tasaMensual(tasaAnual) {
  return Number(tasaAnual) / 100 / 12;
}

// Sistema francés: C = M·i / (1 − (1+i)^−n). Con tasa 0 se indetermina y el capital se reparte en partes iguales.
export function calcularCuota(monto, plazoMeses, tasaAnual) {
  const capital = Number(monto);
  const meses = Number.parseInt(plazoMeses, 10);
  const anual = Number(tasaAnual);

  if (!Number.isFinite(capital) || capital <= 0) return 0;
  if (!Number.isInteger(meses) || meses <= 0) return 0;
  if (!Number.isFinite(anual) || anual < 0) return 0;

  const i = tasaMensual(anual);
  if (i === 0) return redondear(capital / meses);

  const cuota = (capital * i) / (1 - Math.pow(1 + i, -meses));
  return Number.isFinite(cuota) ? redondear(cuota) : 0;
}

// Solo vista previa: el plan que vale es siempre el del backend, con la misma fórmula y redondeo.
export function generarPlan(monto, plazoMeses, tasaAnual) {
  const capital = redondear(monto);
  const meses = Number.parseInt(plazoMeses, 10);
  const cuotaMensual = calcularCuota(monto, plazoMeses, tasaAnual);

  // Plan vacío en vez de lanzar: esto corre mientras el usuario todavía escribe.
  if (cuotaMensual <= 0 || !Number.isInteger(meses) || meses <= 0) {
    return { cuotaMensual: 0, totalIntereses: 0, montoTotal: 0, cuotas: [] };
  }

  const i = tasaMensual(tasaAnual);
  const cuotas = [];

  let saldo = capital;
  let totalIntereses = 0;
  let totalPagado = 0;

  for (let numero = 1; numero <= meses; numero += 1) {
    const saldoInicial = saldo;
    const esUltima = numero === meses;

    const abonoInteres = redondear(saldoInicial * i);

    // La última cuota absorbe los centavos del redondeo para cerrar en 0.00, igual que el backend.
    const cuota = esUltima ? redondear(saldoInicial + abonoInteres) : cuotaMensual;
    const abonoCapital = redondear(cuota - abonoInteres);
    const saldoFinal = esUltima ? 0 : redondear(saldoInicial - abonoCapital);

    cuotas.push({
      numero,
      saldoInicial,
      cuota,
      abonoCapital,
      abonoInteres,
      saldoFinal,
    });

    totalIntereses = redondear(totalIntereses + abonoInteres);
    totalPagado = redondear(totalPagado + cuota);
    saldo = saldoFinal;
  }

  return {
    cuotaMensual,
    totalIntereses,
    montoTotal: totalPagado,
    cuotas,
  };
}

// Replica evaluacion.porcentajeComprometido del backend; null si no hay ingreso válido.
export function porcentajeComprometido(cuotaMensual, ingresoMensual) {
  const cuota = Number(cuotaMensual);
  const ingreso = Number(ingresoMensual);

  if (!Number.isFinite(cuota) || !Number.isFinite(ingreso) || ingreso <= 0) return null;

  return redondear((cuota / ingreso) * 100);
}
