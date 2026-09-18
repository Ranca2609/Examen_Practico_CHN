package gt.gob.chn.prestamos.domain.model.consulta;

import gt.gob.chn.prestamos.domain.model.Prestamo;
import gt.gob.chn.prestamos.domain.model.Validaciones;

public record PrestamoDetalle(
        Prestamo prestamo,
        String numeroSolicitud,
        String nombreCliente,
        String identificacionCliente) {

    public PrestamoDetalle {
        Validaciones.exigirNoNulo(prestamo, "prestamo");
    }
}
