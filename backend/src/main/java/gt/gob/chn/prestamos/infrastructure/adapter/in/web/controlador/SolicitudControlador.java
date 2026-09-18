package gt.gob.chn.prestamos.infrastructure.adapter.in.web.controlador;

import gt.gob.chn.prestamos.domain.model.consulta.FiltroSolicitud;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.domain.model.consulta.SolicitudDetalle;
import gt.gob.chn.prestamos.domain.port.in.GestionarSolicitudesUseCase;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.ExtractorContextoOperacion;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.peticion.AprobarSolicitudRequest;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.peticion.RechazarSolicitudRequest;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.peticion.SimulacionRequest;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.peticion.SolicitudRequest;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.ErrorResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.PaginaResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.SimulacionResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.SolicitudResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador.ConversorEnumWeb;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador.MapeadorWebPagina;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador.MapeadorWebSimulacion;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador.MapeadorWebSolicitud;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/solicitudes")
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Solicitudes", description = "Captura, simulacion y resolucion de solicitudes")
public class SolicitudControlador {

    private final GestionarSolicitudesUseCase gestionarSolicitudes;
    private final ExtractorContextoOperacion extractorContexto;

    public SolicitudControlador(GestionarSolicitudesUseCase gestionarSolicitudes,
                                ExtractorContextoOperacion extractorContexto) {
        this.gestionarSolicitudes = gestionarSolicitudes;
        this.extractorContexto = extractorContexto;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar solicitudes",
            description = "Listado paginado. Filtros opcionales: texto libre (numero de solicitud, "
                    + "destino, nombre o DPI del cliente), cliente, estado, tipo de prestamo, rango de "
                    + "monto solicitado, rango de plazo y rango de fecha de solicitud.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de solicitudes"),
            @ApiResponse(responseCode = "400", description = "Estado o paginacion invalidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PaginaResponse<SolicitudResponse>> listar(
            @Parameter(description = "Texto libre a buscar en numero de solicitud, nombre o DPI "
                    + "del cliente, o destino del prestamo", example = "SC-001-2026")
            @RequestParam(required = false) String busqueda,
            @Parameter(description = "Filtrar por cliente", example = "1")
            @RequestParam(required = false) Long clienteId,
            @Parameter(description = "Filtrar por estado",
                    schema = @Schema(allowableValues = {"EN_PROCESO", "APROBADA", "RECHAZADA"}))
            @RequestParam(required = false) String estado,
            @Parameter(description = "Filtrar por tipo de prestamo",
                    schema = @Schema(allowableValues = {"PERSONAL", "HIPOTECARIO", "VEHICULAR",
                            "EMPRESARIAL", "EDUCATIVO"}))
            @RequestParam(required = false) String tipoPrestamo,
            @Parameter(description = "Monto solicitado minimo, inclusive", example = "5000.00")
            @RequestParam(required = false) BigDecimal montoMinimo,
            @Parameter(description = "Monto solicitado maximo, inclusive", example = "50000.00")
            @RequestParam(required = false) BigDecimal montoMaximo,
            @Parameter(description = "Plazo minimo en meses, inclusive", example = "12")
            @RequestParam(required = false) Integer plazoMinimo,
            @Parameter(description = "Plazo maximo en meses, inclusive", example = "60")
            @RequestParam(required = false) Integer plazoMaximo,
            @Parameter(description = "Solicitudes desde esta fecha, inclusive (aaaa-MM-dd)",
                    example = "2026-01-01")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @Parameter(description = "Solicitudes hasta esta fecha, inclusive (aaaa-MM-dd)",
                    example = "2026-01-31")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @Parameter(description = "Numero de pagina (base 0)", example = "0")
            @RequestParam(defaultValue = "0") @Min(value = 0,
                    message = "La pagina debe ser mayor o igual a 0") int pagina,
            @Parameter(description = "Elementos por pagina", example = "10")
            @RequestParam(defaultValue = "10") @Min(value = 1,
                    message = "El tamano debe ser mayor o igual a 1")
            @Max(value = 100, message = "El tamano no puede exceder 100") int tamano) {

        // Estado y tipo llegan como texto: el conversor responde 400 con los valores permitidos.
        FiltroSolicitud filtro = new FiltroSolicitud(busqueda, clienteId,
                ConversorEnumWeb.aEstadoSolicitud(estado),
                ConversorEnumWeb.aTipoPrestamoOpcional(tipoPrestamo),
                montoMinimo, montoMaximo, plazoMinimo, plazoMaximo,
                fechaDesde, fechaHasta, pagina, tamano);
        PaginaDominio<SolicitudDetalle> resultado = gestionarSolicitudes.listar(filtro);
        return ResponseEntity.ok(
                MapeadorWebPagina.convertir(resultado, MapeadorWebSolicitud::aRespuesta));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ANALISTA')")
    @Operation(summary = "Crear solicitud",
            description = "Registra una solicitud en estado EN_PROCESO y le asigna su correlativo")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Solicitud creada"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos o tipo de prestamo desconocido",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rol sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cliente inexistente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "El cliente no admite una nueva solicitud",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SolicitudResponse> crear(@Valid @RequestBody SolicitudRequest peticion,
                                                    HttpServletRequest http) {
        SolicitudDetalle detalle = gestionarSolicitudes.crear(
                MapeadorWebSolicitud.aComando(peticion), extractorContexto.extraer(http));
        return ResponseEntity
                .created(URI.create("/api/v1/solicitudes/" + detalle.solicitud().getId()))
                .body(MapeadorWebSolicitud.aRespuesta(detalle));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consultar solicitud",
            description = "Devuelve una solicitud con los datos del cliente y su resolucion")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitud encontrada"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Solicitud inexistente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SolicitudResponse> obtener(
            @Parameter(description = "Identificador de la solicitud", example = "1")
            @PathVariable Long id) {
        return ResponseEntity.ok(MapeadorWebSolicitud.aRespuesta(gestionarSolicitudes.obtener(id)));
    }

