package gt.gob.chn.prestamos.domain.port.in;

import gt.gob.chn.prestamos.domain.model.RegistroAuditoria;
import gt.gob.chn.prestamos.domain.model.consulta.FiltroAuditoria;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;

public interface ConsultarAuditoriaUseCase {

    PaginaDominio<RegistroAuditoria> listar(FiltroAuditoria filtro);
}
