import axios from 'axios';
import { limpiarSesion, obtenerToken } from '../almacenamiento/almacenSesion.js';

// Relativa por defecto: el proxy de Vite o de nginx sirve la API en el mismo origen,
// así no hay CORS y el token nunca viaja a otro dominio.
const URL_BASE = import.meta.env?.VITE_API_URL || '/api/v1';

const TIEMPO_ESPERA_MS = 20000;

const clienteHttp = axios.create({
  baseURL: URL_BASE,
  timeout: TIEMPO_ESPERA_MS,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
});

// Callback en vez de window.location: una redirección dura recarga la app, pierde el estado
// de React y ata este módulo al enrutador. Sin manejador, la sesión se limpia igual.
let manejadorNoAutorizado = null;

// Devuelve la función de desregistro, pensada para el cleanup de useEffect.
export function registrarManejadorNoAutorizado(fn) {
  manejadorNoAutorizado = typeof fn === 'function' ? fn : null;
  return () => {
    if (manejadorNoAutorizado === fn) manejadorNoAutorizado = null;
  };
}

// En el login un 401 significa credenciales incorrectas, no sesión vencida: debe llegar al formulario.
function esRutaDeAutenticacion(url = '') {
  return String(url).includes('/auth/login');
}

clienteHttp.interceptors.request.use(
  (configuracion) => {
    const token = obtenerToken();

    if (token && !esRutaDeAutenticacion(configuracion.url)) {
      configuracion.headers = configuracion.headers ?? {};
      configuracion.headers.Authorization = `Bearer ${token}`;
    }

    return configuracion;
  },
  (error) => Promise.reject(error),
);

const MENSAJE_SIN_RED = 'No se pudo conectar con el servidor. Verifique su conexión e intente de nuevo.';
const MENSAJE_TIEMPO = 'El servidor tardó demasiado en responder. Intente de nuevo.';
const MENSAJE_GENERICO = 'Ocurrió un error inesperado. Intente de nuevo.';

// Todo fallo sale como { estado, codigo, mensaje, errores }, venga del backend, de la red o de un timeout.
function normalizarError(error) {
  if (error.response) {
    const { status, data } = error.response;

    if (data && typeof data === 'object') {
      return {
        estado: status,
        codigo: data.codigo ?? 'ERROR_INTERNO',
        mensaje: data.mensaje || MENSAJE_GENERICO,
        errores: Array.isArray(data.errores) ? data.errores : null,
      };
    }

    return {
      estado: status,
      codigo: 'ERROR_INTERNO',
      mensaje: typeof data === 'string' && data ? data : MENSAJE_GENERICO,
      errores: null,
    };
  }

  // Sin respuesta: servidor caído, red, CORS o timeout.
  if (error.request) {
    const fueTiempoAgotado = error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT';
    return {
      estado: 0,
      codigo: fueTiempoAgotado ? 'TIEMPO_AGOTADO' : 'SIN_CONEXION',
      mensaje: fueTiempoAgotado ? MENSAJE_TIEMPO : MENSAJE_SIN_RED,
      errores: null,
    };
  }

  return {
    estado: 0,
    codigo: 'ERROR_INTERNO',
    mensaje: error.message || MENSAJE_GENERICO,
    errores: null,
  };
}

// Con responseType 'blob' axios también envuelve en Blob el cuerpo de un 4xx: se reparsea
// el JSON para que normalizarError no pierda el mensaje del backend.
async function desenvolverErrorBlob(error) {
  const cuerpo = error?.response?.data;

  // Sin DOM (pruebas en Node) no existe Blob.
  if (typeof Blob === 'undefined' || !(cuerpo instanceof Blob)) return;

  try {
    const texto = await cuerpo.text();
    error.response.data = JSON.parse(texto);
  } catch {
    // No es el JSON de error (p. ej. HTML de un proxy): mejor el mensaje genérico que volcar la página.
    error.response.data = null;
  }
}

clienteHttp.interceptors.response.use(
  (respuesta) => {
    // Las descargas necesitan la respuesta completa: el nombre viaja en Content-Disposition.
    if (respuesta.config?.responseType === 'blob') return respuesta;

    // El resto recibe solo el cuerpo, para no acoplar los adaptadores a la envoltura de axios.
    return respuesta.data;
  },

  async (error) => {
    await desenvolverErrorBlob(error);

    const normalizado = normalizarError(error);
    const url = error?.config?.url ?? '';

    if (normalizado.estado === 401 && !esRutaDeAutenticacion(url)) {
      limpiarSesion();

      if (manejadorNoAutorizado) {
        manejadorNoAutorizado();
      }

      normalizado.mensaje = 'Su sesión expiró. Inicie sesión nuevamente.';
    }

    return Promise.reject(normalizado);
  },
);

// Evita que un filtro sin usar viaje como "?estado=" y el backend tenga que interpretar cadenas vacías.
export function parametrosLimpios(parametros = {}) {
  return Object.fromEntries(
    Object.entries(parametros).filter(
      ([, valor]) => valor !== null && valor !== undefined && valor !== '',
    ),
  );
}

export default clienteHttp;
