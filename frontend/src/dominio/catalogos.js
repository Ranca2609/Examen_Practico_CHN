// Espejo de los enum del backend; el tono hace que un estado se vea igual en todo el sistema.
export const ESTADOS_SOLICITUD = {
  EN_PROCESO: { valor: 'EN_PROCESO', etiqueta: 'En proceso', tono: 'advertencia' },
  APROBADA: { valor: 'APROBADA', etiqueta: 'Aprobada', tono: 'exito' },
  RECHAZADA: { valor: 'RECHAZADA', etiqueta: 'Rechazada', tono: 'peligro' },
};

export const ESTADOS_PRESTAMO = {
  VIGENTE: { valor: 'VIGENTE', etiqueta: 'Vigente', tono: 'info' },
  LIQUIDADO: { valor: 'LIQUIDADO', etiqueta: 'Liquidado', tono: 'exito' },
};

export const TIPOS_PRESTAMO = {
  PERSONAL: { valor: 'PERSONAL', etiqueta: 'Personal', tono: 'info' },
  HIPOTECARIO: { valor: 'HIPOTECARIO', etiqueta: 'Hipotecario', tono: 'info' },
  VEHICULAR: { valor: 'VEHICULAR', etiqueta: 'Vehicular', tono: 'info' },
  EMPRESARIAL: { valor: 'EMPRESARIAL', etiqueta: 'Empresarial', tono: 'info' },
  EDUCATIVO: { valor: 'EDUCATIVO', etiqueta: 'Educativo', tono: 'info' },
};

export const ROLES = {
  ADMIN: { valor: 'ADMIN', etiqueta: 'Administrador', tono: 'info' },
  ANALISTA: { valor: 'ANALISTA', etiqueta: 'Analista de crédito', tono: 'info' },
  CAJERO: { valor: 'CAJERO', etiqueta: 'Cajero', tono: 'info' },
  CONSULTA: { valor: 'CONSULTA', etiqueta: 'Consulta', tono: 'neutro' },
};

// El alcance de la prueba solo contempla pagos en efectivo.
export const FORMAS_PAGO = {
  EFECTIVO: { valor: 'EFECTIVO', etiqueta: 'Efectivo', tono: 'neutro' },
};

export const PERMISOS = {
  CREAR_CLIENTE: 'CREAR_CLIENTE',
  EDITAR_CLIENTE: 'EDITAR_CLIENTE',
  ELIMINAR_CLIENTE: 'ELIMINAR_CLIENTE',
  GESTIONAR_SOLICITUD: 'GESTIONAR_SOLICITUD',
  REGISTRAR_PAGO: 'REGISTRAR_PAGO',
  VER_AUDITORIA: 'VER_AUDITORIA',
};

// Solo decide qué se muestra, no es control de seguridad: el backend autoriza cada endpoint.
// Todos los roles consultan, por eso no hay permiso de lectura.
export const PERMISOS_POR_ROL = {
  ADMIN: [
    PERMISOS.CREAR_CLIENTE,
    PERMISOS.EDITAR_CLIENTE,
    PERMISOS.ELIMINAR_CLIENTE,
    PERMISOS.GESTIONAR_SOLICITUD,
    PERMISOS.REGISTRAR_PAGO,
    PERMISOS.VER_AUDITORIA,
  ],
  ANALISTA: [
    PERMISOS.CREAR_CLIENTE,
    PERMISOS.EDITAR_CLIENTE,
    PERMISOS.GESTIONAR_SOLICITUD,
  ],
  CAJERO: [
    PERMISOS.REGISTRAR_PAGO,
  ],
  CONSULTA: [],
};

function aOpciones(catalogo) {
  return Object.values(catalogo).map(({ valor, etiqueta }) => ({ valor, etiqueta }));
}

function entrada(catalogo, valor) {
  return valor ? catalogo[valor] : undefined;
}

export const opcionesEstadoSolicitud = () => aOpciones(ESTADOS_SOLICITUD);
export const opcionesEstadoPrestamo = () => aOpciones(ESTADOS_PRESTAMO);
export const opcionesTipoPrestamo = () => aOpciones(TIPOS_PRESTAMO);
export const opcionesRol = () => aOpciones(ROLES);

// Un valor no reconocido se muestra crudo: si el backend agrega un estado, no queda en blanco.
export const etiquetaEstadoSolicitud = (valor) => entrada(ESTADOS_SOLICITUD, valor)?.etiqueta ?? valor ?? '—';
export const tonoEstadoSolicitud = (valor) => entrada(ESTADOS_SOLICITUD, valor)?.tono ?? 'neutro';

export const etiquetaEstadoPrestamo = (valor) => entrada(ESTADOS_PRESTAMO, valor)?.etiqueta ?? valor ?? '—';
export const tonoEstadoPrestamo = (valor) => entrada(ESTADOS_PRESTAMO, valor)?.tono ?? 'neutro';

