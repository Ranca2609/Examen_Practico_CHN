package gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.peticion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(name = "AprobarSolicitudRequest",
        description = "Condiciones de aprobacion; los campos omitidos toman el valor solicitado")
public record AprobarSolicitudRequest(

        @DecimalMin(value = "0.01", message = "El monto aprobado debe ser mayor a cero")
        @DecimalMax(value = "5000000.00", message = "El monto aprobado maximo es de 5000000.00")
        @Digits(integer = 13, fraction = 2, message = "El monto aprobado admite hasta 2 decimales")
        @Schema(description = "Monto aprobado; no puede exceder el monto solicitado",
                example = "120000.00")
        BigDecimal montoAprobado,

        @Min(value = 6, message = "El plazo aprobado minimo es de 6 meses")
        @Max(value = 360, message = "El plazo aprobado maximo es de 360 meses")
        @Schema(description = "Plazo aprobado en meses", example = "48")
        Integer plazoAprobadoMeses,

        @DecimalMin(value = "0.01", message = "La tasa aprobada minima es 0.01")
        @DecimalMax(value = "100.00", message = "La tasa aprobada maxima es 100.00")
        @Digits(integer = 3, fraction = 2, message = "La tasa aprobada admite hasta 2 decimales")
        @Schema(description = "Tasa de interes anual aprobada en porcentaje", example = "11.75")
        BigDecimal tasaAprobada,

        @Size(max = 500, message = "El motivo no puede exceder 500 caracteres")
        @Schema(description = "Motivo o justificacion de la aprobacion",
                example = "Capacidad de pago suficiente y garantia verificada")
        String motivo) {
}
