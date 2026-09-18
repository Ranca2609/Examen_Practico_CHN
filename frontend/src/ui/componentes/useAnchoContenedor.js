import { useLayoutEffect, useState } from 'react';

// Se dibuja con el ancho real y no estirando un viewBox para que el texto conserve su tamano.
// El ref es de callback (no useRef) para retomar la medicion si el contenedor se remonta.
export function useAnchoContenedor() {
  const [nodo, setNodo] = useState(null);
  const [ancho, setAncho] = useState(0);

  useLayoutEffect(() => {
    if (!nodo) return undefined;

    // Se redondea hacia abajo: medio píxel de más haría aparecer una barra de desplazamiento.
    const medir = () => setAncho(Math.floor(nodo.getBoundingClientRect().width));
    medir();

    if (typeof ResizeObserver === 'undefined') {
      window.addEventListener('resize', medir);
      return () => window.removeEventListener('resize', medir);
    }

    const observador = new ResizeObserver(medir);
    observador.observe(nodo);
    return () => observador.disconnect();
  }, [nodo]);

  return [setNodo, ancho];
}

export default useAnchoContenedor;
