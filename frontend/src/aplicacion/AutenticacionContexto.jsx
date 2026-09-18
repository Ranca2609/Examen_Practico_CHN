import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { permisosDeRol } from '../dominio/catalogos.js';
import * as adaptadorAutenticacion from '../infraestructura/http/adaptadorAutenticacion.js';
import { registrarManejadorNoAutorizado } from '../infraestructura/http/clienteHttp.js';
import {
  guardarSesion,
  guardarUsuario,
  limpiarSesion,
  obtenerToken,
  obtenerUsuario,
} from '../infraestructura/almacenamiento/almacenSesion.js';
import { useNotificaciones } from './NotificacionContexto.jsx';

const AutenticacionContexto = createContext(null);

export function AutenticacionProveedor({ children }) {
  const { notificar } = useNotificaciones();

  const [usuario, setUsuario] = useState(null);
  const [token, setToken] = useState(null);
  // true hasta restaurar la sesión, para que RutaProtegida no redirija al login antes de tiempo.
  const [cargando, setCargando] = useState(true);

  // Ref para leer el usuario vigente en el manejador del 401 sin volver a registrarlo.
  const usuarioRef = useRef(null);
  usuarioRef.current = usuario;

  const limpiarEstado = useCallback(() => {
    limpiarSesion();
    setUsuario(null);
    setToken(null);
  }, []);

  useEffect(() => {
    let activo = true;

    const tokenGuardado = obtenerToken();
    const usuarioGuardado = obtenerUsuario();

    if (!tokenGuardado) {
      setCargando(false);
      return undefined;
    }

    // Se pinta la sesión guardada al instante (sin parpadeo al login) y el token se valida en paralelo.
    setToken(tokenGuardado);
    setUsuario(usuarioGuardado);

    adaptadorAutenticacion
      .perfil()
      .then((perfilVigente) => {
        if (!activo) return;
        setUsuario(perfilVigente);
        guardarUsuario(perfilVigente);
      })
      .catch((error) => {
        if (!activo) return;
        // Otros fallos (servidor caído) no deben expulsar al usuario: se conserva lo guardado.
        if (error?.estado === 401) limpiarEstado();
      })
      .finally(() => {
        if (activo) setCargando(false);
      });

    return () => {
      activo = false;
    };
  }, [limpiarEstado]);

  useEffect(() => {
    // clienteHttp no conoce React: al vaciar el estado, RutaProtegida redirige a /login sin recargar.
    const desregistrar = registrarManejadorNoAutorizado(() => {
      // Solo avisa si había sesión. Se lee la ref y no un actualizador de estado porque
      // React puede ejecutarlo dos veces y duplicar el aviso.
      if (usuarioRef.current) {
        notificar({
          tono: 'advertencia',
          titulo: 'Sesión finalizada',
          mensaje: 'Su sesión expiró. Inicie sesión nuevamente.',
        });
      }

      setUsuario(null);
      setToken(null);
    });

    return desregistrar;
  }, [notificar]);

  // Propaga el error para que el formulario de login lo muestre junto a los campos.
  const iniciarSesion = useCallback(
    async ({ username, contrasena }) => {
      const respuesta = await adaptadorAutenticacion.iniciarSesion({ username, contrasena });

      guardarSesion({ token: respuesta.token, usuario: respuesta.usuario });
      setToken(respuesta.token);
      setUsuario(respuesta.usuario);

      notificar({
        tono: 'exito',
        mensaje: `Bienvenido(a), ${respuesta.usuario?.nombreCompleto ?? respuesta.usuario?.username ?? ''}.`,
      });

      return respuesta.usuario;
    },
    [notificar],
  );

  // JWT sin estado: cerrar sesión es solo local.
  const cerrarSesion = useCallback(() => {
    limpiarEstado();
    notificar({ tono: 'info', mensaje: 'Sesión cerrada correctamente.' });
  }, [limpiarEstado, notificar]);

  const permisos = useMemo(() => permisosDeRol(usuario?.rol), [usuario?.rol]);

  // Solo decide qué se muestra; la autorización real la aplica el backend en cada endpoint.
  const tienePermiso = useCallback((permiso) => permisos.includes(permiso), [permisos]);

  const esRol = useCallback(
    (rol) => {
      if (!usuario?.rol) return false;
      return Array.isArray(rol) ? rol.includes(usuario.rol) : usuario.rol === rol;
    },
    [usuario?.rol],
  );

  const valor = useMemo(
    () => ({
      usuario,
      token,
      autenticado: Boolean(token && usuario),
      cargando,
      permisos,
      iniciarSesion,
      cerrarSesion,
      tienePermiso,
      esRol,
    }),
    [usuario, token, cargando, permisos, iniciarSesion, cerrarSesion, tienePermiso, esRol],
  );

  return <AutenticacionContexto.Provider value={valor}>{children}</AutenticacionContexto.Provider>;
}

export function useAutenticacion() {
  const contexto = useContext(AutenticacionContexto);

  if (!contexto) {
    throw new Error('useAutenticacion debe usarse dentro de <AutenticacionProveedor>.');
  }

  return contexto;
}

export default AutenticacionContexto;
