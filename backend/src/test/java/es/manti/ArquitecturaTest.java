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
     * {@code shared} es transversal: si importa el dominio o la aplicación de un
     * módulo, el módulo deja de ser un módulo y shared se convierte en un cajón
     * de sastre. Los mapeos de dominio viven en el advice de cada módulo y la
     * lógica de negocio en su {@code application}.
     */
    @Test
    void sharedNoDependeDeDominioDeNingunModulo() {
        noClasses().that().resideInAPackage("..shared..")
                .should().dependOnClassesThat().resideInAnyPackage("..domain..", "..application..")
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

    /**
     * FASE 4 — el módulo {@code swipes} comparte ESQUEMA con {@code inmuebles}
     * (lee su tabla en las strings JPQL del deck y de favoritos), pero no su
     * CÓDIGO: la proyección es un record propio y las entidades se referencian
     * por nombre, nunca por import.
     *
     * <p>La regla alcanza al NÚCLEO del módulo (domain, application, infra).
     * La capa {@code api} queda fuera A PROPÓSITO: su único punto de contacto
     * es el DTO público {@code es.manti.inmuebles.api.dto.InmuebleSummary},
     * cuya firma de constructor menciona inevitablemente {@code TipoInmueble} y
     * {@code EstadoInmueble} (el mapper tiene que construirlos para poder
     * devolver el resumen). Ese acoplamiento es api → api y está permitido; lo
     * que esta regla impide es el núcleo de swipes hablando con los internos de
     * inmuebles.</p>
     */
    @Test
    void swipesNoDependeDelDominioNiDeLaInfraestructuraDeInmuebles() {
        noClasses().that().resideInAnyPackage(
                        "es.manti.swipes.domain..",
                        "es.manti.swipes.application..",
                        "es.manti.swipes.infra..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "es.manti.inmuebles.domain..", "es.manti.inmuebles.infra..",
                        "es.manti.inmuebles.application..")
                .check(CLASES);
    }

    /**
     * Ni siquiera la capa {@code api} de swipes puede tocar la infraestructura
     * ni la aplicación de inmuebles: sólo su API pública ({@code api.dto}).
     */
    @Test
    void swipesNoDependeDeLaInfraestructuraNiDeLaAplicacionDeInmuebles() {
        noClasses().that().resideInAPackage("es.manti.swipes..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "es.manti.inmuebles.infra..", "es.manti.inmuebles.application..")
                .check(CLASES);
    }

    /** La regla es de MÓDULO, no sólo de la api: swipes no importa su propia infra. */
    @Test
    void laApiDeSwipesNoDependeDeSuInfraestructura() {
        noClasses().that().resideInAPackage("es.manti.swipes.api..")
                .should().dependOnClassesThat().resideInAPackage("es.manti.swipes.infra..")
                .check(CLASES);
    }
}
