package gt.gob.chn.prestamos.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import gt.gob.chn.prestamos.domain.service.CalculadoraAmortizacion;
import gt.gob.chn.prestamos.domain.service.EvaluadorCapacidadPago;

// El dominio no lleva anotaciones de Spring (lo verifica ArchUnit): el cableado vive aquí.
@Configuration
public class CasosDeUsoConfig {

    @Bean
    CalculadoraAmortizacion calculadoraAmortizacion() {
        return new CalculadoraAmortizacion();
    }

    @Bean
    EvaluadorCapacidadPago evaluadorCapacidadPago() {
        return new EvaluadorCapacidadPago();
    }
}
