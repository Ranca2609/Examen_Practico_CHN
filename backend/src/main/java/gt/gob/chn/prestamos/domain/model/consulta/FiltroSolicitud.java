package gt.gob.chn.prestamos.domain.model.consulta;

import gt.gob.chn.prestamos.domain.model.EstadoSolicitud;
import gt.gob.chn.prestamos.domain.model.TipoPrestamo;
import gt.gob.chn.prestamos.domain.model.Validaciones;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FiltroSolicitud(
        String busqueda,
        Long clienteId,
        EstadoSolicitud estado,
        TipoPrestamo tipoPrestamo,
        BigDecimal montoMinimo,
        BigDecimal montoMaximo,
        Integer plazoMinimo,
        Integer plazoMaximo,
        LocalDate fechaDesde,
        LocalDate fechaHasta,
        int pagina,
        int tamano) {

    public static final int TAMANO_MAXIMO = 100;

    private static final int BUSQUEDA_MAXIMA = 120;

    public FiltroSolicitud {
        busqueda = Validaciones.exigirTextoOpcional(busqueda, "busqueda", BUSQUEDA_MAXIMA);
        // Un rango invertido es un error del solicitante (400), no una busqueda sin resultados.
        Validaciones.exigirOrdenDeRango(montoMinimo, montoMaximo, "monto solicitado");
        Validaciones.exigirOrdenDeRango(plazoMinimo, plazoMaximo, "plazo en meses");
        Validaciones.exigirOrdenCronologico(fechaDesde, fechaHasta, "fecha de solicitud");
        Validaciones.exigirRango(pagina, "pagina", 0, Integer.MAX_VALUE);
        Validaciones.exigirRango(tamano, "tamano de pagina", 1, TAMANO_MAXIMO);
    }

    public static FiltroSolicitud de(Long clienteId, EstadoSolicitud estado, int pagina, int tamano) {
        return new FiltroSolicitud(null, clienteId, estado, null, null, null, null, null,
                null, null, pagina, tamano);
    }

    public boolean tieneFiltrosActivos() {
        return busqueda != null
                || clienteId != null
                || estado != null
                || tipoPrestamo != null
                || montoMinimo != null || montoMaximo != null
                || plazoMinimo != null || plazoMaximo != null
                || fechaDesde != null || fechaHasta != null;
    }
}
