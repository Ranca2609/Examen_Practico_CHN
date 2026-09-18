import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Alerta, Cargando } from '../componentes/index.js';
import { usarSesion } from './usarSesion.js';
import './RutaProtegida.css';

function RutaProtegida({ children, permiso }) {
  const { cargando, autenticado, tienePermiso } = usarSesion();
  const ubicacion = useLocation();

  if (cargando) {
    return <Cargando texto="Verificando sesión..." altura={320} />;
  }

  if (!autenticado) {
    // "desde" lo usa Login para volver a la pantalla solicitada tras el ingreso.
    return <Navigate to="/login" replace state={{ desde: ubicacion }} />;
  }

  // Sin permiso no se redirige: el usuario ya esta autenticado y un 403 explicito es mas claro.
  if (permiso && !tienePermiso(permiso)) {
    return (
      <div className="chn-acceso-denegado">
        <Alerta tono="peligro" titulo="Acceso denegado">
          <p>
            Su rol no cuenta con los privilegios necesarios para consultar esta sección. Si requiere
            acceso, solicítelo al administrador del sistema.
          </p>
        </Alerta>
      </div>
    );
  }

  return children ?? <Outlet />;
}

export default RutaProtegida;
