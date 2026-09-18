package gt.gob.chn.prestamos.domain.model.consulta;

import gt.gob.chn.prestamos.domain.model.SolicitudPrestamo;
import gt.gob.chn.prestamos.domain.model.Validaciones;

public record SolicitudDetalle(
        SolicitudPrestamo solicitud,
        String nombreCliente,
        String identificacionCliente) {

    public SolicitudDetalle {
        Validaciones.exigirNoNulo(solicitud, "solicitud");
    }
}
