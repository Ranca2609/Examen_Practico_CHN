package gt.gob.chn.prestamos.domain.model;

import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

public final class Cliente {

    public static final int EDAD_MINIMA = 18;
    public static final int DIGITOS_IDENTIFICACION = 13;
    public static final int DIGITOS_TELEFONO = 8;

    private static final int NOMBRE_MINIMO = 2;
    private static final int NOMBRE_MAXIMO = 60;
    private static final int DIRECCION_MINIMA = 5;
    private static final int DIRECCION_MAXIMA = 200;
    private static final int CORREO_MAXIMO = 120;

    private final Long id;
    private String nombre;
    private String apellido;
    private final String numeroIdentificacion;
    private final LocalDate fechaNacimiento;
    private String direccion;
    private String correoElectronico;
    private String telefono;
    private final boolean activo;
    private final LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;

    // La mayoria de edad se valida en nuevo(): necesita la fecha actual, que el dominio recibe desde fuera.
    private Cliente(Long id, String nombre, String apellido, String numeroIdentificacion,
                    LocalDate fechaNacimiento, String direccion, String correoElectronico, String telefono,
                    boolean activo, LocalDateTime fechaCreacion, LocalDateTime fechaModificacion) {
        this.id = id;
        this.nombre = Validaciones.exigirTexto(nombre, "nombre", NOMBRE_MINIMO, NOMBRE_MAXIMO);
        this.apellido = Validaciones.exigirTexto(apellido, "apellido", NOMBRE_MINIMO, NOMBRE_MAXIMO);
        this.numeroIdentificacion = Validaciones.exigirDigitos(
                numeroIdentificacion, "numero de identificacion (DPI)", DIGITOS_IDENTIFICACION);
        this.fechaNacimiento = Validaciones.exigirNoNulo(fechaNacimiento, "fecha de nacimiento");
        this.direccion = Validaciones.exigirTexto(direccion, "direccion", DIRECCION_MINIMA, DIRECCION_MAXIMA);
        this.correoElectronico = Validaciones.exigirCorreo(correoElectronico, "correo electronico", CORREO_MAXIMO);
        this.telefono = Validaciones.exigirDigitos(telefono, "telefono", DIGITOS_TELEFONO);
        this.activo = activo;
        this.fechaCreacion = Validaciones.exigirNoNulo(fechaCreacion, "fecha de creacion");
        this.fechaModificacion = fechaModificacion;
    }

    public static Cliente nuevo(String nombre, String apellido, String numeroIdentificacion,
                                LocalDate fechaNacimiento, String direccion, String correoElectronico,
                                String telefono, LocalDateTime ahora) {
        Validaciones.exigirNoNulo(ahora, "fecha de operacion");
        Validaciones.exigirNoNulo(fechaNacimiento, "fecha de nacimiento");
        LocalDate hoy = ahora.toLocalDate();
        if (fechaNacimiento.isAfter(hoy)) {
            throw new ValidacionDominioException("La fecha de nacimiento no puede ser futura.");
        }
        if (Period.between(fechaNacimiento, hoy).getYears() < EDAD_MINIMA) {
            throw new ValidacionDominioException(
                    "El cliente debe ser mayor de edad (" + EDAD_MINIMA + " anios cumplidos).");
        }
        return new Cliente(null, nombre, apellido, numeroIdentificacion, fechaNacimiento, direccion,
                correoElectronico, telefono, true, ahora, null);
    }

    public static Cliente reconstituir(Long id, String nombre, String apellido, String numeroIdentificacion,
                                       LocalDate fechaNacimiento, String direccion, String correoElectronico,
                                       String telefono, boolean activo, LocalDateTime fechaCreacion,
                                       LocalDateTime fechaModificacion) {
        Validaciones.exigirNoNulo(id, "identificador del cliente");
        return new Cliente(id, nombre, apellido, numeroIdentificacion, fechaNacimiento, direccion,
                correoElectronico, telefono, activo, fechaCreacion, fechaModificacion);
    }

    /** DPI y fecha de nacimiento no se editan: son la identidad legal y ya respaldan prestamos. */
    public void actualizarDatos(String nombre, String apellido, String direccion,
                                String correoElectronico, String telefono, LocalDateTime ahora) {
        Validaciones.exigirNoNulo(ahora, "fecha de operacion");
        this.nombre = Validaciones.exigirTexto(nombre, "nombre", NOMBRE_MINIMO, NOMBRE_MAXIMO);
        this.apellido = Validaciones.exigirTexto(apellido, "apellido", NOMBRE_MINIMO, NOMBRE_MAXIMO);
        this.direccion = Validaciones.exigirTexto(direccion, "direccion", DIRECCION_MINIMA, DIRECCION_MAXIMA);
        this.correoElectronico = Validaciones.exigirCorreo(correoElectronico, "correo electronico", CORREO_MAXIMO);
        this.telefono = Validaciones.exigirDigitos(telefono, "telefono", DIGITOS_TELEFONO);
        this.fechaModificacion = ahora;
    }

    public String nombreCompleto() {
        return nombre + " " + apellido;
    }

    public int edad(LocalDate hoy) {
        Validaciones.exigirNoNulo(hoy, "fecha de referencia");
        return Period.between(fechaNacimiento, hoy).getYears();
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public String getNumeroIdentificacion() {
        return numeroIdentificacion;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public String getDireccion() {
        return direccion;
    }

    public String getCorreoElectronico() {
        return correoElectronico;
    }

    public String getTelefono() {
        return telefono;
    }

    public boolean isActivo() {
        return activo;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public LocalDateTime getFechaModificacion() {
        return fechaModificacion;
    }

    /** Igualdad por identidad persistente: dos clientes sin id solo son iguales a si mismos. */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Cliente otro)) {
            return false;
        }
        return id != null && id.equals(otro.id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }

    @Override
    public String toString() {
        return "Cliente{id=" + id + ", numeroIdentificacion=" + numeroIdentificacion
                + ", nombreCompleto=" + nombreCompleto() + ", activo=" + activo + "}";
    }
}
