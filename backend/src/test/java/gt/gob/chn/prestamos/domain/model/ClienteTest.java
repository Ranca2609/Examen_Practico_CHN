package gt.gob.chn.prestamos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Cliente - invariantes de identidad y datos de contacto")
class ClienteTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 3, 10, 9, 30);
    private static final String DPI_VALIDO = "2547896301234";
    private static final String TELEFONO_VALIDO = "55512345";

    @Test
    void registra_cliente_con_datos_validos() {
        Cliente cliente = clienteValido();

        assertThat(cliente.getId()).isNull();
        assertThat(cliente.getNombre()).isEqualTo("Maria Jose");
        assertThat(cliente.getApellido()).isEqualTo("Lopez Garcia");
        assertThat(cliente.getNumeroIdentificacion()).isEqualTo(DPI_VALIDO);
        assertThat(cliente.isActivo()).isTrue();
        assertThat(cliente.getFechaCreacion()).isEqualTo(AHORA);
        assertThat(cliente.getFechaModificacion()).isNull();
        assertThat(cliente.nombreCompleto()).isEqualTo("Maria Jose Lopez Garcia");
    }

    @Test
    void normaliza_el_correo_a_minusculas_y_recorta_espacios_en_el_nombre() {
        Cliente cliente = Cliente.nuevo("  Maria Jose  ", "  Lopez Garcia  ", DPI_VALIDO,
                LocalDate.of(1990, 5, 20), "Zona 10, Ciudad de Guatemala",
                "  MARIA.LOPEZ@Correo.GT  ", TELEFONO_VALIDO, AHORA);

        assertThat(cliente.getCorreoElectronico()).isEqualTo("maria.lopez@correo.gt");
        assertThat(cliente.getNombre()).isEqualTo("Maria Jose");
        assertThat(cliente.getApellido()).isEqualTo("Lopez Garcia");
    }

    @Test
    void calcula_la_edad_cumplida_a_la_fecha_de_referencia() {
        Cliente cliente = clienteValido();

        // Cumple anios el 20 de mayo: al 19 de mayo de 2026 todavia tiene 35.
        assertThat(cliente.edad(LocalDate.of(2026, 5, 19))).isEqualTo(35);
        assertThat(cliente.edad(LocalDate.of(2026, 5, 20))).isEqualTo(36);
    }

    @Test
    void rechaza_cliente_menor_de_edad() {
        LocalDate hace17Anios = AHORA.toLocalDate().minusYears(17);

        assertThatThrownBy(() -> Cliente.nuevo("Juan", "Perez", DPI_VALIDO, hace17Anios,
                "Zona 1, Ciudad de Guatemala", "juan.perez@correo.gt", TELEFONO_VALIDO, AHORA))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("mayor de edad");
    }

    @Test
    void rechaza_fecha_de_nacimiento_futura() {
        LocalDate futura = AHORA.toLocalDate().plusDays(1);

        assertThatThrownBy(() -> Cliente.nuevo("Juan", "Perez", DPI_VALIDO, futura,
                "Zona 1, Ciudad de Guatemala", "juan.perez@correo.gt", TELEFONO_VALIDO, AHORA))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("futura");
    }

    @Test
    void rechaza_dpi_con_doce_digitos() {
        assertThatThrownBy(() -> Cliente.nuevo("Juan", "Perez", "254789630123",
                LocalDate.of(1990, 5, 20), "Zona 1, Ciudad de Guatemala",
                "juan.perez@correo.gt", TELEFONO_VALIDO, AHORA))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("13");
    }

    @Test
    void rechaza_dpi_con_caracteres_no_numericos() {
        assertThatThrownBy(() -> Cliente.nuevo("Juan", "Perez", "25478963A1234",
                LocalDate.of(1990, 5, 20), "Zona 1, Ciudad de Guatemala",
                "juan.perez@correo.gt", TELEFONO_VALIDO, AHORA))
                .isInstanceOf(ValidacionDominioException.class);
    }

    @Test
    void rechaza_correo_con_formato_invalido() {
        assertThatThrownBy(() -> Cliente.nuevo("Juan", "Perez", DPI_VALIDO,
                LocalDate.of(1990, 5, 20), "Zona 1, Ciudad de Guatemala",
                "juan.perez-correo", TELEFONO_VALIDO, AHORA))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("formato");
    }

    @Test
    void rechaza_telefono_de_siete_digitos() {
        assertThatThrownBy(() -> Cliente.nuevo("Juan", "Perez", DPI_VALIDO,
                LocalDate.of(1990, 5, 20), "Zona 1, Ciudad de Guatemala",
                "juan.perez@correo.gt", "5551234", AHORA))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("8");
    }

    @Test
    void rechaza_nombre_vacio() {
        assertThatThrownBy(() -> Cliente.nuevo("   ", "Perez", DPI_VALIDO,
                LocalDate.of(1990, 5, 20), "Zona 1, Ciudad de Guatemala",
                "juan.perez@correo.gt", TELEFONO_VALIDO, AHORA))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("obligatorio");
    }

    @Test
    void rechaza_direccion_demasiado_corta() {
        assertThatThrownBy(() -> Cliente.nuevo("Juan", "Perez", DPI_VALIDO,
                LocalDate.of(1990, 5, 20), "Z 1", "juan.perez@correo.gt", TELEFONO_VALIDO, AHORA))
                .isInstanceOf(ValidacionDominioException.class);
    }

    @Test
    void actualizar_datos_cambia_contacto_y_sella_la_fecha_de_modificacion() {
        Cliente cliente = Cliente.reconstituir(7L, "Maria Jose", "Lopez Garcia", DPI_VALIDO,
                LocalDate.of(1990, 5, 20), "Zona 10, Ciudad de Guatemala",
                "maria.lopez@correo.gt", TELEFONO_VALIDO, true, AHORA, null);
        LocalDateTime despues = AHORA.plusDays(5);

        cliente.actualizarDatos("Maria Jose", "Lopez de Mejia", "Zona 15, Ciudad de Guatemala",
                "MARIA.MEJIA@Correo.GT", "55598765", despues);

        assertThat(cliente.getApellido()).isEqualTo("Lopez de Mejia");
        assertThat(cliente.getCorreoElectronico()).isEqualTo("maria.mejia@correo.gt");
        assertThat(cliente.getTelefono()).isEqualTo("55598765");
        assertThat(cliente.getFechaModificacion()).isEqualTo(despues);
        // El DPI y la fecha de nacimiento son identidad legal: no cambian.
        assertThat(cliente.getNumeroIdentificacion()).isEqualTo(DPI_VALIDO);
        assertThat(cliente.getFechaNacimiento()).isEqualTo(LocalDate.of(1990, 5, 20));
    }

    @Test
    void reconstituir_exige_identificador() {
        assertThatThrownBy(() -> Cliente.reconstituir(null, "Maria Jose", "Lopez Garcia", DPI_VALIDO,
                LocalDate.of(1990, 5, 20), "Zona 10, Ciudad de Guatemala",
                "maria.lopez@correo.gt", TELEFONO_VALIDO, true, AHORA, null))
                .isInstanceOf(ValidacionDominioException.class);
    }

    private static Cliente clienteValido() {
        return Cliente.nuevo("Maria Jose", "Lopez Garcia", DPI_VALIDO, LocalDate.of(1990, 5, 20),
                "Zona 10, Ciudad de Guatemala", "maria.lopez@correo.gt", TELEFONO_VALIDO, AHORA);
    }
}
