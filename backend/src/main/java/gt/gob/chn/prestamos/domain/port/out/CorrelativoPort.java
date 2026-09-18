package gt.gob.chn.prestamos.domain.port.out;

public interface CorrelativoPort {

    // Salen de secuencias de BD: unicos aun con varias instancias en paralelo.
    // Formato: SC-001-2026-000001-3 (PR- para prestamos, RC- para recibos).
    String siguienteNumeroSolicitud();

    String siguienteNumeroPrestamo();

    String siguienteNumeroRecibo();
}
