package gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador;

import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.PaginaResponse;
import java.util.List;
import java.util.function.Function;

public final class MapeadorWebPagina {

    private MapeadorWebPagina() {
    }

    public static <D, R> PaginaResponse<R> convertir(PaginaDominio<D> pagina, Function<D, R> mapa) {
        List<R> contenido = pagina.contenido().stream().map(mapa).toList();
        return new PaginaResponse<>(contenido, pagina.pagina(), pagina.tamano(),
                pagina.totalElementos(), pagina.totalPaginas());
    }
}
