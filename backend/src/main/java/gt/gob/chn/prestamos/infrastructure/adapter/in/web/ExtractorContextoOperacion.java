package gt.gob.chn.prestamos.infrastructure.adapter.in.web;

import gt.gob.chn.prestamos.domain.port.in.command.ContextoOperacion;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ExtractorContextoOperacion {

    private static final String USUARIO_ANONIMO = "anonimo";

    // Largo de auditoria.direccion_ip: una IPv6 con mapeo IPv4.
    private static final int LARGO_MAXIMO_IP = 45;

    private static final String CABECERA_IP_REENVIADA = "X-Forwarded-For";

    public ContextoOperacion extraer(HttpServletRequest peticion) {
        return new ContextoOperacion(usuarioActual(), direccionIp(peticion));
    }

    public String usuarioActual() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        boolean anonima = autenticacion == null
                || !autenticacion.isAuthenticated()
                || autenticacion instanceof AnonymousAuthenticationToken;
        if (anonima || autenticacion.getName() == null || autenticacion.getName().isBlank()) {
            return USUARIO_ANONIMO;
        }
        return autenticacion.getName();
    }

    // Detras del proxy del frontend la IP llega en X-Forwarded-For; el primer valor es el cliente original.
    private String direccionIp(HttpServletRequest peticion) {
        if (peticion == null) {
            return null;
        }
        String reenviada = peticion.getHeader(CABECERA_IP_REENVIADA);
        if (reenviada != null && !reenviada.isBlank()) {
            return recortar(reenviada.split(",")[0].trim());
        }
        return recortar(peticion.getRemoteAddr());
    }

    // Se recorta en lugar de fallar: una IP mal formada no debe impedir la operacion.
    private String recortar(String direccion) {
        if (direccion == null || direccion.isBlank()) {
            return null;
        }
        return direccion.length() <= LARGO_MAXIMO_IP
                ? direccion
                : direccion.substring(0, LARGO_MAXIMO_IP);
    }
}
