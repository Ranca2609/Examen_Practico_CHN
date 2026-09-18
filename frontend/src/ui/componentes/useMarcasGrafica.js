import { useCallback, useEffect, useRef, useState } from 'react';

// Foco itinerante: una sola marca entra en el orden de Tab y las flechas recorren el resto.
export function useMarcasGrafica(cantidad) {
  const [activo, setActivo] = useState(null);
  const [tabulable, setTabulable] = useState(0);
  const nodos = useRef([]);
  const enfocado = useRef(null);

  useEffect(() => {
    if (activo !== null && activo >= cantidad) setActivo(null);
  }, [activo, cantidad]);

  // Un toque fuera de las marcas cierra el tooltip que dejó abierto otro toque.
  useEffect(() => {
    if (activo === null) return undefined;

    const alTocarFuera = (evento) => {
      const dentro = nodos.current.some((nodo) => nodo && nodo.contains(evento.target));
      if (!dentro) setActivo(enfocado.current);
    };
    document.addEventListener('pointerdown', alTocarFuera);
    return () => document.removeEventListener('pointerdown', alTocarFuera);
  }, [activo]);

  const mostrar = useCallback((indice) => setActivo(indice), []);
  const ocultar = useCallback(() => setActivo(enfocado.current), []);

  const indiceTabulable = tabulable < cantidad ? tabulable : 0;

  const propsMarca = (indice) => ({
    ref: (nodo) => {
      nodos.current[indice] = nodo;
    },
    tabIndex: indice === indiceTabulable ? 0 : -1,
    onPointerEnter: () => setActivo(indice),
    onPointerLeave: (evento) => {
      // En pantallas táctiles el "salir" llega al levantar el dedo: se conserva.
      if (evento.pointerType === 'touch') return;
      setActivo(enfocado.current);
    },
    // Un clic no roba el foco: si lo hiciera, el tooltip se quedaria pegado al retirar el raton.
    onMouseDown: (evento) => evento.preventDefault(),
    onFocus: () => {
      enfocado.current = indice;
      setTabulable(indice);
      setActivo(indice);
    },
    onBlur: () => {
      enfocado.current = null;
      setActivo(null);
    },
    onKeyDown: (evento) => {
      let destino = null;
      switch (evento.key) {
        case 'ArrowRight':
        case 'ArrowDown':
          destino = Math.min(indice + 1, cantidad - 1);
          break;
        case 'ArrowLeft':
        case 'ArrowUp':
          destino = Math.max(indice - 1, 0);
          break;
        case 'Home':
          destino = 0;
          break;
        case 'End':
          destino = cantidad - 1;
          break;
        case 'Escape':
          setActivo(null);
          return;
        default:
          return;
      }
      evento.preventDefault();
      nodos.current[destino]?.focus();
    },
  });

  return { activo, mostrar, ocultar, propsMarca };
}

export default useMarcasGrafica;