export const etiquetaTipoPrestamo = (valor) => entrada(TIPOS_PRESTAMO, valor)?.etiqueta ?? valor ?? '—';
export const tonoTipoPrestamo = (valor) => entrada(TIPOS_PRESTAMO, valor)?.tono ?? 'neutro';

export const etiquetaRol = (valor) => entrada(ROLES, valor)?.etiqueta ?? valor ?? '—';
export const tonoRol = (valor) => entrada(ROLES, valor)?.tono ?? 'neutro';

export const etiquetaFormaPago = (valor) => entrada(FORMAS_PAGO, valor)?.etiqueta ?? valor ?? '—';

export const permisosDeRol = (rol) => PERMISOS_POR_ROL[rol] ?? [];

// Replican las validaciones del backend para dar respuesta inmediata; el backend vuelve a validar.
export const LIMITES = {
  MONTO_MINIMO: 1000,
  MONTO_MAXIMO: 5000000,
  PLAZO_MINIMO: 6,
  PLAZO_MAXIMO: 360,
  TASA_MINIMA: 0.01,
  TASA_MAXIMA: 100,
  DIGITOS_DPI: 13,
  DIGITOS_TELEFONO: 8,
  EDAD_MINIMA: 18,
  // % del ingreso que la cuota puede comprometer antes de marcar riesgo.
  PORCENTAJE_ENDEUDAMIENTO_MAXIMO: 40,
};

// Espejo de AccionesAuditoria.java. CLIENTE_ELIMINADO_BD no lo escribe la aplicación sino un trigger de la BD.
export const ACCIONES_AUDITORIA = {
  CLIENTE_CREADO: { valor: 'CLIENTE_CREADO', etiqueta: 'Cliente creado', tono: 'exito' },
  CLIENTE_ACTUALIZADO: {
    valor: 'CLIENTE_ACTUALIZADO',
    etiqueta: 'Cliente actualizado',
    tono: 'advertencia',
  },
  CLIENTE_ELIMINADO: { valor: 'CLIENTE_ELIMINADO', etiqueta: 'Cliente eliminado', tono: 'peligro' },
  CLIENTE_ELIMINADO_BD: {
    valor: 'CLIENTE_ELIMINADO_BD',
    etiqueta: 'Cliente eliminado (base de datos)',
    tono: 'peligro',
  },
  SOLICITUD_CREADA: { valor: 'SOLICITUD_CREADA', etiqueta: 'Solicitud creada', tono: 'info' },
  SOLICITUD_APROBADA: { valor: 'SOLICITUD_APROBADA', etiqueta: 'Solicitud aprobada', tono: 'exito' },
  SOLICITUD_RECHAZADA: {
    valor: 'SOLICITUD_RECHAZADA',
    etiqueta: 'Solicitud rechazada',
    tono: 'peligro',
  },
  PRESTAMO_CREADO: { valor: 'PRESTAMO_CREADO', etiqueta: 'Préstamo creado', tono: 'exito' },
  PRESTAMO_LIQUIDADO: { valor: 'PRESTAMO_LIQUIDADO', etiqueta: 'Préstamo liquidado', tono: 'exito' },
  PAGO_REGISTRADO: { valor: 'PAGO_REGISTRADO', etiqueta: 'Pago registrado', tono: 'exito' },
  LOGIN_EXITOSO: { valor: 'LOGIN_EXITOSO', etiqueta: 'Ingreso correcto', tono: 'info' },
  LOGIN_FALLIDO: { valor: 'LOGIN_FALLIDO', etiqueta: 'Ingreso fallido', tono: 'peligro' },
};

export const ENTIDADES_AUDITORIA = {
  CLIENTE: { valor: 'CLIENTE', etiqueta: 'Cliente', tono: 'neutro' },
  SOLICITUD: { valor: 'SOLICITUD', etiqueta: 'Solicitud', tono: 'neutro' },
  PRESTAMO: { valor: 'PRESTAMO', etiqueta: 'Préstamo', tono: 'neutro' },
  PAGO: { valor: 'PAGO', etiqueta: 'Pago', tono: 'neutro' },
  USUARIO: { valor: 'USUARIO', etiqueta: 'Usuario', tono: 'neutro' },
};

export const opcionesAccionAuditoria = () => aOpciones(ACCIONES_AUDITORIA);
export const opcionesEntidadAuditoria = () => aOpciones(ENTIDADES_AUDITORIA);

// La bitácora es histórica: una acción desconocida se muestra tal cual y en tono neutro.
export const etiquetaAccionAuditoria = (valor) =>
  entrada(ACCIONES_AUDITORIA, valor)?.etiqueta ?? valor ?? '—';
export const tonoAccionAuditoria = (valor) =>
  entrada(ACCIONES_AUDITORIA, valor)?.tono ?? 'neutro';

export const etiquetaEntidadAuditoria = (valor) =>
  entrada(ENTIDADES_AUDITORIA, valor)?.etiqueta ?? valor ?? '—';
