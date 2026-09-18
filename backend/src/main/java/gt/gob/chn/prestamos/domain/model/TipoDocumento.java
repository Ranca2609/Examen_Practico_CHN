package gt.gob.chn.prestamos.domain.model;

public enum TipoDocumento {

    SOLICITUD_CREDITO("SC", "solicitud de credito"),
    PRESTAMO("PR", "prestamo"),
    RECIBO_CAJA("RC", "recibo de caja");

    private final String codigo;
    private final String descripcion;

    TipoDocumento(String codigo, String descripcion) {
        this.codigo = codigo;
        this.descripcion = descripcion;
    }

    public String codigo() {
        return codigo;
    }

    public String descripcion() {
        return descripcion;
    }

    /** Devuelve null si ningun tipo usa el prefijo. */
    public static TipoDocumento desdeCodigo(String codigo) {
        for (TipoDocumento tipo : values()) {
            if (tipo.codigo.equals(codigo)) {
                return tipo;
            }
        }
        return null;
    }
}