    @PostMapping("/{id}/aprobar")
    @PreAuthorize("hasAnyRole('ADMIN','ANALISTA')")
    @Operation(summary = "Aprobar solicitud",
            description = "Aprueba la solicitud y genera el prestamo. Los campos omitidos "
                    + "en el cuerpo toman las condiciones solicitadas por el cliente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitud aprobada"),
            @ApiResponse(responseCode = "400", description = "Condiciones de aprobacion invalidas",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rol sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Solicitud inexistente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "La solicitud ya fue resuelta",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SolicitudResponse> aprobar(
            @Parameter(description = "Identificador de la solicitud", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody AprobarSolicitudRequest peticion,
            HttpServletRequest http) {
        SolicitudDetalle detalle = gestionarSolicitudes.aprobar(
                id, MapeadorWebSolicitud.aComando(peticion), extractorContexto.extraer(http));
        return ResponseEntity.ok(MapeadorWebSolicitud.aRespuesta(detalle));
    }

    @PostMapping("/{id}/rechazar")
    @PreAuthorize("hasAnyRole('ADMIN','ANALISTA')")
    @Operation(summary = "Rechazar solicitud",
            description = "Rechaza la solicitud. El motivo es obligatorio y queda auditado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitud rechazada"),
            @ApiResponse(responseCode = "400", description = "Motivo ausente o demasiado corto",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rol sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Solicitud inexistente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "La solicitud ya fue resuelta",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SolicitudResponse> rechazar(
            @Parameter(description = "Identificador de la solicitud", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody RechazarSolicitudRequest peticion,
            HttpServletRequest http) {
        SolicitudDetalle detalle = gestionarSolicitudes.rechazar(
                id, MapeadorWebSolicitud.aComando(peticion), extractorContexto.extraer(http));
        return ResponseEntity.ok(MapeadorWebSolicitud.aRespuesta(detalle));
    }

    @PostMapping("/simulacion")
    @PreAuthorize("hasAnyRole('ADMIN','ANALISTA')")
    @Operation(summary = "Simular prestamo",
            description = "Calcula el plan de amortizacion y evalua la capacidad de pago "
                    + "sin persistir nada")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado de la simulacion"),
            @ApiResponse(responseCode = "400", description = "Parametros de simulacion invalidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rol sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cliente inexistente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SimulacionResponse> simular(
            @Valid @RequestBody SimulacionRequest peticion) {
        return ResponseEntity.ok(MapeadorWebSimulacion.aRespuesta(
                gestionarSolicitudes.simular(MapeadorWebSolicitud.aComando(peticion))));
    }
}
