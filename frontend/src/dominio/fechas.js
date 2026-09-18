export const MESES_CORTOS = [
  'ene', 'feb', 'mar', 'abr', 'may', 'jun',
  'jul', 'ago', 'sep', 'oct', 'nov', 'dic',
];

export const MESES_LARGOS = [
  'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
  'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
];

// La semana empieza en lunes.
export const DIAS_CORTOS = ['lu', 'ma', 'mi', 'ju', 'vi', 'sa', 'do'];

// Las fechas se manejan como números (año, mes0, día), no como Date: un ISO sin hora
// leído como UTC se muestra un día antes en Guatemala (UTC-6).
const DIAS_POR_MES = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];

const ANIO_MINIMO = 1;
const ANIO_MAXIMO = 9999;

function entero(valor) {
  if (typeof valor !== 'number' && typeof valor !== 'string') return null;
  const texto = String(valor).trim();
  if (texto === '') return null;
  const numero = Number(texto);
  return Number.isInteger(numero) ? numero : null;
}

function rellenar(numero, largo) {
  return String(numero).padStart(largo, '0');
}

function esBisiesto(anio) {
  return (anio % 4 === 0 && anio % 100 !== 0) || anio % 400 === 0;
}

function soloDigitos(texto) {
  return String(texto).replace(/\D+/g, '');
}

export function diasDelMes(anio, mes0) {
  const a = entero(anio);
  const m = entero(mes0);
  if (a === null || m === null) return 0;
  if (a < ANIO_MINIMO || a > ANIO_MAXIMO || m < 0 || m > 11) return 0;
  if (m === 1 && esBisiesto(a)) return 29;
  return DIAS_POR_MES[m];
}

// setUTCFullYear fija año, mes y día de una vez: sin desbordes intermedios y sin que
// los años de dos cifras se lean como 19xx, como haría new Date(99, 0, 1).
export function indiceDiaSemanaLunes(anio, mes0, dia) {
  const a = entero(anio);
  const m = entero(mes0);
  const d = entero(dia);
  if (a === null || m === null || d === null) return null;
  if (d < 1 || d > diasDelMes(a, m)) return null;

  const fecha = new Date(Date.UTC(2000, 0, 1));
  fecha.setUTCFullYear(a, m, d);
  if (Number.isNaN(fecha.getTime())) return null;

  // getUTCDay() empieza en domingo; se rota para que 0 sea lunes.
  return (fecha.getUTCDay() + 6) % 7;
}

// 42 celdas bastan siempre: el peor caso es un mes de 31 días que empieza en domingo (37).
export function rejillaDelMes(anio, mes0) {
  const rejilla = new Array(42).fill(null);
  const total = diasDelMes(anio, mes0);
  if (total === 0) return rejilla;

  const desplazamiento = indiceDiaSemanaLunes(anio, mes0, 1);
  if (desplazamiento === null) return rejilla;

  for (let dia = 1; dia <= total; dia += 1) {
    rejilla[desplazamiento + dia - 1] = dia;
  }
  return rejilla;
}

// No recorta el día al mes: aIso(2023, 1, 29) devuelve ''.
export function aIso(anio, mes0, dia) {
  const a = entero(anio);
  const m = entero(mes0);
  const d = entero(dia);
  if (a === null || m === null || d === null) return '';
  if (d < 1 || d > diasDelMes(a, m)) return '';

  return `${rellenar(a, 4)}-${rellenar(m + 1, 2)}-${rellenar(d, 2)}`;
}

export function esIsoValido(iso) {
  if (typeof iso !== 'string') return false;
  const partes = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso.trim());
  if (!partes) return false;

  const anio = Number(partes[1]);
  const mes = Number(partes[2]);
  const dia = Number(partes[3]);
  if (mes < 1 || mes > 12) return false;
  return dia >= 1 && dia <= diasDelMes(anio, mes - 1);
}

export function desdeIso(iso) {
  if (!esIsoValido(iso)) return null;
  const texto = iso.trim();
  return {
    anio: Number(texto.slice(0, 4)),
    mes0: Number(texto.slice(5, 7)) - 1,
    dia: Number(texto.slice(8, 10)),
  };
}

export function isoAVisible(iso) {
  const partes = desdeIso(iso);
  if (!partes) return '';
  return `${rellenar(partes.dia, 2)}/${rellenar(partes.mes0 + 1, 2)}/${rellenar(partes.anio, 4)}`;
}

