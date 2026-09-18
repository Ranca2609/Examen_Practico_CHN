import { NavLink } from 'react-router-dom';
import { Icono } from '../componentes/index.js';
import { usarSesion } from './usarSesion.js';
import './BarraLateral.css';

const VERSION_SISTEMA = '1.0.0';

// Ocultar el enlace es solo cosmetica: la ruta la protege RutaProtegida y el backend.
const ENLACES = [
  { a: '/', etiqueta: 'Tablero', icono: 'tablero', captura: 'nav-tablero', exacta: true },
  { a: '/clientes', etiqueta: 'Clientes', icono: 'clientes', captura: 'nav-clientes' },
  { a: '/solicitudes', etiqueta: 'Solicitudes', icono: 'solicitudes', captura: 'nav-solicitudes' },
  { a: '/prestamos', etiqueta: 'Préstamos', icono: 'prestamos', captura: 'nav-prestamos' },
  { a: '/pagos', etiqueta: 'Pagos', icono: 'pagos', captura: 'nav-pagos' },
  {
    a: '/auditoria',
    etiqueta: 'Auditoría',
    icono: 'auditoria',
    captura: 'nav-auditoria',
    permiso: 'VER_AUDITORIA',
  },
];

function BarraLateral({ abierta = false, onCerrar }) {
  const { tienePermiso } = usarSesion();
  const visibles = ENLACES.filter((enlace) => !enlace.permiso || tienePermiso(enlace.permiso));

  return (
    <>
      {abierta ? <div className="chn-lateral__velo" onClick={onCerrar} aria-hidden="true" /> : null}

      <aside
        id="chn-barra-lateral"
        className={`chn-lateral ${abierta ? 'chn-lateral--abierta' : ''}`.trim()}
      >
        <div className="chn-lateral__marca">
          <span className="chn-lateral__logo" aria-hidden="true">
            CHN
          </span>
          <span className="chn-lateral__textos">
            <span className="chn-lateral__sistema">Sistema de Préstamos</span>
            <span className="chn-lateral__entidad">Crédito Hipotecario Nacional</span>
          </span>
        </div>

        <nav className="chn-lateral__nav" aria-label="Navegación principal">
          <ul className="chn-lateral__lista">
            {visibles.map((enlace) => (
              <li key={enlace.a}>
                <NavLink
                  to={enlace.a}
                  end={enlace.exacta}
                  data-captura={enlace.captura}
                  className={({ isActive }) =>
                    `chn-lateral__enlace ${isActive ? 'chn-lateral__enlace--activo' : ''}`.trim()
                  }
                  onClick={onCerrar}
                >
                  <Icono nombre={enlace.icono} tamano={18} />
                  <span>{enlace.etiqueta}</span>
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>

        <footer className="chn-lateral__pie">
          <span>Versión {VERSION_SISTEMA}</span>
          <span>Guatemala, C.A.</span>
        </footer>
      </aside>
    </>
  );
}

export default BarraLateral;
