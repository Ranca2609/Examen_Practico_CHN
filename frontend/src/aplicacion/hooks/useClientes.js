import { useCallback, useEffect, useMemo, useState } from 'react';
import * as adaptadorClientes from '../../infraestructura/http/adaptadorClientes.js';
import { useAvisoDeError, useNotificaciones } from '../NotificacionContexto.jsx';
import { fecha as formatoFecha } from '../../dominio/formato.js';
import useFiltros from './useFiltros.js';

// Coinciden con los parámetros de GET /clientes.
export const FILTROS_CLIENTES = {
  busqueda: '',
  nacimientoDesde: '',
  nacimientoHasta: '',
  creacionDesde: '',
  creacionHasta: '',
  activo: '',
};

const RANGOS_CLIENTES = [
  {
    desde: 'nacimientoDesde',
    hasta: 'nacimientoHasta',
    etiqueta: 'Nacimiento',
    describir: (desde, hasta) => describirRangoFechas(desde, hasta),
  },
  {
    desde: 'creacionDesde',
    hasta: 'creacionHasta',
    etiqueta: 'Registro',
    describir: (desde, hasta) => describirRangoFechas(desde, hasta),
  },
];

export function describirRangoFechas(desde, hasta) {
  if (desde && hasta) return `${formatoFecha(desde)} – ${formatoFecha(hasta)}`;
  if (desde) return `desde ${formatoFecha(desde)}`;
  return `hasta ${formatoFecha(hasta)}`;
}

function describirFiltroCliente(clave, valor) {
  if (clave === 'busqueda') return `Contiene «${valor}»`;
  if (clave === 'activo') return valor === 'true' ? 'Solo activos' : 'Solo inactivos';
  return null;
}

export function useClientes({ tamanoInicial = 10 } = {}) {
  const { notificar } = useNotificaciones();
  const avisarError = useAvisoDeError();

  const [clientes, setClientes] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);

  const filtros = useFiltros(FILTROS_CLIENTES, {
    clavesConRetardo: ['busqueda'],
    rangos: RANGOS_CLIENTES,
    describir: describirFiltroCliente,
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
    // Descarta respuestas tardías: una lenta podría pisar el resultado de un filtro más nuevo.
    let activo = true;

    setCargando(true);
    setError(null);

    adaptadorClientes
      .listar({ ...filtros.aplicados, pagina, tamano })
      .then((respuesta) => {
        if (!activo) return;

        setClientes(respuesta?.contenido ?? []);
        setTotalElementos(respuesta?.totalElementos ?? 0);
        setTotalPaginas(respuesta?.totalPaginas ?? 0);
      })
      .catch((fallo) => {
        if (!activo) return;

        setClientes([]);
        setError(fallo);
        avisarError(fallo, 'No se pudo cargar el listado de clientes');
      })
      .finally(() => {
        if (activo) setCargando(false);
      });

    return () => {
      activo = false;
    };
  }, [filtros.aplicados, pagina, tamano, recarga, avisarError]);

  // Las escrituras relanzan el error para que useFormulario marque los campos y el modal siga abierto.
  const crear = useCallback(
    async (datos) => {
      setGuardando(true);
      try {
        const creado = await adaptadorClientes.crear(datos);
        notificar({
          tono: 'exito',
          titulo: 'Cliente registrado',
          mensaje: `Se registró a ${creado?.nombreCompleto ?? 'el cliente'} correctamente.`,
        });
        recargar();
        return creado;
      } catch (fallo) {
        avisarError(fallo, 'No se pudo registrar el cliente');
        throw fallo;
      } finally {
        setGuardando(false);
      }
    },
    [notificar, recargar, avisarError],
  );

  const actualizar = useCallback(
    async (id, datos) => {
      setGuardando(true);
      try {
        const actualizado = await adaptadorClientes.actualizar(id, datos);
        notificar({
          tono: 'exito',
          titulo: 'Cliente actualizado',
          mensaje: 'Los datos del cliente se guardaron correctamente.',
        });
        recargar();
        return actualizado;
      } catch (fallo) {
        avisarError(fallo, 'No se pudo actualizar el cliente');
        throw fallo;
      } finally {
        setGuardando(false);
      }
    },
    [notificar, recargar, avisarError],
  );

  const eliminar = useCallback(
    async (id) => {
      setGuardando(true);
      try {
        await adaptadorClientes.eliminar(id);
        notificar({
          tono: 'exito',
          titulo: 'Cliente eliminado',
          mensaje: 'El cliente se dio de baja correctamente.',
        });

        // Si era el último de la página, retrocede para no quedar en una página inexistente.
        if (clientes.length === 1 && pagina > 0) {
          setPagina((actual) => actual - 1);
        } else {
          recargar();
        }
        return true;
      } catch (fallo) {
        avisarError(fallo, 'No se pudo eliminar el cliente');
        throw fallo;
      } finally {
        setGuardando(false);
      }
    },
    [notificar, recargar, avisarError, clientes.length, pagina],
  );

  const cambiarTamano = useCallback((nuevoTamano) => {
    setTamano(nuevoTamano);
    setPagina(0);
  }, []);

  // La búsqueda de la cabecera escribe en el mismo criterio que el panel, así también sale como pastilla.
  const setBusqueda = useCallback(
    (texto) => filtros.establecerFiltro('busqueda', texto ?? ''),
    [filtros],
  );
  const limpiarBusqueda = useCallback(
    () => filtros.establecerFiltro('busqueda', ''),
    [filtros],
  );

  return {
    clientes,
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

    busqueda: filtros.valores.busqueda,
    setBusqueda,
    limpiarBusqueda,

    pagina,
    tamano,
    totalElementos,
    totalPaginas,
    setPagina,
    cambiarTamano,

    crear,
    actualizar,
    eliminar,
    recargar,
  };
}

