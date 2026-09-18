import { useCallback, useEffect, useMemo, useState } from 'react';
import * as adaptadorSolicitudes from '../../infraestructura/http/adaptadorSolicitudes.js';
import { useAvisoDeError, useNotificaciones } from '../NotificacionContexto.jsx';
import { etiquetaEstadoSolicitud, etiquetaTipoPrestamo } from '../../dominio/catalogos.js';
import { moneda } from '../../dominio/formato.js';
import useFiltros from './useFiltros.js';
import { describirRangoFechas } from './useClientes.js';

// Coinciden con los parámetros de GET /solicitudes.
export const FILTROS_SOLICITUDES = {
  busqueda: '',
  clienteId: '',
  estado: '',
  tipoPrestamo: '',
  montoMinimo: '',
  montoMaximo: '',
  plazoMinimo: '',
  plazoMaximo: '',
  fechaDesde: '',
  fechaHasta: '',
};

const ESCRITOS_A_MANO = [
  'busqueda',
  'montoMinimo',
  'montoMaximo',
  'plazoMinimo',
  'plazoMaximo',
];

function hayExtremo(valor) {
  if (valor === null || valor === undefined) return false;
  return String(valor).trim() !== '';
}

function describirRangoMontos(desde, hasta) {
  if (hayExtremo(desde) && hayExtremo(hasta)) return `${moneda(desde)} – ${moneda(hasta)}`;
  if (hayExtremo(desde)) return `desde ${moneda(desde)}`;
  return `hasta ${moneda(hasta)}`;
}

function describirRangoPlazos(desde, hasta) {
  const meses = (valor) => (Number(valor) === 1 ? 'mes' : 'meses');
  const texto = (valor) => String(valor).trim();

  if (hayExtremo(desde) && hayExtremo(hasta)) {
    return `${texto(desde)} – ${texto(hasta)} ${meses(hasta)}`;
  }
  if (hayExtremo(desde)) return `desde ${texto(desde)} ${meses(desde)}`;
  return `hasta ${texto(hasta)} ${meses(hasta)}`;
}

const RANGOS_SOLICITUDES = [
  {
    desde: 'montoMinimo',
    hasta: 'montoMaximo',
    etiqueta: 'Monto',
    describir: (desde, hasta) => describirRangoMontos(desde, hasta),
  },
  {
    desde: 'plazoMinimo',
    hasta: 'plazoMaximo',
    etiqueta: 'Plazo',
    describir: (desde, hasta) => describirRangoPlazos(desde, hasta),
  },
  {
    desde: 'fechaDesde',
    hasta: 'fechaHasta',
    etiqueta: 'Fecha de solicitud',
    describir: (desde, hasta) => describirRangoFechas(desde, hasta),
  },
];

// Traduce ids a etiquetas con las filas ya cargadas; si aún no se conocen, la pastilla muestra el id.
function indicePor(filas, claveId, claveEtiqueta) {
  const indice = new Map();

  filas.forEach((fila) => {
    const id = fila?.[claveId];
    const etiqueta = fila?.[claveEtiqueta];
    if (id !== null && id !== undefined && etiqueta) indice.set(String(id), etiqueta);
  });

  return indice;
}

function describirFiltroSolicitud(clave, valor, { nombresCliente }) {
  if (clave === 'busqueda') return `Contiene «${valor}»`;
  if (clave === 'estado') return `Estado: ${etiquetaEstadoSolicitud(valor)}`;
  if (clave === 'tipoPrestamo') return `Tipo: ${etiquetaTipoPrestamo(valor)}`;
  if (clave === 'clienteId') {
    return `Cliente: ${nombresCliente.get(String(valor)) ?? `#${valor}`}`;
  }
  return null;
}

