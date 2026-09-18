export const SIN_DATO = '—';

const LOCALE = 'es-GT';

// Un BigDecimal serializado puede llegar como cadena ("1500.00").
function aNumero(valor) {
  if (valor === null || valor === undefined || valor === '') return null;
  const numero = typeof valor === 'number' ? valor : Number(valor);
  return Number.isFinite(numero) ? numero : null;
}

// Una fecha sin hora se construye en hora local: leída como UTC se vería un día antes en UTC-6.
function aFecha(valor) {
  if (!valor) return null;
  if (valor instanceof Date) return Number.isNaN(valor.getTime()) ? null : valor;

  const texto = String(valor).trim();
  const soloFecha = /^(\d{4})-(\d{2})-(\d{2})$/.exec(texto);
  if (soloFecha) {
    return new Date(Number(soloFecha[1]), Number(soloFecha[2]) - 1, Number(soloFecha[3]));
  }

  const fecha = new Date(texto);
  return Number.isNaN(fecha.getTime()) ? null : fecha;
}

const formateadorMoneda = new Intl.NumberFormat(LOCALE, {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

// Símbolo puesto a mano: con style:'currency' GTQ sale como "Q", "GTQ" o con otros espacios según el ICU del navegador.
export function moneda(valor) {
  const numero = aNumero(valor);
  if (numero === null) return SIN_DATO;

  const signo = numero < 0 ? '-' : '';
  return `${signo}Q ${formateadorMoneda.format(Math.abs(numero))}`;
}

export function monedaCorta(valor) {
  const numero = aNumero(valor);
  if (numero === null) return SIN_DATO;

  const signo = numero < 0 ? '-' : '';
  const entero = new Intl.NumberFormat(LOCALE, { maximumFractionDigits: 0 }).format(Math.abs(numero));
  return `${signo}Q ${entero}`;
}

export function numero(valor, decimales = 0) {
  const parseado = aNumero(valor);
  if (parseado === null) return SIN_DATO;

  return new Intl.NumberFormat(LOCALE, {
    minimumFractionDigits: decimales,
    maximumFractionDigits: decimales,
  }).format(parseado);
}

// Recibe el valor ya en escala de porcentaje (12.5 = 12.5 %), como lo entrega el backend.
export function porcentaje(valor, decimales = 2) {
  const parseado = aNumero(valor);
  if (parseado === null) return SIN_DATO;

  return `${new Intl.NumberFormat(LOCALE, {
    minimumFractionDigits: decimales,
    maximumFractionDigits: decimales,
  }).format(parseado)} %`;
}

const doble = (valor) => String(valor).padStart(2, '0');

export function fecha(valor) {
  const parseada = aFecha(valor);
  if (!parseada) return SIN_DATO;

  return `${doble(parseada.getDate())}/${doble(parseada.getMonth() + 1)}/${parseada.getFullYear()}`;
}

export function fechaHora(valor) {
  const parseada = aFecha(valor);
  if (!parseada) return SIN_DATO;

  return `${fecha(parseada)} ${doble(parseada.getHours())}:${doble(parseada.getMinutes())}`;
}

export function fechaISOParaInput(valor) {
  const parseada = aFecha(valor);
  if (!parseada) return '';

  return `${parseada.getFullYear()}-${doble(parseada.getMonth() + 1)}-${doble(parseada.getDate())}`;
}

export function hoyParaInput() {
  return fechaISOParaInput(new Date());
}

// plazo(30) -> "30 meses (2 años y 6 meses)"
export function plazo(meses) {
  const total = aNumero(meses);
  if (total === null) return SIN_DATO;

  const cantidad = Math.trunc(total);
  const textoMeses = `${cantidad} ${cantidad === 1 ? 'mes' : 'meses'}`;
  if (cantidad < 12) return textoMeses;

  const anios = Math.floor(cantidad / 12);
  const resto = cantidad % 12;
  const textoAnios = `${anios} ${anios === 1 ? 'año' : 'años'}`;
  const textoResto = resto > 0 ? ` y ${resto} ${resto === 1 ? 'mes' : 'meses'}` : '';

  return `${textoMeses} (${textoAnios}${textoResto})`;
}

export function iniciales(nombreCompleto) {
  if (!nombreCompleto) return '';

  const partes = String(nombreCompleto).trim().split(/\s+/).filter(Boolean);
  if (partes.length === 0) return '';
  if (partes.length === 1) return partes[0].charAt(0).toUpperCase();

  return (partes[0].charAt(0) + partes[partes.length - 1].charAt(0)).toUpperCase();
}

export function recortar(texto, maximo = 60) {
  if (!texto) return SIN_DATO;

  const limpio = String(texto).trim();
  return limpio.length <= maximo ? limpio : `${limpio.slice(0, maximo - 1)}…`;
}
