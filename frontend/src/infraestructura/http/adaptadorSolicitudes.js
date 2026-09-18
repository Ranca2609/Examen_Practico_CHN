import clienteHttp, { parametrosLimpios } from './clienteHttp.js';

export function listar({
  busqueda,
  clienteId,
  estado,
  tipoPrestamo,
  montoMinimo,
  montoMaximo,
  plazoMinimo,
  plazoMaximo,
  fechaDesde,
  fechaHasta,
  pagina = 0,
  tamano = 10,
} = {}) {
  return clienteHttp.get('/solicitudes', {
    params: parametrosLimpios({
      busqueda,
      clienteId,
      estado,
      tipoPrestamo,
      montoMinimo,
      montoMaximo,
      plazoMinimo,
      plazoMaximo,
      fechaDesde,
      fechaHasta,
      pagina,
      tamano,
    }),
  });
}

export function obtener(id) {
  return clienteHttp.get(`/solicitudes/${id}`);
}

export function crear(datos) {
  return clienteHttp.post('/solicitudes', datos);
}

// Los campos omitidos toman las condiciones que pidió el cliente.
export function aprobar(id, datos = {}) {
  return clienteHttp.post(`/solicitudes/${id}/aprobar`, datos);
}

// El motivo es obligatorio: queda como sustento auditable.
export function rechazar(id, { motivo }) {
  return clienteHttp.post(`/solicitudes/${id}/rechazar`, { motivo });
}

export function simular(datos) {
  return clienteHttp.post('/solicitudes/simulacion', datos);
}
