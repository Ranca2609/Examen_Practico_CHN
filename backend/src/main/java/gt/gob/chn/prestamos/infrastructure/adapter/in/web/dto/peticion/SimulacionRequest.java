package gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.peticion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Schema(name = "SimulacionRequest", description = "Parametros para simular un prestamo")
public record SimulacionRequest(

        @NotNull(message = "El cliente es obligatorio")
        @Positive(message = "El identificador del cliente debe ser positivo")
        @Schema(description = "Identificador del cliente a evaluar", example = "1")
        Long clienteId,

        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "1000.00", message = "El monto minimo es de 1000.00")
        @DecimalMax(value = "5000000.00", message = "El monto maximo es de 5000000.00")
        @Digits(integer = 13, fraction = 2, message = "El monto admite hasta 2 decimales")
        @Schema(description = "Monto a simular en quetzales", example = "150000.00")
        BigDecimal monto,

        @NotNull(message = "El plazo en meses es obligatorio")
        @Min(value = 6, message = "El plazo minimo es de 6 meses")
        @Max(value = 360, message = "El plazo maximo es de 360 meses")
        @Schema(description = "Plazo a simular en meses", example = "60")
        Integer plazoMeses,

        @NotNull(message = "La tasa de interes anual es obligatoria")
        @DecimalMin(value = "0.01", message = "La tasa de interes anual minima es 0.01")
        @DecimalMax(value = "100.00", message = "La tasa de interes anual maxima es 100.00")
        @Digits(integer = 3, fraction = 2, message = "La tasa de interes admite hasta 2 decimales")
        @Schema(description = "Tasa de interes nominal anual en porcentaje", example = "12.50")
        BigDecimal tasaInteresAnual,

        @NotNull(message = "El ingreso mensual es obligatorio")
        @DecimalMin(value = "0.01", message = "El ingreso mensual debe ser mayor a cero")
        @Digits(integer = 13, fraction = 2, message = "El ingreso mensual admite hasta 2 decimales")
        @Schema(description = "Ingreso mensual declarado para evaluar el endeudamiento",
                example = "12500.00")
        BigDecimal ingresoMensual) {
}
