import clienteHttp from './clienteHttp.js';

// carteraPorTipo trae los 5 tipos aunque estén en cero; recaudacionMensual, 12 meses seguidos hasta el actual.
export function obtener() {
  return clienteHttp.get('/resumen');
}
