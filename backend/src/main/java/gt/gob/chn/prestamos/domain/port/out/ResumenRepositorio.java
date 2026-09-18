package gt.gob.chn.prestamos.domain.port.out;

import gt.gob.chn.prestamos.domain.model.CarteraPorTipo;
import gt.gob.chn.prestamos.domain.model.RecaudacionMensual;
import gt.gob.chn.prestamos.domain.model.ResumenGeneral;
import java.time.YearMonth;
import java.util.List;

public interface ResumenRepositorio {

    ResumenGeneral obtener();

    /** Solo trae tipos con prestamos; los faltantes los rellena CarteraPorTipo.completar. */
    List<CarteraPorTipo> carteraPorTipo();

    /** Rango inclusivo y solo meses con pagos; los faltantes los rellena RecaudacionMensual.completar. */
    List<RecaudacionMensual> recaudacionMensual(YearMonth desde, YearMonth hasta);
}
