package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.mapeador;

import gt.gob.chn.prestamos.domain.model.Usuario;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.entidad.UsuarioEntidad;
import org.springframework.stereotype.Component;

@Component
public class MapeadorUsuario {

    public Usuario aDominio(UsuarioEntidad entidad) {
        if (entidad == null) {
            return null;
        }
        return Usuario.reconstituir(
                entidad.getId(),
                entidad.getUsername(),
                entidad.getPasswordHash(),
                entidad.getNombreCompleto(),
                entidad.getCorreo(),
                entidad.getRol(),
                entidad.isActivo(),
                entidad.getIntentosFallidos(),
                entidad.getBloqueadoHasta(),
                entidad.getUltimoAcceso());
    }

    public UsuarioEntidad aEntidad(Usuario usuario) {
        return new UsuarioEntidad(
                usuario.getId(),
                usuario.getUsername(),
                usuario.getPasswordHash(),
                usuario.getNombreCompleto(),
                usuario.getCorreo(),
                usuario.getRol(),
                usuario.isActivo(),
                usuario.getIntentosFallidos(),
                usuario.getBloqueadoHasta(),
                usuario.getUltimoAcceso());
    }
}
