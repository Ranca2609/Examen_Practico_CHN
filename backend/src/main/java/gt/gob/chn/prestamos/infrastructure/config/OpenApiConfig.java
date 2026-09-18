package gt.gob.chn.prestamos.infrastructure.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_BEARER = "bearerAuth";

    private final int puerto;

    public OpenApiConfig(@Value("${server.port:8081}") int puerto) {
        this.puerto = puerto;
    }

    @Bean
    OpenAPI apiPrestamos() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Sistema de Prestamos CHN")
                        .version("1.0.0")
                        .description("""
                                API REST para la gestion de prestamos bancarios del Credito Hipotecario \
                                Nacional de Guatemala: clientes, solicitudes, aprobacion o rechazo, \
                                prestamos, tabla de amortizacion, pagos, resumen y auditoria.

                                Autenticacion: obtenga un token en POST /api/v1/auth/login y pulse \
                                'Authorize' para enviarlo en la cabecera Authorization. Los montos \
                                estan expresados en quetzales (GTQ) y las fechas en zona America/Guatemala.""")
                        .contact(new Contact()
                                .name("Equipo de Tecnologia - CHN")
                                .email("tecnologia@chn.com.gt"))
                        .license(new License()
                                .name("Uso interno - Credito Hipotecario Nacional")
                                .url("https://www.chn.com.gt")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + puerto)
                                .description("Entorno local (docker compose)")))
                .components(new Components().addSecuritySchemes(ESQUEMA_BEARER,
                        new SecurityScheme()
                                .name(ESQUEMA_BEARER)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token JWT emitido por /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_BEARER));
    }
}
