package gt.gob.chn.prestamos.domain.port.in.command;

public record ContextoOperacion(String usuario, String direccionIp) {

    private static final String USUARIO_SISTEMA = "sistema";
    private static final int IP_MAXIMA = 45;

    public ContextoOperacion {
        usuario = (usuario == null || usuario.isBlank()) ? USUARIO_SISTEMA : usuario.trim();
        if (direccionIp != null) {
            String ip = direccionIp.trim();
            direccionIp = ip.isEmpty() ? null : ip.substring(0, Math.min(ip.length(), IP_MAXIMA));
        }
    }

    public static ContextoOperacion delSistema() {
        return new ContextoOperacion(USUARIO_SISTEMA, null);
    }
}
