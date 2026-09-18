package gt.gob.chn.prestamos.arquitectura;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import gt.gob.chn.prestamos.domain.port.out.GeneradorReportePort;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

@DisplayName("Arquitectura hexagonal - reglas de dependencia entre capas")
class ArquitecturaHexagonalTest {

    private static final String PAQUETE_RAIZ = "gt.gob.chn.prestamos";

    private static JavaClasses clases;

    @BeforeAll
    static void importarClasesDeProduccion() {
        // Importador explicito en vez de @AnalyzeClasses: bajo Surefire este puede no hallar clases
        // y las reglas pasarian en verde sin comprobar nada (lo vigila la primera prueba).
        clases = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .withImportOption(new ImportOption.DoNotIncludeJars())
                .importPackages(PAQUETE_RAIZ);
    }

    @Test
    @DisplayName("El análisis realmente importa las clases del proyecto")
    void el_analisis_encuentra_las_clases() {
        assertThat(clases).isNotEmpty();
        assertThat(clases.stream().filter(c -> c.getPackageName().contains(".domain")).count())
                .as("debe haber clases de dominio que analizar")
                .isGreaterThan(20);
    }

    @Test
    @DisplayName("El dominio no depende de ningún framework")
    void el_dominio_no_depende_de_frameworks() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta..",
                        "javax..",
                        "com.fasterxml..",
                        "org.hibernate..",
                        "io.jsonwebtoken..",
                        "org.flywaydb..")
                .because("el núcleo de negocio debe poder compilarse y probarse sin Spring ni JPA");
        regla.check(clases);
    }

    @Test
    @DisplayName("El dominio no depende de la aplicación ni de la infraestructura")
    void el_dominio_no_depende_de_las_capas_externas() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        PAQUETE_RAIZ + ".application..",
                        PAQUETE_RAIZ + ".infrastructure..")
                .check(clases);
    }

    @Test
    @DisplayName("La capa de aplicación no depende de la infraestructura")
    void la_aplicacion_no_depende_de_la_infraestructura() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage(PAQUETE_RAIZ + ".infrastructure..")
                .check(clases);
    }

    @Test
    @DisplayName("El dominio modela el tiempo solo con java.time")
    void el_dominio_usa_solo_la_api_moderna_de_fechas() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().haveFullyQualifiedName("java.util.Date")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.util.Calendar")
                .orShould().dependOnClassesThat().resideInAnyPackage("java.sql..")
                .because("el dominio modela fechas con java.time y no conoce la base de datos")
                .check(clases);
    }

    @Test
    @DisplayName("Los controladores REST residen únicamente en el adaptador web")
    void los_controladores_residen_en_el_adaptador_web() {
        classes()
                .that().areAnnotatedWith(RestController.class)
                .should().resideInAPackage("..infrastructure.adapter.in.web.controlador")
                .andShould().haveSimpleNameEndingWith("Controlador")
                .check(clases);
    }

    @Test
    @DisplayName("Las entidades JPA residen únicamente en el adaptador de persistencia")
    void las_entidades_jpa_residen_en_el_adaptador_de_persistencia() {
        classes()
                .that().areAnnotatedWith(Entity.class)
                .should().resideInAPackage("..infrastructure.adapter.out.persistencia.entidad")
                .andShould().haveSimpleNameEndingWith("Entidad")
                .check(clases);
    }

    @Test
    @DisplayName("Las implementaciones de puertos de salida son adaptadores de salida")
    void las_implementaciones_de_puertos_de_salida_son_adaptadores() {
        noClasses()
                .that().implement(resideInAPackage("..domain.port.out.."))
                .should().resideOutsideOfPackage("..infrastructure.adapter.out..")
                .allowEmptyShould(true)
                .check(clases);
    }

    @Test
    @DisplayName("Los casos de uso se implementan en la capa de aplicación")
    void las_implementaciones_de_casos_de_uso_residen_en_la_capa_de_aplicacion() {
        noClasses()
                .that().implement(resideInAPackage("..domain.port.in"))
                .should().resideOutsideOfPackage("..application.usecase..")
                .allowEmptyShould(true)
                .check(clases);
    }

    // Si OpenPDF o POI llegaran al nucleo, cambiar de generador obligaria a tocar reglas de negocio.
    @Test
    @DisplayName("Las librerías de PDF y Excel solo existen en la infraestructura")
    void las_librerias_de_documentos_no_llegan_al_nucleo() {
        noClasses()
                .that().resideInAnyPackage("..domain..", "..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.lowagie..",
                        "com.github.librepdf..",
                        "org.apache.poi..",
                        "org.openxmlformats..")
                .because("el dominio y los casos de uso describen el reporte sin conocer "
                        + "el formato en que se materializa")
                .check(clases);
    }

    @Test
    @DisplayName("Los generadores de reportes residen en el adaptador de reportes")
    void los_generadores_de_reportes_residen_en_su_adaptador() {
        classes()
                .that().implement(GeneradorReportePort.class)
                .should().resideInAPackage("..infrastructure.adapter.out.reporte")
                .because("PDF y Excel son detalles de salida intercambiables")
                .check(clases);
    }

    @Test
    @DisplayName("Spring Data solo se usa en el adaptador de persistencia")
    void spring_data_solo_se_usa_en_el_adaptador_de_persistencia() {
        noClasses()
                .that().resideOutsideOfPackages(
                        PAQUETE_RAIZ,
                        PAQUETE_RAIZ + ".infrastructure.adapter.out..",
                        PAQUETE_RAIZ + ".infrastructure.config..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.data..")
                .allowEmptyShould(true)
                .check(clases);
    }
}
