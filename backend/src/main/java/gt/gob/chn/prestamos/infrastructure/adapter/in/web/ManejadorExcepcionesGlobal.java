package gt.gob.chn.prestamos.infrastructure.adapter.in.web;

import gt.gob.chn.prestamos.domain.exception.AutenticacionException;
import gt.gob.chn.prestamos.domain.exception.ConflictoRecursoException;
import gt.gob.chn.prestamos.domain.exception.RecursoNoEncontradoException;
import gt.gob.chn.prestamos.domain.exception.ReglaNegocioException;
import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.ErrorCampo;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ManejadorExcepcionesGlobal {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorExcepcionesGlobal.class);

    private static final String MENSAJE_VALIDACION = "La peticion contiene campos invalidos";
    private static final String MENSAJE_ERROR_INTERNO =
            "Ocurrio un error inesperado. Contacte al administrador.";
    private static final String MENSAJE_CUERPO_INVALIDO =
            "El cuerpo de la peticion no tiene un formato JSON valido";
    private static final String MENSAJE_CONFLICTO_DATOS =
            "La operacion viola una restriccion de integridad de los datos";
    private static final String MENSAJE_RUTA_NO_ENCONTRADA = "El recurso solicitado no existe";

    @ExceptionHandler(ValidacionDominioException.class)
    public ResponseEntity<ErrorResponse> manejarValidacionDominio(
            ValidacionDominioException ex, HttpServletRequest peticion) {
        return respuesta(HttpStatus.BAD_REQUEST, ex.codigo(), ex.getMessage(), peticion, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> manejarCuerpoInvalido(
            MethodArgumentNotValidException ex, HttpServletRequest peticion) {
        List<ErrorCampo> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorCampo(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(ErrorCampo::campo))
                .toList();
        return respuesta(HttpStatus.BAD_REQUEST, "VALIDACION", MENSAJE_VALIDACION, peticion, errores);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> manejarParametrosInvalidos(
            HandlerMethodValidationException ex, HttpServletRequest peticion) {
        List<ErrorCampo> errores = ex.getAllErrors().stream()
                .map(this::aErrorCampo)
                .sorted(Comparator.comparing(ErrorCampo::campo))
                .toList();
        return respuesta(HttpStatus.BAD_REQUEST, "VALIDACION", MENSAJE_VALIDACION, peticion, errores);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> manejarRestriccionInvalida(
            ConstraintViolationException ex, HttpServletRequest peticion) {
        List<ErrorCampo> errores = ex.getConstraintViolations().stream()
                .map(this::aErrorCampo)
                .sorted(Comparator.comparing(ErrorCampo::campo))
                .toList();
        return respuesta(HttpStatus.BAD_REQUEST, "VALIDACION", MENSAJE_VALIDACION, peticion, errores);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> manejarJsonIlegible(
            HttpMessageNotReadableException ex, HttpServletRequest peticion) {
        // El mensaje original expone nombres de clases internas, por eso se sustituye.
        LOG.warn("Cuerpo ilegible en {}: {}", peticion.getRequestURI(), ex.getMessage());
        return respuesta(HttpStatus.BAD_REQUEST, "VALIDACION", MENSAJE_CUERPO_INVALIDO,
                peticion, List.of());
    }

    // Se indica el formato esperado: en filtros de fechas, montos y enums un "valor no valido" no es accionable.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> manejarTipoIncompatible(
            MethodArgumentTypeMismatchException ex, HttpServletRequest peticion) {
        String formatoEsperado = formatoEsperado(ex.getRequiredType());
        String mensaje = formatoEsperado == null
                ? "El parametro '" + ex.getName() + "' tiene un valor no valido"
                : "El parametro '" + ex.getName() + "' no tiene un formato valido. "
                        + formatoEsperado;
        return respuesta(HttpStatus.BAD_REQUEST, "VALIDACION", mensaje, peticion,
                List.of(new ErrorCampo(ex.getName(), mensaje)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> manejarParametroFaltante(
            MissingServletRequestParameterException ex, HttpServletRequest peticion) {
        String mensaje = "El parametro '" + ex.getParameterName() + "' es obligatorio";
        return respuesta(HttpStatus.BAD_REQUEST, "VALIDACION", mensaje, peticion,
                List.of(new ErrorCampo(ex.getParameterName(), mensaje)));
    }

    @ExceptionHandler(AutenticacionException.class)
    public ResponseEntity<ErrorResponse> manejarAutenticacion(
            AutenticacionException ex, HttpServletRequest peticion) {
        return respuesta(HttpStatus.UNAUTHORIZED, ex.codigo(), ex.getMessage(), peticion, List.of());
    }

    // Mensaje generico a proposito: no revela si el usuario existe.
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> manejarCredencialesInvalidas(
            BadCredentialsException ex, HttpServletRequest peticion) {
        return respuesta(HttpStatus.UNAUTHORIZED, "AUTENTICACION",
                "Usuario o contrasena incorrectos", peticion, List.of());
    }

    // Sin este manejador el resto de fallos de Spring Security caerian en el generico y responderian 500.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> manejarFalloAutenticacion(
            AuthenticationException ex, HttpServletRequest peticion) {
        return respuesta(HttpStatus.UNAUTHORIZED, "AUTENTICACION",
                "No fue posible autenticar la peticion", peticion, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> manejarAccesoDenegado(
            AccessDeniedException ex, HttpServletRequest peticion) {
        return respuesta(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO",
                "No cuenta con los permisos necesarios para ejecutar esta operacion",
                peticion, List.of());
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> manejarNoEncontrado(
            RecursoNoEncontradoException ex, HttpServletRequest peticion) {
        return respuesta(HttpStatus.NOT_FOUND, ex.codigo(), ex.getMessage(), peticion, List.of());
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> manejarRutaNoEncontrada(
            Exception ex, HttpServletRequest peticion) {
        return respuesta(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", MENSAJE_RUTA_NO_ENCONTRADA,
                peticion, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> manejarMetodoNoPermitido(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest peticion) {
        return respuesta(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NO_PERMITIDO",
                "El metodo " + ex.getMethod() + " no esta permitido en esta ruta",
                peticion, List.of());
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ErrorResponse> manejarReglaNegocio(
            ReglaNegocioException ex, HttpServletRequest peticion) {
        return respuesta(HttpStatus.CONFLICT, ex.codigo(), ex.getMessage(), peticion, List.of());
    }

    @ExceptionHandler(ConflictoRecursoException.class)
    public ResponseEntity<ErrorResponse> manejarConflicto(
            ConflictoRecursoException ex, HttpServletRequest peticion) {
        return respuesta(HttpStatus.CONFLICT, ex.codigo(), ex.getMessage(), peticion, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> manejarIntegridadDatos(
            DataIntegrityViolationException ex, HttpServletRequest peticion) {
        // El detalle de la restriccion revela el esquema, por eso solo va al log.
        LOG.warn("Violacion de integridad en {}: {}", peticion.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return respuesta(HttpStatus.CONFLICT, "DUPLICADO", MENSAJE_CONFLICTO_DATOS,
                peticion, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> manejarErrorInesperado(
            Exception ex, HttpServletRequest peticion) {
        LOG.error("Error no controlado en {} {}", peticion.getMethod(),
                peticion.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.de(HttpStatus.INTERNAL_SERVER_ERROR.value(), "ERROR_INTERNO",
                        MENSAJE_ERROR_INTERNO, peticion.getRequestURI()));
    }

    private ResponseEntity<ErrorResponse> respuesta(HttpStatus estado, String codigo, String mensaje,
                                                    HttpServletRequest peticion,
                                                    List<ErrorCampo> errores) {
        String ruta = peticion.getRequestURI();
        LOG.warn("{} {} -> {} ({}): {}", peticion.getMethod(), ruta, estado.value(), codigo, mensaje);
        return ResponseEntity.status(estado)
                .body(ErrorResponse.de(estado.value(), codigo, mensaje, ruta, errores));
    }

    private ErrorCampo aErrorCampo(MessageSourceResolvable error) {
        String campo = error instanceof FieldError fieldError ? fieldError.getField() : "parametro";
        return new ErrorCampo(campo, error.getDefaultMessage());
    }

    // null para tipos que la API no usa: el llamador cae al mensaje generico.
    private String formatoEsperado(Class<?> tipo) {
        if (tipo == null) {
            return null;
        }
        if (LocalDate.class.equals(tipo)) {
            return "Se espera una fecha en formato aaaa-MM-dd.";
        }
        if (LocalDateTime.class.equals(tipo)) {
            return "Se espera una fecha y hora en formato aaaa-MM-ddTHH:mm:ss.";
        }
        if (BigDecimal.class.equals(tipo) || Double.class.equals(tipo)
                || Float.class.equals(tipo)) {
            return "Se espera un numero decimal con punto como separador, por ejemplo 1500.00.";
        }
        if (Integer.class.equals(tipo) || Long.class.equals(tipo) || Short.class.equals(tipo)
                || int.class.equals(tipo) || long.class.equals(tipo)) {
            return "Se espera un numero entero.";
        }
        if (Boolean.class.equals(tipo) || boolean.class.equals(tipo)) {
            return "Se espera true o false.";
        }
        if (tipo.isEnum()) {
            return "Valores permitidos: " + Arrays.stream(tipo.getEnumConstants())
                    .map(valor -> ((Enum<?>) valor).name())
                    .collect(Collectors.joining(", ")) + ".";
        }
        return null;
    }

    // La ruta de propiedad llega como "metodo.parametro"; el campo es el ultimo segmento.
    private ErrorCampo aErrorCampo(ConstraintViolation<?> violacion) {
        String ruta = violacion.getPropertyPath().toString();
        int ultimoPunto = ruta.lastIndexOf('.');
        String campo = ultimoPunto >= 0 ? ruta.substring(ultimoPunto + 1) : ruta;
        return new ErrorCampo(campo, violacion.getMessage());
    }
}
