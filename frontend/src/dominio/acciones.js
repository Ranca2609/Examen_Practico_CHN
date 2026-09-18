// El color comunica la consecuencia de la acción, no la estética, y es el mismo en todas las pantallas.
export const ACCIONES = {
  ver: { variante: 'info', icono: 'ver', etiqueta: 'Ver' },
  simular: { variante: 'info', icono: 'calculadora', etiqueta: 'Simular' },

  editar: { variante: 'advertencia', icono: 'editar', etiqueta: 'Editar' },

  eliminar: { variante: 'peligro', icono: 'eliminar', etiqueta: 'Eliminar' },
  rechazar: { variante: 'peligro', icono: 'cerrar', etiqueta: 'Rechazar' },

  // El verde se reserva para aprobar y cobrar; si cada "Guardar" fuera verde dejaría de informar.
  aprobar: { variante: 'exito', icono: 'check', etiqueta: 'Aprobar' },
  pagar: { variante: 'exito', icono: 'dinero', etiqueta: 'Registrar pago' },

  // Guardar va en primario: es la acción principal del formulario, no una resolución a favor.
  guardar: { variante: 'primario', icono: 'check', etiqueta: 'Guardar' },
  crear: { variante: 'primario', icono: 'mas', etiqueta: 'Nuevo' },
  buscar: { variante: 'primario', icono: 'buscar', etiqueta: 'Buscar' },

  filtrar: { variante: 'secundario', icono: 'filtro', etiqueta: 'Filtros' },
  descargar: { variante: 'secundario', icono: 'descargar', etiqueta: 'Descargar' },
  refrescar: { variante: 'secundario', icono: 'refrescar', etiqueta: 'Actualizar' },

  cancelar: { variante: 'neutro', icono: 'cerrar', etiqueta: 'Cancelar' },
  imprimir: { variante: 'neutro', icono: 'imprimir', etiqueta: 'Imprimir' },
  limpiar: { variante: 'neutro', icono: 'limpiar', etiqueta: 'Limpiar filtros' },

  volver: { variante: 'texto', icono: 'flechaIzquierda', etiqueta: 'Volver' },
};

const ACCION_NEUTRA = { variante: 'neutro', icono: null, etiqueta: '' };

// Devuelve una copia para que una pantalla no altere el catálogo de las demás.
export function accion(clave) {
  const descriptor = ACCIONES[clave];
  return descriptor ? { ...descriptor } : { ...ACCION_NEUTRA };
}

export const CLAVES_ACCION = Object.keys(ACCIONES);
