package gt.gob.chn.prestamos.domain.model.reporte;

import gt.gob.chn.prestamos.domain.model.Validaciones;
import java.util.List;

public record FilaReporte(List<ValorCelda> celdas) {

    public FilaReporte {
        Validaciones.exigirNoNulo(celdas, "celdas de la fila");
        // List.copyOf tambien rechaza celdas nulas, que el generador veria como NPE al imprimir.
        celdas = List.copyOf(celdas);
    }

    public static FilaReporte de(ValorCelda... celdas) {
        return new FilaReporte(List.of(celdas));
    }
}
