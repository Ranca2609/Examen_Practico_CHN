package gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador;

import gt.gob.chn.prestamos.domain.model.RegistroAuditoria;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.AuditoriaResponse;

public final class MapeadorWebAuditoria {

    private MapeadorWebAuditoria() {
    }

    public static AuditoriaResponse aRespuesta(RegistroAuditoria registro) {
        return new AuditoriaResponse(
                registro.id(),
                registro.usuario(),
                registro.accion(),
                registro.entidad(),
                registro.entidadId(),
                registro.detalle(),
                registro.direccionIp(),
                registro.fecha());
    }
}
