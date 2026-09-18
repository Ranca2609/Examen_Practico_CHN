package gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(name = "ResumenResponse", description = "Indicadores generales de la cartera")
public record ResumenResponse(

        @Schema(description = "Total de clientes registrados", example = "125")
        long totalClientes,

        @Schema(description = "Solicitudes pendientes de resolucion", example = "8")
        long solicitudesEnProceso,

        @Schema(description = "Solicitudes aprobadas", example = "64")
        long solicitudesAprobadas,

        @Schema(description = "Solicitudes rechazadas", example = "12")
        long solicitudesRechazadas,

        @Schema(description = "Prestamos vigentes", example = "51")
        long prestamosVigentes,

        @Schema(description = "Prestamos liquidados", example = "13")
        long prestamosLiquidados,

        @Schema(description = "Suma de los montos aprobados", example = "7850000.00")
        BigDecimal montoTotalAprobado,

        @Schema(description = "Saldo pendiente total de la cartera", example = "6120450.75")
        BigDecimal saldoPendienteTotal,

        @Schema(description = "Total recuperado mediante pagos", example = "1729549.25")
        BigDecimal totalRecuperado,

        @Schema(description = "Cartera por tipo de prestamo: siempre los 5 tipos del catalogo, "
                + "en orden fijo y con ceros para los tipos sin prestamos")
        List<CarteraTipoResponse> carteraPorTipo,

        @Schema(description = "Recaudacion de los ultimos 12 meses en orden ascendente, terminando "
                + "en el mes en curso (hora de Guatemala), con ceros en los meses sin pagos")
        List<RecaudacionMensualResponse> recaudacionMensual) {
}
