package gt.gob.chn.prestamos.infrastructure.adapter.out.seguridad;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Contador en memoria: con varias réplicas cada una cuenta aparte; en producción iría en el gateway o en Redis.
public class LimitadorLoginFiltro extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(LimitadorLoginFiltro.class);

    private static final String RUTA_LOGIN = "/api/v1/auth/login";
    private static final String CODIGO_ERROR = "LIMITE_PETICIONES";
    private static final int ESTADO_DEMASIADAS_PETICIONES = 429;

    private final ConcurrentHashMap<String, Deque<Long>> intentosPorIp = new ConcurrentHashMap<>();

    private final int maxPeticiones;
    private final long ventanaMillis;
    private final ObjectMapper mapeadorJson;

    private volatile long ultimaLimpieza = System.currentTimeMillis();

    public LimitadorLoginFiltro(int maxPeticiones, int ventanaMinutos, ObjectMapper mapeadorJson) {
        this.maxPeticiones = maxPeticiones;
        this.ventanaMillis = ventanaMinutos * 60_000L;
        this.mapeadorJson = mapeadorJson;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest peticion) {
        return !(HttpMethod.POST.matches(peticion.getMethod())
                && RUTA_LOGIN.equals(peticion.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion,
                                    HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {

        // No X-Forwarded-For: el cliente puede falsificarla. Tras un balanceador, usar
        // server.forward-headers-strategy para que Tomcat resuelva la IP real.
        String ip = peticion.getRemoteAddr();

        purgarSiCorresponde();

        if (excedeLimite(ip)) {
            LOG.warn("Límite de intentos de inicio de sesión excedido para la IP {}", ip);
            responderLimiteExcedido(peticion, respuesta);
            return;
        }

        cadena.doFilter(peticion, respuesta);
    }

    private boolean excedeLimite(String ip) {
        long ahora = System.currentTimeMillis();
        Deque<Long> intentos = intentosPorIp.computeIfAbsent(ip, clave -> new ArrayDeque<>());

        // Lock por IP para no serializar los inicios de sesión de otros orígenes.
        synchronized (intentos) {
            while (!intentos.isEmpty() && ahora - intentos.peekFirst() > ventanaMillis) {
                intentos.pollFirst();
            }
            if (intentos.size() >= maxPeticiones) {
                return true;
            }
            intentos.addLast(ahora);
            return false;
        }
    }

    private void purgarSiCorresponde() {
        long ahora = System.currentTimeMillis();
        if (ahora - ultimaLimpieza < ventanaMillis) {
            return;
        }
        ultimaLimpieza = ahora;
        intentosPorIp.entrySet().removeIf(entrada -> {
            Deque<Long> intentos = entrada.getValue();
            synchronized (intentos) {
                while (!intentos.isEmpty() && ahora - intentos.peekFirst() > ventanaMillis) {
                    intentos.pollFirst();
                }
                return intentos.isEmpty();
            }
        });
    }

    private void responderLimiteExcedido(HttpServletRequest peticion,
                                         HttpServletResponse respuesta) throws IOException {

        ErrorResponse cuerpo = ErrorResponse.de(
                ESTADO_DEMASIADAS_PETICIONES,
                CODIGO_ERROR,
                "Demasiados intentos de inicio de sesion. Intente de nuevo en unos minutos.",
                peticion.getRequestURI());

        respuesta.setStatus(ESTADO_DEMASIADAS_PETICIONES);
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        respuesta.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(ventanaMillis / 1000));
        mapeadorJson.writeValue(respuesta.getOutputStream(), cuerpo);
    }
}
