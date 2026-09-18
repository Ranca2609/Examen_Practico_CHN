package gt.gob.chn.prestamos.domain.model.consulta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;
import gt.gob.chn.prestamos.domain.model.EstadoPrestamo;
import gt.gob.chn.prestamos.domain.model.EstadoSolicitud;
import gt.gob.chn.prestamos.domain.model.TipoPrestamo;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Filtros de consulta - criterios opcionales, rangos coherentes y paginacion acotada")
class FiltrosTest {

    private static final LocalDate ANTES = LocalDate.of(2026, 1, 1);
    private static final LocalDate DESPUES = LocalDate.of(2026, 6, 30);

    @Nested
    @DisplayName("FiltroCliente")
    class Clientes {

        @Test
        @DisplayName("La fabrica breve solo fija la busqueda y deja el resto de criterios en null")
        void la_fabrica_breve_deja_los_demas_criterios_en_null() {
            FiltroCliente filtro = FiltroCliente.de("Lopez", 1, 25);

            assertThat(filtro.busqueda()).isEqualTo("Lopez");
            assertThat(filtro.nacimientoDesde()).isNull();
            assertThat(filtro.nacimientoHasta()).isNull();
            assertThat(filtro.creacionDesde()).isNull();
            assertThat(filtro.creacionHasta()).isNull();
            assertThat(filtro.activo()).isNull();
            assertThat(filtro.pagina()).isEqualTo(1);
            assertThat(filtro.tamano()).isEqualTo(25);
        }

        @Test
        @DisplayName("Sin ningun criterio no hay filtros activos: la paginacion no cuenta")
        void sin_criterios_no_hay_filtros_activos() {
            assertThat(FiltroCliente.de(null, 3, 50).tieneFiltrosActivos()).isFalse();
        }

        @Test
        @DisplayName("Cualquier criterio distinto de la paginacion activa el contador de filtros")
        void cualquier_criterio_activa_el_contador_de_filtros() {
            assertThat(FiltroCliente.de("Lopez", 0, 10).tieneFiltrosActivos()).isTrue();
            assertThat(filtroCliente(ANTES, null, null, null, null).tieneFiltrosActivos()).isTrue();
            assertThat(filtroCliente(null, DESPUES, null, null, null).tieneFiltrosActivos()).isTrue();
            assertThat(filtroCliente(null, null, ANTES, null, null).tieneFiltrosActivos()).isTrue();
            assertThat(filtroCliente(null, null, null, DESPUES, null).tieneFiltrosActivos()).isTrue();
            // Incluso "activo = false" es un criterio: solo null significa "todos".
            assertThat(filtroCliente(null, null, null, null, false).tieneFiltrosActivos()).isTrue();
        }

        @Test
        @DisplayName("La pagina negativa y el tamano fuera de 1..100 se rechazan")
        void la_paginacion_fuera_de_rango_se_rechaza() {
            assertThat(FiltroCliente.TAMANO_MAXIMO).isEqualTo(100);

            assertThatThrownBy(() -> FiltroCliente.de(null, -1, 10))
                    .isInstanceOf(ValidacionDominioException.class);
            assertThatThrownBy(() -> FiltroCliente.de(null, 0, 0))
                    .isInstanceOf(ValidacionDominioException.class);
            assertThatThrownBy(() -> FiltroCliente.de(null, 0, FiltroCliente.TAMANO_MAXIMO + 1))
                    .isInstanceOf(ValidacionDominioException.class);
        }

        @Test
        @DisplayName("El rango de nacimiento invertido se rechaza mencionando las fechas")
        void el_rango_de_nacimiento_invertido_se_rechaza() {
            // Se afirma sobre el mensaje: es lo unico que ve el usuario cuando la API responde 400.
            assertThatThrownBy(() -> filtroCliente(DESPUES, ANTES, null, null, null))
                    .isInstanceOf(ValidacionDominioException.class)
                    .hasMessageContaining("fecha");
        }

        @Test
        @DisplayName("El rango de creacion invertido se rechaza mencionando las fechas")
        void el_rango_de_creacion_invertido_se_rechaza() {
            assertThatThrownBy(() -> filtroCliente(null, null, DESPUES, ANTES, null))
                    .isInstanceOf(ValidacionDominioException.class)
                    .hasMessageContaining("fecha");
        }

