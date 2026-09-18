import clienteHttp, { parametrosLimpios } from './clienteHttp.js';
import { guardarArchivo, nombreDesdeCabecera } from './descargas.js';

export function listar({
  busqueda,
  clienteId,
  estado,
  montoMinimo,
  montoMaximo,
  saldoMinimo,
  saldoMaximo,
  desembolsoDesde,
  desembolsoHasta,
  vencimientoDesde,
  vencimientoHasta,
  pagina = 0,
  tamano = 10,
} = {}) {
  return clienteHttp.get('/prestamos', {
    params: parametrosLimpios({
      busqueda,
      clienteId,
      estado,
      montoMinimo,
      montoMaximo,
      saldoMinimo,
      saldoMaximo,
      desembolsoDesde,
      desembolsoHasta,
      vencimientoDesde,
      vencimientoHasta,
      pagina,
      tamano,
    }),
  });
}

export function obtener(id) {
  return clienteHttp.get(`/prestamos/${id}`);
}

export function amortizacion(id) {
  return clienteHttp.get(`/prestamos/${id}/amortizacion`);
}

export function pagosDe(id) {
  return clienteHttp.get(`/prestamos/${id}/pagos`);
}

// Los reportes los arma el backend; aquí solo se pide el binario. El Accept va por formato
// porque clienteHttp pide JSON por omisión y estos endpoints no lo producen.
const ACEPTA_POR_FORMATO = {
  pdf: 'application/pdf',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
};

async function descargarReporte(id, recurso, formato, nombreRespaldo) {
  const acepta = ACEPTA_POR_FORMATO[formato];
  if (!acepta) {
    throw new Error(
      `Formato de reporte no admitido: "${formato}". Use ${Object.keys(ACEPTA_POR_FORMATO).join(' o ')}.`,
    );
  }

  // Con responseType 'blob' clienteHttp devuelve la respuesta completa: el nombre viaja en Content-Disposition.
  const respuesta = await clienteHttp.get(`/prestamos/${id}/${recurso}.${formato}`, {
    responseType: 'blob',
    headers: { Accept: acepta },
  });

  const nombre = nombreDesdeCabecera(respuesta.headers, `${nombreRespaldo}.${formato}`);
  guardarArchivo(respuesta.data, nombre);
  return nombre;
}

export function descargarAmortizacion(id, formato = 'pdf') {
  return descargarReporte(id, 'amortizacion', formato, `plan-amortizacion-${id}`);
}

export function descargarPagos(id, formato = 'pdf') {
  return descargarReporte(id, 'pagos', formato, `historial-pagos-${id}`);
}
