package gt.gob.chn.prestamos.infrastructure.adapter.in.web.controlador;

import gt.gob.chn.prestamos.domain.model.Cliente;
import gt.gob.chn.prestamos.domain.model.consulta.FiltroCliente;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.domain.port.in.ConsultarPrestamosUseCase;
import gt.gob.chn.prestamos.domain.port.in.GestionarClientesUseCase;
import gt.gob.chn.prestamos.domain.port.in.GestionarSolicitudesUseCase;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.ExtractorContextoOperacion;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.peticion.ActualizarClienteRequest;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.peticion.ClienteRequest;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.ClienteResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.ErrorResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.PaginaResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.PrestamoResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.SolicitudResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador.MapeadorWebCliente;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador.MapeadorWebPagina;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador.MapeadorWebPrestamo;
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
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clientes")
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Clientes", description = "Registro, consulta y mantenimiento de clientes")
public class ClienteControlador {

    private final GestionarClientesUseCase gestionarClientes;
    private final GestionarSolicitudesUseCase gestionarSolicitudes;
    private final ConsultarPrestamosUseCase consultarPrestamos;
    private final ExtractorContextoOperacion extractorContexto;

    public ClienteControlador(GestionarClientesUseCase gestionarClientes,
                              GestionarSolicitudesUseCase gestionarSolicitudes,
                              ConsultarPrestamosUseCase consultarPrestamos,
                              ExtractorContextoOperacion extractorContexto) {
        this.gestionarClientes = gestionarClientes;
        this.gestionarSolicitudes = gestionarSolicitudes;
        this.consultarPrestamos = consultarPrestamos;
        this.extractorContexto = extractorContexto;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar clientes",
            description = "Listado paginado. Filtros opcionales: texto libre (nombre, apellido, DPI, "
                    + "correo o telefono), rango de fecha de nacimiento, rango de fecha de registro "
                    + "y estado activo. Todos son combinables y se resuelven en la base de datos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de clientes"),
            @ApiResponse(responseCode = "400", description = "Parametros de paginacion invalidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PaginaResponse<ClienteResponse>> listar(
            @Parameter(description = "Texto libre a buscar en nombre, apellido, DPI, correo o telefono",
                    example = "Ramirez")
            @RequestParam(required = false) String busqueda,
            @Parameter(description = "Nacidos desde esta fecha, inclusive (aaaa-MM-dd)",
                    example = "1990-01-01")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate nacimientoDesde,
            @Parameter(description = "Nacidos hasta esta fecha, inclusive (aaaa-MM-dd)",
                    example = "2000-12-31")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate nacimientoHasta,
            @Parameter(description = "Registrados desde esta fecha, inclusive (aaaa-MM-dd)",
                    example = "2026-01-01")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate creacionDesde,
            @Parameter(description = "Registrados hasta esta fecha, inclusive (aaaa-MM-dd)",
                    example = "2026-01-31")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate creacionHasta,
            @Parameter(description = "Filtrar por estado del cliente; omitirlo devuelve activos e inactivos",
                    example = "true")
            @RequestParam(required = false) Boolean activo,
            @Parameter(description = "Numero de pagina (base 0)", example = "0")
            @RequestParam(defaultValue = "0") @Min(value = 0,
                    message = "La pagina debe ser mayor o igual a 0") int pagina,
            @Parameter(description = "Elementos por pagina", example = "10")
            @RequestParam(defaultValue = "10") @Min(value = 1,
                    message = "El tamano debe ser mayor o igual a 1")
            @Max(value = 100, message = "El tamano no puede exceder 100") int tamano) {

        FiltroCliente filtro = new FiltroCliente(busqueda, nacimientoDesde, nacimientoHasta,
                creacionDesde, creacionHasta, activo, pagina, tamano);
        PaginaDominio<Cliente> resultado = gestionarClientes.listar(filtro);
        return ResponseEntity.ok(
                MapeadorWebPagina.convertir(resultado, MapeadorWebCliente::aRespuesta));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ANALISTA')")
    @Operation(summary = "Registrar cliente",
            description = "Crea un cliente. El DPI y el correo electronico deben ser unicos")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cliente creado"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rol sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "DPI o correo ya registrados",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClienteResponse> registrar(@Valid @RequestBody ClienteRequest peticion,
                                                      HttpServletRequest http) {
        Cliente cliente = gestionarClientes.registrar(
                MapeadorWebCliente.aComando(peticion), extractorContexto.extraer(http));
        return ResponseEntity
                .created(URI.create("/api/v1/clientes/" + cliente.getId()))
                .body(MapeadorWebCliente.aRespuesta(cliente));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consultar cliente", description = "Devuelve un cliente por su identificador")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cliente encontrado"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cliente inexistente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClienteResponse> obtener(
            @Parameter(description = "Identificador del cliente", example = "1")
            @PathVariable Long id) {
        return ResponseEntity.ok(MapeadorWebCliente.aRespuesta(gestionarClientes.obtener(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ANALISTA')")
    @Operation(summary = "Actualizar cliente",
            description = "Modifica los datos de contacto. El DPI y la fecha de nacimiento no son editables")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cliente actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rol sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cliente inexistente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "El correo ya pertenece a otro cliente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClienteResponse> actualizar(
            @Parameter(description = "Identificador del cliente", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody ActualizarClienteRequest peticion,
            HttpServletRequest http) {
        Cliente cliente = gestionarClientes.actualizar(
                id, MapeadorWebCliente.aComando(peticion), extractorContexto.extraer(http));
        return ResponseEntity.ok(MapeadorWebCliente.aRespuesta(cliente));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar cliente",
            description = "Borra el cliente y su historial (pagos, prestamos y solicitudes) "
                    + "en una sola transaccion. Operacion exclusiva del rol ADMIN")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cliente eliminado"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rol sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cliente inexistente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "El cliente tiene operaciones que impiden el borrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> eliminar(
            @Parameter(description = "Identificador del cliente", example = "1")
            @PathVariable Long id,
            HttpServletRequest http) {
        gestionarClientes.eliminar(id, extractorContexto.extraer(http));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/solicitudes")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Solicitudes del cliente",
            description = "Historial completo de solicitudes del cliente, sin paginar")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitudes del cliente"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cliente inexistente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<SolicitudResponse>> listarSolicitudes(
            @Parameter(description = "Identificador del cliente", example = "1")
            @PathVariable Long id) {
        List<SolicitudResponse> solicitudes = gestionarSolicitudes.listarPorCliente(id).stream()
                .map(MapeadorWebSolicitud::aRespuesta)
                .toList();
        return ResponseEntity.ok(solicitudes);
    }

    @GetMapping("/{id}/prestamos")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Prestamos del cliente",
            description = "Historial completo de prestamos del cliente, sin paginar")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Prestamos del cliente"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cliente inexistente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<PrestamoResponse>> listarPrestamos(
            @Parameter(description = "Identificador del cliente", example = "1")
            @PathVariable Long id) {
        List<PrestamoResponse> prestamos = consultarPrestamos.listarPorCliente(id).stream()
                .map(MapeadorWebPrestamo::aRespuesta)
                .toList();
        return ResponseEntity.ok(prestamos);
    }
}
