import { numero } from '../../dominio/formato.js';

// Debe coincidir con --radio-grafica del CSS.
export const RADIO_MARCA = 4;

export const GROSOR_MAXIMO_MARCA = 24;

export const HUECO_MARCAS = 2;

// Se usan para medir texto: deben coincidir con el CSS.
export const FUENTE_EJE = { tamano: 12, peso: 400 };
export const FUENTE_ETIQUETA = { tamano: 12, peso: 700 };

// El backend puede mandar BigDecimal como texto; lo no numerico cuenta como 0.
export function aNumero(valor) {
  const convertido = typeof valor === 'number' ? valor : Number(valor);
  return Number.isFinite(convertido) ? convertido : 0;
}

export function acotar(valor, minimo, maximo) {
  return Math.min(Math.max(valor, minimo), maximo);
}

// Paso de 1, 2 o 5 por potencia de 10. pasoMinimo evita marcas que el eje no distingue:
// con montos sin decimales se pasa 1, porque un paso de 0.5 escribiria "Q 1" dos veces.
export function escalaRedonda(maximo, divisiones = 4, pasoMinimo = 0) {
  if (!(maximo > 0)) return { tope: 1, marcas: [0, 1] };

  const bruto = maximo / divisiones;
  const magnitud = 10 ** Math.floor(Math.log10(bruto));
  const residuo = bruto / magnitud;
  let factor = 10;
  if (residuo <= 1) factor = 1;
  else if (residuo <= 2) factor = 2;
  else if (residuo <= 5) factor = 5;

  const paso = Math.max(factor * magnitud, pasoMinimo);
  const cantidad = Math.max(1, Math.ceil(maximo / paso));
  // Se multiplica en lugar de sumar el paso para no acumular error de coma flotante.
  const marcas = Array.from({ length: cantidad + 1 }, (_, i) => i * paso);
  return { tope: cantidad * paso, marcas };
}

// Base recta sobre la linea base y extremo superior redondeado.
export function trazoColumna(x, yBase, ancho, alto, radio = RADIO_MARCA) {
  if (!(alto > 0) || !(ancho > 0)) return '';
  const r = Math.min(radio, alto, ancho / 2);
  const yTope = yBase - alto;

  return [
    `M${x},${yBase}`,
    `V${yTope + r}`,
    `A${r},${r} 0 0 1 ${x + r},${yTope}`,
    `H${x + ancho - r}`,
    `A${r},${r} 0 0 1 ${x + ancho},${yTope + r}`,
    `V${yBase}`,
    'Z',
  ].join(' ');
}

// Base recta a la izquierda y extremo derecho redondeado.
export function trazoBarra(xBase, yTope, largo, grosor, radio = RADIO_MARCA) {
  if (!(largo > 0) || !(grosor > 0)) return '';
  const r = Math.min(radio, largo, grosor / 2);
  const xFin = xBase + largo;

  return [
    `M${xBase},${yTope}`,
    `H${xFin - r}`,
    `A${r},${r} 0 0 1 ${xFin},${yTope + r}`,
    `V${yTope + grosor - r}`,
    `A${r},${r} 0 0 1 ${xFin - r},${yTope + grosor}`,
    `H${xBase}`,
    'Z',
  ].join(' ');
}

let contextoMedicion = null;
let familiaMedicion = null;

// Sin DOM (pruebas en Node) se devuelve una estimacion prudente.
export function medirTexto(texto, { tamano = 12, peso = 400 } = {}) {
  const cadena = String(texto ?? '');
  const estimacion = cadena.length * tamano * 0.62;
  if (typeof document === 'undefined') return estimacion;

  if (!contextoMedicion) {
    contextoMedicion = document.createElement('canvas').getContext('2d');
    familiaMedicion = window.getComputedStyle(document.body).fontFamily || 'system-ui, sans-serif';
  }
  if (!contextoMedicion) return estimacion;

  contextoMedicion.font = `${peso} ${tamano}px ${familiaMedicion}`;
  return contextoMedicion.measureText(cadena).width;
}

export function anchoMaximo(textos, fuente) {
  return textos.reduce((maximo, texto) => Math.max(maximo, medirTexto(texto, fuente)), 0);
}

export function ajustarTexto(texto, anchoDisponible, fuente) {
  const cadena = String(texto ?? '');
  if (medirTexto(cadena, fuente) <= anchoDisponible) return cadena;

  let corte = cadena.length - 1;
  while (corte > 1 && medirTexto(`${cadena.slice(0, corte)}…`, fuente) > anchoDisponible) {
    corte -= 1;
  }
  return `${cadena.slice(0, corte)}…`;
}

// Mayor residuo en decimas para que la suma de exactamente 100: redondear cada parte
// por separado puede dar 99.9 % (1/1/1) o 100.1 % (3/3/1).
export function repartirPorcentajes(valores) {
  const partes = valores.map((valor) => Math.max(aNumero(valor), 0));
  const total = partes.reduce((suma, parte) => suma + parte, 0);
  if (!(total > 0)) return partes.map(() => 0);

  const exactas = partes.map((parte) => (parte / total) * 1000); // en décimas de punto
  const decimas = exactas.map((exacta) => Math.floor(exacta));
  let faltantes = 1000 - decimas.reduce((suma, decima) => suma + decima, 0);

  exactas
    .map((exacta, indice) => ({ indice, residuo: exacta - decimas[indice] }))
    .sort((a, b) => b.residuo - a.residuo || a.indice - b.indice)
    .forEach(({ indice }) => {
      if (faltantes <= 0) return;
      decimas[indice] += 1;
      faltantes -= 1;
    });

  return decimas.map((decima) => decima / 10);
}

export function textoPorcentaje(porcentaje) {
  const valor = acotar(aNumero(porcentaje), 0, 100);
  return `${numero(valor, Number.isInteger(valor) ? 0 : 1)} %`;
}
