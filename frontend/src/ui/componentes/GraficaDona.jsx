import { Fragment, useId, useMemo } from 'react';
import FiguraGrafica from './FiguraGrafica.jsx';
import TooltipGrafica from './TooltipGrafica.jsx';
import { useMarcasGrafica } from './useMarcasGrafica.js';
import { HUECO_MARCAS, aNumero, medirTexto, repartirPorcentajes, textoPorcentaje } from './graficas.js';
import { numero } from '../../dominio/formato.js';
import './GraficaDona.css';

// Geometria fija en px: el anillo no cambia de tamano con la pantalla.
const TAMANO = 184;
const CENTRO = TAMANO / 2;
const RADIO = 72; // radio medio del anillo
const GROSOR = 20;
const GROSOR_ACTIVO = 26;
const GROSOR_ZONA = 36; // zona de impacto mayor que el segmento
const RADIO_FOCO = 89; // arco del foco del teclado, por fuera del segmento
const HUECO_INTERIOR = 2 * (RADIO - GROSOR / 2) - 16; // ancho útil para el total
const FUENTE_TOTAL = { tamano: 30, peso: 700 };
const FUENTE_TOTAL_REDUCIDA = { tamano: 22, peso: 700 };
const COLOR_POR_OMISION = 'var(--color-grafica-serie)';
const CLAVE_TOTAL = 'total-dona';

// El trazo de un circulo SVG arranca a las 3: se gira para empezar a las 12 en sentido horario.
const GIRO = `rotate(-90 ${CENTRO} ${CENTRO})`;

// Extremos de trazo rectos: el hueco entre segmentos queda de 2px en el centro del anillo.
function arco(radio, inicio, fraccion, conHueco) {
  if (!conHueco) return { strokeDasharray: 'none', strokeDashoffset: 0 };

  const circunferencia = 2 * Math.PI * radio;
  const largo = Math.max(fraccion * circunferencia - HUECO_MARCAS, 0.5);
  return {
    strokeDasharray: `${largo} ${circunferencia - largo}`,
    strokeDashoffset: -(inicio * circunferencia + HUECO_MARCAS / 2),
  };
}

function normalizar(datos) {
  return (Array.isArray(datos) ? datos : []).map((dato, indice) => ({
    ...dato,
    clave: dato.clave ?? indice,
    rotulo: String(dato.rotulo ?? ''),
    valor: Math.max(aNumero(dato.valor), 0),
    color: dato.color || COLOR_POR_OMISION,
  }));
}

