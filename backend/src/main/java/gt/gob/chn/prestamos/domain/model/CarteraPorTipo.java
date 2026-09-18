package gt.gob.chn.prestamos.domain.model;

import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record CarteraPorTipo(
        TipoPrestamo tipoPrestamo,
        long cantidadPrestamos,
        BigDecimal montoAprobado,
        BigDecimal saldoPendiente,
        BigDecimal totalRecuperado) {

    public CarteraPorTipo {
        Validaciones.exigirNoNulo(tipoPrestamo, "tipo de prestamo");
        if (cantidadPrestamos < 0) {
            throw new ValidacionDominioException("La cantidad de prestamos no puede ser negativa.");
        }
        montoAprobado = Validaciones.exigirNoNegativo(Montos.ceroSiNulo(montoAprobado), "monto aprobado");
        saldoPendiente = Validaciones.exigirNoNegativo(Montos.ceroSiNulo(saldoPendiente), "saldo pendiente");
        totalRecuperado = Validaciones.exigirNoNegativo(Montos.ceroSiNulo(totalRecuperado), "total recuperado");
    }

    public static CarteraPorTipo sinPrestamos(TipoPrestamo tipoPrestamo) {
        return new CarteraPorTipo(tipoPrestamo, 0L, Montos.CERO, Montos.CERO, Montos.CERO);
    }

    // Devuelve siempre todos los tipos del catalogo (ceros si faltan) para que el tablero no cambie de forma;
    // filas repetidas se suman porque conteos e importes son aditivos.
    public static List<CarteraPorTipo> completar(List<CarteraPorTipo> carteras) {
        Map<TipoPrestamo, CarteraPorTipo> porTipo = new EnumMap<>(TipoPrestamo.class);
        if (carteras != null) {
            for (CarteraPorTipo cartera : carteras) {
                Validaciones.exigirNoNulo(cartera, "cartera por tipo");
                porTipo.merge(cartera.tipoPrestamo(), cartera, CarteraPorTipo::sumar);
            }
        }

        List<CarteraPorTipo> completa = new ArrayList<>(TipoPrestamo.values().length);
        for (TipoPrestamo tipo : TipoPrestamo.values()) {
            CarteraPorTipo cartera = porTipo.get(tipo);
            completa.add(cartera != null ? cartera : sinPrestamos(tipo));
        }
        return List.copyOf(completa);
    }

    private CarteraPorTipo sumar(CarteraPorTipo otra) {
        return new CarteraPorTipo(
                tipoPrestamo,
                cantidadPrestamos + otra.cantidadPrestamos,
                montoAprobado.add(otra.montoAprobado),
                saldoPendiente.add(otra.saldoPendiente),
                totalRecuperado.add(otra.totalRecuperado));
    }
}
