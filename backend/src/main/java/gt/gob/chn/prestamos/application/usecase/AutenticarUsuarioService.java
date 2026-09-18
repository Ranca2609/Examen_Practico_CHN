package gt.gob.chn.prestamos.application.usecase;

import gt.gob.chn.prestamos.domain.exception.AutenticacionException;
import gt.gob.chn.prestamos.domain.exception.RecursoNoEncontradoException;
import gt.gob.chn.prestamos.domain.model.TokenAcceso;
import gt.gob.chn.prestamos.domain.model.Usuario;
import gt.gob.chn.prestamos.domain.port.in.AutenticarUsuarioUseCase;
import gt.gob.chn.prestamos.domain.port.in.command.ContextoOperacion;
import gt.gob.chn.prestamos.domain.port.in.command.CredencialesCommand;
import gt.gob.chn.prestamos.domain.port.out.AuditoriaPort;
import gt.gob.chn.prestamos.domain.port.out.CodificadorContrasenaPort;
import gt.gob.chn.prestamos.domain.port.out.GeneradorTokenPort;
import gt.gob.chn.prestamos.domain.port.out.RelojPort;
import gt.gob.chn.prestamos.domain.port.out.UsuarioRepositorio;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AutenticarUsuarioService implements AutenticarUsuarioUseCase {

    // Mismo mensaje para cualquier fallo: evita enumerar cuentas válidas.
    private static final String CREDENCIALES_INVALIDAS = "Credenciales inválidas";

    private final UsuarioRepositorio usuarioRepositorio;
    private final CodificadorContrasenaPort codificadorContrasena;
    private final GeneradorTokenPort generadorToken;
    private final AuditoriaPort auditoria;
    private final RelojPort reloj;
    private final int maxIntentosFallidos;
    private final int minutosBloqueo;

    public AutenticarUsuarioService(UsuarioRepositorio usuarioRepositorio,
                                    CodificadorContrasenaPort codificadorContrasena,
                                    GeneradorTokenPort generadorToken,
                                    AuditoriaPort auditoria,
                                    RelojPort reloj,
                                    @Value("${app.seguridad.max-intentos-fallidos:5}")
                                    int maxIntentosFallidos,
                                    @Value("${app.seguridad.minutos-bloqueo:15}")
                                    int minutosBloqueo) {
        this.usuarioRepositorio = usuarioRepositorio;
        this.codificadorContrasena = codificadorContrasena;
        this.generadorToken = generadorToken;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.maxIntentosFallidos = maxIntentosFallidos;
        this.minutosBloqueo = minutosBloqueo;
    }

    @Override
    public TokenAcceso autenticar(CredencialesCommand cmd, ContextoOperacion ctx) {
        LocalDateTime ahora = reloj.ahora();
        Optional<Usuario> encontrado = usuarioRepositorio.buscarPorUsername(cmd.username());

        if (encontrado.isEmpty() || !encontrado.get().isActivo()) {
            auditarFallo(cmd.username(), "Usuario inexistente o inactivo", ctx);
            throw new AutenticacionException(CREDENCIALES_INVALIDAS);
        }

        Usuario usuario = encontrado.get();

        // Se corta antes de comparar el hash: no gasta BCrypt ni lo expone como oráculo de tiempos.
        if (usuario.estaBloqueado(ahora)) {
            auditarFallo(cmd.username(), "Cuenta bloqueada temporalmente", ctx);
            throw new AutenticacionException("La cuenta está bloqueada temporalmente por "
                    + "intentos fallidos. Intente de nuevo más tarde o contacte al administrador.");
        }

        if (!codificadorContrasena.coincide(cmd.contrasena(), usuario.getPasswordHash())) {
            usuario.registrarIntentoFallido(maxIntentosFallidos, minutosBloqueo, ahora);
            usuarioRepositorio.guardar(usuario);
            auditarFallo(cmd.username(),
                    "Contraseña incorrecta (intento " + usuario.getIntentosFallidos() + " de "
                            + maxIntentosFallidos + ")", ctx);
            throw new AutenticacionException(CREDENCIALES_INVALIDAS);
        }

        usuario.registrarAccesoExitoso(ahora);
        Usuario actualizado = usuarioRepositorio.guardar(usuario);

        auditoria.registrar(usuario.getUsername(), AccionesAuditoria.LOGIN_EXITOSO,
                AccionesAuditoria.ENTIDAD_USUARIO, String.valueOf(actualizado.getId()),
                "Ingreso correcto con rol " + usuario.getRol(), ctx.direccionIp());

        return generadorToken.generar(usuario);
    }

    @Override
    @Transactional(readOnly = true)
    public Usuario perfil(String username) {
        return usuarioRepositorio.buscarPorUsername(username)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", username));
    }

    // La causa real queda solo en la bitácora; nunca viaja en la respuesta HTTP.
    private void auditarFallo(String username, String detalle, ContextoOperacion ctx) {
        auditoria.registrar(username == null ? "desconocido" : username,
                AccionesAuditoria.LOGIN_FALLIDO, AccionesAuditoria.ENTIDAD_USUARIO, null,
                detalle, ctx.direccionIp());
    }
}
