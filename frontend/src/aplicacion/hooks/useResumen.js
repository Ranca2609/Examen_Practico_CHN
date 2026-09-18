import { useCallback, useEffect, useState } from 'react';
import * as adaptadorResumen from '../../infraestructura/http/adaptadorResumen.js';
import { useAvisoDeError } from '../NotificacionContexto.jsx';

const RESUMEN_VACIO = {
  totalClientes: 0,
  solicitudesEnProceso: 0,
  solicitudesAprobadas: 0,
  solicitudesRechazadas: 0,
  prestamosVigentes: 0,
  prestamosLiquidados: 0,
  montoTotalAprobado: 0,
  saldoPendienteTotal: 0,
  totalRecuperado: 0,
  carteraPorTipo: [],
  recaudacionMensual: [],
};

const comoLista = (valor) => (Array.isArray(valor) ? valor : []);

// Rellena los indicadores que falten y fuerza las series a lista: un null rompería las gráficas.
function normalizarResumen(datos) {
  const resumen = { ...RESUMEN_VACIO, ...(datos ?? {}) };
  return {
    ...resumen,
    carteraPorTipo: comoLista(resumen.carteraPorTipo),
    recaudacionMensual: comoLista(resumen.recaudacionMensual),
  };
}

// resumen es null hasta la primera respuesta: así no se presentan ceros inventados como reales.
export function useResumen() {
  const avisarError = useAvisoDeError();

  const [resumen, setResumen] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);

  const [recarga, setRecarga] = useState(0);
  const recargar = useCallback(() => setRecarga((valor) => valor + 1), []);

  useEffect(() => {
    let activo = true;

    setCargando(true);
    setError(null);

    adaptadorResumen
      .obtener()
      .then((datos) => {
        if (activo) setResumen(normalizarResumen(datos));
      })
      .catch((fallo) => {
        if (!activo) return;

        // Conserva el último resumen bueno: el error no se disfraza de cartera vacía.
        setError(fallo);
        avisarError(fallo, 'No se pudieron cargar los indicadores');
      })
      .finally(() => {
        if (activo) setCargando(false);
      });

    return () => {
      activo = false;
    };
  }, [recarga, avisarError]);

  return { resumen, cargando, error, recargar };
}

export default useResumen;
