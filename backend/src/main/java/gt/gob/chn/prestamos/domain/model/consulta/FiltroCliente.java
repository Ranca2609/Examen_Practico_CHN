package gt.gob.chn.prestamos.domain.model.consulta;

import gt.gob.chn.prestamos.domain.model.Validaciones;
import java.time.LocalDate;

public record FiltroCliente(
        String busqueda,
        LocalDate nacimientoDesde,
        LocalDate nacimientoHasta,
        LocalDate creacionDesde,
        LocalDate creacionHasta,
        Boolean activo,
        int pagina,
        int tamano) {

    public static final int TAMANO_MAXIMO = 100;

    private static final int BUSQUEDA_MAXIMA = 120;

    public FiltroCliente {
        busqueda = Validaciones.exigirTextoOpcional(busqueda, "busqueda", BUSQUEDA_MAXIMA);
        // Un rango invertido es un error del solicitante (400), no una busqueda sin resultados.
        Validaciones.exigirOrdenCronologico(nacimientoDesde, nacimientoHasta, "nacimiento");
        Validaciones.exigirOrdenCronologico(creacionDesde, creacionHasta, "creacion");
        Validaciones.exigirRango(pagina, "pagina", 0, Integer.MAX_VALUE);
        Validaciones.exigirRango(tamano, "tamano de pagina", 1, TAMANO_MAXIMO);
    }

    public static FiltroCliente de(String busqueda, int pagina, int tamano) {
        return new FiltroCliente(busqueda, null, null, null, null, null, pagina, tamano);
    }

    public boolean tieneFiltrosActivos() {
        return busqueda != null
                || nacimientoDesde != null || nacimientoHasta != null
                || creacionDesde != null || creacionHasta != null
                || activo != null;
    }
}
