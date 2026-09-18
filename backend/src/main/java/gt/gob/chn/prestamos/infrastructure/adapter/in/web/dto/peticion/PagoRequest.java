package gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.peticion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(name = "PagoRequest", description = "Datos para registrar un pago sobre un prestamo")
public record PagoRequest(

        @NotNull(message = "El prestamo es obligatorio")
        @Positive(message = "El identificador del prestamo debe ser positivo")
        @Schema(description = "Identificador del prestamo a abonar", example = "1")
        Long prestamoId,

        @NotNull(message = "El monto del pago es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto del pago debe ser mayor a cero")
        @Digits(integer = 13, fraction = 2, message = "El monto del pago admite hasta 2 decimales")
        @Schema(description = "Monto del pago en quetzales; no puede exceder el saldo pendiente",
                example = "2670.35")
        BigDecimal monto,

        @Size(max = 300, message = "Las observaciones no pueden exceder 300 caracteres")
        @Schema(description = "Observaciones del cajero",
                example = "Pago de cuota correspondiente a enero")
        String observaciones) {
}
