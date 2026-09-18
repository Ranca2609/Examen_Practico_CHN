import { useMemo } from 'react';
import FiguraGrafica from './FiguraGrafica.jsx';
import TooltipGrafica from './TooltipGrafica.jsx';
import { useAnchoContenedor } from './useAnchoContenedor.js';
import { useMarcasGrafica } from './useMarcasGrafica.js';
import {
  FUENTE_EJE,
  FUENTE_ETIQUETA,
  GROSOR_MAXIMO_MARCA,
  aNumero,
  acotar,
  anchoMaximo,
  escalaRedonda,
  medirTexto,
  trazoColumna,
} from './graficas.js';
import { numero } from '../../dominio/formato.js';
import './GraficaColumnas.css';

const ALTO_TRAZADO = 200;
const MARGEN_SUPERIOR = 24; // sitio para la etiqueta de la columna más alta
const ALTO_EJE_X = 40; // rótulo del periodo + rótulo del grupo
const ALTO_LIENZO = MARGEN_SUPERIOR + ALTO_TRAZADO + ALTO_EJE_X;
const MARGEN_DERECHO = 4;
const SEPARACION_EJE_Y = 8;
const PROPORCION_GROSOR = 0.6; // la columna ocupa ~60 % de su banda; el resto es aire
const Y_ROTULO = 17;
const Y_GRUPO = 33;
const SEPARACION_ETIQUETA = 6;
const EXTRA_REALCE = 8; // la banda de realce asoma un poco sobre la cuadrícula

const COLOR_SERIE = 'var(--color-grafica-serie)';
const COLOR_SERIE_SUAVE = 'var(--color-grafica-serie-suave)';

function normalizar(datos) {
  return (Array.isArray(datos) ? datos : []).map((dato, indice) => ({
    ...dato,
    clave: dato.clave ?? indice,
    titulo: dato.titulo ?? dato.rotulo ?? '',
    valor: aNumero(dato.valor),
    detalles: Array.isArray(dato.detalles) ? dato.detalles : [],
  }));
}

function resumenAccesible(titulo, filas, nombreSerie, rotuloResaltado, formatoValor) {
  const total = filas.reduce((suma, fila) => suma + fila.valor, 0);
  const mayor = filas.reduce((maximo, fila) => (fila.valor > maximo.valor ? fila : maximo), filas[0]);
  const resaltada = filas.find((fila) => fila.resaltado);

  const partes = [
    `${titulo}, desde ${filas[0].titulo} hasta ${filas[filas.length - 1].titulo}`,
    `${nombreSerie} en el periodo: ${formatoValor(total)}`,
    `Valor más alto: ${mayor.titulo}, ${formatoValor(mayor.valor)}`,
  ];
  if (resaltada) partes.push(`${rotuloResaltado} (${resaltada.titulo}): ${formatoValor(resaltada.valor)}`);
  return `${partes.join('. ')}.`;
}

function textoMarca(columna, nombreSerie, rotuloResaltado, formatoValor) {
  const periodo = columna.resaltado ? `${columna.titulo} (${rotuloResaltado})` : columna.titulo;
  const detalles = columna.detalles.map((detalle) => `${detalle.etiqueta}: ${detalle.valor}`);
  return [`${periodo}. ${nombreSerie}: ${formatoValor(columna.valor)}`, ...detalles].join('. ');
}

