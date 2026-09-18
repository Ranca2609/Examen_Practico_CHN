package gt.gob.chn.prestamos.domain.model.reporte;

import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;
import java.util.Arrays;
import java.util.stream.Collectors;

public enum FormatoReporte {

    PDF("pdf", "application/pdf"),
    EXCEL("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final String extension;
    private final String tipoContenido;

    FormatoReporte(String extension, String tipoContenido) {
        this.extension = extension;
        this.tipoContenido = tipoContenido;
    }

    /** Sin el punto: "pdf", "xlsx". */
    public String extension() {
        return extension;
    }

    public String tipoContenido() {
        return tipoContenido;
    }

    /** Sin distinguir mayusculas; lo desconocido es error de validacion para responder 400 y no un 500. */
    public static FormatoReporte desdeExtension(String extension) {
        if (extension != null) {
            String pedida = extension.trim();
            for (FormatoReporte formato : values()) {
                if (formato.extension.equalsIgnoreCase(pedida)) {
                    return formato;
                }
            }
        }
        throw new ValidacionDominioException(
                "Formato de reporte invalido. Valores permitidos: " + extensionesPermitidas() + ".");
    }

    private static String extensionesPermitidas() {
        return Arrays.stream(values())
                .map(FormatoReporte::extension)
                .collect(Collectors.joining(", "));
    }
}
