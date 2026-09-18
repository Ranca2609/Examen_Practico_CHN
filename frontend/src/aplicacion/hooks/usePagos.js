import { useCallback, useEffect, useMemo, useState } from 'react';
import * as adaptadorPagos from '../../infraestructura/http/adaptadorPagos.js';
import { useAvisoDeError, useNotificaciones } from '../NotificacionContexto.jsx';
import { moneda } from '../../dominio/formato.js';
import useFiltros from './useFiltros.js';
import { describirRangoFechas } from './useClientes.js';

// Coinciden con los parámetros de GET /pagos.
export const FILTROS_PAGOS = {
  busqueda: '',
  prestamoId: '',
  clienteId: '',
  montoMinimo: '',
  montoMaximo: '',
  fechaDesde: '',
  fechaHasta: '',
  usuarioRegistro: '',
};

const ESCRITOS_A_MANO = ['busqueda', 'montoMinimo', 'montoMaximo', 'usuarioRegistro'];

function hayExtremo(valor) {
  if (valor === null || valor === undefined) return false;
  return String(valor).trim() !== '';
}

function describirRangoMontos(desde, hasta) {
  if (hayExtremo(desde) && hayExtremo(hasta)) return `${moneda(desde)} – ${moneda(hasta)}`;
  if (hayExtremo(desde)) return `desde ${moneda(desde)}`;
  return `hasta ${moneda(hasta)}`;
}

const RANGOS_PAGOS = [
  {
    desde: 'montoMinimo',
    hasta: 'montoMaximo',
    etiqueta: 'Monto',
    describir: (desde, hasta) => describirRangoMontos(desde, hasta),
  },
  {
    desde: 'fechaDesde',
    hasta: 'fechaHasta',
    etiqueta: 'Fecha de pago',
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

function describirFiltroPago(clave, valor, { nombresCliente, numerosPrestamo }) {
  if (clave === 'busqueda') return `Contiene «${valor}»`;
  if (clave === 'clienteId') {
    return `Cliente: ${nombresCliente.get(String(valor)) ?? `#${valor}`}`;
  }
  if (clave === 'prestamoId') {
    return `Préstamo: ${numerosPrestamo.get(String(valor)) ?? `#${valor}`}`;
  }
  if (clave === 'usuarioRegistro') return `Registrado por: ${valor}`;
  return null;
}

// "Limpiar" vuelve a los valores iniciales: son el contexto desde el que se abrió el listado.
export function usePagos({ prestamoInicial = '', clienteInicial = '', tamanoInicial = 10 } = {}) {
  const { notificar } = useNotificaciones();
  const avisarError = useAvisoDeError();

  const [pagos, setPagos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);

  // useFiltros solo lee estos valores al montar, así que basta con armarlos una vez.
  const valoresIniciales = useMemo(
    () => ({
      ...FILTROS_PAGOS,
      prestamoId: prestamoInicial ?? '',
      clienteId: clienteInicial ?? '',
    }),
    [prestamoInicial, clienteInicial],
  );

  const nombresCliente = useMemo(() => indicePor(pagos, 'clienteId', 'nombreCliente'), [pagos]);
  const numerosPrestamo = useMemo(
    () => indicePor(pagos, 'prestamoId', 'numeroPrestamo'),
    [pagos],
  );

  const describir = useCallback(
    (clave, valor) => describirFiltroPago(clave, valor, { nombresCliente, numerosPrestamo }),
    [nombresCliente, numerosPrestamo],
  );

  const filtros = useFiltros(valoresIniciales, {
    clavesConRetardo: ESCRITOS_A_MANO,
    rangos: RANGOS_PAGOS,
    describir,
  });

  const [pagina, setPagina] = useState(0);
  const [tamano, setTamano] = useState(tamanoInicial);
  const [totalElementos, setTotalElementos] = useState(0);
  const [totalPaginas, setTotalPaginas] = useState(0);

  const [guardando, setGuardando] = useState(false);
  const [comprobante, setComprobante] = useState(null);

  const [recarga, setRecarga] = useState(0);
  const recargar = useCallback(() => setRecarga((valor) => valor + 1), []);

  useEffect(() => {
    setPagina(0);
  }, [filtros.aplicados]);

  useEffect(() => {
    let activo = true;

    setCargando(true);
    setError(null);

    adaptadorPagos
      .listar({ ...filtros.aplicados, pagina, tamano })
      .then((respuesta) => {
        if (!activo) return;

        setPagos(respuesta?.contenido ?? []);
        setTotalElementos(respuesta?.totalElementos ?? 0);
        setTotalPaginas(respuesta?.totalPaginas ?? 0);
      })
      .catch((fallo) => {
        if (!activo) return;

        setPagos([]);
        setError(fallo);
        avisarError(fallo, 'No se pudo cargar el listado de pagos');
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

  // El backend liquida el préstamo cuando el saldo llega a cero; aquí solo se avisa del cambio.
  const registrar = useCallback(
    async (datos) => {
      setGuardando(true);
      try {
        const nuevoPago = await adaptadorPagos.registrar(datos);

        setComprobante(nuevoPago);
        notificar({
          tono: 'exito',
          titulo: 'Pago registrado',
          mensaje: `Recibo ${nuevoPago?.numeroRecibo ?? ''} aplicado al préstamo ${nuevoPago?.numeroPrestamo ?? ''}.`,
        });

        if (Number(nuevoPago?.saldoPosterior) === 0) {
          notificar({
            tono: 'info',
            titulo: 'Préstamo liquidado',
            mensaje: 'El saldo quedó en cero; el préstamo ya no admite más pagos.',
          });
        }

        recargar();
        return nuevoPago;
      } catch (fallo) {
        avisarError(fallo, 'No se pudo registrar el pago');
        throw fallo;
      } finally {
        setGuardando(false);
      }
    },
    [notificar, recargar, avisarError],
  );

  const limpiarComprobante = useCallback(() => setComprobante(null), []);

  // Atajos para pantallas que aún no usan el panel de filtros.
  const cambiarPrestamo = useCallback(
    (nuevoPrestamo) => filtros.establecerFiltro('prestamoId', nuevoPrestamo ?? ''),
    [filtros],
  );

  const cambiarCliente = useCallback(
    (nuevoCliente) => filtros.establecerFiltro('clienteId', nuevoCliente ?? ''),
    [filtros],
  );

  return {
    pagos,
    cargando,
    error,
    guardando,
    comprobante,
    limpiarComprobante,

    filtros: filtros.valores,
    establecerFiltro: filtros.establecerFiltro,
    establecerFiltros: filtros.establecerFiltros,
    quitarFiltro: filtros.quitarFiltro,
    limpiarFiltros: filtros.limpiarFiltros,
    filtrosActivos: filtros.filtrosActivos,
    chips: filtros.chips,

    prestamoId: filtros.valores.prestamoId,
    clienteId: filtros.valores.clienteId,
    cambiarPrestamo,
    cambiarCliente,

    pagina,
    tamano,
    totalElementos,
    totalPaginas,
    setPagina,
    cambiarTamano,

    registrar,
    recargar,
  };
}

export default usePagos;
