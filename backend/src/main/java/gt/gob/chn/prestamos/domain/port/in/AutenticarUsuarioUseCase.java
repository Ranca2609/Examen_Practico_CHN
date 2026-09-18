package gt.gob.chn.prestamos.domain.port.in;

import gt.gob.chn.prestamos.domain.model.TokenAcceso;
import gt.gob.chn.prestamos.domain.model.Usuario;
import gt.gob.chn.prestamos.domain.port.in.command.ContextoOperacion;
import gt.gob.chn.prestamos.domain.port.in.command.CredencialesCommand;

public interface AutenticarUsuarioUseCase {

    /** El error es generico a proposito: no revela si fallo el usuario o la contrasena. */
    TokenAcceso autenticar(CredencialesCommand cmd, ContextoOperacion ctx);

    Usuario perfil(String username);
}
