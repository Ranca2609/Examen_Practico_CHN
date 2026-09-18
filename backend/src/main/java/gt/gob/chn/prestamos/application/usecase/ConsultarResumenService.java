package gt.gob.chn.prestamos.application.usecase;

import gt.gob.chn.prestamos.domain.model.CarteraPorTipo;
import gt.gob.chn.prestamos.domain.model.RecaudacionMensual;
import gt.gob.chn.prestamos.domain.model.ResumenGeneral;
import gt.gob.chn.prestamos.domain.port.in.ConsultarResumenUseCase;
import gt.gob.chn.prestamos.domain.port.out.RelojPort;
import gt.gob.chn.prestamos.domain.port.out.ResumenRepositorio;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ConsultarResumenService implements ConsultarResumenUseCase {

    public static final int MESES_RECAUDACION = 12;

    private final ResumenRepositorio resumenRepositorio;
    private final RelojPort reloj;

    public ConsultarResumenService(ResumenRepositorio resumenRepositorio, RelojPort reloj) {
        this.resumenRepositorio = resumenRepositorio;
        this.reloj = reloj;
    }

    @Override
    public ResumenGeneral obtener() {
        // Las tres vistas se leen en sentencias separadas, sin foto única: un pago confirmado
        // entre lecturas puede descuadrar totales y series. Aceptable en un tablero informativo.
        YearMonth hasta = YearMonth.from(reloj.hoy());
        YearMonth desde = hasta.minusMonths(MESES_RECAUDACION - 1L);

        // Las vistas omiten grupos sin datos; completar() rellena con ceros tipos y meses faltantes.
        List<CarteraPorTipo> cartera = CarteraPorTipo.completar(resumenRepositorio.carteraPorTipo());
        List<RecaudacionMensual> recaudacion = RecaudacionMensual.completar(
                resumenRepositorio.recaudacionMensual(desde, hasta), desde, hasta);

        return resumenRepositorio.obtener().conSeries(cartera, recaudacion);
    }
}
