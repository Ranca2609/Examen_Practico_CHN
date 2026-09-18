package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.mapeador;

import gt.gob.chn.prestamos.domain.model.RegistroAuditoria;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.entidad.AuditoriaEntidad;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class MapeadorAuditoria {

    public RegistroAuditoria aDominio(AuditoriaEntidad entidad) {
        if (entidad == null) {
            return null;
        }
        return new RegistroAuditoria(
                entidad.getId(),
                entidad.getUsuario(),
                entidad.getAccion(),
                entidad.getEntidad(),
                entidad.getEntidadId(),
                entidad.getDetalle(),
                entidad.getDireccionIp(),
                entidad.getFecha());
    }

    public AuditoriaEntidad aEntidad(String usuario, String accion, String entidad, String entidadId,
                                     String detalle, String direccionIp, LocalDateTime fecha) {
        return new AuditoriaEntidad(null, usuario, accion, entidad, entidadId, detalle, direccionIp, fecha);
    }
}
