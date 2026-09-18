package gt.gob.chn.prestamos.domain.model.consulta;

import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;
import gt.gob.chn.prestamos.domain.model.Validaciones;
import java.util.List;

public record PaginaDominio<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas) {

    public PaginaDominio {
        contenido = contenido == null ? List.of() : List.copyOf(contenido);
        Validaciones.exigirRango(pagina, "pagina", 0, Integer.MAX_VALUE);
        Validaciones.exigirRango(tamano, "tamano de pagina", 1, Integer.MAX_VALUE);
        if (totalElementos < 0) {
            throw new ValidacionDominioException("El total de elementos no puede ser negativo.");
        }
    }

    public static <T> PaginaDominio<T> de(List<T> contenido, int pagina, int tamano, long totalElementos) {
        int totalPaginas = tamano <= 0 ? 0 : (int) ((totalElementos + tamano - 1) / tamano);
        return new PaginaDominio<>(contenido, pagina, tamano, totalElementos, totalPaginas);
    }

    public static <T> PaginaDominio<T> vacia(int pagina, int tamano) {
        return de(List.of(), pagina, tamano, 0L);
    }
}