export function useClienteDetalle(id) {
  const avisarError = useAvisoDeError();

  const [cliente, setCliente] = useState(null);
  const [solicitudes, setSolicitudes] = useState([]);
  const [prestamos, setPrestamos] = useState([]);
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!id) {
      setCliente(null);
      setSolicitudes([]);
      setPrestamos([]);
      return undefined;
    }

    let activo = true;
    setCargando(true);
    setError(null);

    Promise.all([
      adaptadorClientes.obtener(id),
      adaptadorClientes.solicitudesDe(id),
      adaptadorClientes.prestamosDe(id),
    ])
      .then(([datosCliente, susSolicitudes, susPrestamos]) => {
        if (!activo) return;

        setCliente(datosCliente);
        setSolicitudes(Array.isArray(susSolicitudes) ? susSolicitudes : susSolicitudes?.contenido ?? []);
        setPrestamos(Array.isArray(susPrestamos) ? susPrestamos : susPrestamos?.contenido ?? []);
      })
      .catch((fallo) => {
        if (!activo) return;
        setError(fallo);
        avisarError(fallo, 'No se pudo cargar el detalle del cliente');
      })
      .finally(() => {
        if (activo) setCargando(false);
      });

    return () => {
      activo = false;
    };
  }, [id, avisarError]);

  return { cliente, solicitudes, prestamos, cargando, error };
}

// El backend responde 400 con páginas de más de 100: el catálogo se arma por páginas, con un techo.
const TAMANO_PAGINA_CATALOGO = 100;
const MAXIMO_PAGINAS_CATALOGO = 10;

export function useOpcionesClientes({ tamano = TAMANO_PAGINA_CATALOGO } = {}) {
  const avisarError = useAvisoDeError();

  const [clientes, setClientes] = useState([]);
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
        const respuesta = await adaptadorClientes.listar({ pagina, tamano: tamanoPagina });
        acumulado.push(...(respuesta?.contenido ?? []));
        totalPaginas = respuesta?.totalPaginas ?? 1;
        pagina += 1;
      }
      return acumulado;
    };

    cargarTodo()
      .then((todos) => {
        if (activo) setClientes(todos);
      })
      .catch((fallo) => {
        if (!activo) return;
        setClientes([]);
        avisarError(fallo, 'No se pudo cargar el catálogo de clientes');
      })
      .finally(() => {
        if (activo) setCargando(false);
      });

    return () => {
      activo = false;
    };
  }, [tamanoPagina, avisarError]);

  const opciones = useMemo(
    () =>
      clientes.map((cliente) => ({
        valor: String(cliente.id),
        // Se incluye el DPI porque puede haber homónimos.
        etiqueta: `${cliente.nombreCompleto} — DPI ${cliente.numeroIdentificacion}`,
      })),
    [clientes],
  );

  return { clientes, opciones, cargando };
}

export default useClientes;
