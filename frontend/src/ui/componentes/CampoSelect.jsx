import { useCallback, useEffect, useId, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import Icono from './Icono.jsx';
import { usarContextoCampo } from './contextoCampo.js';
import './base.css';
import './CampoSelect.css';

// Por debajo de este umbral el buscador estorba; el catalogo de clientes puede traer cientos de filas.
const MINIMO_PARA_BUSCADOR = 8;

// Ventana (ms) para que varias teclas seguidas cuenten como una sola busqueda, como en un <select> nativo.
const MARGEN_TECLEO = 500;

// Los estimados solo sirven en el primer calculo, antes de que el panel exista y se pueda medir.
const SEPARACION_PANEL = 4;
const MARGEN_VENTANA = 8;
const ALTO_ESTIMADO_PANEL = 280;
const ANCHO_ESTIMADO_PANEL = 240;

/* Marcas diacriticas que deja suelta normalize('NFD'). */
const DIACRITICOS = /[̀-ͯ]/g;

function sinTildes(texto) {
  return String(texto ?? '')
    .normalize('NFD')
    .replace(DIACRITICOS, '')
    .toLowerCase();
}

function esImprimible(evento) {
  return evento.key.length === 1 && !evento.ctrlKey && !evento.metaKey && !evento.altKey;
}

// Lista propia porque la de un <select> nativo no admite estilo; va en un portal para que un Modal no la recorte.
function CampoSelect({
  id,
  nombre,
  valor,
  onChange,
  opciones = [],
  placeholder,
  error,
  deshabilitado = false,
  buscable,
  etiquetaVacio = 'Ningún resultado para esa búsqueda',
  className = '',
  'aria-describedby': descritoExterno,
  ...resto
}) {
  const { descrito, invalido } = usarContextoCampo();
  const idInterno = useId();

  const disparadorRef = useRef(null);
  const panelRef = useRef(null);
  const buscadorRef = useRef(null);
  const temporizadorFocoRef = useRef(0);
  const tecleoRef = useRef({ texto: '', instante: 0 });

  const [abierto, setAbierto] = useState(false);
  const [busqueda, setBusqueda] = useState('');
  const [indiceActivo, setIndiceActivo] = useState(0);
  const [posicion, setPosicion] = useState(null);

  // Los filtros guardan cadenas pero algunas pantallas pasan ids numericos: se normaliza aqui.
  const valorActual = valor === undefined || valor === null ? '' : String(valor);

  const listaCruda = Array.isArray(opciones) ? opciones : [];

  const hayBuscador =
    buscable === undefined ? listaCruda.length >= MINIMO_PARA_BUSCADOR : Boolean(buscable);

  const idBase = id || idInterno;
  const idLista = `${idBase}-lista`;
  const idOpcion = (indice) => `${idBase}-opcion-${indice}`;

  // El indice se fija antes de filtrar para que el id de cada opcion sea estable.
  const todas = useMemo(() => {
    const filas = [];
    if (placeholder) filas.push({ clave: '', etiqueta: String(placeholder), vacia: true });
    (Array.isArray(opciones) ? opciones : []).forEach((opcion) => {
      filas.push({
        clave: opcion.valor === undefined || opcion.valor === null ? '' : String(opcion.valor),
        etiqueta: String(opcion.etiqueta ?? ''),
        vacia: false,
      });
    });
    return filas.map((fila, indice) => ({ ...fila, indice, comparable: sinTildes(fila.etiqueta) }));
  }, [opciones, placeholder]);

  // Con la busqueda vacia se devuelve la lista completa TAL CUAL, para que un
  // indice de "todas" siga sirviendo como posicion en la lista visible.
  const lista = useMemo(() => {
    const consulta = sinTildes(busqueda).trim();
    if (!hayBuscador || consulta === '') return todas;
    return todas.filter((fila) => fila.comparable.includes(consulta));
  }, [todas, busqueda, hayBuscador]);

  // Se acota al leer: si la lista se encoge, el indice nunca apunta fuera y no hace falta un efecto.
  const indiceSeguro = lista.length === 0 ? -1 : Math.min(indiceActivo, lista.length - 1);
  const opcionActiva = indiceSeguro >= 0 ? lista[indiceSeguro] : null;

  const elegida = todas.find((fila) => fila.clave === valorActual);
  const textoDisparador = elegida ? elegida.etiqueta : placeholder || '';

  const cerrar = useCallback((devolverFoco) => {
    setAbierto(false);
    setBusqueda('');
    if (devolverFoco) disparadorRef.current?.focus();
  }, []);

  const abrir = useCallback(
    (caracter) => {
      if (deshabilitado) return;
      setBusqueda('');
      let inicial = todas.findIndex((fila) => fila.clave === valorActual);
      if (caracter) {
        const buscado = sinTildes(caracter);
        const salto = todas.findIndex((fila) => fila.comparable.startsWith(buscado));
        if (salto >= 0) inicial = salto;
      }
      setIndiceActivo(inicial < 0 ? 0 : inicial);
      setAbierto(true);
    },
    [deshabilitado, todas, valorActual],
  );

  const elegir = useCallback(
    (opcion) => {
      // Repetir la seleccion no emite: cada cambio de filtro dispara una consulta al backend.
      if (opcion.clave !== valorActual && onChange) {
        onChange({ target: { name: nombre, value: opcion.clave } });
      }
      cerrar(true);
    },
    [valorActual, onChange, nombre, cerrar],
  );

  // Con el panel en un portal, relatedTarget no sirve: se mira el activeElement real
  // tras un timeout 0, cuando el foco nuevo ya esta puesto.
  const alPerderFoco = useCallback(() => {
    window.clearTimeout(temporizadorFocoRef.current);
    temporizadorFocoRef.current = window.setTimeout(() => {
      const activo = document.activeElement;
      if (disparadorRef.current?.contains(activo)) return;
      if (panelRef.current?.contains(activo)) return;
      cerrar(false);
    }, 0);
  }, [cerrar]);

  useEffect(() => () => window.clearTimeout(temporizadorFocoRef.current), []);

  const recalcular = useCallback(() => {
    const disparador = disparadorRef.current;
    if (!disparador) return;
    const caja = disparador.getBoundingClientRect();
    const panel = panelRef.current;
    const alto = panel ? panel.offsetHeight : ALTO_ESTIMADO_PANEL;
    const medido = panel ? panel.offsetWidth : ANCHO_ESTIMADO_PANEL;
    // Nunca mas estrecho que el disparador; el tope de 480px lo pone el max-width del CSS.
    const ancho = Math.max(medido, caja.width);

    const cabeAbajo = window.innerHeight - caja.bottom >= alto + MARGEN_VENTANA;
    const arriba = !cabeAbajo && caja.top >= alto + MARGEN_VENTANA;
    const superior = arriba ? caja.top - alto - SEPARACION_PANEL : caja.bottom + SEPARACION_PANEL;

    let izquierda = caja.left;
    if (izquierda + ancho > window.innerWidth - MARGEN_VENTANA) {
      izquierda = caja.right - ancho;
    }
    izquierda = Math.max(MARGEN_VENTANA, izquierda);

    setPosicion((previa) =>
      previa &&
      previa.superior === superior &&
      previa.izquierda === izquierda &&
      previa.ancho === caja.width &&
      previa.arriba === arriba
        ? previa
        : { superior, izquierda, ancho: caja.width, arriba },
    );
  }, []);

  useLayoutEffect(() => {
    if (!abierto) return undefined;
    recalcular();
    window.addEventListener('scroll', recalcular, { passive: true, capture: true });
    window.addEventListener('resize', recalcular, { passive: true });
    return () => {
      window.removeEventListener('scroll', recalcular, { capture: true });
      window.removeEventListener('resize', recalcular);
    };
  }, [abierto, recalcular]);

  // Se observa el tamano real y no la busqueda: asi tambien se recoloca si un catalogo
  // termina de cargar con el panel abierto (y volteado hacia arriba).
  useEffect(() => {
    const panel = panelRef.current;
    if (!abierto || !panel || typeof ResizeObserver === 'undefined') return undefined;
    const observador = new ResizeObserver(recalcular);
    observador.observe(panel);
    return () => observador.disconnect();
  }, [abierto, recalcular]);

  useEffect(() => {
    if (!abierto) return undefined;
    const alPulsarFuera = (evento) => {
      if (disparadorRef.current?.contains(evento.target)) return;
      if (panelRef.current?.contains(evento.target)) return;
      setAbierto(false);
      setBusqueda('');
    };
    document.addEventListener('mousedown', alPulsarFuera);
    document.addEventListener('touchstart', alPulsarFuera, { passive: true });
    return () => {
      document.removeEventListener('mousedown', alPulsarFuera);
      document.removeEventListener('touchstart', alPulsarFuera);
    };
  }, [abierto]);

  // Con buscador el foco vive en el cuadro de busqueda; las flechas las atiende el panel.
  useEffect(() => {
    if (!abierto || !hayBuscador) return;
    buscadorRef.current?.focus();
  }, [abierto, hayBuscador]);

  useEffect(() => {
    if (!abierto) return;
    panelRef.current
      ?.querySelector('.chn-select__opcion--activa')
      ?.scrollIntoView({ block: 'nearest' });
  }, [abierto, indiceSeguro, busqueda]);

  const mover = (salto) => {
    const total = lista.length;
    if (total === 0) return;
    // Envoltura a los extremos, como en el patron combobox de la guia ARIA.
    setIndiceActivo(((indiceSeguro + salto) % total + total) % total);
  };

  const saltarEscribiendo = (caracter) => {
    const ahora = Date.now();
    const previo = ahora - tecleoRef.current.instante < MARGEN_TECLEO ? tecleoRef.current.texto : '';
    const acumulado = previo + caracter;
    tecleoRef.current = { texto: acumulado, instante: ahora };
    const buscado = sinTildes(acumulado);
    const indice = lista.findIndex((fila) => fila.comparable.startsWith(buscado));
    if (indice >= 0) setIndiceActivo(indice);
  };

  const manejarAbierto = (evento, enBuscador) => {
    switch (evento.key) {
      case 'ArrowDown':
        evento.preventDefault();
        mover(1);
        return;
      case 'ArrowUp':
        evento.preventDefault();
        mover(-1);
        return;
      case 'Home':
        evento.preventDefault();
        setIndiceActivo(0);
        return;
      case 'End':
        evento.preventDefault();
        setIndiceActivo(Math.max(lista.length - 1, 0));
        return;
      case 'Enter':
        evento.preventDefault();
        if (opcionActiva) elegir(opcionActiva);
        return;
      case ' ':
        // En el cuadro de busqueda la barra escribe un espacio, no selecciona.
        if (enBuscador) return;
        evento.preventDefault();
        if (opcionActiva) elegir(opcionActiva);
        return;
      case 'Escape':
        // stopPropagation: no debe cerrar el Modal que aloja el campo.
        evento.preventDefault();
        evento.stopPropagation();
        cerrar(true);
        return;
      case 'Tab':
        // Sin preventDefault: el navegador sigue desde el disparador hacia el control siguiente.
        cerrar(true);
        return;
      default:
        if (!enBuscador && esImprimible(evento)) {
          evento.preventDefault();
          saltarEscribiendo(evento.key);
        }
    }
  };

  const alTeclearDisparador = (evento) => {
    // Sin buscador el foco sigue en el disparador con el panel abierto: la lista se navega desde aqui.
    if (abierto) {
      manejarAbierto(evento, false);
      return;
    }

    if (
      evento.key === 'ArrowDown' ||
      evento.key === 'ArrowUp' ||
      evento.key === 'Enter' ||
      evento.key === ' '
    ) {
      // preventDefault evita que Enter y la barra sinteticen un clic (que
      // volveria a cerrar el panel) y que la barra desplace la pagina.
      evento.preventDefault();
      abrir();
      return;
    }

    if (evento.key === 'Home' || evento.key === 'End') return;

    if (esImprimible(evento)) {
      evento.preventDefault();
      abrir(evento.key);
    }
  };

  const clasesDisparador = [
    'chn-select',
    'chn-control',
    error ? 'chn-control--error' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  const captura = resto['data-captura'];

  const panel = (
    <div
      className={`chn-select__panel ${posicion && posicion.arriba ? 'chn-select__panel--arriba' : ''}`.trim()}
      id={idLista}
      role="listbox"
      aria-label="Opciones"
      ref={panelRef}
      style={{
        top: `${posicion ? posicion.superior : 0}px`,
        left: `${posicion ? posicion.izquierda : 0}px`,
        minWidth: posicion ? `${posicion.ancho}px` : undefined,
      }}
      onKeyDown={(evento) => manejarAbierto(evento, evento.target === buscadorRef.current)}
      onBlur={alPerderFoco}
    >
      {hayBuscador ? (
        <div className="chn-select__busqueda">
          <span className="chn-select__lupa">
            <Icono nombre="buscar" tamano={16} />
          </span>
          <input
            ref={buscadorRef}
            type="text"
            className="chn-control chn-select__buscador"
            value={busqueda}
            onChange={(evento) => {
              setBusqueda(evento.target.value);
              setIndiceActivo(0);
            }}
            placeholder="Buscar..."
            aria-label="Buscar opción"
            aria-activedescendant={opcionActiva ? idOpcion(opcionActiva.indice) : undefined}
            autoComplete="off"
            data-captura={captura ? `${captura}-buscar` : undefined}
          />
        </div>
      ) : null}

      {lista.map((fila) => (
        <div
          key={fila.indice}
          id={idOpcion(fila.indice)}
          role="option"
          aria-selected={fila.clave === valorActual}
          data-valor={fila.clave}
          className={[
            'chn-select__opcion',
            fila.vacia ? 'chn-select__opcion--vacia' : '',
            fila.clave === valorActual ? 'chn-select__opcion--elegida' : '',
            opcionActiva && fila.indice === opcionActiva.indice ? 'chn-select__opcion--activa' : '',
          ]
            .filter(Boolean)
            .join(' ')}
          // Evita que el mousedown saque el foco del disparador y cierre el panel antes del clic.
          onMouseDown={(evento) => evento.preventDefault()}
          onClick={() => elegir(fila)}
        >
          <span className="chn-select__etiqueta">{fila.etiqueta}</span>
          <span className="chn-select__marca" aria-hidden="true">
            {fila.clave === valorActual ? <Icono nombre="check" tamano={16} /> : null}
          </span>
        </div>
      ))}

      {lista.length === 0 ? <p className="chn-select__vacio">{etiquetaVacio}</p> : null}
    </div>
  );

  return (
    <>
      <button
        {...resto}
        ref={disparadorRef}
        // type="button" siempre: dentro de un <form>, un boton sin tipo lo enviaria.
        type="button"
        id={id}
        className={clasesDisparador}
        role="combobox"
        aria-expanded={abierto}
        aria-haspopup="listbox"
        aria-controls={idLista}
        aria-describedby={descritoExterno || descrito}
        aria-invalid={error || invalido ? true : undefined}
        // Sin buscador el foco no se mueve: la opcion activa se anuncia aqui.
        aria-activedescendant={abierto && !hayBuscador && opcionActiva ? idOpcion(opcionActiva.indice) : undefined}
        // data-valor expone la seleccion a las pruebas automatizadas sin depender del texto visible.
        data-valor={valorActual}
        disabled={deshabilitado}
        onClick={() => (abierto ? cerrar(true) : abrir())}
        onKeyDown={alTeclearDisparador}
        onBlur={alPerderFoco}
      >
        <span className="chn-select__texto">{textoDisparador}</span>
        <Icono nombre="chevronAbajo" tamano={18} className="chn-select__flecha" />
      </button>

      {abierto ? createPortal(panel, document.body) : null}
    </>
  );
}

export default CampoSelect;
