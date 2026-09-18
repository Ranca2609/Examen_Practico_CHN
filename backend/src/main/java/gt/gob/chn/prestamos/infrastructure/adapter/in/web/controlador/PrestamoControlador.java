package gt.gob.chn.prestamos.infrastructure.adapter.in.web.controlador;

import gt.gob.chn.prestamos.domain.model.consulta.FiltroPrestamo;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.domain.model.consulta.PrestamoDetalle;
import gt.gob.chn.prestamos.domain.model.reporte.ArchivoGenerado;
import gt.gob.chn.prestamos.domain.model.reporte.FormatoReporte;
import gt.gob.chn.prestamos.domain.port.in.ConsultarPrestamosUseCase;
import gt.gob.chn.prestamos.domain.port.in.GenerarReportesUseCase;
import gt.gob.chn.prestamos.domain.port.in.RegistrarPagosUseCase;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.ExtractorContextoOperacion;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.ErrorResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.PagoResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.PaginaResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.PlanAmortizacionResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.PrestamoResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador.ConversorEnumWeb;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador.MapeadorWebPagina;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador.MapeadorWebPago;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador.MapeadorWebPrestamo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/prestamos")
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Prestamos", description = "Consulta de prestamos, amortizacion y pagos aplicados")
public class PrestamoControlador {

    private final ConsultarPrestamosUseCase consultarPrestamos;
    private final RegistrarPagosUseCase registrarPagos;
    private final GenerarReportesUseCase generarReportes;
    private final ExtractorContextoOperacion extractorContexto;

