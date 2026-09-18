import { Route, Routes } from 'react-router-dom';

import AppLayout from './ui/disenio/AppLayout.jsx';
import RutaProtegida from './ui/disenio/RutaProtegida.jsx';
import { PERMISOS } from './dominio/catalogos.js';

import Login from './ui/paginas/Login.jsx';
import Tablero from './ui/paginas/Tablero.jsx';
import Clientes from './ui/paginas/Clientes.jsx';
import Solicitudes from './ui/paginas/Solicitudes.jsx';
import SolicitudNueva from './ui/paginas/SolicitudNueva.jsx';
import SolicitudDetalle from './ui/paginas/SolicitudDetalle.jsx';
import Prestamos from './ui/paginas/Prestamos.jsx';
import PrestamoDetalle from './ui/paginas/PrestamoDetalle.jsx';
import Pagos from './ui/paginas/Pagos.jsx';
import Auditoria from './ui/paginas/Auditoria.jsx';
import NoEncontrado from './ui/paginas/NoEncontrado.jsx';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />

      {/* Layout compartido: la sesión se verifica una vez y el marco no se remonta al navegar. */}
      <Route
        element={
          <RutaProtegida>
            <AppLayout />
          </RutaProtegida>
        }
      >
        <Route path="/" element={<Tablero />} />
        <Route path="/clientes" element={<Clientes />} />

        <Route path="/solicitudes" element={<Solicitudes />} />
        <Route path="/solicitudes/nueva" element={<SolicitudNueva />} />
        <Route path="/solicitudes/:id" element={<SolicitudDetalle />} />

        <Route path="/prestamos" element={<Prestamos />} />
        <Route path="/prestamos/:id" element={<PrestamoDetalle />} />

        <Route path="/pagos" element={<Pagos />} />

        {/* Solo evita llegar a un 403: el backend exige el mismo permiso. */}
        <Route
          path="/auditoria"
          element={
            <RutaProtegida permiso={PERMISOS.VER_AUDITORIA}>
              <Auditoria />
            </RutaProtegida>
          }
        />

        <Route path="*" element={<NoEncontrado />} />
      </Route>
    </Routes>
  );
}
