package gt.gob.chn.prestamos.domain.port.in;

import gt.gob.chn.prestamos.domain.model.reporte.ArchivoGenerado;
import gt.gob.chn.prestamos.domain.model.reporte.FormatoReporte;
import gt.gob.chn.prestamos.domain.port.in.command.ContextoOperacion;

public interface GenerarReportesUseCase {

    // ctx es obligatorio: los reportes exportan datos personales y cada descarga se audita.
    ArchivoGenerado planAmortizacion(Long prestamoId, FormatoReporte formato, ContextoOperacion ctx);

    ArchivoGenerado historialPagos(Long prestamoId, FormatoReporte formato, ContextoOperacion ctx);
}
