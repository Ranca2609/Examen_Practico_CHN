package gt.gob.chn.prestamos.infrastructure.adapter.out.seguridad;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import gt.gob.chn.prestamos.domain.model.TokenAcceso;
import gt.gob.chn.prestamos.domain.model.Usuario;
import gt.gob.chn.prestamos.domain.port.out.GeneradorTokenPort;
import gt.gob.chn.prestamos.infrastructure.config.PropiedadesAplicacion;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class ProveedorJwt implements GeneradorTokenPort {

    private static final Logger LOG = LoggerFactory.getLogger(ProveedorJwt.class);

    private final SecretKey clave;
    private final String emisor;
    private final Duration vigencia;

    public ProveedorJwt(PropiedadesAplicacion propiedades) {
        PropiedadesAplicacion.Jwt jwt = propiedades.seguridad().jwt();
        // El largo mínimo del secreto (32) ya lo valida PropiedadesAplicacion al arrancar.
        this.clave = Keys.hmacShaKeyFor(jwt.secreto().getBytes(StandardCharsets.UTF_8));
        this.emisor = jwt.emisor();
        this.vigencia = Duration.ofMinutes(jwt.expiracionMinutos());
    }

    @Override
    public TokenAcceso generar(Usuario usuario) {
        // Instant y no el RelojPort: las marcas de un JWT son epoch UTC por especificación.
        Instant emitido = Instant.now();
        Instant expira = emitido.plus(vigencia);

        String token = Jwts.builder()
                .subject(usuario.getUsername())
                .claim("rol", usuario.getRol().name())
                .claim("nombre", usuario.getNombreCompleto())
                .issuer(emisor)
                .issuedAt(Date.from(emitido))
                .expiration(Date.from(expira))
                .signWith(clave, Jwts.SIG.HS256)
                .compact();

        return TokenAcceso.bearer(
                token,
                vigencia.toSeconds(),
                usuario.getUsername(),
                usuario.getNombreCompleto(),
                usuario.getRol());
    }

    // Vacío ante cualquier fallo, sin distinguir el motivo, para no dar pistas a un atacante.
    public Optional<DatosToken> validar(String token) {
        try {
            Claims cuerpo = Jwts.parser()
                    .verifyWith(clave)
                    .requireIssuer(emisor)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Optional.of(new DatosToken(
                    cuerpo.getSubject(),
                    cuerpo.get("rol", String.class),
                    cuerpo.get("nombre", String.class)));

        } catch (JwtException | IllegalArgumentException ex) {
            // Solo el tipo de fallo: el token nunca debe quedar en la bitácora.
            LOG.debug("Token rechazado: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
