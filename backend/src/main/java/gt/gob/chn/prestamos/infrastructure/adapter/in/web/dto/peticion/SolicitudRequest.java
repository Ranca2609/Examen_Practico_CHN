package gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.peticion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(name = "SolicitudRequest", description = "Datos para crear una solicitud de prestamo")
public record SolicitudRequest(

        @NotNull(message = "El cliente es obligatorio")
        @Positive(message = "El identificador del cliente debe ser positivo")
        @Schema(description = "Identificador del cliente solicitante", example = "1")
        Long clienteId,

        @NotNull(message = "El monto solicitado es obligatorio")
        @DecimalMin(value = "1000.00", message = "El monto solicitado minimo es de 1000.00")
        @DecimalMax(value = "5000000.00", message = "El monto solicitado maximo es de 5000000.00")
        @Digits(integer = 13, fraction = 2, message = "El monto solicitado admite hasta 2 decimales")
        @Schema(description = "Monto solicitado en quetzales", example = "150000.00")
        BigDecimal montoSolicitado,

        @NotNull(message = "El plazo en meses es obligatorio")
        @Min(value = 6, message = "El plazo minimo es de 6 meses")
        @Max(value = 360, message = "El plazo maximo es de 360 meses")
        @Schema(description = "Plazo solicitado en meses", example = "60")
        Integer plazoMeses,

        @NotNull(message = "La tasa de interes anual es obligatoria")
        @DecimalMin(value = "0.01", message = "La tasa de interes anual minima es 0.01")
        @DecimalMax(value = "100.00", message = "La tasa de interes anual maxima es 100.00")
        @Digits(integer = 3, fraction = 2, message = "La tasa de interes admite hasta 2 decimales")
        @Schema(description = "Tasa de interes nominal anual en porcentaje", example = "12.50")
        BigDecimal tasaInteresAnual,

        // String y no enum: un valor invalido responde 400 con los permitidos, no el error generico de Jackson.
        @NotBlank(message = "El tipo de prestamo es obligatorio")
        @Schema(description = "Tipo de prestamo",
                allowableValues = {"PERSONAL", "HIPOTECARIO", "VEHICULAR", "EMPRESARIAL", "EDUCATIVO"},
                example = "PERSONAL")
        String tipoPrestamo,

        @NotBlank(message = "El destino del prestamo es obligatorio")
        @Size(min = 5, max = 200, message = "El destino debe tener entre 5 y 200 caracteres")
        @Schema(description = "Destino o finalidad del financiamiento",
                example = "Remodelacion de vivienda familiar")
        String destino,

        @NotNull(message = "El ingreso mensual declarado es obligatorio")
        @DecimalMin(value = "0.01", message = "El ingreso mensual declarado debe ser mayor a cero")
        @Digits(integer = 13, fraction = 2, message = "El ingreso mensual admite hasta 2 decimales")
        @Schema(description = "Ingreso mensual declarado por el cliente", example = "12500.00")
        BigDecimal ingresoMensualDeclarado,

        @Size(max = 500, message = "Las observaciones no pueden exceder 500 caracteres")
        @Schema(description = "Observaciones del asesor que captura la solicitud",
                example = "Cliente con historial crediticio favorable")
        String observaciones) {
}