// Hasta 6 segmentos; con mas, o con valores muy parecidos, se lee mejor GraficaBarras.
// Los colores deben contrastar en todos los pares: en un anillo todos se tocan.
function GraficaDona({
  titulo,
  datos = [],
  unidad = ['elemento', 'elementos'],
  nombreSerie = 'Cantidad',
  encabezadoCategoria = 'Categoría',
  formatoValor = numero,
  cargando = false,
  tituloVacio,
  mensajeVacio,
  className = '',
  ...resto
}) {
  const idMascara = `chn-dona-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`;
  const filas = useMemo(() => normalizar(datos), [datos]);
  const total = filas.reduce((suma, fila) => suma + fila.valor, 0);
  const vacio = !(total > 0);
  const [unidadSingular, unidadPlural] = unidad;
  const nombreUnidad = (cantidad) => (cantidad === 1 ? unidadSingular : unidadPlural);

  // Un solo reparto que suma 100 % para leyenda, tabla, tooltip y nombres accesibles.
  const porcentajes = useMemo(() => {
    const repartidos = repartirPorcentajes(filas.map((fila) => fila.valor));
    const textos = new Map(filas.map((fila, indice) => [fila.clave, textoPorcentaje(repartidos[indice])]));
    textos.set(CLAVE_TOTAL, textoPorcentaje(100));
    return textos;
  }, [filas]);
  const porcentajeDe = (fila) => porcentajes.get(fila.clave) ?? textoPorcentaje(0);

  const segmentos = useMemo(() => {
    if (vacio) return [];
    let acumulado = 0;
    return filas
      .filter((fila) => fila.valor > 0)
      .map((fila, indice) => {
        const fraccion = fila.valor / total;
        const segmento = { ...fila, indice, inicio: acumulado, fraccion };
        acumulado += fraccion;
        return segmento;
      });
  }, [filas, total, vacio]);
  const conHueco = segmentos.length > 1;

  const { activo, mostrar, ocultar, propsMarca } = useMarcasGrafica(segmentos.length);
  const activa = activo !== null ? segmentos[activo] : null;

  const textoTotal = formatoValor(total);
  const fuenteTotal = medirTexto(textoTotal, FUENTE_TOTAL) <= HUECO_INTERIOR ? FUENTE_TOTAL : FUENTE_TOTAL_REDUCIDA;

  const resumen = vacio
    ? titulo
    : `${titulo}: ${textoTotal} ${nombreUnidad(total)} en total. ${filas
        .map((fila) => `${fila.rotulo}: ${formatoValor(fila.valor)} (${porcentajeDe(fila)})`)
        .join('. ')}.`;

  const tabla = {
    columnas: [
      { clave: 'rotulo', encabezado: encabezadoCategoria },
      {
        clave: 'valor',
        encabezado: nombreSerie,
        alineacion: 'derecha',
        render: (fila) => formatoValor(fila.valor),
      },
      {
        clave: 'porcentaje',
        encabezado: 'Porcentaje',
        alineacion: 'derecha',
        render: (fila) => porcentajeDe(fila),
      },
    ],
    filas: [...filas, { clave: CLAVE_TOTAL, rotulo: 'Total', valor: total }],
    claveFila: (fila) => fila.clave,
  };

  // Ancla del tooltip: borde exterior del segmento, a mitad de su arco.
  let ancla = null;
  if (activa) {
    const angulo = 2 * Math.PI * (activa.inicio + activa.fraccion / 2);
    const radioAncla = RADIO + GROSOR_ACTIVO / 2;
    ancla = { x: CENTRO + radioAncla * Math.sin(angulo), y: CENTRO - radioAncla * Math.cos(angulo) };
  }

  return (
    <FiguraGrafica
      titulo={titulo}
      vacio={vacio}
      cargando={cargando}
      tituloVacio={tituloVacio}
      mensajeVacio={mensajeVacio}
      tabla={tabla}
      className={`chn-grafica-dona ${className}`.trim()}
      {...resto}
    >
      <div className="chn-grafica-dona__cuerpo">
        <div className="chn-grafica__lienzo chn-grafica-dona__lienzo" style={{ width: TAMANO, height: TAMANO }}>
          <svg className="chn-grafica__svg" width={TAMANO} height={TAMANO} role="img" aria-label={resumen}>
            <defs>
              {/* En una mascara el blanco significa "visible"; no es un color de la interfaz. */}
              <mask id={idMascara} maskUnits="userSpaceOnUse" x={0} y={0} width={TAMANO} height={TAMANO}>
                <circle
                  className="chn-anim-dibujar-trazo"
                  cx={CENTRO}
                  cy={CENTRO}
                  r={RADIO}
                  fill="none"
                  stroke="#fff"
                  strokeWidth={GROSOR_ZONA}
                  pathLength={100}
                  strokeDasharray="101 101"
                  strokeDashoffset={0}
                  transform={GIRO}
                />
              </mask>
            </defs>

            <g mask={`url(#${idMascara})`}>
              {segmentos.map((segmento) => (
                <circle
                  key={segmento.clave}
                  className="chn-grafica-dona__segmento"
                  cx={CENTRO}
                  cy={CENTRO}
                  r={RADIO}
                  fill="none"
                  transform={GIRO}
                  style={{
                    stroke: segmento.color,
                    strokeWidth: segmento.indice === activo ? GROSOR_ACTIVO : GROSOR,
                    ...arco(RADIO, segmento.inicio, segmento.fraccion, conHueco),
                  }}
                />
              ))}
            </g>

            {/* Total al centro. */}
            <text
              className="chn-grafica-dona__total"
              x={CENTRO}
              y={CENTRO + 1}
              textAnchor="middle"
              style={{ fontSize: fuenteTotal.tamano }}
            >
              {textoTotal}
            </text>
            <text className="chn-grafica__texto" x={CENTRO} y={CENTRO + 21} textAnchor="middle">
              {nombreUnidad(total)}
            </text>
          </svg>

          {/* Capa interactiva: zona ancha y transparente por segmento mas su arco de foco. */}
          <svg
            className="chn-grafica__interaccion"
            width={TAMANO}
            height={TAMANO}
            role="group"
            aria-label={`${titulo}: detalle por ${encabezadoCategoria.toLowerCase()}. Use las flechas para recorrerlo.`}
          >
            {segmentos.map((segmento) => (
              <Fragment key={segmento.clave}>
                <circle
                  {...propsMarca(segmento.indice)}
                  className="chn-grafica-dona__zona"
                  cx={CENTRO}
                  cy={CENTRO}
                  r={RADIO}
                  fill="none"
                  strokeWidth={GROSOR_ZONA}
                  transform={GIRO}
                  style={arco(RADIO, segmento.inicio, segmento.fraccion, conHueco)}
                  role="img"
                  aria-label={`${segmento.rotulo}: ${formatoValor(segmento.valor)} ${nombreUnidad(
                    segmento.valor,
                  )}, ${porcentajeDe(segmento)} del total`}
                />
                <circle
                  className="chn-grafica-dona__foco"
                  cx={CENTRO}
                  cy={CENTRO}
                  r={RADIO_FOCO}
                  fill="none"
                  strokeWidth={2}
                  transform={GIRO}
                  style={arco(RADIO_FOCO, segmento.inicio, segmento.fraccion, conHueco)}
                  aria-hidden="true"
                />
              </Fragment>
            ))}
          </svg>

          {activa && ancla ? (
            <TooltipGrafica
              key={activa.clave}
              x={ancla.x}
              y={ancla.y}
              limites={{ ancho: TAMANO, alto: TAMANO }}
              titulo={activa.rotulo}
              valor={formatoValor(activa.valor)}
              complemento={nombreUnidad(activa.valor)}
              color={activa.color}
              detalles={[{ etiqueta: 'Del total', valor: porcentajeDe(activa) }]}
            />
          ) : null}
        </div>

        {/* Etiquetado directo: la identidad no depende solo del color. */}
        <ul className="chn-grafica-dona__leyenda">
          {filas.map((fila) => {
            const segmento = segmentos.find((otro) => otro.clave === fila.clave);
            const esActiva = Boolean(segmento) && segmento.indice === activo;
            return (
              <li
                key={fila.clave}
                className={`chn-grafica-dona__elemento ${esActiva ? 'chn-grafica-dona__elemento--activo' : ''}`.trim()}
                onPointerEnter={segmento ? () => mostrar(segmento.indice) : undefined}
                onPointerLeave={segmento ? ocultar : undefined}
              >
                <span className="chn-grafica-dona__muestra" style={{ backgroundColor: fila.color }} aria-hidden="true" />
                <span className="chn-grafica-dona__nombre">{fila.rotulo}</span>
                <span className="chn-grafica-dona__cantidad">{formatoValor(fila.valor)}</span>
                <span className="chn-grafica-dona__porcentaje">{porcentajeDe(fila)}</span>
              </li>
            );
          })}
        </ul>
      </div>
    </FiguraGrafica>
  );
}

export default GraficaDona;
