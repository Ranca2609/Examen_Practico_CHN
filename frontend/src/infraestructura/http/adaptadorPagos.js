import clienteHttp, { parametrosLimpios } from './clienteHttp.js';

export function listar({
  busqueda,
  prestamoId,
  clienteId,
  montoMinimo,
  montoMaximo,
  fechaDesde,
  fechaHasta,
  usuarioRegistro,
  pagina = 0,
  tamano = 10,
} = {}) {
  return clienteHttp.get('/pagos', {
    params: parametrosLimpios({
      busqueda,
      prestamoId,
      clienteId,
      montoMinimo,
      montoMaximo,
      fechaDesde,
      fechaHasta,
      usuarioRegistro,
      pagina,
      tamano,
    }),
  });
}

export function registrar(datos) {
  return clienteHttp.post('/pagos', datos);
}
