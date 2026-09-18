package gt.gob.chn.prestamos.domain.model;

import java.time.LocalDateTime;

public record RegistroAuditoria(
        Long id,
        String usuario,
        String accion,
        String entidad,
        String entidadId,
        String detalle,
        String direccionIp,
        LocalDateTime fecha) {

    private static final int DETALLE_MAXIMO = 1000;
    private static final int IP_MAXIMA = 45;

    public RegistroAuditoria {
        usuario = Validaciones.exigirTexto(usuario, "usuario de la auditoria", 1, 50);
        accion = Validaciones.exigirTexto(accion, "accion auditada", 1, 50);
        entidad = Validaciones.exigirTexto(entidad, "entidad auditada", 1, 50);
        entidadId = recortar(entidadId, 50);
        detalle = recortar(detalle, DETALLE_MAXIMO);
        direccionIp = recortar(direccionIp, IP_MAXIMA);
        Validaciones.exigirNoNulo(fecha, "fecha de la auditoria");
    }

    // Recorta en lugar de fallar: auditar nunca debe interrumpir la operacion auditada.
    private static String recortar(String valor, int maximo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo);
    }
}
