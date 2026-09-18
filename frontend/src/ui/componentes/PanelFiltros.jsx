import { useId, useState } from 'react';
import Boton from './Boton.jsx';
import Etiqueta from './Etiqueta.jsx';
import Icono from './Icono.jsx';
import './PanelFiltros.css';

function textoActivos(cantidad) {
  return cantidad === 1 ? '1 filtro aplicado' : `${cantidad} filtros aplicados`;
}

function nombreDeChip(etiqueta) {
  return typeof etiqueta === 'string' && etiqueta.trim() ? etiqueta : 'seleccionado';
}

function PanelFiltros({
  children,
  cantidadActivos = 0,
  onLimpiar,
  abiertoInicial = false,
  resumen = null,
  titulo = 'Filtros de búsqueda',
  chips = [],
  className = '',
  ...resto
}) {
  const [abierto, setAbierto] = useState(Boolean(abiertoInicial));
  const idCuerpo = useId();

  // Se tolera recibir texto o valores negativos desde la pagina.
  const activos = Math.max(0, Number(cantidadActivos) || 0);
  const pastillas = Array.isArray(chips) ? chips.filter(Boolean) : [];

  const clases = ['chn-panel-filtros', abierto ? 'chn-panel-filtros--abierto' : '', className]
    .filter(Boolean)
    .join(' ');

  return (
    <section className={clases} {...resto} data-captura="panel-filtros">
      <div className="chn-panel-filtros__barra">
        <button
          type="button"
          className="chn-panel-filtros__cabecera"
          onClick={() => setAbierto((estaAbierto) => !estaAbierto)}
          aria-expanded={abierto}
          aria-controls={idCuerpo}
          data-captura="filtros-alternar"
        >
          <span className="chn-panel-filtros__icono" aria-hidden="true">
            <Icono nombre="filtro" tamano={18} />
          </span>

          <span className="chn-panel-filtros__titulo">{titulo}</span>

          {activos > 0 ? (
            <Etiqueta tono="info" className="chn-panel-filtros__distintivo">
              {textoActivos(activos)}
            </Etiqueta>
          ) : null}

          <span className="chn-panel-filtros__chevron" aria-hidden="true">
            <Icono nombre="chevronAbajo" tamano={18} />
          </span>
        </button>

        <div className="chn-panel-filtros__extras">
          {resumen ? <div className="chn-panel-filtros__resumen">{resumen}</div> : null}

          {/* Limpiar solo aparece si hay algo que limpiar. */}
          {activos > 0 && onLimpiar ? (
            <Boton
              variante="neutro"
              tono="suave"
              tamano="sm"
              iconoIzquierda="limpiar"
              onClick={onLimpiar}
              data-captura="filtros-limpiar"
            >
              Limpiar filtros
            </Boton>
          ) : null}
        </div>
      </div>

      {/* Fuera del cuerpo para que sigan visibles con el panel plegado. */}
      {pastillas.length > 0 ? (
        <ul className="chn-panel-filtros__chips">
          {pastillas.map((chip) => (
            <li key={chip.clave}>
              <span className="chn-panel-filtros__chip">
                <span className="chn-panel-filtros__chip-texto">{chip.etiqueta}</span>
                {chip.onQuitar ? (
                  <button
                    type="button"
                    className="chn-panel-filtros__chip-quitar"
                    onClick={chip.onQuitar}
                    aria-label={`Quitar filtro ${nombreDeChip(chip.etiqueta)}`}
                  >
                    <Icono nombre="cerrar" tamano={12} />
                  </button>
                ) : null}
              </span>
            </li>
          ))}
        </ul>
      ) : null}

      <div className="chn-panel-filtros__cuerpo" id={idCuerpo}>
        <div className="chn-panel-filtros__rejilla">{children}</div>
      </div>
    </section>
  );
}

export default PanelFiltros;
