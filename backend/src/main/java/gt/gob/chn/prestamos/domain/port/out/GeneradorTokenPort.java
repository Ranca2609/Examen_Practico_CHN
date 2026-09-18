package gt.gob.chn.prestamos.domain.port.out;

import gt.gob.chn.prestamos.domain.model.TokenAcceso;
import gt.gob.chn.prestamos.domain.model.Usuario;

public interface GeneradorTokenPort {

    TokenAcceso generar(Usuario usuario);
}
