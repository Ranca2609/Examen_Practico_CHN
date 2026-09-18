package gt.gob.chn.prestamos.domain.model.reporte;

import gt.gob.chn.prestamos.domain.model.Validaciones;

public record ParDato(String etiqueta, String valor) {

    public ParDato {
        etiqueta = Validaciones.exigirTexto(etiqueta, "etiqueta del dato del encabezado", 1, 60);
        // Un dato opcional ausente se imprime en blanco: es informacion que falta, no un error.
        valor = valor == null ? "" : valor.trim();
    }
}
