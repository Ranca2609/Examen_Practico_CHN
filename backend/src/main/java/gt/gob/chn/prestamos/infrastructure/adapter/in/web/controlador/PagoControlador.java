package gt.gob.chn.prestamos.infrastructure.adapter.in.web.controlador;

import gt.gob.chn.prestamos.domain.model.consulta.FiltroPago;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.domain.model.consulta.PagoDetalle;
import gt.gob.chn.prestamos.domain.port.in.RegistrarPagosUseCase;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.ExtractorContextoOperacion;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.peticion.PagoRequest;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.ErrorResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.PagoResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.PaginaResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador.MapeadorWebPagina;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador.MapeadorWebPago;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pagos")
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Pagos", description = "Registro de pagos en efectivo y consulta de recibos")
public class PagoControlador {

    private final RegistrarPagosUseCase registrarPagos;
    private final ExtractorContextoOperacion extractorContexto;

    public PagoControlador(RegistrarPagosUseCase registrarPagos,
                           ExtractorContextoOperacion extractorContexto) {
        this.registrarPagos = registrarPagos;
        this.extractorContexto = extractorContexto;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar pagos",
            description = "Listado paginado. Filtros opcionales: texto libre (numero de recibo, "
                    + "numero de prestamo, nombre o DPI del cliente), prestamo, cliente, rango de monto, "
                    + "rango de fecha de pago y usuario que registro el pago.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de pagos"),
            @ApiResponse(responseCode = "400", description = "Parametros de paginacion invalidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PaginaResponse<PagoResponse>> listar(
            @Parameter(description = "Texto libre a buscar en numero de recibo, numero de "
                    + "prestamo, nombre o DPI del cliente", example = "RC-001-2026")
            @RequestParam(required = false) String busqueda,
            @Parameter(description = "Filtrar por prestamo", example = "1")
            @RequestParam(required = false) Long prestamoId,
            @Parameter(description = "Filtrar por cliente", example = "1")
            @RequestParam(required = false) Long clienteId,
            @Parameter(description = "Monto minimo del abono, inclusive", example = "500.00")
            @RequestParam(required = false) BigDecimal montoMinimo,
            @Parameter(description = "Monto maximo del abono, inclusive", example = "10000.00")
            @RequestParam(required = false) BigDecimal montoMaximo,
            @Parameter(description = "Pagos desde esta fecha, inclusive (aaaa-MM-dd)",
                    example = "2026-01-01")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @Parameter(description = "Pagos hasta esta fecha, inclusive (aaaa-MM-dd)",
                    example = "2026-01-31")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @Parameter(description = "Usuario que registro el pago; coincidencia parcial",
                    example = "cajero")
            @RequestParam(required = false) String usuarioRegistro,
            @Parameter(description = "Numero de pagina (base 0)", example = "0")
            @RequestParam(defaultValue = "0") @Min(value = 0,
                    message = "La pagina debe ser mayor o igual a 0") int pagina,
            @Parameter(description = "Elementos por pagina", example = "10")
            @RequestParam(defaultValue = "10") @Min(value = 1,
                    message = "El tamano debe ser mayor o igual a 1")
            @Max(value = 100, message = "El tamano no puede exceder 100") int tamano) {

        FiltroPago filtro = new FiltroPago(busqueda, prestamoId, clienteId,
                montoMinimo, montoMaximo, fechaDesde, fechaHasta, usuarioRegistro,
                pagina, tamano);
        PaginaDominio<PagoDetalle> resultado = registrarPagos.listar(filtro);
        return ResponseEntity.ok(
                MapeadorWebPagina.convertir(resultado, MapeadorWebPago::aRespuesta));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
    @Operation(summary = "Registrar pago",
            description = "Aplica un abono al prestamo, genera el recibo y liquida el prestamo "
                    + "cuando el saldo llega a cero")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pago registrado"),
            @ApiResponse(responseCode = "400", description = "Monto invalido",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rol sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Prestamo inexistente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Prestamo ya liquidado o monto mayor al saldo",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PagoResponse> registrar(@Valid @RequestBody PagoRequest peticion,
                                                   HttpServletRequest http) {
        PagoDetalle detalle = registrarPagos.registrar(
                MapeadorWebPago.aComando(peticion), extractorContexto.extraer(http));
        return ResponseEntity
                .created(URI.create("/api/v1/pagos/" + detalle.pago().getId()))
                .body(MapeadorWebPago.aRespuesta(detalle));
    }
}
