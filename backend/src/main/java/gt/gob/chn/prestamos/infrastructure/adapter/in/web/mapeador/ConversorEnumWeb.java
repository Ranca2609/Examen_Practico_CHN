package gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador;

import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;
import gt.gob.chn.prestamos.domain.model.EstadoPrestamo;
import gt.gob.chn.prestamos.domain.model.EstadoSolicitud;
import gt.gob.chn.prestamos.domain.model.TipoPrestamo;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class ConversorEnumWeb {

    private ConversorEnumWeb() {
    }

    public static EstadoSolicitud aEstadoSolicitud(String valor) {
        if (esVacio(valor)) {
            return null;
        }
        return convertir(EstadoSolicitud.class, valor, "Estado invalido");
    }

    public static EstadoPrestamo aEstadoPrestamo(String valor) {
        if (esVacio(valor)) {
            return null;
        }
        return convertir(EstadoPrestamo.class, valor, "Estado invalido");
    }

    public static TipoPrestamo aTipoPrestamo(String valor) {
        if (esVacio(valor)) {
            throw new ValidacionDominioException("El tipo de prestamo es obligatorio");
        }
        return convertir(TipoPrestamo.class, valor, "Tipo de prestamo invalido");
    }

    public static TipoPrestamo aTipoPrestamoOpcional(String valor) {
        if (esVacio(valor)) {
            return null;
        }
        return convertir(TipoPrestamo.class, valor, "Tipo de prestamo invalido");
    }

    // Conversion manual: el 400 enumera los valores permitidos en vez del mensaje opaco de Spring/Jackson.
    private static <E extends Enum<E>> E convertir(Class<E> tipo, String valor, String prefijo) {
        try {
            return Enum.valueOf(tipo, valor.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidacionDominioException(
                    prefijo + ". Valores permitidos: " + valoresPermitidos(tipo));
        }
    }

    private static <E extends Enum<E>> String valoresPermitidos(Class<E> tipo) {
        return Arrays.stream(tipo.getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
