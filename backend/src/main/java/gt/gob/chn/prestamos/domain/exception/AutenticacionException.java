package gt.gob.chn.prestamos.domain.exception;

public class AutenticacionException extends DomainException {

    private static final long serialVersionUID = 1L;

    public AutenticacionException(String mensaje) {
        super(mensaje);
    }

    @Override
    public String codigo() {
        return "AUTENTICACION";
    }
}
