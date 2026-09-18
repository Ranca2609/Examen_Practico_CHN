package gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador;

import gt.gob.chn.prestamos.domain.model.TokenAcceso;
import gt.gob.chn.prestamos.domain.model.Usuario;
import gt.gob.chn.prestamos.domain.port.in.command.CredencialesCommand;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.peticion.LoginRequest;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.TokenResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.UsuarioResponse;

public final class MapeadorWebUsuario {

    private MapeadorWebUsuario() {
    }

    public static CredencialesCommand aComando(LoginRequest peticion) {
        return new CredencialesCommand(peticion.username(), peticion.contrasena());
    }

    public static TokenResponse aRespuesta(TokenAcceso token) {
        return new TokenResponse(
                token.token(),
                token.tipo(),
                token.expiraEnSegundos(),
                aUsuarioResponse(token));
    }

    // Los claims no llevan id ni correo; el perfil completo se obtiene en GET /auth/perfil.
    private static UsuarioResponse aUsuarioResponse(TokenAcceso token) {
        return new UsuarioResponse(
                null,
                token.username(),
                token.nombreCompleto(),
                null,
                token.rol().name());
    }

    public static UsuarioResponse aRespuesta(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getUsername(),
                usuario.getNombreCompleto(),
                usuario.getCorreo(),
                usuario.getRol().name());
    }
}
