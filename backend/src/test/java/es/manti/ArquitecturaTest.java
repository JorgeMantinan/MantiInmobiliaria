package es.manti;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Reglas de arquitectura del monolito modular (skill estructura-spring-boot):
 * {@code api → application → domain ← infra}, con el dominio como núcleo.
 */
class ArquitecturaTest {

    private static final JavaClasses CLASES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("es.manti");

    @Test
    void elDominioNoDependeDeApiNiInfraestructura() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infra..")
                .check(CLASES);
    }

    @Test
    void applicationNoDependeDeApi() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..api..")
                .check(CLASES);
    }

    @Test
    void applicationNoDependeDeInfraestructura() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..infra..")
                .check(CLASES);
    }

    /**
     * {@code shared} es transversal: si importa el dominio de un módulo, el
     * módulo deja de ser un módulo y shared se convierte en un cajón de sastre.
     * Los mapeos de dominio viven en el advice de cada módulo.
     */
    @Test
    void sharedNoDependeDeDominioDeNingunModulo() {
        noClasses().that().resideInAPackage("..shared..")
                .should().dependOnClassesThat().resideInAnyPackage("..domain..")
                .check(CLASES);
    }

    @Test
    void laApiDeUsuariosNoTocaElDominioNiLaInfraestructuraDeInmuebles() {
        noClasses().that().resideInAPackage("es.manti.usuarios.api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "es.manti.inmuebles.domain..", "es.manti.inmuebles.infra..",
                        "es.manti.inmuebles.application..")
                .check(CLASES);
    }

    @Test
    void laApiDeInmueblesNoTocaElDominioNiLaInfraestructuraDeUsuarios() {
        noClasses().that().resideInAPackage("es.manti.inmuebles.api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "es.manti.usuarios.domain..", "es.manti.usuarios.infra..",
                        "es.manti.usuarios.application..")
                .check(CLASES);
    }

    @Test
    void laApiDeUsuariosNoDependeDeSuInfraestructura() {
        noClasses().that().resideInAPackage("es.manti.usuarios.api..")
                .should().dependOnClassesThat().resideInAPackage("es.manti.usuarios.infra..")
                .check(CLASES);
    }

    @Test
    void laApiDeInmueblesNoDependeDeSuInfraestructura() {
        noClasses().that().resideInAPackage("es.manti.inmuebles.api..")
                .should().dependOnClassesThat().resideInAPackage("es.manti.inmuebles.infra..")
                .check(CLASES);
    }

    @Test
    void laAplicacionDeInmueblesNoDependeDeApiNiInfra() {
        noClasses().that().resideInAPackage("es.manti.inmuebles.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "es.manti.inmuebles.api..", "es.manti.inmuebles.infra..")
                .check(CLASES);
    }

    @Test
    void sharedNoDependeDeApiNiInfraestructuraDeModulos() {
        noClasses().that().resideInAPackage("es.manti.shared..")
                .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infra..")
                .check(CLASES);
    }
}
