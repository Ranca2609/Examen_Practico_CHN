package gt.gob.chn.prestamos.infrastructure.adapter.in.web.controlador;

import gt.gob.chn.prestamos.domain.model.RegistroAuditoria;
import gt.gob.chn.prestamos.domain.model.consulta.FiltroAuditoria;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.domain.port.in.ConsultarAuditoriaUseCase;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.AuditoriaResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.ErrorResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.PaginaResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador.MapeadorWebAuditoria;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador.MapeadorWebPagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auditoria")
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Auditoria", description = "Bitacora de operaciones sensibles (solo ADMIN)")
public class AuditoriaControlador {

    private final ConsultarAuditoriaUseCase consultarAuditoria;

    public AuditoriaControlador(ConsultarAuditoriaUseCase consultarAuditoria) {
        this.consultarAuditoria = consultarAuditoria;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar auditoria",
            description = "Registros de la bitacora en orden descendente por fecha. Filtros "
                    + "opcionales: texto libre (usuario, detalle o identificador afectado), usuario, "
                    + "accion, entidad y rango de fecha.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de registros de auditoria"),
            @ApiResponse(responseCode = "400", description = "Parametros de filtro o de paginacion invalidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rol sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PaginaResponse<AuditoriaResponse>> listar(
            @Parameter(description = "Texto libre a buscar en usuario, detalle o identificador "
                    + "de la entidad", example = "PAGO_REGISTRADO")
            @RequestParam(required = false) String busqueda,
            @Parameter(description = "Usuario que ejecuto la operacion; coincidencia parcial",
                    example = "admin")
            @RequestParam(required = false) String usuario,
            @Parameter(description = "Accion auditada; valor exacto",
                    example = "PAGO_REGISTRADO")
            @RequestParam(required = false) String accion,
            @Parameter(description = "Entidad afectada; valor exacto",
                    schema = @Schema(allowableValues = {"CLIENTE", "SOLICITUD", "PRESTAMO",
                            "PAGO", "USUARIO"}))
            @RequestParam(required = false) String entidad,
            @Parameter(description = "Registros desde esta fecha, inclusive (aaaa-MM-dd)",
                    example = "2026-01-01")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @Parameter(description = "Registros hasta esta fecha, inclusive (aaaa-MM-dd)",
                    example = "2026-01-31")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @Parameter(description = "Numero de pagina (base 0)", example = "0")
            @RequestParam(defaultValue = "0") @Min(value = 0,
                    message = "La pagina debe ser mayor o igual a 0") int pagina,
            @Parameter(description = "Elementos por pagina", example = "20")
            @RequestParam(defaultValue = "10") @Min(value = 1,
                    message = "El tamano debe ser mayor o igual a 1")
            @Max(value = 100, message = "El tamano no puede exceder 100") int tamano) {

        // Accion y entidad no son enums de dominio: viajan tal cual y se comparan por valor exacto.
        FiltroAuditoria filtro = new FiltroAuditoria(busqueda, usuario, accion, entidad,
                fechaDesde, fechaHasta, pagina, tamano);
        PaginaDominio<RegistroAuditoria> resultado = consultarAuditoria.listar(filtro);
        return ResponseEntity.ok(
                MapeadorWebPagina.convertir(resultado, MapeadorWebAuditoria::aRespuesta));
    }
}
