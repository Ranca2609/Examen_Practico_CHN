import { useLayoutEffect, useRef, useState } from 'react';
import './TooltipGrafica.css';

const SEPARACION = 10;
const MARGEN = 4;

// Siempre dentro del lienzo: la tarjeta que lo contiene recorta lo que se sale (overflow: hidden).
function colocar({ x, y, yInferior, anchoMarca, anchoLienzo, altoLienzo, ancho, alto }) {
  const centrado = Math.min(Math.max(x - ancho / 2, MARGEN), Math.max(MARGEN, anchoLienzo - ancho - MARGEN));

  if (y - alto - SEPARACION >= 0) {
    return { izquierda: centrado, arriba: y - alto - SEPARACION };
  }
  if (yInferior + SEPARACION + alto <= altoLienzo) {
    return { izquierda: centrado, arriba: yInferior + SEPARACION };
  }

  // Sin sitio arriba ni abajo (una columna que llega al tope): a un lado de la marca.
  const arribaLateral = Math.min(Math.max(y, 0), Math.max(0, altoLienzo - alto));
  const aLaDerecha = x + anchoMarca / 2 + SEPARACION;
  if (aLaDerecha + ancho <= anchoLienzo - MARGEN) {
    return { izquierda: aLaDerecha, arriba: arribaLateral };
  }
  const aLaIzquierda = x - anchoMarca / 2 - SEPARACION - ancho;
  if (aLaIzquierda >= MARGEN) {
    return { izquierda: aLaIzquierda, arriba: arribaLateral };
  }

  // Lienzo angosto: arriba del todo y pegado al borde con mas espacio, para tapar
  // lo menos posible de la marca activa en lugar de quedar centrado sobre ella.
  const espacioDerecha = anchoLienzo - (x + anchoMarca / 2);
  const espacioIzquierda = x - anchoMarca / 2;
  const alBordeDerecho = Math.max(MARGEN, anchoLienzo - ancho - MARGEN);
  return { izquierda: espacioDerecha > espacioIzquierda ? alBordeDerecho : MARGEN, arriba: 0 };
}

// aria-hidden: la marca ya anuncia lo mismo en su aria-label. Quien lo usa le pone
// key={clave de la marca} para que se vuelva a medir desde cero al cambiar de marca.
function TooltipGrafica({
  x,
  y,
  yInferior,
  anchoMarca = 0,
  limites,
  titulo,
  valor,
  complemento,
  color,
  detalles = [],
}) {
  const ref = useRef(null);
  const [posicion, setPosicion] = useState(null);
  const anchoLienzo = limites?.ancho ?? 0;
  const altoLienzo = limites?.alto ?? 0;

  // useLayoutEffect: se mide antes de pintar para que no aparezca un instante mal colocado.
  useLayoutEffect(() => {
    const nodo = ref.current;
    if (!nodo) return;

    const nueva = colocar({
      x,
      y,
      yInferior: yInferior ?? y,
      anchoMarca,
      anchoLienzo: anchoLienzo || nodo.offsetWidth,
      altoLienzo: altoLienzo || nodo.offsetHeight,
      ancho: nodo.offsetWidth,
      alto: nodo.offsetHeight,
    });

    setPosicion((previa) =>
      previa && previa.izquierda === nueva.izquierda && previa.arriba === nueva.arriba ? previa : nueva,
    );
  }, [x, y, yInferior, anchoMarca, anchoLienzo, altoLienzo, titulo, valor, complemento, detalles]);

  // Sin posicion se mide en el origen del lienzo, donde toma su ancho natural
  // (anclado cerca del borde derecho se encogeria).
  return (
    <div
      ref={ref}
      className="chn-tooltip-grafica"
      aria-hidden="true"
      style={{
        left: posicion?.izquierda ?? 0,
        top: posicion?.arriba ?? 0,
        visibility: posicion ? 'visible' : 'hidden',
      }}
    >
      {titulo ? <p className="chn-tooltip-grafica__titulo">{titulo}</p> : null}
      <p className="chn-tooltip-grafica__principal">
        {color ? <span className="chn-tooltip-grafica__clave" style={{ backgroundColor: color }} /> : null}
        <strong className="chn-tooltip-grafica__valor">{valor}</strong>
        {complemento ? <span className="chn-tooltip-grafica__complemento">{complemento}</span> : null}
      </p>
      {detalles.length > 0 ? (
        <dl className="chn-tooltip-grafica__detalles">
          {detalles.map((detalle) => (
            <div key={detalle.etiqueta} className="chn-tooltip-grafica__detalle">
              <dt>{detalle.etiqueta}</dt>
              <dd>{detalle.valor}</dd>
            </div>
          ))}
        </dl>
      ) : null}
    </div>
  );
}

export default TooltipGrafica;