function calcularGeometria(filas, ancho, formatoEje, formatoEtiqueta, pasoMinimoEje) {
  const maximo = Math.max(...filas.map((fila) => fila.valor));
  const { tope, marcas } = escalaRedonda(maximo, 4, pasoMinimoEje);
  const textosEje = marcas.map((valor) => formatoEje(valor));

  const izquierda = Math.ceil(anchoMaximo(textosEje, FUENTE_EJE)) + SEPARACION_EJE_Y;
  const derecha = ancho - MARGEN_DERECHO;
  const banda = Math.max(derecha - izquierda, filas.length) / filas.length;
  const grosor = acotar(Math.round(banda * PROPORCION_GROSOR), 2, GROSOR_MAXIMO_MARCA);
  const base = MARGEN_SUPERIOR + ALTO_TRAZADO;
  const escalaY = (valor) => base - (valor / tope) * ALTO_TRAZADO;

  const columnas = filas.map((fila, indice) => {
    const inicioBanda = izquierda + banda * indice;
    const centro = inicioBanda + banda / 2;
    // Un valor positivo diminuto conserva al menos 1px para no confundirse con cero.
    const alto = fila.valor > 0 ? Math.max(1, base - escalaY(fila.valor)) : 0;
    return {
      ...fila,
      indice,
      inicioBanda,
      centro,
      tope: base - alto,
      trazo: trazoColumna(centro - grosor / 2, base, grosor, alto),
    };
  });

  // Si los rotulos no caben se escribe uno de cada N, contando desde el ultimo
  // para que el periodo resaltado (el mes en curso) conserve siempre el suyo.
  const anchoRotulo = anchoMaximo(
    filas.map((fila) => fila.rotulo),
    FUENTE_ETIQUETA,
  );
  const paso = Math.max(1, Math.ceil((anchoRotulo + 6) / banda));
  const rotulos = columnas.filter((columna) => (filas.length - 1 - columna.indice) % paso === 0);

  // Si dos grupos (anios) chocan se queda el mas reciente, que es el que marca el cambio.
  const grupos = [];
  let grupoAnterior = null;
  rotulos.forEach((columna) => {
    const grupo = columna.grupo === undefined || columna.grupo === null ? '' : String(columna.grupo);
    if (!grupo || grupo === grupoAnterior) return;
    grupoAnterior = grupo;

    const anchoTexto = medirTexto(grupo, FUENTE_EJE);
    const x = acotar(columna.centro, anchoTexto / 2, ancho - anchoTexto / 2);
    const previo = grupos[grupos.length - 1];
    if (previo && x - anchoTexto / 2 < previo.x + previo.ancho / 2 + 6) grupos.pop();
    grupos.push({ clave: columna.clave, texto: grupo, x, ancho: anchoTexto });
  });

  // Solo se etiquetan la resaltada y la mas alta; el resto lo dan el eje, el tooltip y la tabla.
  // Cada etiqueta se sube sobre las columnas que tapa y se descarta si choca con otra.
  const indiceMayor = columnas.reduce(
    (elegido, columna) => (columna.valor > columnas[elegido].valor ? columna.indice : elegido),
    0,
  );
  const candidatas = [...columnas.filter((columna) => columna.resaltado), columnas[indiceMayor]];
  const etiquetas = [];
  candidatas.forEach((columna) => {
    if (etiquetas.some((etiqueta) => etiqueta.clave === columna.clave)) return;

    const texto = formatoEtiqueta(columna.valor);
    const anchoTexto = medirTexto(texto, FUENTE_ETIQUETA);
    const x = acotar(columna.centro, izquierda + anchoTexto / 2, ancho - anchoTexto / 2);
    const debajo = columnas.filter(
      (otra) => otra.centro + grosor / 2 >= x - anchoTexto / 2 && otra.centro - grosor / 2 <= x + anchoTexto / 2,
    );
    const y = Math.min(columna.tope, ...debajo.map((otra) => otra.tope)) - SEPARACION_ETIQUETA;

    const choca = etiquetas.some(
      (otra) => Math.abs(otra.x - x) < (otra.ancho + anchoTexto) / 2 + 4 && Math.abs(otra.y - y) < 14,
    );
    if (!choca) etiquetas.push({ clave: columna.clave, texto, x, y, ancho: anchoTexto });
  });

  return {
    base,
    izquierda,
    derecha,
    banda,
    grosor,
    columnas,
    rotulos,
    grupos,
    etiquetas,
    marcas: marcas.map((valor, indice) => ({
      valor,
      texto: textosEje[indice],
      // +0.5 alinea la línea de 1px con la rejilla de píxeles: queda nítida.
      y: Math.round(escalaY(valor)) + 0.5,
    })),
  };
}

