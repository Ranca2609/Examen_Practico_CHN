package gt.gob.chn.prestamos.infrastructure.adapter.out.seguridad;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JwtAutenticacionFiltro extends OncePerRequestFilter {

    private static final String CABECERA = "Authorization";
    private static final String PREFIJO_BEARER = "Bearer ";
    // hasRole("ADMIN") busca la autoridad ROLE_ADMIN.
    private static final String PREFIJO_ROL = "ROLE_";

    private final ProveedorJwt proveedorJwt;

    public JwtAutenticacionFiltro(ProveedorJwt proveedorJwt) {
        this.proveedorJwt = proveedorJwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion,
                                    HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {

        extraerToken(peticion)
                .flatMap(proveedorJwt::validar)
                .ifPresent(datos -> autenticar(datos, peticion));

        // Un token inválido no se rechaza aquí: la cadena de seguridad decide 401 o acceso público.
        cadena.doFilter(peticion, respuesta);
    }

    private Optional<String> extraerToken(HttpServletRequest peticion) {
        String cabecera = peticion.getHeader(CABECERA);
        if (cabecera == null || !cabecera.startsWith(PREFIJO_BEARER)) {
            return Optional.empty();
        }
        String token = cabecera.substring(PREFIJO_BEARER.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    private void autenticar(DatosToken datos, HttpServletRequest peticion) {
        // Sin consulta a BD por petición; credenciales en null para no retener la contraseña.
        var autoridades = List.of(new SimpleGrantedAuthority(PREFIJO_ROL + datos.rol()));
        var autenticacion = new UsernamePasswordAuthenticationToken(datos.username(), null, autoridades);
        // Los detalles conservan la IP de origen que registra la auditoría.
        autenticacion.setDetails(new WebAuthenticationDetailsSource().buildDetails(peticion));

        SecurityContextHolder.getContext().setAuthentication(autenticacion);
    }
}
