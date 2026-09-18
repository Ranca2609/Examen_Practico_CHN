package gt.gob.chn.prestamos.domain.model.consulta;

import gt.gob.chn.prestamos.domain.model.EstadoPrestamo;
import gt.gob.chn.prestamos.domain.model.Validaciones;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FiltroPrestamo(
        String busqueda,
        Long clienteId,
        EstadoPrestamo estado,
        BigDecimal montoMinimo,
        BigDecimal montoMaximo,
        BigDecimal saldoMinimo,
        BigDecimal saldoMaximo,
        LocalDate desembolsoDesde,
        LocalDate desembolsoHasta,
        LocalDate vencimientoDesde,
        LocalDate vencimientoHasta,
        int pagina,
        int tamano) {

    public static final int TAMANO_MAXIMO = 100;

    private static final int BUSQUEDA_MAXIMA = 120;

    public FiltroPrestamo {
        busqueda = Validaciones.exigirTextoOpcional(busqueda, "busqueda", BUSQUEDA_MAXIMA);
        // Un rango invertido es un error del solicitante (400), no una busqueda sin resultados.
        Validaciones.exigirOrdenDeRango(montoMinimo, montoMaximo, "monto aprobado");
        Validaciones.exigirOrdenDeRango(saldoMinimo, saldoMaximo, "saldo pendiente");
        Validaciones.exigirOrdenCronologico(desembolsoDesde, desembolsoHasta, "desembolso");
        Validaciones.exigirOrdenCronologico(vencimientoDesde, vencimientoHasta, "vencimiento");
        Validaciones.exigirRango(pagina, "pagina", 0, Integer.MAX_VALUE);
        Validaciones.exigirRango(tamano, "tamano de pagina", 1, TAMANO_MAXIMO);
    }

    public static FiltroPrestamo de(Long clienteId, EstadoPrestamo estado, int pagina, int tamano) {
        return new FiltroPrestamo(null, clienteId, estado, null, null, null, null,
                null, null, null, null, pagina, tamano);
    }

    public boolean tieneFiltrosActivos() {
        return busqueda != null
                || clienteId != null
                || estado != null
                || montoMinimo != null || montoMaximo != null
                || saldoMinimo != null || saldoMaximo != null
                || desembolsoDesde != null || desembolsoHasta != null
                || vencimientoDesde != null || vencimientoHasta != null;
    }

    // El saldo es una columna calculada no mapeada en PrestamoEntidad: el adaptador filtra
    // por la expresion equivalente (montoTotalAPagar - totalPagado).
    public boolean tieneRangoDeSaldo() {
        return saldoMinimo != null || saldoMaximo != null;
    }
}
