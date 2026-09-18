import { useCallback, useEffect, useState } from 'react';
import * as adaptadorAuditoria from '../../infraestructura/http/adaptadorAuditoria.js';
import { useAvisoDeError } from '../NotificacionContexto.jsx';
import useFiltros from './useFiltros.js';
import { describirRangoFechas } from './useClientes.js';

// Coinciden con los parámetros de GET /auditoria.
export const FILTROS_AUDITORIA = {
  busqueda: '',
  usuario: '',
  accion: '',
  entidad: '',
  fechaDesde: '',
  fechaHasta: '',
};

// accion y entidad se escriben a mano: la bitácora no tiene catálogo cerrado de acciones.
const ESCRITOS_A_MANO = ['busqueda', 'usuario', 'accion', 'entidad'];

const RANGOS_AUDITORIA = [
  {
    desde: 'fechaDesde',
    hasta: 'fechaHasta',
    etiqueta: 'Fecha',
    describir: (desde, hasta) => describirRangoFechas(desde, hasta),
  },
];

function textoLegible(valor) {
  const texto = String(valor ?? '').trim();
  if (!texto) return '';

  const legible = texto.replace(/_/g, ' ').toLowerCase();
  return legible.charAt(0).toUpperCase() + legible.slice(1);
}

function describirFiltroAuditoria(clave, valor) {
  if (clave === 'busqueda') return `Contiene «${valor}»`;
  if (clave === 'usuario') return `Usuario: ${valor}`;
  if (clave === 'accion') return `Acción: ${textoLegible(valor)}`;
  if (clave === 'entidad') return `Entidad: ${textoLegible(valor)}`;
  return null;
}

export function useAuditoria({ tamanoInicial = 20 } = {}) {
  const avisarError = useAvisoDeError();

  const [registros, setRegistros] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);

  const filtros = useFiltros(FILTROS_AUDITORIA, {
    clavesConRetardo: ESCRITOS_A_MANO,
    rangos: RANGOS_AUDITORIA,
    describir: describirFiltroAuditoria,
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

    adaptadorAuditoria
      .listar({ ...filtros.aplicados, pagina, tamano })
      .then((respuesta) => {
        if (!activo) return;

        setRegistros(respuesta?.contenido ?? []);
        setTotalElementos(respuesta?.totalElementos ?? 0);
        setTotalPaginas(respuesta?.totalPaginas ?? 0);
      })
      .catch((fallo) => {
        if (!activo) return;

        setRegistros([]);
        setError(fallo);
        avisarError(fallo, 'No se pudo cargar la bitácora de auditoría');
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

  return {
    registros,
    cargando,
    error,

    filtros: filtros.valores,
    establecerFiltro: filtros.establecerFiltro,
    establecerFiltros: filtros.establecerFiltros,
    quitarFiltro: filtros.quitarFiltro,
    limpiarFiltros: filtros.limpiarFiltros,
    filtrosActivos: filtros.filtrosActivos,
    chips: filtros.chips,

    pagina,
    tamano,
    totalElementos,
    totalPaginas,
    setPagina,
    cambiarTamano,

    recargar,
  };
}

export default useAuditoria;
