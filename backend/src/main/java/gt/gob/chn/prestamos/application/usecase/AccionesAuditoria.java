package gt.gob.chn.prestamos.application.usecase;

public final class AccionesAuditoria {

    public static final String ENTIDAD_CLIENTE = "CLIENTE";
    public static final String ENTIDAD_SOLICITUD = "SOLICITUD";
    public static final String ENTIDAD_PRESTAMO = "PRESTAMO";
    public static final String ENTIDAD_PAGO = "PAGO";
    public static final String ENTIDAD_USUARIO = "USUARIO";

    public static final String CLIENTE_CREADO = "CLIENTE_CREADO";
    public static final String CLIENTE_ACTUALIZADO = "CLIENTE_ACTUALIZADO";
    public static final String CLIENTE_ELIMINADO = "CLIENTE_ELIMINADO";

    public static final String SOLICITUD_CREADA = "SOLICITUD_CREADA";
    public static final String SOLICITUD_APROBADA = "SOLICITUD_APROBADA";
    public static final String SOLICITUD_RECHAZADA = "SOLICITUD_RECHAZADA";

    public static final String PRESTAMO_CREADO = "PRESTAMO_CREADO";
    public static final String PRESTAMO_LIQUIDADO = "PRESTAMO_LIQUIDADO";
    public static final String PAGO_REGISTRADO = "PAGO_REGISTRADO";

    public static final String LOGIN_EXITOSO = "LOGIN_EXITOSO";
    public static final String LOGIN_FALLIDO = "LOGIN_FALLIDO";

    // Un reporte saca datos personales del sistema: su descarga se audita como una modificación.
    public static final String REPORTE_DESCARGADO = "REPORTE_DESCARGADO";

    private AccionesAuditoria() {
    }
}
