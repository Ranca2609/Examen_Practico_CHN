package gt.gob.chn.prestamos.domain.port.out;

public interface CodificadorContrasenaPort {

    String codificar(String contrasenaPlana);

    /** Verifica en tiempo constante si la contrasena corresponde al hash almacenado. */
    boolean coincide(String contrasenaPlana, String hash);
}
