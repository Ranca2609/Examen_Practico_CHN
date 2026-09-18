import { useAutenticacion } from '../../aplicacion/AutenticacionContexto.jsx';
import { etiquetaRol, permisosDeRol } from '../../dominio/catalogos.js';

export function usarSesion() {
  const contexto = useAutenticacion() || {};
  const usuario = contexto.usuario || null;
  const rol = usuario ? usuario.rol : null;

  // Respaldo con la matriz del dominio para que el layout no falle con un contexto incompleto.
  const tienePermiso =
    typeof contexto.tienePermiso === 'function'
      ? contexto.tienePermiso
      : (permiso) => permisosDeRol(rol).includes(permiso);

  return {
    usuario,
    cargando: Boolean(contexto.cargando),
    autenticado: contexto.autenticado ?? Boolean(usuario),
    tienePermiso,
    cerrarSesion: contexto.cerrarSesion || (() => {}),
    etiquetaRol: rol ? etiquetaRol(rol) : '',
  };
}

export default usarSesion;
