package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.adaptador;

import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

final class PaginacionJpa {

    private static final int TAMANO_PREDETERMINADO = 10;
    private static final int TAMANO_MAXIMO = 100;

    private PaginacionJpa() {
    }

    // Los filtros ya validan; esto evita que PageRequest lance IllegalArgumentException con valores sueltos.
    static PageRequest solicitud(int pagina, int tamano) {
        int paginaSegura = Math.max(pagina, 0);
        int tamanoSeguro = tamano < 1 ? TAMANO_PREDETERMINADO : Math.min(tamano, TAMANO_MAXIMO);
        return PageRequest.of(paginaSegura, tamanoSeguro);
    }

    // Sin Sort: el orden lo fija cada consulta JPQL.
    static <E, D> PaginaDominio<D> convertir(Page<E> pagina, Function<E, D> conversor) {
        List<D> contenido = pagina.getContent().stream().map(conversor).toList();
        return PaginaDominio.de(contenido, pagina.getNumber(), pagina.getSize(), pagina.getTotalElements());
    }
}
