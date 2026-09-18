package gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador;

import gt.gob.chn.prestamos.domain.service.ResultadoEvaluacion;
import gt.gob.chn.prestamos.domain.service.ResultadoSimulacion;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.EvaluacionResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.SimulacionResponse;

public final class MapeadorWebSimulacion {

    private MapeadorWebSimulacion() {
    }

    public static SimulacionResponse aRespuesta(ResultadoSimulacion resultado) {
        return new SimulacionResponse(
                aRespuesta(resultado.evaluacion()),
                MapeadorWebPrestamo.aRespuesta(resultado.plan()));
    }

    public static EvaluacionResponse aRespuesta(ResultadoEvaluacion evaluacion) {
        return new EvaluacionResponse(
                evaluacion.porcentajeComprometido(),
                evaluacion.recomendado(),
                evaluacion.observacion(),
                evaluacion.prestamosVigentes());
    }
}
