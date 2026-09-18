import { useEffect, useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { Notificaciones } from '../componentes/index.js';
import BarraLateral from './BarraLateral.jsx';
import BarraSuperior from './BarraSuperior.jsx';
import './AppLayout.css';

const TITULOS = [
  { patron: /^\/auditoria/, titulo: 'Bitácora de auditoría' },
  { patron: /^\/clientes/, titulo: 'Clientes' },
  { patron: /^\/solicitudes/, titulo: 'Solicitudes de préstamo' },
  { patron: /^\/prestamos/, titulo: 'Préstamos' },
  { patron: /^\/pagos/, titulo: 'Pagos' },
  { patron: /^\/$/, titulo: 'Tablero' },
];

function tituloDeRuta(ruta) {
  const coincidencia = TITULOS.find((entrada) => entrada.patron.test(ruta));
  return coincidencia ? coincidencia.titulo : 'Sistema de Préstamos';
}

function AppLayout() {
  const [menuAbierto, setMenuAbierto] = useState(false);
  const { pathname } = useLocation();

  // El menu off-canvas taparia el contenido de la nueva ruta.
  useEffect(() => {
    setMenuAbierto(false);
  }, [pathname]);

  return (
    <div className="chn-layout">
      <a className="chn-layout__salto" href="#chn-contenido">
        Saltar al contenido principal
      </a>

      <BarraLateral abierta={menuAbierto} onCerrar={() => setMenuAbierto(false)} />

      <div className="chn-layout__principal">
        <BarraSuperior
          titulo={tituloDeRuta(pathname)}
          menuAbierto={menuAbierto}
          onAlternarMenu={() => setMenuAbierto((abierto) => !abierto)}
        />

        <main className="chn-layout__contenido" id="chn-contenido">
          {/* key por ruta: fuerza el remontaje para reiniciar la animacion de entrada. */}
          <div className="chn-layout__limite chn-anim-aparecer" key={pathname}>
            <Outlet />
          </div>
        </main>
      </div>

      <Notificaciones />
    </div>
  );
}

export default AppLayout;
