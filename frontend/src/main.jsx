import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';

import App from './App.jsx';
import { AutenticacionProveedor } from './aplicacion/AutenticacionContexto.jsx';
import { NotificacionProveedor } from './aplicacion/NotificacionContexto.jsx';

// animaciones.css entra por @import en global.css para fijar la cascada tokens -> animaciones -> base.
import './estilos/tokens.css';
import './estilos/global.css';

// NotificacionProveedor va por fuera de AutenticacionProveedor: este avisa cuando la sesión expira.
createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <NotificacionProveedor>
        <AutenticacionProveedor>
          <App />
        </AutenticacionProveedor>
      </NotificacionProveedor>
    </BrowserRouter>
  </StrictMode>,
);
