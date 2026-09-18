import { useCallback, useEffect, useMemo, useState } from 'react';
import * as adaptadorPrestamos from '../../infraestructura/http/adaptadorPrestamos.js';
import { ESTADOS_PRESTAMO, etiquetaEstadoPrestamo } from '../../dominio/catalogos.js';
import { moneda } from '../../dominio/formato.js';
import { useAvisoDeError } from '../NotificacionContexto.jsx';
import useFiltros from './useFiltros.js';
import { describirRangoFechas } from './useClientes.js';

// Coinciden con los parámetros de GET /prestamos.
export const FILTROS_PRESTAMOS = {
  busqueda: '',
  clienteId: '',
  estado: '',
  montoMinimo: '',
  montoMaximo: '',
  saldoMinimo: '',
  saldoMaximo: '',
  desembolsoDesde: '',
  desembolsoHasta: '',
  vencimientoDesde: '',
  vencimientoHasta: '',
};

const ESCRITOS_A_MANO = [
  'busqueda',
  'montoMinimo',
  'montoMaximo',
  'saldoMinimo',
  'saldoMaximo',
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

const RANGOS_PRESTAMOS = [
  {
    desde: 'montoMinimo',
    hasta: 'montoMaximo',
    etiqueta: 'Monto',
    describir: (desde, hasta) => describirRangoMontos(desde, hasta),
  },
  {
    desde: 'saldoMinimo',
    hasta: 'saldoMaximo',
    etiqueta: 'Saldo',
    describir: (desde, hasta) => describirRangoMontos(desde, hasta),
  },
  {
    desde: 'desembolsoDesde',
    hasta: 'desembolsoHasta',
    etiqueta: 'Desembolso',
    describir: (desde, hasta) => describirRangoFechas(desde, hasta),
  },
  {
    desde: 'vencimientoDesde',
    hasta: 'vencimientoHasta',
    etiqueta: 'Vencimiento',
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

function describirFiltroPrestamo(clave, valor, { nombresCliente }) {
  if (clave === 'busqueda') return `Contiene «${valor}»`;
  if (clave === 'estado') return `Estado: ${etiquetaEstadoPrestamo(valor)}`;
  if (clave === 'clienteId') {
    return `Cliente: ${nombresCliente.get(String(valor)) ?? `#${valor}`}`;
  }
  return null;
}

// "Limpiar" vuelve a los valores iniciales: son el contexto desde el que se abrió el listado.
export function usePrestamos({ estadoInicial = '', clienteInicial = '', tamanoInicial = 10 } = {}) {
  const avisarError = useAvisoDeError();

  const [prestamos, setPrestamos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);

  // useFiltros solo lee estos valores al montar, así que basta con armarlos una vez.
  const valoresIniciales = useMemo(
    () => ({
      ...FILTROS_PRESTAMOS,
      estado: estadoInicial ?? '',
      clienteId: clienteInicial ?? '',
    }),
    [estadoInicial, clienteInicial],
  );

  const nombresCliente = useMemo(
    () => indicePor(prestamos, 'clienteId', 'nombreCliente'),
    [prestamos],
  );

  const describir = useCallback(
    (clave, valor) => describirFiltroPrestamo(clave, valor, { nombresCliente }),
    [nombresCliente],
  );

  const filtros = useFiltros(valoresIniciales, {
    clavesConRetardo: ESCRITOS_A_MANO,
    rangos: RANGOS_PRESTAMOS,
    describir,
  });

  const [pagina, setPagina] = useState(0);
  const [tamano, setTamano] = useState(tamanoInicial);
  const [totalElementos, setTotalElementos] = useState(0);
  const [totalPaginas, setTotalPaginas] = useState(0);

  const [recarga, setRecarga] = useState(0);
  const recargar = useCallback(() => setRecarga((valor) => valor + 1), []);

  useEffect(() => {
    setPagina(0);
  }, [filtros.aplicados]);

  useEffect(() => {
    let activo = true;

    setCargando(true);
    setError(null);

    adaptadorPrestamos
      .listar({ ...filtros.aplicados, pagina, tamano })
      .then((respuesta) => {
        if (!activo) return;

        setPrestamos(respuesta?.contenido ?? []);
        setTotalElementos(respuesta?.totalElementos ?? 0);
        setTotalPaginas(respuesta?.totalPaginas ?? 0);
      })
      .catch((fallo) => {
        if (!activo) return;

        setPrestamos([]);
        setError(fallo);
        avisarError(fallo, 'No se pudo cargar el listado de préstamos');
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
    prestamos,
    cargando,
    error,

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

    recargar,
  };
}

export function usePrestamo(id) {
  const avisarError = useAvisoDeError();

  const [prestamo, setPrestamo] = useState(null);
  const [amortizacion, setAmortizacion] = useState(null);
  const [pagos, setPagos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);

  const [recarga, setRecarga] = useState(0);
  const recargar = useCallback(() => setRecarga((valor) => valor + 1), []);

  useEffect(() => {
    if (!id) {
      setPrestamo(null);
      setAmortizacion(null);
      setPagos([]);
      setCargando(false);
      return undefined;
    }

    let activo = true;
    setCargando(true);
    setError(null);

    Promise.all([
      adaptadorPrestamos.obtener(id),
      adaptadorPrestamos.amortizacion(id),
      adaptadorPrestamos.pagosDe(id),
    ])
      .then(([datosPrestamo, plan, listaPagos]) => {
        if (!activo) return;

        setPrestamo(datosPrestamo);
        setAmortizacion(plan);
        // El backend puede devolver el arreglo directo o una página; se aceptan ambos.
        setPagos(Array.isArray(listaPagos) ? listaPagos : listaPagos?.contenido ?? []);
      })
      .catch((fallo) => {
        if (!activo) return;

        setError(fallo);
        avisarError(fallo, 'No se pudo cargar el detalle del préstamo');
      })
      .finally(() => {
        if (activo) setCargando(false);
      });

    return () => {
      activo = false;
    };
  }, [id, recarga, avisarError]);

  return { prestamo, amortizacion, pagos, cargando, error, recargar };
}

// El backend responde 400 con páginas de más de 100: el catálogo se arma por páginas, con un techo.
// Por defecto solo VIGENTES: un préstamo liquidado ya no admite pagos.
const TAMANO_PAGINA_CATALOGO = 100;
const MAXIMO_PAGINAS_CATALOGO = 10;

export function useOpcionesPrestamos({ soloVigentes = true, tamano = TAMANO_PAGINA_CATALOGO } = {}) {
  const avisarError = useAvisoDeError();

  const [prestamos, setPrestamos] = useState([]);
  const [cargando, setCargando] = useState(true);

  const tamanoPagina = Math.min(tamano, TAMANO_PAGINA_CATALOGO);

  useEffect(() => {
    let activo = true;
    setCargando(true);

    const cargarTodo = async () => {
      const acumulado = [];
      let pagina = 0;
      let totalPaginas = 1;

      while (pagina < totalPaginas && pagina < MAXIMO_PAGINAS_CATALOGO) {
        const respuesta = await adaptadorPrestamos.listar({
          estado: soloVigentes ? ESTADOS_PRESTAMO.VIGENTE.valor : '',
          pagina,
          tamano: tamanoPagina,
        });
        acumulado.push(...(respuesta?.contenido ?? []));
        totalPaginas = respuesta?.totalPaginas ?? 1;
        pagina += 1;
      }
      return acumulado;
    };

    cargarTodo()
      .then((todos) => {
        if (activo) setPrestamos(todos);
      })
      .catch((fallo) => {
        if (!activo) return;
        setPrestamos([]);
        avisarError(fallo, 'No se pudo cargar el catálogo de préstamos');
      })
      .finally(() => {
        if (activo) setCargando(false);
      });

    return () => {
      activo = false;
    };
  }, [soloVigentes, tamanoPagina, avisarError]);

  const opciones = useMemo(
    () =>
      prestamos.map((prestamo) => ({
        valor: String(prestamo.id),
        etiqueta: `${prestamo.numeroPrestamo} — ${prestamo.nombreCliente}`,
      })),
    [prestamos],
  );

  return { prestamos, opciones, cargando };
}

export default usePrestamos;
