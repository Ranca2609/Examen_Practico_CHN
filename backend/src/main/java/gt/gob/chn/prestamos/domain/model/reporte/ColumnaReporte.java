package gt.gob.chn.prestamos.domain.model.reporte;

import gt.gob.chn.prestamos.domain.model.Validaciones;

public record ColumnaReporte(String titulo, Alineacion alineacion) {

    public enum Alineacion {
        IZQUIERDA,
        CENTRO,
        DERECHA
    }

    public ColumnaReporte {
        titulo = Validaciones.exigirTexto(titulo, "titulo de la columna", 1, 60);
        // Izquierda por defecto: el adaptador no tiene que defenderse de un nulo.
        alineacion = alineacion == null ? Alineacion.IZQUIERDA : alineacion;
    }

    public static ColumnaReporte izquierda(String titulo) {
        return new ColumnaReporte(titulo, Alineacion.IZQUIERDA);
    }

    public static ColumnaReporte centro(String titulo) {
        return new ColumnaReporte(titulo, Alineacion.CENTRO);
    }

    public static ColumnaReporte derecha(String titulo) {
        return new ColumnaReporte(titulo, Alineacion.DERECHA);
    }
}
