package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.adaptador;

import java.time.LocalDate;
import java.time.LocalDateTime;

final class CriteriosJpa {

    private CriteriosJpa() {
    }

    // null es "criterio no informado": desactiva el ":criterio IS NULL OR ..." de la consulta.
    static String textoONulo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    static LocalDateTime inicioDelDia(LocalDate fecha) {
        return fecha == null ? null : fecha.atStartOfDay();
    }

    // Limite EXCLUSIVO para columnas DATETIME2: con "<= hasta" se perderian los registros
    // posteriores a las 00:00 del dia "hasta". Las columnas DATE comparan el LocalDate directo.
    static LocalDateTime inicioDelDiaSiguiente(LocalDate fecha) {
        return fecha == null ? null : fecha.plusDays(1).atStartOfDay();
    }
}
