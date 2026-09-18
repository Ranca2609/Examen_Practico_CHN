// sessionStorage y no localStorage: en equipos compartidos el token muere al cerrar la pestaña.
// Lo ideal sería una cookie HttpOnly, pero la prueba define autenticación por cabecera Bearer.
const CLAVE_TOKEN = 'chn.token';
const CLAVE_USUARIO = 'chn.usuario';

// Todo acceso va en try/catch: en modo privado o con almacenamiento bloqueado, el navegador lanza.
function almacen() {
  try {
    return typeof window !== 'undefined' ? window.sessionStorage : null;
  } catch {
    return null;
  }
}

export function guardarSesion({ token, usuario }) {
  const deposito = almacen();
  if (!deposito) return false;

  try {
    if (token) deposito.setItem(CLAVE_TOKEN, token);
    if (usuario) deposito.setItem(CLAVE_USUARIO, JSON.stringify(usuario));
    return true;
  } catch {
    // Cuota agotada o almacenamiento bloqueado: la sesión vive solo en memoria.
    return false;
  }
}

export function guardarUsuario(usuario) {
  const deposito = almacen();
  if (!deposito || !usuario) return false;

  try {
    deposito.setItem(CLAVE_USUARIO, JSON.stringify(usuario));
    return true;
  } catch {
    return false;
  }
}

export function obtenerToken() {
  const deposito = almacen();
  if (!deposito) return null;

  try {
    return deposito.getItem(CLAVE_TOKEN);
  } catch {
    return null;
  }
}

// Con el JSON corrupto se limpia la sesión: mejor pedir credenciales que arrastrar un estado inconsistente.
export function obtenerUsuario() {
  const deposito = almacen();
  if (!deposito) return null;

  try {
    const crudo = deposito.getItem(CLAVE_USUARIO);
    return crudo ? JSON.parse(crudo) : null;
  } catch {
    limpiarSesion();
    return null;
  }
}

export function limpiarSesion() {
  const deposito = almacen();
  if (!deposito) return;

  try {
    deposito.removeItem(CLAVE_TOKEN);
    deposito.removeItem(CLAVE_USUARIO);
  } catch {
    // El estado en memoria ya se descartó.
  }
}

// No valida la vigencia del token: eso lo decide el backend.
export function haySesion() {
  return Boolean(obtenerToken());
}
