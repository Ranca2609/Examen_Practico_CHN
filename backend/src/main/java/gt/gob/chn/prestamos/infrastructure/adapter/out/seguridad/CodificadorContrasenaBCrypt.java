package gt.gob.chn.prestamos.infrastructure.adapter.out.seguridad;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import gt.gob.chn.prestamos.domain.port.out.CodificadorContrasenaPort;

@Component
public class CodificadorContrasenaBCrypt implements CodificadorContrasenaPort {

    private final PasswordEncoder codificador;

    public CodificadorContrasenaBCrypt(PasswordEncoder codificador) {
        this.codificador = codificador;
    }

    @Override
    public String codificar(String contrasenaPlana) {
        return codificador.encode(contrasenaPlana);
    }

    @Override
    public boolean coincide(String contrasenaPlana, String hash) {
        // false en lugar de excepción: no debe distinguirse un usuario inexistente de una clave errónea.
        if (contrasenaPlana == null || hash == null) {
            return false;
        }
        return codificador.matches(contrasenaPlana, hash);
    }
}
