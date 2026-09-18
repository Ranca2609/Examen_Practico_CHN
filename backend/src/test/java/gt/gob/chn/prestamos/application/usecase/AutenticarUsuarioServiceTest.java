package gt.gob.chn.prestamos.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gt.gob.chn.prestamos.domain.exception.AutenticacionException;
import gt.gob.chn.prestamos.domain.exception.RecursoNoEncontradoException;
import gt.gob.chn.prestamos.domain.model.Rol;
import gt.gob.chn.prestamos.domain.model.TokenAcceso;
import gt.gob.chn.prestamos.domain.model.Usuario;
import gt.gob.chn.prestamos.domain.port.in.command.ContextoOperacion;
import gt.gob.chn.prestamos.domain.port.in.command.CredencialesCommand;
import gt.gob.chn.prestamos.domain.port.out.AuditoriaPort;
import gt.gob.chn.prestamos.domain.port.out.CodificadorContrasenaPort;
import gt.gob.chn.prestamos.domain.port.out.GeneradorTokenPort;
import gt.gob.chn.prestamos.domain.port.out.RelojPort;
import gt.gob.chn.prestamos.domain.port.out.UsuarioRepositorio;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AutenticarUsuarioService - credenciales, bloqueo y auditoria")
class AutenticarUsuarioServiceTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 3, 10, 9, 30);
    private static final ContextoOperacion CONTEXTO = new ContextoOperacion("anonimo", "10.0.0.7");
    private static final String HASH = "$2a$12$hashDePrueba";
    private static final String CONTRASENA = "Chn2026*Demo";
    private static final int MAX_INTENTOS = 5;
    private static final int MINUTOS_BLOQUEO = 15;

    @Mock
    private UsuarioRepositorio usuarioRepositorio;

    @Mock
    private CodificadorContrasenaPort codificadorContrasena;

    @Mock
    private GeneradorTokenPort generadorToken;

    @Mock
    private AuditoriaPort auditoriaPort;

    @Mock
    private RelojPort relojPort;

    private AutenticarUsuarioService servicio;

    @BeforeEach
    void prepararServicio() {
        // Limites de bloqueo (app.seguridad.*) fijados a los valores por defecto del sistema.
        servicio = new AutenticarUsuarioService(usuarioRepositorio, codificadorContrasena,
                generadorToken, auditoriaPort, relojPort, MAX_INTENTOS, MINUTOS_BLOQUEO);
    }

    @Test
    void autentica_credenciales_validas_devuelve_el_token_y_audita_el_ingreso() {
        when(relojPort.ahora()).thenReturn(AHORA);
        when(usuarioRepositorio.buscarPorUsername("admin")).thenReturn(Optional.of(usuarioActivo()));
        when(codificadorContrasena.coincide(CONTRASENA, HASH)).thenReturn(true);
        when(usuarioRepositorio.guardar(any(Usuario.class))).thenAnswer(llamada -> llamada.getArgument(0));
        when(generadorToken.generar(any(Usuario.class))).thenReturn(tokenDemo());

        TokenAcceso token = servicio.autenticar(new CredencialesCommand("admin", CONTRASENA), CONTEXTO);

        assertThat(token.token()).isEqualTo("jwt-de-prueba");
        assertThat(token.tipo()).isEqualTo("Bearer");
        assertThat(token.username()).isEqualTo("admin");
        assertThat(token.rol()).isEqualTo(Rol.ADMIN);

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepositorio).guardar(guardado.capture());
        assertThat(guardado.getValue().getIntentosFallidos()).isZero();
        assertThat(guardado.getValue().getBloqueadoHasta()).isNull();
        assertThat(guardado.getValue().getUltimoAcceso()).isEqualTo(AHORA);

        verify(auditoriaPort).registrar(eq("admin"), eq(AccionesAuditoria.LOGIN_EXITOSO),
                eq(AccionesAuditoria.ENTIDAD_USUARIO), eq("1"), anyString(), eq("10.0.0.7"));
    }

    @Test
    void usuario_inexistente_y_usuario_inactivo_devuelven_el_mismo_mensaje_generico() {
        when(relojPort.ahora()).thenReturn(AHORA);
        when(usuarioRepositorio.buscarPorUsername("fantasma")).thenReturn(Optional.empty());
        when(usuarioRepositorio.buscarPorUsername("inactivo")).thenReturn(Optional.of(usuarioInactivo()));

        Throwable inexistente = catchThrowable(() ->
                servicio.autenticar(new CredencialesCommand("fantasma", CONTRASENA), CONTEXTO));
        Throwable inactivo = catchThrowable(() ->
                servicio.autenticar(new CredencialesCommand("inactivo", CONTRASENA), CONTEXTO));

        assertThat(inexistente).isInstanceOf(AutenticacionException.class);
        assertThat(inactivo).isInstanceOf(AutenticacionException.class);
        // Mismo mensaje en ambos casos: un atacante no puede enumerar usuarios validos.
        assertThat(inactivo.getMessage()).isEqualTo(inexistente.getMessage());
        assertThat(inexistente.getMessage()).doesNotContain("fantasma");
        // El motivo real solo viaja a la bitacora, nunca a la respuesta.
        verify(auditoriaPort, never()).registrar(anyString(),
                eq(AccionesAuditoria.LOGIN_EXITOSO), anyString(), any(), any(), any());
    }

    @Test
    void contrasena_incorrecta_incrementa_y_persiste_los_intentos_fallidos() {
        when(relojPort.ahora()).thenReturn(AHORA);
        when(usuarioRepositorio.buscarPorUsername("admin")).thenReturn(Optional.of(usuarioActivo()));
        when(codificadorContrasena.coincide("clave-incorrecta", HASH)).thenReturn(false);
        when(usuarioRepositorio.guardar(any(Usuario.class))).thenAnswer(llamada -> llamada.getArgument(0));

        assertThatThrownBy(() -> servicio.autenticar(
                new CredencialesCommand("admin", "clave-incorrecta"), CONTEXTO))
                .isInstanceOf(AutenticacionException.class);

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepositorio).guardar(guardado.capture());
        assertThat(guardado.getValue().getIntentosFallidos()).isEqualTo(1);
        assertThat(guardado.getValue().estaBloqueado(AHORA)).isFalse();
        verify(generadorToken, never()).generar(any());
        verify(auditoriaPort).registrar(eq("admin"), eq(AccionesAuditoria.LOGIN_FALLIDO),
                eq(AccionesAuditoria.ENTIDAD_USUARIO), any(), anyString(), eq("10.0.0.7"));
    }

    @Test
    void al_agotar_los_intentos_permitidos_la_cuenta_queda_bloqueada() {
        Usuario usuario = Usuario.reconstituir(1L, "admin", HASH, "Administrador del Sistema",
                "admin@chn.com.gt", Rol.ADMIN, true, MAX_INTENTOS - 1, null, null);
        when(relojPort.ahora()).thenReturn(AHORA);
        when(usuarioRepositorio.buscarPorUsername("admin")).thenReturn(Optional.of(usuario));
        when(codificadorContrasena.coincide("clave-incorrecta", HASH)).thenReturn(false);
        when(usuarioRepositorio.guardar(any(Usuario.class))).thenAnswer(llamada -> llamada.getArgument(0));

        assertThatThrownBy(() -> servicio.autenticar(
                new CredencialesCommand("admin", "clave-incorrecta"), CONTEXTO))
                .isInstanceOf(AutenticacionException.class);

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepositorio).guardar(guardado.capture());
        assertThat(guardado.getValue().estaBloqueado(AHORA)).isTrue();
        assertThat(guardado.getValue().getBloqueadoHasta())
                .isEqualTo(AHORA.plusMinutes(MINUTOS_BLOQUEO));
    }

    @Test
    void usuario_bloqueado_no_llega_a_validar_la_contrasena() {
        Usuario bloqueado = Usuario.reconstituir(1L, "admin", HASH, "Administrador del Sistema",
                "admin@chn.com.gt", Rol.ADMIN, true, 0, AHORA.plusMinutes(10), null);
        when(relojPort.ahora()).thenReturn(AHORA);
        when(usuarioRepositorio.buscarPorUsername("admin")).thenReturn(Optional.of(bloqueado));

        assertThatThrownBy(() -> servicio.autenticar(
                new CredencialesCommand("admin", CONTRASENA), CONTEXTO))
                .isInstanceOf(AutenticacionException.class);

        // Cortocircuito: con la cuenta bloqueada no se prueba el hash ni se emite token.
        verify(codificadorContrasena, never()).coincide(anyString(), anyString());
        verify(generadorToken, never()).generar(any());
        verify(usuarioRepositorio, never()).guardar(any());
    }

    @Test
    void perfil_devuelve_el_usuario_autenticado() {
        when(usuarioRepositorio.buscarPorUsername("admin")).thenReturn(Optional.of(usuarioActivo()));

        Usuario perfil = servicio.perfil("admin");

        assertThat(perfil.getUsername()).isEqualTo("admin");
        assertThat(perfil.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(perfil.getNombreCompleto()).isEqualTo("Administrador del Sistema");
    }

    @Test
    void perfil_de_un_usuario_inexistente_lanza_no_encontrado() {
        when(usuarioRepositorio.buscarPorUsername("fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.perfil("fantasma"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    private static Usuario usuarioActivo() {
        return Usuario.reconstituir(1L, "admin", HASH, "Administrador del Sistema",
                "admin@chn.com.gt", Rol.ADMIN, true, 0, null, null);
    }

    private static Usuario usuarioInactivo() {
        return Usuario.reconstituir(2L, "inactivo", HASH, "Usuario Dado de Baja",
                "inactivo@chn.com.gt", Rol.CONSULTA, false, 0, null, null);
    }

    private static TokenAcceso tokenDemo() {
        return new TokenAcceso("jwt-de-prueba", "Bearer", 28800L, "admin",
                "Administrador del Sistema", Rol.ADMIN);
    }
}
