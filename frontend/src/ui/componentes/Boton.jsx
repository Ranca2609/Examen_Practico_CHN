import { useEffect, useRef } from 'react';
import Icono from './Icono.jsx';
import './base.css';
import './Boton.css';

function tamanoIcono(tamano, soloIcono) {
  if (soloIcono) {
    if (tamano === 'sm') return 16;
    if (tamano === 'lg') return 22;
    return 18;
  }
  return tamano === 'sm' ? 14 : 16;
}

function contenidoIcono(iconoIzquierda, tamano, soloIcono) {
  if (!iconoIzquierda) return null;
  if (typeof iconoIzquierda === 'string') {
    return <Icono nombre={iconoIzquierda} tamano={tamanoIcono(tamano, soloIcono)} />;
  }
  return iconoIzquierda;
}

// Las acciones de apoyo van con contorno para no competir con la principal.
// "texto" y "enlace" no hacen falta: su CSS se declara despues de los tonos y gana.
const TONO_POR_VARIANTE = {
  neutro: 'contorno',
  secundario: 'contorno',
};

function Boton({
  children,
  variante = 'primario',
  tono,
  tamano = 'md',
  tipo = 'button',
  cargando = false,
  deshabilitado = false,
  soloIcono = false,
  iconoIzquierda = null,
  onClick,
  ancho = 'auto',
  className = '',
  ...resto
}) {
  const inhabilitado = deshabilitado || cargando;
  const etiquetaAccesible = resto['aria-label'];

  const avisoEmitido = useRef(false);
  useEffect(() => {
    if (!import.meta.env.DEV || avisoEmitido.current) return;
    if (soloIcono && !etiquetaAccesible) {
      avisoEmitido.current = true;
      console.warn(
        'Boton: con soloIcono=true hace falta aria-label; sin el, el boton no tiene nombre accesible.',
      );
    }
  }, [soloIcono, etiquetaAccesible]);

  const tonoEfectivo = tono ?? TONO_POR_VARIANTE[variante] ?? 'solido';

  const clases = [
    'chn-boton',
    `chn-boton--${variante}`,
    `chn-boton--${tonoEfectivo}`,
    `chn-boton--${tamano}`,
    soloIcono ? 'chn-boton--solo-icono' : '',
    ancho === 'completo' ? 'chn-boton--completo' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <button
      type={tipo}
      className={clases}
      onClick={onClick}
      disabled={inhabilitado}
      aria-busy={cargando || undefined}
      {...resto}
    >
      {cargando ? (
        <span className="chn-boton__spinner" aria-hidden="true" />
      ) : (
        contenidoIcono(iconoIzquierda, tamano, soloIcono)
      )}
      {children && !soloIcono ? <span className="chn-boton__texto">{children}</span> : null}
    </button>
  );
}

export default Boton;
