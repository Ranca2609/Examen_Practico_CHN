package gt.gob.chn.prestamos.domain.model.consulta;

import gt.gob.chn.prestamos.domain.model.Validaciones;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FiltroPago(
        String busqueda,
        Long prestamoId,
        Long clienteId,
        BigDecimal montoMinimo,
        BigDecimal montoMaximo,
        LocalDate fechaDesde,
        LocalDate fechaHasta,
        String usuarioRegistro,
        int pagina,
        int tamano) {

    public static final int TAMANO_MAXIMO = 100;

    private static final int BUSQUEDA_MAXIMA = 120;

    // Ancho de la columna de usuario en la base.
    private static final int USUARIO_MAXIMO = 50;

    public FiltroPago {
        busqueda = Validaciones.exigirTextoOpcional(busqueda, "busqueda", BUSQUEDA_MAXIMA);
        usuarioRegistro = Validaciones.exigirTextoOpcional(
                usuarioRegistro, "usuario de registro", USUARIO_MAXIMO);
        // Un rango invertido es un error del solicitante (400), no una busqueda sin resultados.
        Validaciones.exigirOrdenDeRango(montoMinimo, montoMaximo, "monto del pago");
        Validaciones.exigirOrdenCronologico(fechaDesde, fechaHasta, "fecha de pago");
        Validaciones.exigirRango(pagina, "pagina", 0, Integer.MAX_VALUE);
        Validaciones.exigirRango(tamano, "tamano de pagina", 1, TAMANO_MAXIMO);
    }

    public static FiltroPago de(Long prestamoId, Long clienteId, int pagina, int tamano) {
        return new FiltroPago(null, prestamoId, clienteId, null, null, null, null, null,
                pagina, tamano);
    }

    public boolean tieneFiltrosActivos() {
        return busqueda != null
                || prestamoId != null
                || clienteId != null
                || montoMinimo != null || montoMaximo != null
                || fechaDesde != null || fechaHasta != null
                || usuarioRegistro != null;
    }
}
