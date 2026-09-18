import clienteHttp, { parametrosLimpios } from './clienteHttp.js';

// El filtrado ocurre en la base de datos, no en el navegador, para que la paginación sea exacta.
export function listar({
  busqueda,
  nacimientoDesde,
  nacimientoHasta,
  creacionDesde,
  creacionHasta,
  activo,
  pagina = 0,
  tamano = 10,
} = {}) {
  return clienteHttp.get('/clientes', {
    params: parametrosLimpios({
      busqueda,
      nacimientoDesde,
      nacimientoHasta,
      creacionDesde,
      creacionHasta,
      activo,
      pagina,
      tamano,
    }),
  });
}

export function obtener(id) {
  return clienteHttp.get(`/clientes/${id}`);
}

export function crear(datos) {
  return clienteHttp.post('/clientes', datos);
}

// El DPI y la fecha de nacimiento no son editables.
export function actualizar(id, datos) {
  return clienteHttp.put(`/clientes/${id}`, datos);
}

// El backend lo rechaza si el cliente tiene préstamos vigentes.
export function eliminar(id) {
  return clienteHttp.delete(`/clientes/${id}`);
}

export function solicitudesDe(id) {
  return clienteHttp.get(`/clientes/${id}/solicitudes`);
}

export function prestamosDe(id) {
  return clienteHttp.get(`/clientes/${id}/prestamos`);
}
