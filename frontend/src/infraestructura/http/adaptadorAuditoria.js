import clienteHttp, { parametrosLimpios } from './clienteHttp.js';

export function listar({
  busqueda,
  usuario,
  accion,
  entidad,
  fechaDesde,
  fechaHasta,
  pagina = 0,
  tamano = 20,
} = {}) {
  return clienteHttp.get('/auditoria', {
    params: parametrosLimpios({
      busqueda,
      usuario,
      accion,
      entidad,
      fechaDesde,
      fechaHasta,
      pagina,
      tamano,
    }),
  });
}
