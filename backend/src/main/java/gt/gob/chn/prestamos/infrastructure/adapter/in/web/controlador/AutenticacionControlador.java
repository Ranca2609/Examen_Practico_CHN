package gt.gob.chn.prestamos.infrastructure.adapter.in.web.controlador;

import gt.gob.chn.prestamos.domain.model.TokenAcceso;
import gt.gob.chn.prestamos.domain.model.Usuario;
import gt.gob.chn.prestamos.domain.port.in.AutenticarUsuarioUseCase;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.ExtractorContextoOperacion;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.peticion.LoginRequest;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.ErrorResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.TokenResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.UsuarioResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador.MapeadorWebUsuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Validated
@Tag(name = "Autenticacion", description = "Emision de tokens JWT y perfil del usuario")
public class AutenticacionControlador {

    private final AutenticarUsuarioUseCase autenticarUsuario;
    private final ExtractorContextoOperacion extractorContexto;

    public AutenticacionControlador(AutenticarUsuarioUseCase autenticarUsuario,
                                    ExtractorContextoOperacion extractorContexto) {
        this.autenticarUsuario = autenticarUsuario;
        this.extractorContexto = extractorContexto;
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesion",
            description = "Valida las credenciales y devuelve un token JWT. "
                    + "Tras 5 intentos fallidos la cuenta se bloquea 15 minutos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticacion exitosa"),
            @ApiResponse(responseCode = "400", description = "Credenciales con formato invalido",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Usuario o contrasena incorrectos, "
                    + "cuenta inactiva o bloqueada",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Demasiados intentos desde la misma IP",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TokenResponse> iniciarSesion(@Valid @RequestBody LoginRequest peticion,
                                                       HttpServletRequest http) {
        // El contexto se arma antes de autenticar: la auditoria debe registrar tambien los fallos.
        TokenAcceso token = autenticarUsuario.autenticar(
                MapeadorWebUsuario.aComando(peticion), extractorContexto.extraer(http));
        return ResponseEntity.ok(MapeadorWebUsuario.aRespuesta(token));
    }

    @GetMapping("/perfil")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Consultar perfil",
            description = "Devuelve los datos del usuario propietario del token enviado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil del usuario autenticado"),
            @ApiResponse(responseCode = "401", description = "Token ausente, invalido o expirado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "El usuario del token ya no existe",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UsuarioResponse> consultarPerfil() {
        Usuario usuario = autenticarUsuario.perfil(extractorContexto.usuarioActual());
        return ResponseEntity.ok(MapeadorWebUsuario.aRespuesta(usuario));
    }
}