// Acepta aaaa-mm-dd pegado o autocompletado además de dd/mm/aaaa. Con ocho dígitos
// seguidos se prueba ddmmaaaa y, si esa fecha no existe, aaaammdd.
export function visibleAIso(texto) {
  if (typeof texto !== 'string' && typeof texto !== 'number') return null;
  const limpio = String(texto).trim();
  if (limpio === '') return null;

  const anioPrimero = /^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})$/.exec(limpio);
  if (anioPrimero) {
    const iso = aIso(Number(anioPrimero[1]), Number(anioPrimero[2]) - 1, Number(anioPrimero[3]));
    return iso === '' ? null : iso;
  }

  const diaPrimero = /^(\d{1,2})[-/.](\d{1,2})[-/.](\d{4})$/.exec(limpio);
  if (diaPrimero) {
    const iso = aIso(Number(diaPrimero[3]), Number(diaPrimero[2]) - 1, Number(diaPrimero[1]));
    return iso === '' ? null : iso;
  }

  if (/^\d{8}$/.test(limpio)) {
    const comoDiaPrimero = aIso(
      Number(limpio.slice(4, 8)),
      Number(limpio.slice(2, 4)) - 1,
      Number(limpio.slice(0, 2)),
    );
    if (comoDiaPrimero !== '') return comoDiaPrimero;

    const comoAnioPrimero = aIso(
      Number(limpio.slice(0, 4)),
      Number(limpio.slice(4, 6)) - 1,
      Number(limpio.slice(6, 8)),
    );
    return comoAnioPrimero === '' ? null : comoAnioPrimero;
  }

  return null;
}

// La barra solo aparece si hay dígitos detrás ('15', no '15/'): así el retroceso no se
// atasca volviendo a poner el separador.
export function enmascararFecha(textoCrudo) {
  if (textoCrudo === null || textoCrudo === undefined) return '';

  const digitos = soloDigitos(textoCrudo).slice(0, 8);
  if (digitos.length === 0) return '';

  let resultado;
  if (digitos.length <= 2) {
    resultado = digitos;
  } else if (digitos.length <= 4) {
    resultado = `${digitos.slice(0, 2)}/${digitos.slice(2)}`;
  } else {
    resultado = `${digitos.slice(0, 2)}/${digitos.slice(2, 4)}/${digitos.slice(4)}`;
  }
  return resultado.slice(0, 10);
}

// Hora local, no UTC: en UTC-6 restaría un día por la noche.
export function hoyIso() {
  const ahora = new Date();
  return aIso(ahora.getFullYear(), ahora.getMonth(), ahora.getDate());
}

// Las fechas vacías o inválidas quedan al final al ordenar. El ISO se ordena bien como texto.
export function comparaIso(a, b) {
  const aValido = esIsoValido(a);
  const bValido = esIsoValido(b);
  if (!aValido && !bValido) return 0;
  if (!aValido) return 1;
  if (!bValido) return -1;

  const uno = a.trim();
  const otro = b.trim();
  if (uno === otro) return 0;
  return uno < otro ? -1 : 1;
}

// Límites inclusivos; un min o max vacío o inválido significa "sin ese límite".
export function dentroDeRango(iso, min, max) {
  if (!esIsoValido(iso)) return false;
  const fecha = iso.trim();

  if (esIsoValido(min) && fecha < min.trim()) return false;
  if (esIsoValido(max) && fecha > max.trim()) return false;
  return true;
}

export function sumarDias(iso, dias) {
  const partes = desdeIso(iso);
  const n = entero(dias);
  if (!partes || n === null) return '';

  const fecha = new Date(Date.UTC(2000, 0, 1));
  fecha.setUTCFullYear(partes.anio, partes.mes0, partes.dia + n);
  if (Number.isNaN(fecha.getTime())) return '';

  return aIso(fecha.getUTCFullYear(), fecha.getUTCMonth(), fecha.getUTCDate());
}

// Recorta al último día del mes destino: '2024-01-31' + 1 -> '2024-02-29', no el 2 de marzo.
export function sumarMeses(iso, meses) {
  const partes = desdeIso(iso);
  const n = entero(meses);
  if (!partes || n === null) return '';

  const totalMeses = partes.anio * 12 + partes.mes0 + n;
  const anio = Math.floor(totalMeses / 12);
  const mes0 = totalMeses - anio * 12;
  const tope = diasDelMes(anio, mes0);
  if (tope === 0) return '';

  return aIso(anio, mes0, Math.min(partes.dia, tope));
}
