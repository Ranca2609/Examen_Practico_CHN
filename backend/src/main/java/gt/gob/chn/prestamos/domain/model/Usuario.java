package gt.gob.chn.prestamos.domain.model;

import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

public final class Usuario {

    private static final Pattern PATRON_USERNAME = Pattern.compile("^[a-z0-9._-]+$");
    private static final int USERNAME_MINIMO = 3;
    private static final int USERNAME_MAXIMO = 50;
    private static final int HASH_MAXIMO = 100;
    private static final int NOMBRE_MAXIMO = 120;
    private static final int CORREO_MAXIMO = 120;

    private final Long id;
    private final String username;
    private final String passwordHash;
    private final String nombreCompleto;
    private final String correo;
    private final Rol rol;
    private final boolean activo;
    private int intentosFallidos;
    private LocalDateTime bloqueadoHasta;
    private LocalDateTime ultimoAcceso;

    private Usuario(Long id, String username, String passwordHash, String nombreCompleto, String correo,
                    Rol rol, boolean activo, int intentosFallidos, LocalDateTime bloqueadoHasta,
                    LocalDateTime ultimoAcceso) {
        this.id = id;
        this.username = validarUsername(username);
        this.passwordHash = Validaciones.exigirTexto(passwordHash, "hash de la contrasena", 8, HASH_MAXIMO);
        this.nombreCompleto = Validaciones.exigirTexto(nombreCompleto, "nombre completo", 3, NOMBRE_MAXIMO);
        this.correo = Validaciones.exigirCorreo(correo, "correo", CORREO_MAXIMO);
        this.rol = Validaciones.exigirNoNulo(rol, "rol");
        this.activo = activo;
        this.intentosFallidos = Validaciones.exigirRango(intentosFallidos, "intentos fallidos", 0, Integer.MAX_VALUE);
        this.bloqueadoHasta = bloqueadoHasta;
        this.ultimoAcceso = ultimoAcceso;
    }

    public static Usuario nuevo(String username, String passwordHash, String nombreCompleto, String correo, Rol rol) {
        return new Usuario(null, username, passwordHash, nombreCompleto, correo, rol, true, 0, null, null);
    }

    public static Usuario reconstituir(Long id, String username, String passwordHash, String nombreCompleto,
                                       String correo, Rol rol, boolean activo, int intentosFallidos,
                                       LocalDateTime bloqueadoHasta, LocalDateTime ultimoAcceso) {
        Validaciones.exigirNoNulo(id, "identificador del usuario");
        return new Usuario(id, username, passwordHash, nombreCompleto, correo, rol, activo, intentosFallidos,
                bloqueadoHasta, ultimoAcceso);
    }

    public boolean estaBloqueado(LocalDateTime ahora) {
        Validaciones.exigirNoNulo(ahora, "fecha de operacion");
        return bloqueadoHasta != null && ahora.isBefore(bloqueadoHasta);
    }

    // El contador se reinicia al bloquear: al vencer el bloqueo se recupera el cupo completo de intentos.
    public void registrarIntentoFallido(int maxIntentos, int minutosBloqueo, LocalDateTime ahora) {
        Validaciones.exigirNoNulo(ahora, "fecha de operacion");
        Validaciones.exigirRango(maxIntentos, "maximo de intentos", 1, 100);
        Validaciones.exigirRango(minutosBloqueo, "minutos de bloqueo", 1, 1440);
        this.intentosFallidos++;
        if (this.intentosFallidos >= maxIntentos) {
            this.bloqueadoHasta = ahora.plusMinutes(minutosBloqueo);
            this.intentosFallidos = 0;
        }
    }

    public void registrarAccesoExitoso(LocalDateTime ahora) {
        Validaciones.exigirNoNulo(ahora, "fecha de operacion");
        this.intentosFallidos = 0;
        this.bloqueadoHasta = null;
        this.ultimoAcceso = ahora;
    }

    private static String validarUsername(String valor) {
        String limpio = Validaciones.exigirTexto(valor, "usuario", USERNAME_MINIMO, USERNAME_MAXIMO).toLowerCase();
        if (!PATRON_USERNAME.matcher(limpio).matches()) {
            throw new ValidacionDominioException(
                    "El usuario solo admite letras, numeros, punto, guion y guion bajo.");
        }
        return limpio;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public String getCorreo() {
        return correo;
    }

    public Rol getRol() {
        return rol;
    }

    public boolean isActivo() {
        return activo;
    }

    public int getIntentosFallidos() {
        return intentosFallidos;
    }

    public LocalDateTime getBloqueadoHasta() {
        return bloqueadoHasta;
    }

    public LocalDateTime getUltimoAcceso() {
        return ultimoAcceso;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Usuario otro)) {
            return false;
        }
        return id != null && id.equals(otro.id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }

    /** Nunca incluye el hash de la contrasena: evita filtrarlo en logs o trazas. */
    @Override
    public String toString() {
        return "Usuario{id=" + id + ", username=" + username + ", rol=" + rol + ", activo=" + activo + "}";
    }
}
