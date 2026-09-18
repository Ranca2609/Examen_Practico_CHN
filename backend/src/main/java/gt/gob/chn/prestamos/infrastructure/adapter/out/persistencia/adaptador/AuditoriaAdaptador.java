package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.adaptador;

import gt.gob.chn.prestamos.domain.model.RegistroAuditoria;
import gt.gob.chn.prestamos.domain.model.consulta.FiltroAuditoria;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.domain.port.out.AuditoriaPort;
import gt.gob.chn.prestamos.domain.port.out.RelojPort;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.mapeador.MapeadorAuditoria;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.AuditoriaJpaRepositorio;
import org.springframework.stereotype.Component;

@Component
public class AuditoriaAdaptador implements AuditoriaPort {

    private static final int LARGO_DETALLE = 1000;
    private static final int LARGO_USUARIO = 50;
    private static final int LARGO_ACCION = 50;
    private static final int LARGO_ENTIDAD = 50;
    private static final int LARGO_ENTIDAD_ID = 50;
    private static final int LARGO_IP = 45;
    private static final String USUARIO_SISTEMA = "sistema";

    private final AuditoriaJpaRepositorio repositorio;
    private final MapeadorAuditoria mapeador;
    private final RelojPort reloj;

    public AuditoriaAdaptador(AuditoriaJpaRepositorio repositorio, MapeadorAuditoria mapeador,
                              RelojPort reloj) {
        this.repositorio = repositorio;
        this.mapeador = mapeador;
        this.reloj = reloj;
    }

    // Corre en la transaccion del caso de uso: si la operacion se revierte, el registro tambien.
    @Override
    public void registrar(String usuario, String accion, String entidad, String entidadId,
                          String detalle, String direccionIp) {
        repositorio.save(mapeador.aEntidad(
                recortar(valorODefecto(usuario), LARGO_USUARIO),
                recortar(accion, LARGO_ACCION),
                recortar(entidad, LARGO_ENTIDAD),
                recortar(entidadId, LARGO_ENTIDAD_ID),
                recortar(detalle, LARGO_DETALLE),
                recortar(direccionIp, LARGO_IP),
                reloj.ahora()));
    }

    @Override
    public PaginaDominio<RegistroAuditoria> listar(FiltroAuditoria filtro) {
        return PaginacionJpa.convertir(
                repositorio.buscar(
                        CriteriosJpa.textoONulo(filtro.busqueda()),
                        CriteriosJpa.textoONulo(filtro.usuario()),
                        CriteriosJpa.textoONulo(filtro.accion()),
                        CriteriosJpa.textoONulo(filtro.entidad()),
                        CriteriosJpa.inicioDelDia(filtro.fechaDesde()),
                        CriteriosJpa.inicioDelDiaSiguiente(filtro.fechaHasta()),
                        PaginacionJpa.solicitud(filtro.pagina(), filtro.tamano())),
                mapeador::aDominio);
    }

    // La columna usuario es NOT NULL: las tareas automaticas quedan como "sistema".
    private String valorODefecto(String usuario) {
        return usuario == null || usuario.isBlank() ? USUARIO_SISTEMA : usuario;
    }

    // Un texto que excede la columna no debe tumbar la operacion de negocio auditada.
    private String recortar(String valor, int largoMaximo) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= largoMaximo ? valor : valor.substring(0, largoMaximo);
    }
}
