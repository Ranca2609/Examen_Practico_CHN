package gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "PaginaResponse", description = "Pagina de resultados")
public record PaginaResponse<T>(

        @Schema(description = "Elementos de la pagina actual")
        List<T> contenido,

        @Schema(description = "Numero de pagina solicitada (base 0)", example = "0")
        int pagina,

        @Schema(description = "Cantidad de elementos por pagina", example = "10")
        int tamano,

        @Schema(description = "Total de elementos que cumplen el filtro", example = "125")
        long totalElementos,

        @Schema(description = "Total de paginas disponibles", example = "13")
        int totalPaginas) {
}
