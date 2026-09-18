import { useMemo } from 'react';
import FiguraGrafica from './FiguraGrafica.jsx';
import TooltipGrafica from './TooltipGrafica.jsx';
import { useAnchoContenedor } from './useAnchoContenedor.js';
import { useMarcasGrafica } from './useMarcasGrafica.js';
import { FUENTE_EJE, aNumero, ajustarTexto, anchoMaximo, trazoBarra } from './graficas.js';
import { numero } from '../../dominio/formato.js';
import './GraficaBarras.css';

const ALTO_FILA_CON_NOTA = 46;
const ALTO_FILA_SIN_NOTA = 34;
const GROSOR_BARRA = 18;
const RELLENO_VERTICAL = 4;
const RELLENO_IZQUIERDO = 8; // el rótulo no toca el borde de la banda de realce
const HOLGURA_ROTULOS = 16;
const SEPARACION_VALOR = 8;
const PROPORCION_MAXIMA_ROTULOS = 0.42; // los rótulos nunca se comen la barra
const LARGO_MINIMO = 2; // un valor positivo diminuto sigue viéndose

const FUENTE_ROTULO = { tamano: 13, peso: 600 };
const FUENTE_VALOR = { tamano: 13, peso: 700 };
const COLOR_SERIE = 'var(--color-grafica-serie)';

function normalizar(datos) {
  return (Array.isArray(datos) ? datos : []).map((dato, indice) => ({
    ...dato,
    clave: dato.clave ?? indice,
    rotulo: String(dato.rotulo ?? ''),
    nota: dato.nota ? String(dato.nota) : '',
    valor: aNumero(dato.valor),
    detalles: Array.isArray(dato.detalles) ? dato.detalles : [],
  }));
}

function resumenAccesible(titulo, filas, formatoValor) {
  return `${titulo}. ${filas.map((fila) => `${fila.rotulo}: ${formatoValor(fila.valor)}`).join('; ')}.`;
}

function textoMarca(fila, nombreSerie, formatoValor) {
  const detalles = fila.detalles.map((detalle) => `${detalle.etiqueta}: ${detalle.valor}`);
  return [`${fila.rotulo}. ${nombreSerie}: ${formatoValor(fila.valor)}`, ...detalles].join('. ');
}

function calcularGeometria(filas, ancho, formatoEtiqueta) {
  const conNota = filas.some((fila) => fila.nota);
  const altoFila = conNota ? ALTO_FILA_CON_NOTA : ALTO_FILA_SIN_NOTA;
  const alto = RELLENO_VERTICAL * 2 + altoFila * filas.length;

  const anchoTextos = Math.max(
    anchoMaximo(
      filas.map((fila) => fila.rotulo),
      FUENTE_ROTULO,
    ),
    anchoMaximo(
      filas.map((fila) => fila.nota),
      FUENTE_EJE,
    ),
  );
  const xBase = Math.min(
    Math.ceil(anchoTextos) + RELLENO_IZQUIERDO + HOLGURA_ROTULOS,
    Math.floor(ancho * PROPORCION_MAXIMA_ROTULOS),
  );
  const anchoRotulo = xBase - RELLENO_IZQUIERDO - HOLGURA_ROTULOS / 2;

  // La barra más larga deja sitio para la etiqueta de valor más ancha.
  const valores = filas.map((fila) => formatoEtiqueta(fila.valor));
  const anchoValor = Math.ceil(anchoMaximo(valores, FUENTE_VALOR));
  const largoMaximo = Math.max(ancho - xBase - SEPARACION_VALOR - anchoValor - 4, 16);
  const maximo = Math.max(...filas.map((fila) => fila.valor));

  const barras = filas.map((fila, indice) => {
    const arriba = RELLENO_VERTICAL + altoFila * indice;
    const centro = conNota ? arriba + 14 : arriba + altoFila / 2;
    const largo = fila.valor > 0 && maximo > 0 ? Math.max(LARGO_MINIMO, (fila.valor / maximo) * largoMaximo) : 0;
    return {
      ...fila,
      indice,
      arriba,
      centro,
      largo,
      rotuloVisible: ajustarTexto(fila.rotulo, anchoRotulo, FUENTE_ROTULO),
      notaVisible: fila.nota ? ajustarTexto(fila.nota, anchoRotulo, FUENTE_EJE) : '',
      textoValor: valores[indice],
      trazo: trazoBarra(xBase, centro - GROSOR_BARRA / 2, largo, GROSOR_BARRA),
    };
  });

  return { alto, altoFila, xBase, barras };
}

