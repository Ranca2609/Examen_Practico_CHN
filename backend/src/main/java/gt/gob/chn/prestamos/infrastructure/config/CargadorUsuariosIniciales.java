package gt.gob.chn.prestamos.infrastructure.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import gt.gob.chn.prestamos.domain.model.Rol;
import gt.gob.chn.prestamos.domain.model.Usuario;
import gt.gob.chn.prestamos.domain.port.out.CodificadorContrasenaPort;
import gt.gob.chn.prestamos.domain.port.out.UsuarioRepositorio;

// Idempotente: un reinicio nunca sobrescribe una contraseña ya cambiada por el administrador.
@Component
@Profile("!test")
public class CargadorUsuariosIniciales implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(CargadorUsuariosIniciales.class);

    // Respaldo público solo para evaluar la prueba si falta la variable de entorno; su uso se advierte en el log.
    private static final String CONTRASENA_DEMO = "Chn2026*Demo";

    private final UsuarioRepositorio usuarioRepositorio;
    private final CodificadorContrasenaPort codificador;
    private final PropiedadesAplicacion propiedades;

    public CargadorUsuariosIniciales(UsuarioRepositorio usuarioRepositorio,
                                     CodificadorContrasenaPort codificador,
                                     PropiedadesAplicacion propiedades) {
        this.usuarioRepositorio = usuarioRepositorio;
        this.codificador = codificador;
        this.propiedades = propiedades;
    }

    @Override
    public void run(ApplicationArguments args) {
        PropiedadesAplicacion.UsuariosIniciales claves = propiedades.usuariosIniciales();

        List<Semilla> semillas = List.of(
                new Semilla("admin", claves.adminPassword(),
                        "Administrador del Sistema", "admin@chn.com.gt", Rol.ADMIN),
                new Semilla("analista", claves.analistaPassword(),
                        "Ana Lucia Sandoval", "analista@chn.com.gt", Rol.ANALISTA),
                new Semilla("cajero", claves.cajeroPassword(),
                        "Carlos Mejia", "cajero@chn.com.gt", Rol.CAJERO),
                new Semilla("consulta", claves.consultaPassword(),
                        "Usuario de Consulta", "consulta@chn.com.gt", Rol.CONSULTA));

        semillas.forEach(this::crearSiNoExiste);
    }

    private void crearSiNoExiste(Semilla semilla) {
        if (usuarioRepositorio.existePorUsername(semilla.username())) {
            LOG.debug("El usuario '{}' ya existe; no se modifica.", semilla.username());
            return;
        }

        boolean usaDemo = esVacia(semilla.contrasena());
        String contrasena = usaDemo ? CONTRASENA_DEMO : semilla.contrasena();

        Usuario usuario = Usuario.nuevo(
                semilla.username(),
                codificador.codificar(contrasena),
                semilla.nombreCompleto(),
                semilla.correo(),
                semilla.rol());

        usuarioRepositorio.guardar(usuario);

        if (usaDemo) {
            LOG.warn("ADVERTENCIA DE SEGURIDAD: el usuario '{}' ({}) se creó con la contraseña de "
                            + "demostración porque no se definió su variable de entorno. "
                            + "Cámbiela antes de usar el sistema en producción.",
                    semilla.username(), semilla.rol());
        } else {
            LOG.info("Usuario inicial '{}' creado con rol {}.", semilla.username(), semilla.rol());
        }
    }

    private boolean esVacia(String valor) {
        return valor == null || valor.isBlank();
    }

    private record Semilla(String username, String contrasena, String nombreCompleto,
                           String correo, Rol rol) {
    }
}
