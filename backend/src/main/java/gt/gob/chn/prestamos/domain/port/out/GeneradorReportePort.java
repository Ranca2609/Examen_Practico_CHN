package gt.gob.chn.prestamos.domain.port.out;

import gt.gob.chn.prestamos.domain.model.reporte.ArchivoGenerado;
import gt.gob.chn.prestamos.domain.model.reporte.DocumentoReporte;
import gt.gob.chn.prestamos.domain.model.reporte.FormatoReporte;

public interface GeneradorReportePort {

    /** Clave con la que el caso de uso elige implementacion: agregar un formato no toca su codigo. */
    FormatoReporte formato();

    ArchivoGenerado generar(DocumentoReporte documento);
}
