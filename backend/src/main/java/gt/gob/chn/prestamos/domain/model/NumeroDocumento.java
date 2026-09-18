package gt.gob.chn.prestamos.domain.model;

import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record NumeroDocumento(TipoDocumento tipo, String agencia, int anio, long correlativo) {

    /** TT-AAA-AAAA- (12) + correlativo de hasta 9 digitos + -D (2). */
    public static final int LONGITUD_MAXIMA = 23;

    private static final long CORRELATIVO_MAXIMO = 999_999_999L;
    private static final Pattern PATRON_AGENCIA = Pattern.compile("^[0-9]{3}$");
    // TT-AAA-AAAA-NNNNNN-D, p. ej. PR-001-2026-000004-3.
    private static final Pattern PATRON =
            Pattern.compile("^([A-Z]{2})-([0-9]{3})-([0-9]{4})-([0-9]{6,9})-([0-9])$");

    public NumeroDocumento {
        Validaciones.exigirNoNulo(tipo, "tipo de documento");
        if (agencia == null || !PATRON_AGENCIA.matcher(agencia).matches()) {
            throw new ValidacionDominioException("El codigo de agencia debe tener exactamente 3 digitos.");
        }
        Validaciones.exigirRango(anio, "anio del documento", 2000, 9999);
        if (correlativo < 1 || correlativo > CORRELATIVO_MAXIMO) {
            throw new ValidacionDominioException(
                    "El correlativo del documento debe estar entre 1 y " + CORRELATIVO_MAXIMO + ".");
        }
    }

    public static String exigir(String texto, TipoDocumento esperado) {
        return interpretar(texto, esperado).valor();
    }

    public static NumeroDocumento interpretar(String texto, TipoDocumento esperado) {
        Validaciones.exigirNoNulo(esperado, "tipo de documento");
        String campo = "numero de " + esperado.descripcion();
        String limpio = Validaciones.exigirTexto(texto, campo, 1, LONGITUD_MAXIMA);

        Matcher partes = PATRON.matcher(limpio);
        if (!partes.matches()) {
            throw new ValidacionDominioException("El " + campo + " no tiene el formato "
                    + esperado.codigo() + "-AAA-AAAA-NNNNNN-D.");
        }
        if (!esperado.codigo().equals(partes.group(1))) {
            throw new ValidacionDominioException("El " + campo + " debe iniciar con " + esperado.codigo() + ".");
        }
        NumeroDocumento numero = new NumeroDocumento(esperado, partes.group(2),
                Integer.parseInt(partes.group(3)), Long.parseLong(partes.group(4)));
        if (numero.digitoVerificador() != partes.group(5).charAt(0) - '0') {
            throw new ValidacionDominioException("El " + campo + " tiene un digito verificador incorrecto.");
        }
        // Un correlativo con ceros de mas (0000004) seria otro texto para el mismo documento.
        if (!numero.valor().equals(limpio)) {
            throw new ValidacionDominioException("El " + campo + " no tiene el formato "
                    + esperado.codigo() + "-AAA-AAAA-NNNNNN-D.");
        }
        return numero;
    }

    public String valor() {
        return String.format(Locale.ROOT, "%s-%s-%04d-%06d-%d",
                tipo.codigo(), agencia, anio, correlativo, digitoVerificador());
    }

    public int digitoVerificador() {
        return luhn(cuerpoNumerico());
    }

    @Override
    public String toString() {
        return valor();
    }

    // Luhn sobre todo el numero con las letras en base 36 (como IBAN): cambiar el prefijo invalida el verificador.
    private String cuerpoNumerico() {
        StringBuilder digitos = new StringBuilder();
        for (char letra : tipo.codigo().toCharArray()) {
            digitos.append(Character.getNumericValue(letra)); // A=10 ... Z=35
        }
        return digitos.append(agencia)
                .append(String.format(Locale.ROOT, "%04d%06d", anio, correlativo))
                .toString();
    }

    // Se duplica desde el ultimo digito porque el verificador ocupara la posicion de la derecha.
    static int luhn(String digitos) {
        int suma = 0;
        boolean duplicar = true;
        for (int i = digitos.length() - 1; i >= 0; i--) {
            int digito = digitos.charAt(i) - '0';
            if (duplicar) {
                digito *= 2;
                if (digito > 9) {
                    digito -= 9;
                }
            }
            suma += digito;
            duplicar = !duplicar;
        }
        return (10 - suma % 10) % 10;
    }
}
