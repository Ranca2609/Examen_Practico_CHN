package gt.gob.chn.prestamos.application.usecase;

import gt.gob.chn.prestamos.domain.model.RegistroAuditoria;
import gt.gob.chn.prestamos.domain.model.Validaciones;
import gt.gob.chn.prestamos.domain.model.consulta.FiltroAuditoria;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.domain.port.in.ConsultarAuditoriaUseCase;
import gt.gob.chn.prestamos.domain.port.out.AuditoriaPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ConsultarAuditoriaService implements ConsultarAuditoriaUseCase {

    private final AuditoriaPort auditoria;

    public ConsultarAuditoriaService(AuditoriaPort auditoria) {
        this.auditoria = auditoria;
    }

    @Override
    public PaginaDominio<RegistroAuditoria> listar(FiltroAuditoria filtro) {
        // FiltroAuditoria ya valida criterios y acota el tamaño de página; no se duplica aquí.
        Validaciones.exigirNoNulo(filtro, "filtro de auditoría");
        return auditoria.listar(filtro);
    }
}
