package es.manti;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import es.manti.inmuebles.domain.EstadoInmueble;
import es.manti.inmuebles.domain.TipoInmueble;
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
     * <p>Esta regla es del NÚCLEO del módulo (domain, application, infra) y es
     * absoluta: ni una sola clase del dominio ajeno, tampoco los enums. Lo que
     * la capa {@code api} puede tocar — sólo enums, nada más — lo mira
     * {@link #swipesNoDependeDelDominioDeInmueblesSalvoLosDosEnums()}.</p>
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

    /**
     * FASE 4 — <b>api → dominio ajeno: sólo los enums pinados
     * {@code TipoInmueble}/{@code EstadoInmueble} (uso interno del mapper de
     * {@code InmuebleSummary})</b>. El resto del paquete
     * {@code es.manti.inmuebles.domain..} —la entidad {@code Inmueble}, las
     * Specification, lo que sea— queda prohibido para TODO el módulo
     * {@code swipes}, capa {@code api} incluida.
     *
     * <p>La excepción es inevitable y está justificada: MapStruct genera
     * {@code SwipeMapperImpl}, cuyo método {@code toSummary} debe instanciar
     * {@code InmuebleSummary}, un record cuya firma expone esos dos enums. La
     * unicidad de la excepción la garantiza el predicado, que está escrito
     * contra los FQCN concretos: si mañana aparece una tercera clase del
     * dominio de inmuebles en swipes, la regla muerde.</p>
     */
    @Test
    void swipesNoDependeDelDominioDeInmueblesSalvoLosDosEnums() {
        DescribedPredicate<JavaClass> dominioDeInmuebles =
                JavaClass.Predicates.resideInAPackage("es.manti.inmuebles.domain..");
        DescribedPredicate<JavaClass> enumsPinados =
                JavaClass.Predicates.belongToAnyOf(TipoInmueble.class, EstadoInmueble.class);

        noClasses().that().resideInAPackage("es.manti.swipes..")
                .should().dependOnClassesThat(
                        dominioDeInmuebles.and(DescribedPredicate.not(enumsPinados)))
                .because("la api de swipes sólo puede usar el DTO público InmuebleSummary; "
                        + "de inmuebles.domain sólo se admiten los enums pinados "
                        + "TipoInmueble y EstadoInmueble que exige la firma del record")
                .check(CLASES);
    }

    /** La regla es de MÓDULO, no sólo de la api: swipes no importa su propia infra. */
    @Test
    void laApiDeSwipesNoDependeDeSuInfraestructura() {
        noClasses().that().resideInAPackage("es.manti.swipes.api..")
                .should().dependOnClassesThat().resideInAPackage("es.manti.swipes.infra..")
                .check(CLASES);
    }

    // ------------------------------------------------------------------
    // FASE 5 — módulo profesionales (gremios, solicitudes y outbox)
    // ------------------------------------------------------------------

    /**
     * El núcleo de profesionales (domain, application, infra) es AUTÓNOMO: ni
     * una sola clase de los demás módulos. Comparte ESQUEMA con nadie, así que
     * la regla es absoluta, sin enums pinados. La capa {@code api} entra en la
     * regla: sólo puede ver su propio módulo (y {@code shared}).
     */
    @Test
    void profesionalesNoDependeDeNingunOtroModulo() {
        noClasses().that().resideInAnyPackage(
                        "es.manti.profesionales.api..",
                        "es.manti.profesionales.domain..",
                        "es.manti.profesionales.application..",
                        "es.manti.profesionales.infra..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "es.manti.inmuebles.domain..", "es.manti.inmuebles.infra..",
                        "es.manti.inmuebles.application..",
                        "es.manti.usuarios.domain..", "es.manti.usuarios.infra..",
                        "es.manti.usuarios.application..",
                        "es.manti.swipes.domain..", "es.manti.swipes.infra..",
                        "es.manti.swipes.application..")
                .check(CLASES);
    }

    /** La api de profesionales sólo ve su application y su domain, nunca su infra. */
    @Test
    void laApiDeProfesionalesNoDependeDeSuInfraestructura() {
        noClasses().that().resideInAPackage("es.manti.profesionales.api..")
                .should().dependOnClassesThat().resideInAPackage("es.manti.profesionales.infra..")
                .check(CLASES);
    }
}
