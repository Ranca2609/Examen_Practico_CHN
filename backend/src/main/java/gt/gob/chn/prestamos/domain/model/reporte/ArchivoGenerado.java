package gt.gob.chn.prestamos.domain.model.reporte;

import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;
import gt.gob.chn.prestamos.domain.model.Validaciones;

public record ArchivoGenerado(String nombre, String tipoContenido, byte[] contenido) {

    // contenido se expone sin copiar a proposito: puede pesar megabytes y solo lo consume la respuesta HTTP.
    // Por lo mismo, equals compara el arreglo por referencia; nada depende de esa igualdad.
    public ArchivoGenerado {
        nombre = Validaciones.exigirTexto(nombre, "nombre del archivo", 5, 150);
        tipoContenido = Validaciones.exigirTexto(tipoContenido, "tipo de contenido del archivo", 3, 150);
        Validaciones.exigirNoNulo(contenido, "contenido del archivo");
        // Cero bytes delata un generador que fallo a medias: mejor cortar que entregar un archivo ilegible.
        if (contenido.length == 0) {
            throw new ValidacionDominioException("El reporte generado no puede estar vacio.");
        }
    }

    public static ArchivoGenerado de(DocumentoReporte documento, FormatoReporte formato, byte[] contenido) {
        Validaciones.exigirNoNulo(documento, "documento del reporte");
        Validaciones.exigirNoNulo(formato, "formato del reporte");
        return new ArchivoGenerado(documento.nombreArchivo(formato), formato.tipoContenido(), contenido);
    }

    public int tamanoEnBytes() {
        return contenido.length;
    }

    @Override
    public String toString() {
        // Se resume el contenido: volcar el arreglo en una traza no aporta nada y es enorme.
        return "ArchivoGenerado{nombre=" + nombre + ", tipoContenido=" + tipoContenido
                + ", bytes=" + contenido.length + "}";
    }
}
