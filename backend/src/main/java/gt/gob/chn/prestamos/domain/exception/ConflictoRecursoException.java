package gt.gob.chn.prestamos.domain.exception;

public class ConflictoRecursoException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ConflictoRecursoException(String mensaje) {
        super(mensaje);
    }

    @Override
    public String codigo() {
        return "DUPLICADO";
    }
}
