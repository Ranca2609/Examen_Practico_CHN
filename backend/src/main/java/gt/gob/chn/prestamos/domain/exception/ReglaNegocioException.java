package gt.gob.chn.prestamos.domain.exception;

public class ReglaNegocioException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }

    @Override
    public String codigo() {
        return "REGLA_NEGOCIO";
    }
}
