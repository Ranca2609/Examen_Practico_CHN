package gt.gob.chn.prestamos.domain.port.out;

import gt.gob.chn.prestamos.domain.model.RegistroAuditoria;
import gt.gob.chn.prestamos.domain.model.consulta.FiltroAuditoria;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;

public interface AuditoriaPort {

    // Un fallo al auditar no debe interrumpir la operacion de negocio: lo absorbe el adaptador.
    void registrar(String usuario, String accion, String entidad, String entidadId,
                   String detalle, String direccionIp);

    /** Filtra en la base de datos: en memoria el total de paginas dejaria de ser correcto. */
    PaginaDominio<RegistroAuditoria> listar(FiltroAuditoria filtro);
}