function GraficaColumnas({
  titulo,
  datos = [],
  nombreSerie = 'Valor',
  encabezadoCategoria = 'Periodo',
  rotuloResaltado = 'Periodo actual',
  formatoValor = numero,
  formatoEtiqueta,
  formatoEje,
  pasoMinimoEje = 0,
  cargando = false,
  tituloVacio,
  mensajeVacio,
  className = '',
  ...resto
}) {
  const formatoCorto = formatoEtiqueta ?? formatoValor;
  const formatoMarcas = formatoEje ?? formatoCorto;

  const filas = useMemo(() => normalizar(datos), [datos]);
  const vacio = filas.every((fila) => fila.valor <= 0);

  const [refLienzo, ancho] = useAnchoContenedor();
  const { activo, propsMarca } = useMarcasGrafica(filas.length);

  const geometria = useMemo(
    () =>
      ancho > 0 && !vacio ? calcularGeometria(filas, ancho, formatoMarcas, formatoCorto, pasoMinimoEje) : null,
    [filas, ancho, vacio, formatoMarcas, formatoCorto, pasoMinimoEje],
  );

  const tabla = {
    columnas: [
      {
        clave: 'titulo',
        encabezado: encabezadoCategoria,
        render: (fila) => (fila.resaltado ? `${fila.titulo} (${rotuloResaltado.toLowerCase()})` : fila.titulo),
      },
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
    esFilaActiva: (fila) => Boolean(fila.resaltado),
  };

  const activa = geometria && activo !== null ? geometria.columnas[activo] : null;
  const altoZona = ALTO_TRAZADO + EXTRA_REALCE + ALTO_EJE_X - 4;

  return (
    <FiguraGrafica
      titulo={titulo}
      vacio={vacio}
      cargando={cargando}
      tituloVacio={tituloVacio}
      mensajeVacio={mensajeVacio}
      tabla={tabla}
      className={`chn-grafica-columnas ${className}`.trim()}
      {...resto}
    >
      <div ref={refLienzo} className="chn-grafica__lienzo" style={{ height: ALTO_LIENZO }}>
        {geometria ? (
          <>
            <svg
              className="chn-grafica__svg"
              width={ancho}
              height={ALTO_LIENZO}
              role="img"
              aria-label={resumenAccesible(titulo, filas, nombreSerie, rotuloResaltado, formatoValor)}
            >
              {/* Banda de realce detrás de la columna activa. */}
              {activa ? (
                <rect
                  className="chn-grafica__realce"
                  x={activa.inicioBanda + 1}
                  y={MARGEN_SUPERIOR - EXTRA_REALCE}
                  width={Math.max(geometria.banda - 2, 1)}
                  height={altoZona}
                  rx={6}
                />
              ) : null}

              {/* Cuadrícula (la línea del cero la dibuja el eje, más abajo) y marcas del eje Y. */}
              {geometria.marcas.map((marca) =>
                marca.valor === 0 ? null : (
                  <line
                    key={`cuadricula-${marca.valor}`}
                    className="chn-grafica__cuadricula"
                    x1={geometria.izquierda}
                    x2={geometria.derecha}
                    y1={marca.y}
                    y2={marca.y}
                  />
                ),
              )}
              {geometria.marcas.map((marca) => (
                <text
                  key={`marca-${marca.valor}`}
                  className="chn-grafica__texto chn-grafica__texto--cifra"
                  x={geometria.izquierda - SEPARACION_EJE_Y}
                  y={marca.y}
                  textAnchor="end"
                  dominantBaseline="central"
                >
                  {marca.texto}
                </text>
              ))}

              {/* Columnas: crecen desde la línea base, una tras otra. */}
              {geometria.columnas.map((columna) =>
                columna.trazo ? (
                  <path
                    key={columna.clave}
                    className={[
                      'chn-grafica-columnas__columna',
                      columna.resaltado ? 'chn-grafica-columnas__columna--enfasis' : '',
                      'chn-anim-crecer-vertical',
                      'chn-anim-escalonado',
                    ]
                      .filter(Boolean)
                      .join(' ')}
                    d={columna.trazo}
                    style={{ '--indice': Math.min(columna.indice, 10) }}
                  />
                ) : null,
              )}

              {/* Línea base, encima del pie de las columnas para anclarlas. */}
              <line
                className="chn-grafica__eje"
                x1={geometria.izquierda}
                x2={geometria.derecha}
                y1={geometria.base + 0.5}
                y2={geometria.base + 0.5}
              />

              {/* Rótulos del periodo y del grupo. */}
              {geometria.rotulos.map((columna) => (
                <text
                  key={`rotulo-${columna.clave}`}
                  className={`chn-grafica__texto ${columna.resaltado ? 'chn-grafica__texto--fuerte' : ''}`.trim()}
                  x={columna.centro}
                  y={geometria.base + Y_ROTULO}
                  textAnchor="middle"
                >
                  {columna.rotulo}
                </text>
              ))}
              {geometria.grupos.map((grupo) => (
                <text
                  key={`grupo-${grupo.clave}`}
                  className="chn-grafica__texto"
                  x={grupo.x}
                  y={geometria.base + Y_GRUPO}
                  textAnchor="middle"
                >
                  {grupo.texto}
                </text>
              ))}

              {/* Etiquetas de valor selectivas. */}
              {geometria.etiquetas.map((etiqueta) => (
                <text
                  key={`etiqueta-${etiqueta.clave}`}
                  className="chn-grafica__texto chn-grafica__texto--fuerte"
                  x={etiqueta.x}
                  y={etiqueta.y}
                  textAnchor="middle"
                >
                  {etiqueta.texto}
                </text>
              ))}
            </svg>

            {/* Capa interactiva: una zona por columna, del alto de toda la banda. */}
            <svg
              className="chn-grafica__interaccion"
              width={ancho}
              height={ALTO_LIENZO}
              role="group"
              aria-label={`${titulo}: detalle por ${encabezadoCategoria.toLowerCase()}. Use las flechas para recorrerlo.`}
            >
              {geometria.columnas.map((columna) => (
                <rect
                  key={columna.clave}
                  {...propsMarca(columna.indice)}
                  className="chn-grafica__zona"
                  x={columna.inicioBanda + 1}
                  y={MARGEN_SUPERIOR - EXTRA_REALCE}
                  width={Math.max(geometria.banda - 2, 1)}
                  height={altoZona}
                  rx={6}
                  role="img"
                  aria-label={textoMarca(columna, nombreSerie, rotuloResaltado, formatoValor)}
                />
              ))}
            </svg>

            {activa ? (
              <TooltipGrafica
                key={activa.clave}
                x={activa.centro}
                y={activa.tope}
                yInferior={geometria.base}
                anchoMarca={geometria.grosor}
                limites={{ ancho, alto: ALTO_LIENZO }}
                titulo={activa.resaltado ? `${activa.titulo} · ${rotuloResaltado}` : activa.titulo}
                valor={formatoValor(activa.valor)}
                complemento={nombreSerie}
                color={activa.resaltado ? COLOR_SERIE : COLOR_SERIE_SUAVE}
                detalles={activa.detalles}
              />
            ) : null}
          </>
        ) : null}
      </div>
    </FiguraGrafica>
  );
}

export default GraficaColumnas;
