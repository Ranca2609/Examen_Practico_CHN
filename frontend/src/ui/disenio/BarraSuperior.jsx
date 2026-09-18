import { Boton, Icono } from '../componentes/index.js';
import { usarSesion } from './usarSesion.js';
import './BarraSuperior.css';

function iniciales(nombre) {
  if (!nombre) return '?';
  return nombre
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((parte) => parte.charAt(0).toUpperCase())
    .join('');
}

function BarraSuperior({ titulo, menuAbierto = false, onAlternarMenu }) {
  const { usuario, etiquetaRol, cerrarSesion } = usarSesion();
  const nombre = (usuario && (usuario.nombreCompleto || usuario.username)) || 'Usuario';

  return (
    <header className="chn-superior">
      <div className="chn-superior__izquierda">
        <button
          type="button"
          className="chn-superior__menu"
          onClick={onAlternarMenu}
          aria-label={menuAbierto ? 'Cerrar menú de navegación' : 'Abrir menú de navegación'}
          aria-expanded={menuAbierto}
          aria-controls="chn-barra-lateral"
        >
          <Icono nombre={menuAbierto ? 'cerrar' : 'menu'} tamano={20} />
        </button>
        <p className="chn-superior__titulo">{titulo}</p>
      </div>

      <div className="chn-superior__derecha">
        <div className="chn-superior__usuario" data-captura="barra-usuario">
          <span className="chn-superior__avatar" aria-hidden="true">
            {iniciales(nombre)}
          </span>
          <span className="chn-superior__datos">
            <span className="chn-superior__nombre">{nombre}</span>
            <span className="chn-superior__rol">{etiquetaRol}</span>
          </span>
        </div>

        <Boton
          variante="secundario"
          tamano="sm"
          iconoIzquierda="salir"
          onClick={cerrarSesion}
          data-captura="boton-salir"
        >
          Cerrar sesión
        </Boton>
      </div>
    </header>
  );
}

export default BarraSuperior;
