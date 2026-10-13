package es.manti.support;

import java.util.List;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.MergedContextConfiguration;

/**
 * Registra (via META-INF/spring.factories, issue #28) un ContextCustomizer
 * para TODOS los contextos @SpringBootTest, sin tocar las ~30 clases de test
 * existentes. Boot adapta cada customizer a un ApplicationContextInitializer:
 * se ejecuta despues de cargar application-test.yml y ANTES de crear el
 * DataSource, que es el momento en que {@link TestDatabaseBootstrap} puede
 * asegurar la BD de tests.
 */
public class TestDatabaseContextCustomizerFactory implements ContextCustomizerFactory {

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass,
                                                     List<ContextConfigurationAttributes> configs) {
        return new TestDatabaseContextCustomizer();
    }

    /**
     * Stateless: equals/hashCode por tipo para no invalidar la cache de
     * contextos de Spring Test entre clases de test equivalentes.
     */
    static final class TestDatabaseContextCustomizer implements ContextCustomizer {

        @Override
        public void customizeContext(ConfigurableApplicationContext context,
                                     MergedContextConfiguration mergedConfig) {
            Environment env = context.getEnvironment();
            if (!perfilTestActivo(env)) {
                return; // fuera del perfil test: esta suite jamas prepara BDs
            }
            String url = env.getProperty("spring.datasource.url");
            if (url == null || !url.startsWith("jdbc:postgresql:")) {
                return; // datasource no soportado por el bootstrap: no tocar nada
            }
            TestDatabaseBootstrap.asegurar(
                    env.getProperty("TEST_ADMIN_URL", "jdbc:postgresql://localhost:5433/postgres"),
                    env.getProperty("TEST_ADMIN_USER", "manti"),
                    env.getProperty("TEST_ADMIN_PASSWORD", "manti_dev"),
                    nombreDeBase(url),
                    env.getProperty("spring.datasource.username", ""),
                    env.getProperty("spring.datasource.password", ""));
        }

        private static boolean perfilTestActivo(Environment env) {
            for (String perfil : env.getActiveProfiles()) {
                if ("test".equals(perfil)) {
                    return true;
                }
            }
            return false;
        }

        /** jdbc:postgresql://host:puerto/nombre?opts -&gt; nombre. */
        static String nombreDeBase(String url) {
            String sinOpciones = url.substring(url.lastIndexOf('/') + 1);
            int interrogacion = sinOpciones.indexOf('?');
            return (interrogacion >= 0 ? sinOpciones.substring(0, interrogacion) : sinOpciones).trim();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof TestDatabaseContextCustomizer;
        }

        @Override
        public int hashCode() {
            return TestDatabaseContextCustomizer.class.hashCode();
        }
    }
}