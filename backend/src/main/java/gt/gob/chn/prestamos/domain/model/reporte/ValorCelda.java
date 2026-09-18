package gt.gob.chn.prestamos.domain.model.reporte;

import gt.gob.chn.prestamos.domain.model.Validaciones;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public sealed interface ValorCelda {

    // Tipado a proposito: Excel necesita celdas numericas reales para sumar y ordenar,
    // y al ser sellada cada generador resuelve un switch exhaustivo.

    /** Null se normaliza a "" porque una celda en blanco es legitima. */
    record Texto(String valor) implements ValorCelda {
        public Texto {
            valor = valor == null ? "" : valor.trim();
        }
    }

    record Entero(int valor) implements ValorCelda {
    }

    record Moneda(BigDecimal valor) implements ValorCelda {
        public Moneda {
            Validaciones.exigirNoNulo(valor, "importe de la celda");
        }
    }

    /** Ya expresado en porcentaje: 12.50 significa 12.50 %, no 0.125. */
    record Porcentaje(BigDecimal valor) implements ValorCelda {
        public Porcentaje {
            Validaciones.exigirNoNulo(valor, "porcentaje de la celda");
        }
    }

    record Fecha(LocalDate valor) implements ValorCelda {
        public Fecha {
            Validaciones.exigirNoNulo(valor, "fecha de la celda");
        }
    }

    record FechaHora(LocalDateTime valor) implements ValorCelda {
        public FechaHora {
            Validaciones.exigirNoNulo(valor, "fecha y hora de la celda");
        }
    }
}
