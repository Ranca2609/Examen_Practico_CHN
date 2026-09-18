package gt.gob.chn.prestamos.infrastructure.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Validada para que una configuración ausente o insegura impida el arranque en vez de fallar en caliente.
@Validated
@ConfigurationProperties(prefix = "app")
public record PropiedadesAplicacion(

        @NotNull @Valid Seguridad seguridad,

        @NotNull @Valid Cors cors,

        // También decide las rutas de Flyway (ver migraciones.locations-* en application.yml).
        boolean datosDemo,

        @NotNull @Valid UsuariosIniciales usuariosIniciales,

        @NotNull @Valid Correlativos correlativos) {

    // La agencia viaja en cada número oficial (SC-001-2026-000001-3): un código inválido no debe arrancar.
    public record Correlativos(
            @NotBlank @Pattern(regexp = "[0-9]{3}", message = "El código de agencia debe tener 3 dígitos")
            String agencia) {
    }

    public record Seguridad(

            @NotNull @Valid Jwt jwt,

            @Min(3) int maxIntentosFallidos,

            @Min(1) int minutosBloqueo,

            @NotNull @Valid Login login) {
    }

    public record Jwt(

            // jjwt rechaza claves HS256 de menos de 32 bytes.
            @NotBlank @Size(min = 32, message = "El secreto JWT debe tener al menos 32 caracteres")
            String secreto,

            @Min(5) long expiracionMinutos,

            @NotBlank String emisor) {

        public long expiracionSegundos() {
            return expiracionMinutos * 60;
        }
    }

    public record Login(

            @Min(1) int maxPeticiones,

            @Min(1) int ventanaMinutos) {
    }

    public record Cors(@NotEmpty List<String> origenes) {
    }

    // Vacías en el archivo versionado: el valor real llega por variable de entorno.
    public record UsuariosIniciales(

            String adminPassword,

            String analistaPassword,

            String cajeroPassword,

            String consultaPassword) {
    }
}
