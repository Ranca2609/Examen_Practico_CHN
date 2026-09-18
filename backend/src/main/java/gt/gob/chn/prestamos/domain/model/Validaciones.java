package gt.gob.chn.prestamos.domain.model;

import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;
import java.math.BigDecimal;
import java.util.regex.Pattern;

public final class Validaciones {

    /** Formato de correo deliberadamente simple: filtra errores de captura sin rechazar direcciones validas. */
    private static final Pattern PATRON_CORREO =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Pattern PATRON_DIGITOS = Pattern.compile("^[0-9]+$");

    private Validaciones() {
    }

    public static <T> T exigirNoNulo(T valor, String campo) {
        if (valor == null) {
            throw new ValidacionDominioException("El campo " + campo + " es obligatorio.");
        }
        return valor;
    }

    public static String exigirTexto(String valor, String campo, int minimo, int maximo) {
        if (valor == null || valor.isBlank()) {
            throw new ValidacionDominioException("El campo " + campo + " es obligatorio.");
        }
        String limpio = valor.trim();
        if (limpio.length() < minimo || limpio.length() > maximo) {
            throw new ValidacionDominioException(
                    "El campo " + campo + " debe tener entre " + minimo + " y " + maximo + " caracteres.");
        }
        return limpio;
    }

    /** Null o vacio se normalizan a null para no guardar cadenas en blanco. */
    public static String exigirTextoOpcional(String valor, String campo, int maximo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpio = valor.trim();
        if (limpio.length() > maximo) {
            throw new ValidacionDominioException(
                    "El campo " + campo + " no puede exceder " + maximo + " caracteres.");
        }
        return limpio;
    }

    public static BigDecimal exigirRango(BigDecimal valor, String campo, BigDecimal minimo, BigDecimal maximo) {
        exigirNoNulo(valor, campo);
        if (valor.compareTo(minimo) < 0 || valor.compareTo(maximo) > 0) {
            throw new ValidacionDominioException("El campo " + campo + " debe estar entre "
                    + minimo.toPlainString() + " y " + maximo.toPlainString() + ".");
        }
        return valor;
    }

    public static int exigirRango(int valor, String campo, int minimo, int maximo) {
        if (valor < minimo || valor > maximo) {
            throw new ValidacionDominioException(
                    "El campo " + campo + " debe estar entre " + minimo + " y " + maximo + ".");
        }
        return valor;
    }

    public static BigDecimal exigirPositivo(BigDecimal valor, String campo) {
        exigirNoNulo(valor, campo);
        if (valor.signum() <= 0) {
            throw new ValidacionDominioException("El campo " + campo + " debe ser mayor que cero.");
        }
        return valor;
    }

    public static BigDecimal exigirNoNegativo(BigDecimal valor, String campo) {
        exigirNoNulo(valor, campo);
        if (valor.signum() < 0) {
            throw new ValidacionDominioException("El campo " + campo + " no puede ser negativo.");
        }
        return valor;
    }

    public static String exigirDigitos(String valor, String campo, int cantidad) {
        if (valor == null || valor.isBlank()) {
            throw new ValidacionDominioException("El campo " + campo + " es obligatorio.");
        }
        String limpio = valor.trim();
        if (limpio.length() != cantidad || !PATRON_DIGITOS.matcher(limpio).matches()) {
            throw new ValidacionDominioException(
                    "El campo " + campo + " debe contener exactamente " + cantidad + " digitos numericos.");
        }
        return limpio;
    }

    /** Devuelve el correo en minusculas para que la unicidad no dependa de mayusculas. */
    public static String exigirCorreo(String valor, String campo, int maximo) {
        if (valor == null || valor.isBlank()) {
            throw new ValidacionDominioException("El campo " + campo + " es obligatorio.");
        }
        String limpio = valor.trim().toLowerCase();
        if (limpio.length() > maximo) {
            throw new ValidacionDominioException(
                    "El campo " + campo + " no puede exceder " + maximo + " caracteres.");
        }
        if (!PATRON_CORREO.matcher(limpio).matches()) {
            throw new ValidacionDominioException("El campo " + campo + " no tiene un formato valido.");
        }
        return limpio;
    }

    // Extremos opcionales: solo se comparan si ambos vienen informados.
    // Comparable<? super T> porque LocalDate se compara contra ChronoLocalDate, no contra si mismo.
    public static <T extends Comparable<? super T>> void exigirOrdenCronologico(
            T desde, T hasta, String queRango) {
        if (desde != null && hasta != null && desde.compareTo(hasta) > 0) {
            throw new ValidacionDominioException("La fecha inicial del rango de " + queRango
                    + " no puede ser posterior a la fecha final.");
        }
    }

    public static void exigirOrdenDeRango(BigDecimal minimo, BigDecimal maximo, String queRango) {
        if (minimo != null && maximo != null && minimo.compareTo(maximo) > 0) {
            throw new ValidacionDominioException("El monto minimo del rango de " + queRango
                    + " no puede ser mayor que el monto maximo.");
        }
    }

    public static void exigirOrdenDeRango(Integer minimo, Integer maximo, String queRango) {
        if (minimo != null && maximo != null && minimo.compareTo(maximo) > 0) {
            throw new ValidacionDominioException("El valor minimo del rango de " + queRango
                    + " no puede ser mayor que el valor maximo.");
        }
    }
}
