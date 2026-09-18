package gt.gob.chn.prestamos.domain.port.in.command;

public record CredencialesCommand(String username, String contrasena) {

    // Evita que la contrasena termine en un log o en una traza.
    @Override
    public String toString() {
        return "CredencialesCommand{username=" + username + ", contrasena=***}";
    }
}
