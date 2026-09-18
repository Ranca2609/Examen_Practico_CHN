package gt.gob.chn.prestamos;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// Sin esta exclusion Boot crea un usuario "user" en memoria con la contraseña en el log;
// la autenticacion real es JWT contra dbo.usuarios.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
// Solo el paquete de config: escanear la raiz recorre clases de test y rompe los cortes @WebMvcTest.
@ConfigurationPropertiesScan("gt.gob.chn.prestamos.infrastructure.config")
public class PrestamosApplication {

    private static final String ZONA_GUATEMALA = "America/Guatemala";

    static {
        // Antes de crear el DataSource: el contenedor arranca en UTC y un pago de las 23:00
        // quedaria con fecha del dia siguiente.
        TimeZone.setDefault(TimeZone.getTimeZone(ZONA_GUATEMALA));
    }

    public static void main(String[] args) {
        SpringApplication.run(PrestamosApplication.class, args);
    }
}