function GraficaBarras({
  titulo,
  datos = [],
  nombreSerie = 'Valor',
  encabezadoCategoria = 'Categoría',
  formatoValor = numero,
  formatoEtiqueta,
  cargando = false,
  tituloVacio,
  mensajeVacio,
  className = '',
  ...resto
}) {
  const formatoCorto = formatoEtiqueta ?? formatoValor;

  const filas = useMemo(() => normalizar(datos), [datos]);
  const vacio = filas.every((fila) => fila.valor <= 0);

  const [refLienzo, ancho] = useAnchoContenedor();
  const { activo, propsMarca } = useMarcasGrafica(filas.length);

  const geometria = useMemo(
    () => (ancho > 0 && !vacio ? calcularGeometria(filas, ancho, formatoCorto) : null),
    [filas, ancho, vacio, formatoCorto],
  );

  // Alto conocido antes de medir el ancho: el lienzo reserva su sitio y no salta.
  const conNota = filas.some((fila) => fila.nota);
  const altoLienzo =
    geometria?.alto ?? RELLENO_VERTICAL * 2 + (conNota ? ALTO_FILA_CON_NOTA : ALTO_FILA_SIN_NOTA) * filas.length;

  const tabla = {
    columnas: [
      { clave: 'rotulo', encabezado: encabezadoCategoria },
      {
        clave: 'valor',
        encabezado: nombreSerie,
        alineacion: 'derecha',
        render: (fila) => formatoValor(fila.valor),
      },
      ...(filas[0]?.detalles ?? []).map((detalle, posicion) => ({
        clave: `detalle-${posicion}`,
        encabezado: detalle.etiqueta,
        alineacion: 'derecha',
        render: (fila) => fila.detalles[posicion]?.valor ?? '—',
      })),
    ],
    filas,
    claveFila: (fila) => fila.clave,
  };

  const activa = geometria && activo !== null ? geometria.barras[activo] : null;

  return (
    <FiguraGrafica
      titulo={titulo}
      vacio={vacio}
      cargando={cargando}
      tituloVacio={tituloVacio}
      mensajeVacio={mensajeVacio}
      tabla={tabla}
      className={`chn-grafica-barras ${className}`.trim()}
      {...resto}
    >
      <div ref={refLienzo} className="chn-grafica__lienzo" style={{ height: altoLienzo }}>
        {geometria ? (
          <>
            <svg
              className="chn-grafica__svg"
              width={ancho}
              height={geometria.alto}
              role="img"
              aria-label={resumenAccesible(titulo, filas, formatoValor)}
            >
              {/* Banda de realce detrás de la fila activa. */}
              {activa ? (
                <rect
                  className="chn-grafica__realce"
                  x={0}
                  y={activa.arriba + 1}
                  width={ancho}
                  height={geometria.altoFila - 2}
                  rx={8}
                />
              ) : null}

              {geometria.barras.map((barra) => (
                <g key={barra.clave}>
                  <text
                    className="chn-grafica__texto chn-grafica-barras__rotulo"
                    x={RELLENO_IZQUIERDO}
                    y={barra.centro}
                    dominantBaseline="central"
                  >
                    {barra.rotuloVisible}
                  </text>
                  {barra.notaVisible ? (
                    <text
                      className="chn-grafica__texto"
                      x={RELLENO_IZQUIERDO}
                      y={barra.arriba + 32}
                      dominantBaseline="central"
                    >
                      {barra.notaVisible}
                    </text>
                  ) : null}
                  {barra.trazo ? (
                    <path
                      className="chn-grafica-barras__barra chn-anim-crecer-horizontal chn-anim-escalonado"
                      d={barra.trazo}
                      style={{ '--indice': Math.min(barra.indice, 10) }}
                    />
                  ) : null}
                  <text
                    className="chn-grafica__texto chn-grafica__texto--fuerte chn-grafica-barras__valor"
                    x={geometria.xBase + barra.largo + SEPARACION_VALOR}
                    y={barra.centro}
                    dominantBaseline="central"
                  >
                    {barra.textoValor}
                  </text>
                </g>
              ))}

              {/* Línea base: única referencia de escala, porque cada barra lleva su valor. */}
              <line
                className="chn-grafica__eje"
                x1={geometria.xBase + 0.5}
                x2={geometria.xBase + 0.5}
                y1={RELLENO_VERTICAL}
                y2={geometria.alto - RELLENO_VERTICAL}
              />
            </svg>

            {/* Capa interactiva: una zona por fila, de todo el ancho. */}
            <svg
              className="chn-grafica__interaccion"
              width={ancho}
              height={geometria.alto}
              role="group"
              aria-label={`${titulo}: detalle por ${encabezadoCategoria.toLowerCase()}. Use las flechas para recorrerlo.`}
            >
              {geometria.barras.map((barra) => (
                <rect
                  key={barra.clave}
                  {...propsMarca(barra.indice)}
                  className="chn-grafica__zona"
                  x={1}
                  y={barra.arriba + 1}
                  width={Math.max(ancho - 2, 1)}
                  height={geometria.altoFila - 2}
                  rx={8}
                  role="img"
                  aria-label={textoMarca(barra, nombreSerie, formatoValor)}
                />
              ))}
            </svg>

            {activa ? (
              <TooltipGrafica
                key={activa.clave}
                x={geometria.xBase + activa.largo}
                y={activa.arriba}
                yInferior={activa.arriba + geometria.altoFila}
                limites={{ ancho, alto: geometria.alto }}
                titulo={activa.rotulo}
                valor={formatoValor(activa.valor)}
                complemento={nombreSerie}
                color={COLOR_SERIE}
                detalles={activa.detalles}
              />
            ) : null}
          </>
        ) : null}
      </div>
    </FiguraGrafica>
  );
}

export default GraficaBarras;
