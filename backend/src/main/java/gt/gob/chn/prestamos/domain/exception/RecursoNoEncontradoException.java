package gt.gob.chn.prestamos.domain.exception;

public class RecursoNoEncontradoException extends DomainException {

    private static final long serialVersionUID = 1L;

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }

    public RecursoNoEncontradoException(String entidad, Object id) {
        super("No se encontro " + entidad + " con identificador " + id + ".");
    }

    @Override
    public String codigo() {
        return "NO_ENCONTRADO";
    }
}
