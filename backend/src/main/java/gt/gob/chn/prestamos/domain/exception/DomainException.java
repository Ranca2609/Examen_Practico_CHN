package gt.gob.chn.prestamos.domain.exception;

public abstract class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    protected DomainException(String mensaje) {
        super(mensaje);
    }

    /** Codigo corto y estable del error (no depende de HTTP ni del framework). */
    public abstract String codigo();
}
