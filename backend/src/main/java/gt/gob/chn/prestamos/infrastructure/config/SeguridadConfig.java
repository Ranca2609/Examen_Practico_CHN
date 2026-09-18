package gt.gob.chn.prestamos.infrastructure.config;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.ErrorResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.out.seguridad.JwtAutenticacionFiltro;
import gt.gob.chn.prestamos.infrastructure.adapter.out.seguridad.LimitadorLoginFiltro;
import gt.gob.chn.prestamos.infrastructure.adapter.out.seguridad.ProveedorJwt;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SeguridadConfig {

    private static final Logger LOG = LoggerFactory.getLogger(SeguridadConfig.class);

    // 2^12 rondas: equilibrio entre resistencia a fuerza bruta y latencia del login.
    private static final int FUERZA_BCRYPT = 12;

    private static final String ROL_ADMIN = "ADMIN";
    private static final String ROL_ANALISTA = "ANALISTA";
    private static final String ROL_CAJERO = "CAJERO";

    private static final String[] RUTAS_DOCUMENTACION = {
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
    };

    private final PropiedadesAplicacion propiedades;
    private final ObjectMapper mapeadorJson;

    public SeguridadConfig(PropiedadesAplicacion propiedades, ObjectMapper mapeadorJson) {
        this.propiedades = propiedades;
        this.mapeadorJson = mapeadorJson;
    }

    // Cadena aparte: el CSP "default-src 'none'" de la API impediría cargar los scripts de Swagger UI.
    @Bean
    @Order(1)
    SecurityFilterChain cadenaDocumentacion(HttpSecurity http) throws Exception {
        http.securityMatcher(RUTAS_DOCUMENTACION)
                .authorizeHttpRequests(rutas -> rutas.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(cabeceras -> cabeceras
                        .frameOptions(marcos -> marcos.deny())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data:; script-src 'self'; "
                                        + "frame-ancestors 'none'")));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain cadenaApi(HttpSecurity http, ProveedorJwt proveedorJwt) throws Exception {

        // No son @Component: Spring Boot los registraría además como filtros del servlet, fuera de la cadena.
        var filtroJwt = new JwtAutenticacionFiltro(proveedorJwt);
        var filtroLimitador = new LimitadorLoginFiltro(
                propiedades.seguridad().login().maxPeticiones(),
                propiedades.seguridad().login().ventanaMinutos(),
                mapeadorJson);

        http
                // Sin CSRF: la credencial viaja en la cabecera Authorization, no en cookies.
                .csrf(AbstractHttpConfigurer::disable)

                .cors(cors -> cors.configurationSource(fuenteCors()))

                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // El preflight de CORS viaja sin cabecera Authorization.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // El orden importa: las reglas específicas van antes que el GET general.
                        .requestMatchers("/api/v1/auditoria/**").hasRole(ROL_ADMIN)

                        // Borrar un cliente arrastra sus solicitudes, préstamos y pagos.
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/clientes/**").hasRole(ROL_ADMIN)

                        .requestMatchers(HttpMethod.POST, "/api/v1/clientes").hasAnyRole(ROL_ADMIN, ROL_ANALISTA)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/clientes/**").hasAnyRole(ROL_ADMIN, ROL_ANALISTA)
                        .requestMatchers(HttpMethod.POST, "/api/v1/solicitudes/**").hasAnyRole(ROL_ADMIN, ROL_ANALISTA)
                        .requestMatchers(HttpMethod.POST, "/api/v1/solicitudes").hasAnyRole(ROL_ADMIN, ROL_ANALISTA)

                        .requestMatchers(HttpMethod.POST, "/api/v1/pagos").hasAnyRole(ROL_ADMIN, ROL_CAJERO)

                        .requestMatchers(HttpMethod.GET, "/api/v1/**").authenticated()

                        .anyRequest().authenticated())

                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(puntoEntradaNoAutenticado())
                        .accessDeniedHandler(manejadorAccesoDenegado()))

                .headers(cabeceras -> cabeceras
                        .frameOptions(marcos -> marcos.deny())
                        // Lambda vacía: activa X-Content-Type-Options: nosniff con sus valores por defecto.
                        .contentTypeOptions(tipos -> {
                        })
                        .referrerPolicy(politica -> politica.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                        // La API solo devuelve JSON: no debe cargar ningún recurso.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'")))

                // El limitador va antes que el JWT para cortar la fuerza bruta sin verificar firmas.
                .addFilterBefore(filtroLimitador, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    PasswordEncoder codificadorContrasenas() {
        return new BCryptPasswordEncoder(FUERZA_BCRYPT);
    }

    // Lista blanca, nunca comodín; sin credenciales de cookie porque el token va en cabecera.
    @Bean
    CorsConfigurationSource fuenteCors() {
        List<String> origenes = propiedades.cors().origenes();
        LOG.info("CORS habilitado para los orígenes: {}", origenes);

        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(origenes);
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuracion.setExposedHeaders(List.of("Location"));
        configuracion.setAllowCredentials(false);
        configuracion.setMaxAge(3600L);

        var fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", configuracion);
        return fuente;
    }

    private AuthenticationEntryPoint puntoEntradaNoAutenticado() {
        return (peticion, respuesta, excepcion) -> escribirError(
                peticion, respuesta,
                HttpServletResponse.SC_UNAUTHORIZED,
                "AUTENTICACION",
                "Credenciales ausentes o token invalido. Inicie sesion nuevamente.");
    }

    private AccessDeniedHandler manejadorAccesoDenegado() {
        return (peticion, respuesta, excepcion) -> escribirError(
                peticion, respuesta,
                HttpServletResponse.SC_FORBIDDEN,
                "ACCESO_DENEGADO",
                "Su rol no tiene permiso para ejecutar esta operacion.");
    }

    // Mensaje genérico a propósito: no revela si el usuario existe ni qué rol haría falta.
    private void escribirError(HttpServletRequest peticion,
                               HttpServletResponse respuesta,
                               int estado,
                               String codigo,
                               String mensaje) throws IOException {

        ErrorResponse cuerpo = ErrorResponse.de(estado, codigo, mensaje, peticion.getRequestURI());

        respuesta.setStatus(estado);
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        mapeadorJson.writeValue(respuesta.getOutputStream(), cuerpo);
    }
}
