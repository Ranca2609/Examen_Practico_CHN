package gt.gob.chn.prestamos.infrastructure.adapter.in.web.controlador;

import gt.gob.chn.prestamos.domain.port.in.ConsultarResumenUseCase;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.ErrorResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.ResumenResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador.MapeadorWebResumen;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/resumen")
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Resumen", description = "Indicadores generales de la cartera de prestamos")
public class ResumenControlador {

    private final ConsultarResumenUseCase consultarResumen;

    public ResumenControlador(ConsultarResumenUseCase consultarResumen) {
        this.consultarResumen = consultarResumen;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consultar resumen",
            description = "Totales de clientes, solicitudes, prestamos y montos de la cartera, mas dos "
                    + "series para las graficas del tablero: carteraPorTipo (siempre los 5 tipos de "
                    + "prestamo en orden fijo, con ceros si un tipo no tiene prestamos) y "
                    + "recaudacionMensual (12 meses contiguos en orden ascendente que terminan en el "
                    + "mes en curso, hora de Guatemala, con ceros en los meses sin pagos)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Indicadores generales"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ResumenResponse> obtener() {
        return ResponseEntity.ok(MapeadorWebResumen.aRespuesta(consultarResumen.obtener()));
    }
}
