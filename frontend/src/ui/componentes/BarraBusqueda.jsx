import { useId } from 'react';
import Icono from './Icono.jsx';
import './base.css';
import './BarraBusqueda.css';

// A diferencia de los Campo*, onChange recibe el texto (no el evento) para admitir onChange={setTexto}.
function BarraBusqueda({
  valor,
  onChange,
  placeholder = 'Buscar...',
  onLimpiar,
  className = '',
  ...resto
}) {
  const idCampo = useId();
  const texto = valor ?? '';

  return (
    <div className={`chn-busqueda ${className}`.trim()} role="search">
      <label className="chn-oculto-visual" htmlFor={idCampo}>
        {placeholder}
      </label>

      <span className="chn-busqueda__icono">
        <Icono nombre="buscar" tamano={16} />
      </span>

      <input
        id={idCampo}
        type="search"
        className="chn-control chn-busqueda__control"
        value={texto}
        onChange={(evento) => {
          if (onChange) onChange(evento.target.value, evento);
        }}
        placeholder={placeholder}
        {...resto}
      />

      {onLimpiar && texto ? (
        <button
          type="button"
          className="chn-busqueda__limpiar"
          onClick={onLimpiar}
          aria-label="Limpiar búsqueda"
        >
          <Icono nombre="cerrar" tamano={14} />
        </button>
      ) : null}
    </div>
  );
}

export default BarraBusqueda;