        @Test
        @DisplayName("Un rango abierto por un extremo es valido: ese extremo no restringe")
        void un_rango_con_un_solo_extremo_es_valido() {
            assertThatCode(() -> filtroCliente(ANTES, null, null, DESPUES, null))
                    .doesNotThrowAnyException();
            assertThatCode(() -> filtroCliente(null, DESPUES, ANTES, null, null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Un rango con ambos extremos iguales es valido porque es inclusivo")
        void un_rango_con_extremos_iguales_es_valido() {
            assertThatCode(() -> filtroCliente(ANTES, ANTES, null, null, null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("La busqueda se recorta y en blanco equivale a null")
        void la_busqueda_se_normaliza() {
            assertThat(FiltroCliente.de("   Lopez Garcia   ", 0, 10).busqueda())
                    .isEqualTo("Lopez Garcia");

            FiltroCliente enBlanco = FiltroCliente.de("   ", 0, 10);
            assertThat(enBlanco.busqueda()).isNull();
            assertThat(enBlanco.tieneFiltrosActivos()).isFalse();
        }

        @Test
        @DisplayName("Una busqueda desproporcionada se rechaza para no armar un LIKE inmanejable")
        void una_busqueda_demasiado_larga_se_rechaza() {
            assertThatThrownBy(() -> FiltroCliente.de("x".repeat(500), 0, 10))
                    .isInstanceOf(ValidacionDominioException.class);
        }

        private FiltroCliente filtroCliente(LocalDate nacimientoDesde, LocalDate nacimientoHasta,
                                            LocalDate creacionDesde, LocalDate creacionHasta,
                                            Boolean activo) {
            return new FiltroCliente(null, nacimientoDesde, nacimientoHasta,
                    creacionDesde, creacionHasta, activo, 0, 10);
        }
    }

    @Nested
    @DisplayName("FiltroSolicitud")
    class Solicitudes {

        @Test
        @DisplayName("La fabrica breve solo fija cliente y estado, y deja el resto en null")
        void la_fabrica_breve_deja_los_demas_criterios_en_null() {
            FiltroSolicitud filtro = FiltroSolicitud.de(7L, EstadoSolicitud.APROBADA, 0, 10);

            assertThat(filtro.clienteId()).isEqualTo(7L);
            assertThat(filtro.estado()).isEqualTo(EstadoSolicitud.APROBADA);
            assertThat(filtro.busqueda()).isNull();
            assertThat(filtro.tipoPrestamo()).isNull();
            assertThat(filtro.montoMinimo()).isNull();
            assertThat(filtro.montoMaximo()).isNull();
            assertThat(filtro.plazoMinimo()).isNull();
            assertThat(filtro.plazoMaximo()).isNull();
            assertThat(filtro.fechaDesde()).isNull();
            assertThat(filtro.fechaHasta()).isNull();
        }

        @Test
        @DisplayName("Sin criterios no hay filtros activos; con cualquiera de ellos si")
        void el_contador_de_filtros_refleja_los_criterios_presentes() {
            assertThat(FiltroSolicitud.de(null, null, 0, 10).tieneFiltrosActivos()).isFalse();
            assertThat(FiltroSolicitud.de(7L, null, 0, 10).tieneFiltrosActivos()).isTrue();
            assertThat(FiltroSolicitud.de(null, EstadoSolicitud.EN_PROCESO, 0, 10)
                    .tieneFiltrosActivos()).isTrue();
            assertThat(solicitudCon(TipoPrestamo.HIPOTECARIO, null, null, null, null, null, null)
                    .tieneFiltrosActivos()).isTrue();
            assertThat(solicitudCon(null, new BigDecimal("1000"), null, null, null, null, null)
                    .tieneFiltrosActivos()).isTrue();
            assertThat(solicitudCon(null, null, null, 12, null, null, null)
                    .tieneFiltrosActivos()).isTrue();
            assertThat(solicitudCon(null, null, null, null, null, ANTES, null)
                    .tieneFiltrosActivos()).isTrue();
        }

        @Test
        @DisplayName("La pagina negativa y el tamano fuera de 1..100 se rechazan")
        void la_paginacion_fuera_de_rango_se_rechaza() {
            assertThat(FiltroSolicitud.TAMANO_MAXIMO).isEqualTo(100);

            assertThatThrownBy(() -> FiltroSolicitud.de(null, null, -1, 10))
                    .isInstanceOf(ValidacionDominioException.class);
            assertThatThrownBy(() -> FiltroSolicitud.de(null, null, 0, 0))
                    .isInstanceOf(ValidacionDominioException.class);
            assertThatThrownBy(() ->
                    FiltroSolicitud.de(null, null, 0, FiltroSolicitud.TAMANO_MAXIMO + 1))
                    .isInstanceOf(ValidacionDominioException.class);
        }

        @Test
        @DisplayName("El monto minimo mayor que el maximo se rechaza mencionando el monto")
        void el_rango_de_monto_invertido_se_rechaza() {
            assertThatThrownBy(() -> solicitudCon(null, new BigDecimal("50000"),
                    new BigDecimal("10000"), null, null, null, null))
                    .isInstanceOf(ValidacionDominioException.class)
                    .hasMessageContaining("monto");
        }

        @Test
        @DisplayName("El plazo minimo mayor que el maximo se rechaza mencionando el plazo")
        void el_rango_de_plazo_invertido_se_rechaza() {
            assertThatThrownBy(() -> solicitudCon(null, null, null, 48, 12, null, null))
                    .isInstanceOf(ValidacionDominioException.class)
                    .hasMessageContaining("plazo");
        }

        @Test
        @DisplayName("El rango de fecha de solicitud invertido se rechaza mencionando las fechas")
        void el_rango_de_fecha_invertido_se_rechaza() {
            assertThatThrownBy(() -> solicitudCon(null, null, null, null, null, DESPUES, ANTES))
                    .isInstanceOf(ValidacionDominioException.class)
                    .hasMessageContaining("fecha");
        }

        @Test
        @DisplayName("Los rangos abiertos por un extremo son validos")
        void un_rango_con_un_solo_extremo_es_valido() {
            assertThatCode(() ->
                    solicitudCon(null, new BigDecimal("10000"), null, 12, null, ANTES, null))
                    .doesNotThrowAnyException();
            assertThatCode(() ->
                    solicitudCon(null, null, new BigDecimal("10000"), null, 48, null, DESPUES))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("La busqueda se recorta y en blanco equivale a null")
        void la_busqueda_se_normaliza() {
            FiltroSolicitud conTexto = new FiltroSolicitud("  SC-001-2026-000001-3  ", null, null, null,
                    null, null, null, null, null, null, 0, 10);
            assertThat(conTexto.busqueda()).isEqualTo("SC-001-2026-000001-3");
            assertThat(conTexto.tieneFiltrosActivos()).isTrue();

            FiltroSolicitud enBlanco = new FiltroSolicitud("   ", null, null, null,
                    null, null, null, null, null, null, 0, 10);
            assertThat(enBlanco.busqueda()).isNull();
            assertThat(enBlanco.tieneFiltrosActivos()).isFalse();
        }

        private FiltroSolicitud solicitudCon(TipoPrestamo tipoPrestamo,
                                             BigDecimal montoMinimo, BigDecimal montoMaximo,
                                             Integer plazoMinimo, Integer plazoMaximo,
                                             LocalDate fechaDesde, LocalDate fechaHasta) {
            return new FiltroSolicitud(null, null, null, tipoPrestamo, montoMinimo, montoMaximo,
                    plazoMinimo, plazoMaximo, fechaDesde, fechaHasta, 0, 10);
        }
    }

    @Nested
    @DisplayName("FiltroPrestamo")
    class Prestamos {

        @Test
        @DisplayName("La fabrica breve solo fija cliente y estado, y deja el resto en null")
        void la_fabrica_breve_deja_los_demas_criterios_en_null() {
            FiltroPrestamo filtro = FiltroPrestamo.de(7L, EstadoPrestamo.VIGENTE, 2, 5);

            assertThat(filtro.clienteId()).isEqualTo(7L);
            assertThat(filtro.estado()).isEqualTo(EstadoPrestamo.VIGENTE);
            assertThat(filtro.busqueda()).isNull();
            assertThat(filtro.montoMinimo()).isNull();
            assertThat(filtro.montoMaximo()).isNull();
            assertThat(filtro.saldoMinimo()).isNull();
            assertThat(filtro.saldoMaximo()).isNull();
            assertThat(filtro.desembolsoDesde()).isNull();
            assertThat(filtro.desembolsoHasta()).isNull();
            assertThat(filtro.vencimientoDesde()).isNull();
            assertThat(filtro.vencimientoHasta()).isNull();
            assertThat(filtro.pagina()).isEqualTo(2);
            assertThat(filtro.tamano()).isEqualTo(5);
        }

        @Test
        @DisplayName("Sin criterios no hay filtros activos; con cualquiera de ellos si")
        void el_contador_de_filtros_refleja_los_criterios_presentes() {
            assertThat(FiltroPrestamo.de(null, null, 0, 10).tieneFiltrosActivos()).isFalse();
            assertThat(FiltroPrestamo.de(7L, null, 0, 10).tieneFiltrosActivos()).isTrue();
            assertThat(FiltroPrestamo.de(null, EstadoPrestamo.VIGENTE, 0, 10)
                    .tieneFiltrosActivos()).isTrue();
            assertThat(prestamoCon(new BigDecimal("1000"), null, null, null, null, null, null, null)
                    .tieneFiltrosActivos()).isTrue();
            assertThat(prestamoCon(null, null, new BigDecimal("500"), null, null, null, null, null)
                    .tieneFiltrosActivos()).isTrue();
            assertThat(prestamoCon(null, null, null, null, ANTES, null, null, null)
                    .tieneFiltrosActivos()).isTrue();
            assertThat(prestamoCon(null, null, null, null, null, null, null, DESPUES)
                    .tieneFiltrosActivos()).isTrue();
        }

        @Test
        @DisplayName("La pagina negativa y el tamano fuera de 1..100 se rechazan")
        void la_paginacion_fuera_de_rango_se_rechaza() {
            assertThat(FiltroPrestamo.TAMANO_MAXIMO).isEqualTo(100);

            assertThatThrownBy(() -> FiltroPrestamo.de(null, null, -1, 10))
                    .isInstanceOf(ValidacionDominioException.class);
            assertThatThrownBy(() -> FiltroPrestamo.de(null, null, 0, 0))
                    .isInstanceOf(ValidacionDominioException.class);
            assertThatThrownBy(() ->
                    FiltroPrestamo.de(null, null, 0, FiltroPrestamo.TAMANO_MAXIMO + 1))
                    .isInstanceOf(ValidacionDominioException.class);
        }

        @Test
        @DisplayName("El monto aprobado minimo mayor que el maximo se rechaza mencionando el monto")
        void el_rango_de_monto_invertido_se_rechaza() {
            assertThatThrownBy(() -> prestamoCon(new BigDecimal("80000"), new BigDecimal("20000"),
                    null, null, null, null, null, null))
                    .isInstanceOf(ValidacionDominioException.class)
                    .hasMessageContaining("monto");
        }

        @Test
        @DisplayName("El saldo pendiente minimo mayor que el maximo se rechaza")
        void el_rango_de_saldo_invertido_se_rechaza() {
            assertThatThrownBy(() -> prestamoCon(null, null, new BigDecimal("9000"),
                    new BigDecimal("1000"), null, null, null, null))
                    .isInstanceOf(ValidacionDominioException.class);
        }

        @Test
        @DisplayName("Los rangos de desembolso y de vencimiento invertidos se rechazan")
        void los_rangos_de_fecha_invertidos_se_rechazan() {
            assertThatThrownBy(() -> prestamoCon(null, null, null, null, DESPUES, ANTES, null, null))
                    .isInstanceOf(ValidacionDominioException.class)
                    .hasMessageContaining("fecha");
            assertThatThrownBy(() -> prestamoCon(null, null, null, null, null, null, DESPUES, ANTES))
                    .isInstanceOf(ValidacionDominioException.class)
                    .hasMessageContaining("fecha");
        }

        @Test
        @DisplayName("Los rangos abiertos por un extremo son validos")
        void un_rango_con_un_solo_extremo_es_valido() {
            assertThatCode(() -> prestamoCon(new BigDecimal("1000"), null, null,
                    new BigDecimal("5000"), ANTES, null, null, DESPUES))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("La busqueda se recorta y en blanco equivale a null")
        void la_busqueda_se_normaliza() {
            FiltroPrestamo conTexto = new FiltroPrestamo("  PR-001-2026-000001-9  ", null, null, null,
                    null, null, null, null, null, null, null, 0, 10);
            assertThat(conTexto.busqueda()).isEqualTo("PR-001-2026-000001-9");
            assertThat(conTexto.tieneFiltrosActivos()).isTrue();

            FiltroPrestamo enBlanco = new FiltroPrestamo("  ", null, null, null,
                    null, null, null, null, null, null, null, 0, 10);
            assertThat(enBlanco.busqueda()).isNull();
            assertThat(enBlanco.tieneFiltrosActivos()).isFalse();
        }

        private FiltroPrestamo prestamoCon(BigDecimal montoMinimo, BigDecimal montoMaximo,
                                           BigDecimal saldoMinimo, BigDecimal saldoMaximo,
                                           LocalDate desembolsoDesde, LocalDate desembolsoHasta,
                                           LocalDate vencimientoDesde, LocalDate vencimientoHasta) {
            return new FiltroPrestamo(null, null, null, montoMinimo, montoMaximo,
                    saldoMinimo, saldoMaximo, desembolsoDesde, desembolsoHasta,
                    vencimientoDesde, vencimientoHasta, 0, 10);
        }
    }

    @Nested
    @DisplayName("FiltroPago")
    class Pagos {

        @Test
        @DisplayName("La fabrica breve solo fija prestamo y cliente, y deja el resto en null")
        void la_fabrica_breve_deja_los_demas_criterios_en_null() {
            FiltroPago filtro = FiltroPago.de(30L, 7L, 0, 10);

            assertThat(filtro.prestamoId()).isEqualTo(30L);
            assertThat(filtro.clienteId()).isEqualTo(7L);
            assertThat(filtro.busqueda()).isNull();
            assertThat(filtro.montoMinimo()).isNull();
            assertThat(filtro.montoMaximo()).isNull();
            assertThat(filtro.fechaDesde()).isNull();
            assertThat(filtro.fechaHasta()).isNull();
            assertThat(filtro.usuarioRegistro()).isNull();
        }

        @Test
        @DisplayName("Sin criterios no hay filtros activos; con cualquiera de ellos si")
        void el_contador_de_filtros_refleja_los_criterios_presentes() {
            assertThat(FiltroPago.de(null, null, 0, 10).tieneFiltrosActivos()).isFalse();
            assertThat(FiltroPago.de(30L, null, 0, 10).tieneFiltrosActivos()).isTrue();
            assertThat(FiltroPago.de(null, 7L, 0, 10).tieneFiltrosActivos()).isTrue();
            assertThat(pagoCon(new BigDecimal("100"), null, null, null, null)
                    .tieneFiltrosActivos()).isTrue();
            assertThat(pagoCon(null, null, ANTES, null, null).tieneFiltrosActivos()).isTrue();
            assertThat(pagoCon(null, null, null, null, "cajero1").tieneFiltrosActivos()).isTrue();
        }

        @Test
        @DisplayName("La pagina negativa y el tamano fuera de 1..100 se rechazan")
        void la_paginacion_fuera_de_rango_se_rechaza() {
            assertThat(FiltroPago.TAMANO_MAXIMO).isEqualTo(100);

            assertThatThrownBy(() -> FiltroPago.de(null, null, -1, 10))
                    .isInstanceOf(ValidacionDominioException.class);
            assertThatThrownBy(() -> FiltroPago.de(null, null, 0, 0))
                    .isInstanceOf(ValidacionDominioException.class);
            assertThatThrownBy(() -> FiltroPago.de(null, null, 0, FiltroPago.TAMANO_MAXIMO + 1))
                    .isInstanceOf(ValidacionDominioException.class);
        }

        @Test
        @DisplayName("El monto minimo mayor que el maximo se rechaza mencionando el monto")
        void el_rango_de_monto_invertido_se_rechaza() {
            assertThatThrownBy(() -> pagoCon(new BigDecimal("5000"), new BigDecimal("500"),
                    null, null, null))
                    .isInstanceOf(ValidacionDominioException.class)
                    .hasMessageContaining("monto");
        }

        @Test
        @DisplayName("El rango de fecha de pago invertido se rechaza mencionando las fechas")
        void el_rango_de_fecha_invertido_se_rechaza() {
            assertThatThrownBy(() -> pagoCon(null, null, DESPUES, ANTES, null))
                    .isInstanceOf(ValidacionDominioException.class)
                    .hasMessageContaining("fecha");
        }

        @Test
        @DisplayName("Los rangos abiertos por un extremo son validos")
        void un_rango_con_un_solo_extremo_es_valido() {
            assertThatCode(() -> pagoCon(new BigDecimal("500"), null, null, DESPUES, null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("La busqueda se recorta y en blanco equivale a null")
        void la_busqueda_se_normaliza() {
            FiltroPago conTexto = new FiltroPago(" RC-001-2026-000001-4 ", null, null, null, null,
                    null, null, null, 0, 10);
            assertThat(conTexto.busqueda()).isEqualTo("RC-001-2026-000001-4");
            assertThat(conTexto.tieneFiltrosActivos()).isTrue();

            FiltroPago enBlanco = new FiltroPago("    ", null, null, null, null,
                    null, null, null, 0, 10);
            assertThat(enBlanco.busqueda()).isNull();
            assertThat(enBlanco.tieneFiltrosActivos()).isFalse();
        }

        private FiltroPago pagoCon(BigDecimal montoMinimo, BigDecimal montoMaximo,
                                   LocalDate fechaDesde, LocalDate fechaHasta,
                                   String usuarioRegistro) {
            return new FiltroPago(null, null, null, montoMinimo, montoMaximo,
                    fechaDesde, fechaHasta, usuarioRegistro, 0, 10);
        }
    }

    @Nested
    @DisplayName("FiltroAuditoria")
    class Auditoria {

        @Test
        @DisplayName("La fabrica breve solo fija la paginacion y deja todos los criterios en null")
        void la_fabrica_breve_deja_los_demas_criterios_en_null() {
            FiltroAuditoria filtro = FiltroAuditoria.de(0, 20);

            assertThat(filtro.busqueda()).isNull();
            assertThat(filtro.usuario()).isNull();
            assertThat(filtro.accion()).isNull();
            assertThat(filtro.entidad()).isNull();
            assertThat(filtro.fechaDesde()).isNull();
            assertThat(filtro.fechaHasta()).isNull();
            assertThat(filtro.pagina()).isZero();
            assertThat(filtro.tamano()).isEqualTo(20);
            assertThat(filtro.tieneFiltrosActivos()).isFalse();
        }

        @Test
        @DisplayName("Cualquier criterio de la bitacora activa el contador de filtros")
        void el_contador_de_filtros_refleja_los_criterios_presentes() {
            assertThat(auditoriaCon(null, "admin", null, null, null, null)
                    .tieneFiltrosActivos()).isTrue();
            assertThat(auditoriaCon(null, null, "PAGO_REGISTRADO", null, null, null)
                    .tieneFiltrosActivos()).isTrue();
            assertThat(auditoriaCon(null, null, null, "CLIENTE", null, null)
                    .tieneFiltrosActivos()).isTrue();
            assertThat(auditoriaCon(null, null, null, null, ANTES, null)
                    .tieneFiltrosActivos()).isTrue();
            assertThat(auditoriaCon("recibo", null, null, null, null, null)
                    .tieneFiltrosActivos()).isTrue();
        }

        @Test
        @DisplayName("La pagina negativa y el tamano fuera de 1..100 se rechazan")
        void la_paginacion_fuera_de_rango_se_rechaza() {
            assertThat(FiltroAuditoria.TAMANO_MAXIMO).isEqualTo(100);

            assertThatThrownBy(() -> FiltroAuditoria.de(-1, 10))
                    .isInstanceOf(ValidacionDominioException.class);
            assertThatThrownBy(() -> FiltroAuditoria.de(0, 0))
                    .isInstanceOf(ValidacionDominioException.class);
            assertThatThrownBy(() -> FiltroAuditoria.de(0, FiltroAuditoria.TAMANO_MAXIMO + 1))
                    .isInstanceOf(ValidacionDominioException.class);
        }

        @Test
        @DisplayName("El rango de fecha invertido se rechaza mencionando las fechas")
        void el_rango_de_fecha_invertido_se_rechaza() {
            assertThatThrownBy(() -> auditoriaCon(null, null, null, null, DESPUES, ANTES))
                    .isInstanceOf(ValidacionDominioException.class)
                    .hasMessageContaining("fecha");
        }

        @Test
        @DisplayName("Un rango abierto por un extremo es valido")
        void un_rango_con_un_solo_extremo_es_valido() {
            assertThatCode(() -> auditoriaCon(null, null, null, null, ANTES, null))
                    .doesNotThrowAnyException();
            assertThatCode(() -> auditoriaCon(null, null, null, null, null, DESPUES))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("La busqueda se recorta y en blanco equivale a null")
        void la_busqueda_se_normaliza() {
            assertThat(auditoriaCon("  PAGO  ", null, null, null, null, null).busqueda())
                    .isEqualTo("PAGO");

            FiltroAuditoria enBlanco = auditoriaCon("   ", null, null, null, null, null);
            assertThat(enBlanco.busqueda()).isNull();
            assertThat(enBlanco.tieneFiltrosActivos()).isFalse();
        }

        private FiltroAuditoria auditoriaCon(String busqueda, String usuario, String accion,
                                             String entidad, LocalDate fechaDesde,
                                             LocalDate fechaHasta) {
            return new FiltroAuditoria(busqueda, usuario, accion, entidad,
                    fechaDesde, fechaHasta, 0, 10);
        }
    }
}
