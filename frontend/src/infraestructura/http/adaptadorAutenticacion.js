import clienteHttp from './clienteHttp.js';

// Credenciales en el cuerpo, nunca en la URL: así no quedan en logs ni en el historial.
export function iniciarSesion({ username, contrasena }) {
  return clienteHttp.post('/auth/login', { username, contrasena });
}

export function perfil() {
  return clienteHttp.get('/auth/perfil');
}
