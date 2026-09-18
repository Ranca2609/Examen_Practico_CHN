package gt.gob.chn.prestamos.domain.model.reporte;

import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;
import gt.gob.chn.prestamos.domain.model.Validaciones;
import java.time.LocalDateTime;
import java.util.List;

public record DocumentoReporte(
        String titulo,
        String subtitulo,
        List<ParDato> datosEncabezado,
        List<ColumnaReporte> columnas,
        List<FilaReporte> filas,
        List<ValorCelda> totales,
        String notaPie,
        String generadoPor,
        LocalDateTime generadoEn,
        String nombreArchivoBase) {

    public DocumentoReporte {
        titulo = Validaciones.exigirTexto(titulo, "titulo del reporte", 3, 120);
        subtitulo = textoOVacio(subtitulo);
        notaPie = textoOVacio(notaPie);
        generadoPor = Validaciones.exigirTexto(generadoPor, "usuario que genera el reporte", 1, 50);
        Validaciones.exigirNoNulo(generadoEn, "fecha de generacion del reporte");
        nombreArchivoBase = Validaciones.exigirTexto(nombreArchivoBase, "nombre base del archivo", 3, 100);

        // Copias inmutables: el mismo documento puede pasar por varios generadores.
        datosEncabezado = datosEncabezado == null ? List.of() : List.copyOf(datosEncabezado);
        columnas = columnas == null ? List.of() : List.copyOf(columnas);
        filas = filas == null ? List.of() : List.copyOf(filas);
        totales = totales == null ? List.of() : List.copyOf(totales);

        if (columnas.isEmpty()) {
            throw new ValidacionDominioException("El reporte debe declarar al menos una columna.");
        }
        // Sin filas es valido: el reporte deja constancia de que no hay movimientos.
        if (!totales.isEmpty() && totales.size() != columnas.size()) {
            throw new ValidacionDominioException("La fila de totales debe tener "
                    + columnas.size() + " celdas para cuadrar con las columnas del reporte; "
                    + "las celdas que no aplican se envian como texto vacio.");
        }
    }

    public String nombreArchivo(FormatoReporte formato) {
        Validaciones.exigirNoNulo(formato, "formato del reporte");
        return nombreArchivoBase + "." + formato.extension();
    }

    public boolean estaVacio() {
        return filas.isEmpty();
    }

    public boolean tieneTotales() {
        return !totales.isEmpty();
    }

    private static String textoOVacio(String valor) {
        return valor == null ? "" : valor.trim();
    }
}