    public PrestamoControlador(ConsultarPrestamosUseCase consultarPrestamos,
                               RegistrarPagosUseCase registrarPagos,
                               GenerarReportesUseCase generarReportes,
                               ExtractorContextoOperacion extractorContexto) {
        this.consultarPrestamos = consultarPrestamos;
        this.registrarPagos = registrarPagos;
        this.generarReportes = generarReportes;
        this.extractorContexto = extractorContexto;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar prestamos",
            description = "Listado paginado. Filtros opcionales: texto libre (numero de prestamo, "
                    + "numero de solicitud, nombre o DPI del cliente), cliente, estado, rango de monto "
                    + "aprobado, rango de saldo pendiente y rangos de fecha de desembolso y vencimiento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de prestamos"),
            @ApiResponse(responseCode = "400", description = "Estado o paginacion invalidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PaginaResponse<PrestamoResponse>> listar(
            @Parameter(description = "Texto libre a buscar en numero de prestamo, numero de "
                    + "solicitud, nombre o DPI del cliente", example = "PR-001-2026")
            @RequestParam(required = false) String busqueda,
            @Parameter(description = "Filtrar por cliente", example = "1")
            @RequestParam(required = false) Long clienteId,
            @Parameter(description = "Filtrar por estado",
                    schema = @Schema(allowableValues = {"VIGENTE", "LIQUIDADO"}))
            @RequestParam(required = false) String estado,
            @Parameter(description = "Monto aprobado minimo, inclusive", example = "5000.00")
            @RequestParam(required = false) BigDecimal montoMinimo,
            @Parameter(description = "Monto aprobado maximo, inclusive", example = "50000.00")
            @RequestParam(required = false) BigDecimal montoMaximo,
            @Parameter(description = "Saldo pendiente minimo, inclusive", example = "0.00")
            @RequestParam(required = false) BigDecimal saldoMinimo,
            @Parameter(description = "Saldo pendiente maximo, inclusive", example = "25000.00")
            @RequestParam(required = false) BigDecimal saldoMaximo,
            @Parameter(description = "Desembolsados desde esta fecha, inclusive (aaaa-MM-dd)",
                    example = "2026-01-01")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desembolsoDesde,
            @Parameter(description = "Desembolsados hasta esta fecha, inclusive (aaaa-MM-dd)",
                    example = "2026-01-31")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desembolsoHasta,
            @Parameter(description = "Con vencimiento desde esta fecha, inclusive (aaaa-MM-dd)",
                    example = "2027-01-01")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimientoDesde,
            @Parameter(description = "Con vencimiento hasta esta fecha, inclusive (aaaa-MM-dd)",
                    example = "2027-12-31")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimientoHasta,
            @Parameter(description = "Numero de pagina (base 0)", example = "0")
            @RequestParam(defaultValue = "0") @Min(value = 0,
                    message = "La pagina debe ser mayor o igual a 0") int pagina,
            @Parameter(description = "Elementos por pagina", example = "10")
            @RequestParam(defaultValue = "10") @Min(value = 1,
                    message = "El tamano debe ser mayor o igual a 1")
            @Max(value = 100, message = "El tamano no puede exceder 100") int tamano) {

        // El estado llega como texto; el conversor responde 400 con los valores permitidos.
        FiltroPrestamo filtro = new FiltroPrestamo(busqueda, clienteId,
                ConversorEnumWeb.aEstadoPrestamo(estado),
                montoMinimo, montoMaximo, saldoMinimo, saldoMaximo,
                desembolsoDesde, desembolsoHasta, vencimientoDesde, vencimientoHasta,
                pagina, tamano);
        PaginaDominio<PrestamoDetalle> resultado = consultarPrestamos.listar(filtro);
        return ResponseEntity.ok(
                MapeadorWebPagina.convertir(resultado, MapeadorWebPrestamo::aRespuesta));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consultar prestamo",
            description = "Devuelve el prestamo con su saldo pendiente y porcentaje pagado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Prestamo encontrado"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Prestamo inexistente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PrestamoResponse> obtener(
            @Parameter(description = "Identificador del prestamo", example = "1")
            @PathVariable Long id) {
        return ResponseEntity.ok(MapeadorWebPrestamo.aRespuesta(consultarPrestamos.obtener(id)));
    }

    @GetMapping("/{id}/amortizacion")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tabla de amortizacion",
            description = "Plan de pagos calculado con el sistema frances para las condiciones "
                    + "aprobadas del prestamo")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plan de amortizacion"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Prestamo inexistente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PlanAmortizacionResponse> amortizacion(
            @Parameter(description = "Identificador del prestamo", example = "1")
            @PathVariable Long id) {
        return ResponseEntity.ok(
                MapeadorWebPrestamo.aRespuesta(consultarPrestamos.planAmortizacion(id)));
    }

    @GetMapping("/{id}/pagos")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Pagos del prestamo",
            description = "Historial completo de pagos aplicados al prestamo, sin paginar")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagos del prestamo"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Prestamo inexistente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<PagoResponse>> listarPagos(
            @Parameter(description = "Identificador del prestamo", example = "1")
            @PathVariable Long id) {
        List<PagoResponse> pagos = registrarPagos.listarPorPrestamo(id).stream()
                .map(MapeadorWebPago::aRespuesta)
                .toList();
        return ResponseEntity.ok(pagos);
    }

    // Extension en la ruta para que el navegador proponga el nombre con el formato correcto;
    // FormatoReporte convierte una extension desconocida en 400 en lugar de 404.
    @GetMapping("/{id}/amortizacion.{extension}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Descargar el plan de amortizacion (PDF o Excel)",
            description = "Genera en el servidor el plan de pagos del prestamo con encabezado y "
                    + "pie en todas las paginas. Rutas disponibles: amortizacion.pdf y "
                    + "amortizacion.xlsx. La descarga queda registrada en la bitacora.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Archivo del reporte",
                    content = @Content(mediaType = "application/octet-stream",
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "Formato de archivo no soportado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Prestamo inexistente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<byte[]> descargarAmortizacion(
            @Parameter(description = "Identificador del prestamo", example = "1")
            @PathVariable Long id,
            @Parameter(description = "Formato del archivo", example = "pdf",
                    schema = @Schema(allowableValues = {"pdf", "xlsx"}))
            @PathVariable String extension,
            HttpServletRequest http) {
        ArchivoGenerado archivo = generarReportes.planAmortizacion(
                id, FormatoReporte.desdeExtension(extension), extractorContexto.extraer(http));
        return respuestaDeDescarga(archivo);
    }

    @GetMapping("/{id}/pagos.{extension}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Descargar el historial de pagos (PDF o Excel)",
            description = "Genera en el servidor el historial de pagos del prestamo con encabezado "
                    + "y pie en todas las paginas. Rutas disponibles: pagos.pdf y pagos.xlsx. Un "
                    + "prestamo sin pagos produce un documento con el encabezado y la nota "
                    + "correspondiente. La descarga queda registrada en la bitacora.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Archivo del reporte",
                    content = @Content(mediaType = "application/octet-stream",
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "Formato de archivo no soportado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Prestamo inexistente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<byte[]> descargarPagos(
            @Parameter(description = "Identificador del prestamo", example = "1")
            @PathVariable Long id,
            @Parameter(description = "Formato del archivo", example = "xlsx",
                    schema = @Schema(allowableValues = {"pdf", "xlsx"}))
            @PathVariable String extension,
            HttpServletRequest http) {
        ArchivoGenerado archivo = generarReportes.historialPagos(
                id, FormatoReporte.desdeExtension(extension), extractorContexto.extraer(http));
        return respuestaDeDescarga(archivo);
    }

    // filename* (RFC 5987) conserva acentos para clientes modernos; no-store porque el
    // archivo lleva datos personales y no debe quedar en caches intermedias.
    private ResponseEntity<byte[]> respuestaDeDescarga(ArchivoGenerado archivo) {
        // Sin comillas, el valor no puede romper la cabecera.
        String nombre = archivo.nombre().replace("\"", "");
        String codificado = URLEncoder.encode(nombre, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archivo.tipoContenido()))
                .contentLength(archivo.tamanoEnBytes())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre
                        + "\"; filename*=UTF-8''" + codificado)
                .body(archivo.contenido());
    }
}
