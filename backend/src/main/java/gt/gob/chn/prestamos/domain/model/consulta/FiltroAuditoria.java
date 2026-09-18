package gt.gob.chn.prestamos.domain.model.consulta;

import gt.gob.chn.prestamos.domain.model.Validaciones;
import java.time.LocalDate;

public record FiltroAuditoria(
        String busqueda,
        String usuario,
        String accion,
        String entidad,
        LocalDate fechaDesde,
        LocalDate fechaHasta,
        int pagina,
        int tamano) {

    public static final int TAMANO_MAXIMO = 100;

    private static final int BUSQUEDA_MAXIMA = 120;

    // Ancho de las columnas usuario, accion y entidad en la bitacora.
    private static final int ETIQUETA_MAXIMA = 50;

    public FiltroAuditoria {
        busqueda = Validaciones.exigirTextoOpcional(busqueda, "busqueda", BUSQUEDA_MAXIMA);
        usuario = Validaciones.exigirTextoOpcional(usuario, "usuario", ETIQUETA_MAXIMA);
        accion = Validaciones.exigirTextoOpcional(accion, "accion", ETIQUETA_MAXIMA);
        entidad = Validaciones.exigirTextoOpcional(entidad, "entidad", ETIQUETA_MAXIMA);
        // Un rango invertido es un error del solicitante (400), no una busqueda sin resultados.
        Validaciones.exigirOrdenCronologico(fechaDesde, fechaHasta, "fecha de la bitacora");
        Validaciones.exigirRango(pagina, "pagina", 0, Integer.MAX_VALUE);
        Validaciones.exigirRango(tamano, "tamano de pagina", 1, TAMANO_MAXIMO);
    }

    public static FiltroAuditoria de(int pagina, int tamano) {
        return new FiltroAuditoria(null, null, null, null, null, null, pagina, tamano);
    }

    public boolean tieneFiltrosActivos() {
        return busqueda != null
                || usuario != null
                || accion != null
                || entidad != null
                || fechaDesde != null || fechaHasta != null;
    }
}