// "Limpiar" vuelve a los valores iniciales: son el contexto desde el que se abrió el listado.
export function useSolicitudes({ estadoInicial = '', clienteInicial = '', tamanoInicial = 10 } = {}) {
  const { notificar } = useNotificaciones();
  const avisarError = useAvisoDeError();

  const [solicitudes, setSolicitudes] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);

  // useFiltros solo lee estos valores al montar, así que basta con armarlos una vez.
  const valoresIniciales = useMemo(
    () => ({
      ...FILTROS_SOLICITUDES,
      estado: estadoInicial ?? '',
      clienteId: clienteInicial ?? '',
    }),
    [estadoInicial, clienteInicial],
  );

  const nombresCliente = useMemo(
    () => indicePor(solicitudes, 'clienteId', 'nombreCliente'),
    [solicitudes],
  );

  const describir = useCallback(
    (clave, valor) => describirFiltroSolicitud(clave, valor, { nombresCliente }),
    [nombresCliente],
  );

  const filtros = useFiltros(valoresIniciales, {
    clavesConRetardo: ESCRITOS_A_MANO,
    rangos: RANGOS_SOLICITUDES,
    describir,
  });

  const [pagina, setPagina] = useState(0);
  const [tamano, setTamano] = useState(tamanoInicial);
  const [totalElementos, setTotalElementos] = useState(0);
  const [totalPaginas, setTotalPaginas] = useState(0);

  const [guardando, setGuardando] = useState(false);

  const [recarga, setRecarga] = useState(0);
  const recargar = useCallback(() => setRecarga((valor) => valor + 1), []);

  useEffect(() => {
    setPagina(0);
  }, [filtros.aplicados]);

  useEffect(() => {
    let activo = true;

    setCargando(true);
    setError(null);

    adaptadorSolicitudes
      .listar({ ...filtros.aplicados, pagina, tamano })
      .then((respuesta) => {
        if (!activo) return;

        setSolicitudes(respuesta?.contenido ?? []);
        setTotalElementos(respuesta?.totalElementos ?? 0);
        setTotalPaginas(respuesta?.totalPaginas ?? 0);
      })
      .catch((fallo) => {
        if (!activo) return;

        setSolicitudes([]);
        setError(fallo);
        avisarError(fallo, 'No se pudo cargar el listado de solicitudes');
      })
      .finally(() => {
        if (activo) setCargando(false);
      });

    return () => {
      activo = false;
    };
  }, [filtros.aplicados, pagina, tamano, recarga, avisarError]);

  const cambiarTamano = useCallback((nuevoTamano) => {
    setTamano(nuevoTamano);
    setPagina(0);
  }, []);

  const crear = useCallback(
    async (datos) => {
      setGuardando(true);
      try {
        const creada = await adaptadorSolicitudes.crear(datos);
        notificar({
          tono: 'exito',
          titulo: 'Solicitud registrada',
          mensaje: `Se creó la solicitud ${creada?.numeroSolicitud ?? ''} en estado "En proceso".`,
        });
        recargar();
        return creada;
      } catch (fallo) {
        avisarError(fallo, 'No se pudo registrar la solicitud');
        throw fallo;
      } finally {
        setGuardando(false);
      }
    },
    [notificar, recargar, avisarError],
  );

  // Atajos para pantallas que aún no usan el panel de filtros.
  const cambiarEstado = useCallback(
    (nuevoEstado) => filtros.establecerFiltro('estado', nuevoEstado ?? ''),
    [filtros],
  );

  const cambiarCliente = useCallback(
    (nuevoCliente) => filtros.establecerFiltro('clienteId', nuevoCliente ?? ''),
    [filtros],
  );

  return {
    solicitudes,
    cargando,
    error,
    guardando,

    filtros: filtros.valores,
    establecerFiltro: filtros.establecerFiltro,
    establecerFiltros: filtros.establecerFiltros,
    quitarFiltro: filtros.quitarFiltro,
    limpiarFiltros: filtros.limpiarFiltros,
    filtrosActivos: filtros.filtrosActivos,
    chips: filtros.chips,

    estado: filtros.valores.estado,
    clienteId: filtros.valores.clienteId,
    cambiarEstado,
    cambiarCliente,

    pagina,
    tamano,
    totalElementos,
    totalPaginas,
    setPagina,
    cambiarTamano,

    crear,
    recargar,
  };
}

export function useSolicitud(id) {
  const { notificar } = useNotificaciones();
  const avisarError = useAvisoDeError();

  const [solicitud, setSolicitud] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [procesando, setProcesando] = useState(false);

  const [recarga, setRecarga] = useState(0);
  const recargar = useCallback(() => setRecarga((valor) => valor + 1), []);

  useEffect(() => {
    if (!id) {
      setSolicitud(null);
      setCargando(false);
      return undefined;
    }

    let activo = true;
    setCargando(true);
    setError(null);

    adaptadorSolicitudes
      .obtener(id)
      .then((datos) => {
        if (activo) setSolicitud(datos);
      })
      .catch((fallo) => {
        if (!activo) return;
        setSolicitud(null);
        setError(fallo);
        avisarError(fallo, 'No se pudo cargar la solicitud');
      })
      .finally(() => {
        if (activo) setCargando(false);
      });

    return () => {
      activo = false;
    };
  }, [id, recarga, avisarError]);

  const aprobar = useCallback(
    async (datos = {}) => {
      setProcesando(true);
      try {
        const prestamo = await adaptadorSolicitudes.aprobar(id, datos);
        notificar({
          tono: 'exito',
          titulo: 'Solicitud aprobada',
          mensaje: `Se generó el préstamo ${prestamo?.numeroPrestamo ?? ''} con su plan de pagos.`,
        });
        recargar();
        return prestamo;
      } catch (fallo) {
        avisarError(fallo, 'No se pudo aprobar la solicitud');
        throw fallo;
      } finally {
        setProcesando(false);
      }
    },
    [id, notificar, recargar, avisarError],
  );

  const rechazar = useCallback(
    async ({ motivo }) => {
      setProcesando(true);
      try {
        const resultado = await adaptadorSolicitudes.rechazar(id, { motivo });
        notificar({
          tono: 'info',
          titulo: 'Solicitud rechazada',
          mensaje: 'El motivo quedó registrado en la resolución de la solicitud.',
        });
        recargar();
        return resultado;
      } catch (fallo) {
        avisarError(fallo, 'No se pudo rechazar la solicitud');
        throw fallo;
      } finally {
        setProcesando(false);
      }
    },
    [id, notificar, recargar, avisarError],
  );

  return { solicitud, cargando, error, procesando, aprobar, rechazar, recargar };
}

// El backend es la fuente de verdad; dominio/amortizacion.js solo da la vista previa mientras se escribe.
export function useSimulacion() {
  const avisarError = useAvisoDeError();

  const [resultado, setResultado] = useState(null);
  const [simulando, setSimulando] = useState(false);
  const [error, setError] = useState(null);

  const simular = useCallback(
    async (datos) => {
      setSimulando(true);
      setError(null);
      try {
        const respuesta = await adaptadorSolicitudes.simular(datos);
        setResultado(respuesta);
        return respuesta;
      } catch (fallo) {
        setResultado(null);
        setError(fallo);
        avisarError(fallo, 'No se pudo realizar la simulación');
        throw fallo;
      } finally {
        setSimulando(false);
      }
    },
    [avisarError],
  );

  const limpiar = useCallback(() => {
    setResultado(null);
    setError(null);
  }, []);

  return { resultado, simulando, error, simular, limpiar };
}

export default useSolicitudes;
