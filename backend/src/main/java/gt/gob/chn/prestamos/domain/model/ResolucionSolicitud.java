package gt.gob.chn.prestamos.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ResolucionSolicitud(
        LocalDateTime fechaResolucion,
        String usuarioResolucion,
        BigDecimal montoAprobado,
        Integer plazoAprobadoMeses,
        BigDecimal tasaAprobada,
        String motivo) {

    public ResolucionSolicitud {
        Validaciones.exigirNoNulo(fechaResolucion, "fecha de resolucion");
        usuarioResolucion = Validaciones.exigirTexto(usuarioResolucion, "usuario que resuelve", 3, 50);
        montoAprobado = Montos.normalizar(montoAprobado);
        tasaAprobada = Montos.normalizar(tasaAprobada);
    }

    public static ResolucionSolicitud aprobacion(LocalDateTime fecha, String usuario, BigDecimal montoAprobado,
                                                 int plazoAprobadoMeses, BigDecimal tasaAprobada, String motivo) {
        return new ResolucionSolicitud(fecha, usuario, montoAprobado, plazoAprobadoMeses, tasaAprobada, motivo);
    }

    public static ResolucionSolicitud rechazo(LocalDateTime fecha, String usuario, String motivo) {
        return new ResolucionSolicitud(fecha, usuario, null, null, null, motivo);
    }
}
