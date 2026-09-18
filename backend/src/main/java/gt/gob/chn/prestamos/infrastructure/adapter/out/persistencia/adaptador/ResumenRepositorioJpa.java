package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.adaptador;

import gt.gob.chn.prestamos.domain.model.CarteraPorTipo;
import gt.gob.chn.prestamos.domain.model.Montos;
import gt.gob.chn.prestamos.domain.model.RecaudacionMensual;
import gt.gob.chn.prestamos.domain.model.ResumenGeneral;
import gt.gob.chn.prestamos.domain.model.TipoPrestamo;
import gt.gob.chn.prestamos.domain.port.out.ResumenRepositorio;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.ResumenJpaRepositorio;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion.CarteraTipoProyeccion;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion.RecaudacionMensualProyeccion;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion.ResumenProyeccion;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ResumenRepositorioJpa implements ResumenRepositorio {

    private final ResumenJpaRepositorio repositorio;

    public ResumenRepositorioJpa(ResumenJpaRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public ResumenGeneral obtener() {
        return repositorio.obtenerResumen()
                .map(this::aDominio)
                // Base de datos sin movimientos: se devuelven ceros, no nulos.
                .orElseGet(ResumenGeneral::vacio);
    }

    @Override
    public List<CarteraPorTipo> carteraPorTipo() {
        return repositorio.carteraPorTipo().stream()
                .map(this::carteraADominio)
                .toList();
    }

    @Override
    public List<RecaudacionMensual> recaudacionMensual(YearMonth desde, YearMonth hasta) {
        return repositorio.recaudacionMensual(aAnioMes(desde), aAnioMes(hasta)).stream()
                .map(this::recaudacionADominio)
                .toList();
    }

    private ResumenGeneral aDominio(ResumenProyeccion proyeccion) {
        return new ResumenGeneral(
                proyeccion.getTotalClientes(),
                proyeccion.getSolicitudesEnProceso(),
                proyeccion.getSolicitudesAprobadas(),
                proyeccion.getSolicitudesRechazadas(),
                proyeccion.getPrestamosVigentes(),
                proyeccion.getPrestamosLiquidados(),
                monto(proyeccion.getMontoTotalAprobado()),
                monto(proyeccion.getSaldoPendienteTotal()),
                monto(proyeccion.getTotalRecuperado()));
    }

    // ck_solicitudes_tipo usa cotejo binario, asi que el texto coincide con el enum; trim()
    // cubre los espacios finales, que SQL Server ignora al comparar.
    private CarteraPorTipo carteraADominio(CarteraTipoProyeccion proyeccion) {
        return new CarteraPorTipo(
                TipoPrestamo.valueOf(proyeccion.getTipoPrestamo().trim()),
                proyeccion.getCantidadPrestamos(),
                monto(proyeccion.getMontoAprobado()),
                monto(proyeccion.getSaldoPendiente()),
                monto(proyeccion.getTotalRecuperado()));
    }

    private RecaudacionMensual recaudacionADominio(RecaudacionMensualProyeccion proyeccion) {
        return new RecaudacionMensual(
                YearMonth.of(proyeccion.getAnio(), proyeccion.getMes()),
                proyeccion.getCantidadPagos(),
                monto(proyeccion.getMontoRecaudado()));
    }

    // 2025-10 -> 202510: formato de los extremos del BETWEEN de la consulta mensual.
    private static int aAnioMes(YearMonth periodo) {
        return periodo.getYear() * 100 + periodo.getMonthValue();
    }

    private BigDecimal monto(BigDecimal valor) {
        return Montos.normalizar(Montos.ceroSiNulo(valor));
    }
}
