package gt.gob.chn.prestamos.domain.exception;

public class ValidacionDominioException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ValidacionDominioException(String mensaje) {
        super(mensaje);
    }

    @Override
    public String codigo() {
        return "VALIDACION";
    }
}
