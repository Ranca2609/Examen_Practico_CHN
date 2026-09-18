package gt.gob.chn.prestamos.domain.model;

import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;

public record TokenAcceso(
        String token,
        String tipo,
        long expiraEnSegundos,
        String username,
        String nombreCompleto,
        Rol rol) {

    public static final String TIPO_BEARER = "Bearer";

    public TokenAcceso {
        Validaciones.exigirTexto(token, "token", 10, 4096);
        tipo = (tipo == null || tipo.isBlank()) ? TIPO_BEARER : tipo.trim();
        if (expiraEnSegundos <= 0) {
            throw new ValidacionDominioException("La vigencia del token debe ser mayor que cero.");
        }
        username = Validaciones.exigirTexto(username, "usuario", 3, 50);
        nombreCompleto = Validaciones.exigirTexto(nombreCompleto, "nombre completo", 3, 120);
        Validaciones.exigirNoNulo(rol, "rol");
    }

    public static TokenAcceso bearer(String token, long expiraEnSegundos, String username,
                                     String nombreCompleto, Rol rol) {
        return new TokenAcceso(token, TIPO_BEARER, expiraEnSegundos, username, nombreCompleto, rol);
    }
}
