import { useCallback, useEffect, useId, useRef } from 'react';
import { createPortal } from 'react-dom';
import Icono from './Icono.jsx';
import './Modal.css';

const SELECTOR_ENFOCABLE = [
  'a[href]',
  'button:not([disabled])',
  'textarea:not([disabled])',
  'input:not([disabled]):not([type="hidden"])',
  'select:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(', ');

function elementosEnfocables(contenedor) {
  if (!contenedor) return [];
  return Array.from(contenedor.querySelectorAll(SELECTOR_ENFOCABLE)).filter(
    (elemento) => elemento.offsetParent !== null || elemento === document.activeElement,
  );
}

function Modal({ abierto, titulo, onCerrar, children, pie, ancho = 'md', className = '', ...resto }) {
  const idTitulo = useId();
  const panelRef = useRef(null);
  const focoPrevioRef = useRef(null);

  const cerrar = useCallback(() => {
    if (onCerrar) onCerrar();
  }, [onCerrar]);

  useEffect(() => {
    if (!abierto) return undefined;

    focoPrevioRef.current = document.activeElement;
    const desbordamientoPrevio = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    const temporizador = window.setTimeout(() => {
      const enfocables = elementosEnfocables(panelRef.current);
      (enfocables[0] || panelRef.current)?.focus();
    }, 0);

    return () => {
      window.clearTimeout(temporizador);
      document.body.style.overflow = desbordamientoPrevio;
      const anterior = focoPrevioRef.current;
      if (anterior && typeof anterior.focus === 'function') anterior.focus();
    };
  }, [abierto]);

  // Escape cierra el modal aunque el foco este fuera del panel.
  useEffect(() => {
    if (!abierto) return undefined;

    const alPresionar = (evento) => {
      if (evento.key === 'Escape') {
        evento.stopPropagation();
        cerrar();
      }
    };

    document.addEventListener('keydown', alPresionar);
    return () => document.removeEventListener('keydown', alPresionar);
  }, [abierto, cerrar]);

  if (!abierto) return null;

  // Trampa de foco: Tab cicla dentro del panel.
  const alTabular = (evento) => {
    if (evento.key !== 'Tab') return;
    const enfocables = elementosEnfocables(panelRef.current);
    if (enfocables.length === 0) {
      evento.preventDefault();
      return;
    }
    const primero = enfocables[0];
    const ultimo = enfocables[enfocables.length - 1];

    if (evento.shiftKey && document.activeElement === primero) {
      evento.preventDefault();
      ultimo.focus();
    } else if (!evento.shiftKey && document.activeElement === ultimo) {
      evento.preventDefault();
      primero.focus();
    }
  };

  return createPortal(
    <div
      className="chn-modal__fondo"
      onMouseDown={(evento) => {
        if (evento.target === evento.currentTarget) cerrar();
      }}
    >
      <div
        className={`chn-modal chn-modal--${ancho} ${className}`.trim()}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titulo ? idTitulo : undefined}
        aria-label={titulo ? undefined : 'Ventana de diálogo'}
        tabIndex={-1}
        ref={panelRef}
        onKeyDown={alTabular}
        {...resto}
      >
        <header className="chn-modal__encabezado">
          {titulo ? (
            <h2 className="chn-modal__titulo" id={idTitulo}>
              {titulo}
            </h2>
          ) : (
            <span />
          )}
          <button type="button" className="chn-modal__cerrar" onClick={cerrar} aria-label="Cerrar ventana">
            <Icono nombre="cerrar" tamano={18} />
          </button>
        </header>

        <div className="chn-modal__cuerpo">{children}</div>

        {pie ? <footer className="chn-modal__pie">{pie}</footer> : null}
      </div>
    </div>,
    document.body,
  );
}

export default Modal;
