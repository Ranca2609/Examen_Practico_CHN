package gt.gob.chn.prestamos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Usuario - bloqueo por intentos fallidos y reinicio de contador")
class UsuarioTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 3, 10, 9, 30);
    private static final int MAX_INTENTOS = 5;
    private static final int MINUTOS_BLOQUEO = 15;

    @Test
    void nace_activo_sin_intentos_fallidos_ni_bloqueo() {
        Usuario usuario = usuarioNuevo();

        assertThat(usuario.isActivo()).isTrue();
        assertThat(usuario.getIntentosFallidos()).isZero();
        assertThat(usuario.getBloqueadoHasta()).isNull();
        assertThat(usuario.getUltimoAcceso()).isNull();
        assertThat(usuario.estaBloqueado(AHORA)).isFalse();
    }

    @Test
    void acumula_intentos_fallidos_sin_bloquear_antes_del_maximo() {
        Usuario usuario = usuarioNuevo();

        for (int intento = 1; intento < MAX_INTENTOS; intento++) {
            usuario.registrarIntentoFallido(MAX_INTENTOS, MINUTOS_BLOQUEO, AHORA);
        }

        assertThat(usuario.getIntentosFallidos()).isEqualTo(MAX_INTENTOS - 1);
        assertThat(usuario.estaBloqueado(AHORA)).isFalse();
        assertThat(usuario.getBloqueadoHasta()).isNull();
    }

    @Test
    void bloquea_la_cuenta_al_alcanzar_el_maximo_de_intentos() {
        Usuario usuario = usuarioNuevo();

        for (int intento = 0; intento < MAX_INTENTOS; intento++) {
            usuario.registrarIntentoFallido(MAX_INTENTOS, MINUTOS_BLOQUEO, AHORA);
        }

        assertThat(usuario.estaBloqueado(AHORA)).isTrue();
        assertThat(usuario.getBloqueadoHasta()).isEqualTo(AHORA.plusMinutes(MINUTOS_BLOQUEO));
        // Al bloquear se reinicia el contador: al vencer el plazo vuelve el cupo completo.
        assertThat(usuario.getIntentosFallidos()).isZero();
    }

    @Test
    void desbloquea_al_vencer_el_plazo_de_bloqueo() {
        Usuario usuario = usuarioNuevo();
        for (int intento = 0; intento < MAX_INTENTOS; intento++) {
            usuario.registrarIntentoFallido(MAX_INTENTOS, MINUTOS_BLOQUEO, AHORA);
        }

        assertThat(usuario.estaBloqueado(AHORA.plusMinutes(MINUTOS_BLOQUEO - 1))).isTrue();
        assertThat(usuario.estaBloqueado(AHORA.plusMinutes(MINUTOS_BLOQUEO))).isFalse();
        assertThat(usuario.estaBloqueado(AHORA.plusHours(1))).isFalse();
    }

    @Test
    void registrar_acceso_exitoso_reinicia_intentos_y_limpia_el_bloqueo() {
        Usuario usuario = Usuario.reconstituir(1L, "analista", "$2a$12$hashDePrueba",
                "Ana Lucia Sandoval", "ana.sandoval@chn.com.gt", Rol.ANALISTA, true,
                3, AHORA.plusMinutes(10), null);
        assertThat(usuario.estaBloqueado(AHORA)).isTrue();

        usuario.registrarAccesoExitoso(AHORA);

        assertThat(usuario.getIntentosFallidos()).isZero();
        assertThat(usuario.getBloqueadoHasta()).isNull();
        assertThat(usuario.estaBloqueado(AHORA)).isFalse();
        assertThat(usuario.getUltimoAcceso()).isEqualTo(AHORA);
    }

    @Test
    void reconstituir_conserva_el_rol_y_el_estado_de_bloqueo_persistidos() {
        Usuario usuario = Usuario.reconstituir(2L, "cajero", "$2a$12$hashDePrueba", "Carlos Mejia",
                "carlos.mejia@chn.com.gt", Rol.CAJERO, false, 2, null, AHORA.minusDays(1));

        assertThat(usuario.getId()).isEqualTo(2L);
        assertThat(usuario.getRol()).isEqualTo(Rol.CAJERO);
        assertThat(usuario.isActivo()).isFalse();
        assertThat(usuario.getIntentosFallidos()).isEqualTo(2);
        assertThat(usuario.estaBloqueado(AHORA)).isFalse();
        assertThat(usuario.getUltimoAcceso()).isEqualTo(AHORA.minusDays(1));
    }

    private static Usuario usuarioNuevo() {
        return Usuario.nuevo("admin", "$2a$12$hashDePrueba", "Administrador del Sistema",
                "admin@chn.com.gt", Rol.ADMIN);
    }
}
