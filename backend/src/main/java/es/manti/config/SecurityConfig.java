package es.manti.config;

import es.manti.shared.config.BodySizeLimitFilter;
import es.manti.usuarios.api.GoogleOAuth2FailureHandler;
import es.manti.usuarios.api.GoogleOAuth2SuccessHandler;
import es.manti.usuarios.application.AuthProperties;
import es.manti.usuarios.application.IntentosLogin;
import es.manti.usuarios.application.JwtService;
import es.manti.usuarios.application.LoginProteccionProperties;
import es.manti.usuarios.application.PasswordResetProperties;
import es.manti.usuarios.infra.filter.AccountRateLimitFilter;
import es.manti.usuarios.infra.filter.AuthRateLimitFilter;
import es.manti.usuarios.infra.filter.CookieEndpointCsrfFilter;
import es.manti.shared.error.ProblemAccessDeniedHandler;
import es.manti.shared.error.ProblemAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Cadena de seguridad (Spring Security 7).
 *
 * <ul>
 *   <li>Abiertos: rutas de autenticación, GET de {@code /api/v1/inmuebles}
 *       (portal público, FASE 2), {@code /actuator/health} y springdoc. El
 *       resto de {@code /actuator} (metrics, prometheus...) exige rol ADMIN.</li>
 *   <li>Todo lo demás autenticado con Bearer JWT (resource server) — sin
 *       {@code httpBasic} ni {@code formLogin}.</li>
 *   <li>CORS: allowlist desde {@code ALLOWED_ORIGINS} con credenciales
 *       (nunca {@code *}).</li>
 *   <li>CSRF: deshabilitado SÓLO porque los endpoints con cookie exigen la
 *       cabecera {@code X-Requested-With: XMLHttpRequest}
 *       ({@link CookieEndpointCsrfFilter}); se documenta en el PR.</li>
 *   <li>Rate limit en memoria por IP para login/registro
 *       ({@link AuthRateLimitFilter}) y, además en login, backoff por cuenta +
 *       ventana por par sha256(IP+email) (#10/#5,
 *       {@link AccountRateLimitFilter}).</li>
 *   <li>Límite de tamaño de cuerpo: {@code Content-Length} &gt; 256 KiB → 413
 *       ({@link BodySizeLimitFilter}).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class,
        PasswordResetProperties.class, LoginProteccionProperties.class})
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthProperties authProperties;
    private final CorsProperties corsProperties;
    private final LoginProteccionProperties loginProteccionProperties;

    /**
     * Firewall con {@code ;} permitido SÓLO bajo {@code /api/v1/auth/}:
     * los filtros de auth normalizan la ruta (ver {@link AuthPathParamsHttpFirewall}),
     * de modo que {@code /login;foo=1} y variantes percent-encoded caen en la
     * misma lógica de rate limit/CSRF que la ruta canónica. El resto de la app
     * conserva StrictHttpFirewall por defecto.
     */
    @Bean
    WebSecurityCustomizer authPathParamsFirewall() {
        return web -> web.httpFirewall(new AuthPathParamsHttpFirewall());
    }

    // Los handlers se inyectan como parámetros del @Bean (no en el constructor
    // de la configuración) para evitar un ciclo: SecurityConfig → handler →
    // AuthService → PasswordEncoder (@Bean de esta misma clase). IntentosLogin
    // no participa en ese ciclo (no depende de PasswordEncoder).
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            GoogleOAuth2SuccessHandler googleSuccessHandler,
                                            GoogleOAuth2FailureHandler googleFailureHandler,
                                            IntentosLogin intentosLogin)
            throws Exception {
        ProblemAuthenticationEntryPoint authenticationEntryPoint =
                new ProblemAuthenticationEntryPoint();
        ProblemAccessDeniedHandler accessDeniedHandler = new ProblemAccessDeniedHandler();

        http
                // CSRF deshabilitado a propósito: protección equivalente por cabecera
                // X-Requested-With en /refresh y /logout (CookieEndpointCsrfFilter).
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Sesiones sólo durante el baile OAuth2 de Google; el resto es stateless.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/oauth2/token",
                                // issue #57 — restablecimiento de contraseña:
                                // anónimos a propósito; el "factor" es el enlace
                                // de un solo uso que sólo llega por email.
                                "/api/v1/auth/password/forgot",
                                "/api/v1/auth/password/reset").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        // FASE 2 — lectura PÚBLICA del portal de inmuebles.
                        // SÓLO GET, SÓLO el recurso de lectura y SÓLO id numérico:
                        // los escritos del backoffice viven en
                        // /api/v1/admin/inmuebles (rol ADMIN vía @PreAuthorize) y
                        // no existe ningún POST/PUT/DELETE bajo
                        // /api/v1/inmuebles (un anónimo que lo intente recibe 401
                        // aquí; con token, 405 al no haber mapping). El patrón
                        // {id:[0-9]+} acota el comodín a PathVariables de tipo
                        // Long: así no se abren por error rutas como
                        // /api/v1/inmuebles/<cualquier-cosa> (esas caen en 401
                        // en vez de devolver un 404 de la app).
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/inmuebles",
                                "/api/v1/inmuebles/{id:[0-9]+}").permitAll()
                        // issue #12 — PRECIOS DE LA ZONA (MIVAU): datos abiertos
                        // del Ministerio, consulta PÚBLICA de sólo lectura.
                        // Sólo este matcher y sólo GET: la reimportación de los
                        // CSV vive en /api/v1/admin/mercado con @PreAuthorize
                        // ADMIN y no se abre aquí (cualquiera de sus métodos
                        // cae en .anyRequest().authenticated() → 401).
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/mercado").permitAll()
                        // FASE 5 — PORTAL PÚBLICO de profesionales y solicitudes.
                        // SÓLO estos tres matchers y SIEMPRE con método explícito,
                        // para no abrir por error nada más:
                        //   * catálogo de oficios (GET) — estático, cacheado 60 s;
                        //   * solicitud de servicio (POST) — anónima y con rate
                        //     limit de 10/hora por IP (AuthRateLimitFilter);
                        //   * registro de profesional (POST) — anónimo, rate
                        //     limit de 5/hora por IP.
                        // El backoffice /api/v1/admin/** NO se abre aquí: exige
                        // Bearer con rol ADMIN (@PreAuthorize en cada controller)
                        // y cualquier otra ruta cae en .anyRequest().authenticated().
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/gremios").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/solicitudes",
                                "/api/v1/profesionales/registro").permitAll()
                        // /api/v1/public/** queda sin abrir hasta que exista el
                        // módulo de contenido público con consumidores reales.
                        // FASE 7 (issue #25) — ACTUADOR:
                        //   * /actuator/health SÍ sigue anónimo (lo usan el
                        //     healthcheck del hosting y docker);
                        //   * TODO lo demás bajo /actuator (metrics, prometheus,
                        //     info, env...) exige rol ADMIN: no se publican p50/p95
                        //     ni el estado interno a cualquiera. Orden importa:
                        //     el matcher del health va ANTES del comodín.
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // springdoc: /v3/api-docs (+ .yaml/.yml/.json y swagger-config)
                        .requestMatchers("/v3/api-docs", "/v3/api-docs.*", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/oauth2/authorization/google")
                        .successHandler(googleSuccessHandler)
                        .failureHandler(googleFailureHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthoritiesConverter()))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .headers(headers -> headers
                        .addHeaderWriter(new StaticHeadersWriter("Referrer-Policy", "no-referrer")))
                // Filtros propios antes de la comprobación de autorización:
                // límite de tamaño de cuerpo (413), rate limit por IP, CSRF
                // por cabecera en los endpoints con cookie y la protección de
                // login por cuenta/par (#10/#5). El de cuerpo va PRIMERO: se
                // rechaza el payload antes de contar el rate limit y antes de
                // que el filtro de login lea el cuerpo para extraer el email.
                .addFilterBefore(new BodySizeLimitFilter(), AuthorizationFilter.class)
                .addFilterBefore(new CookieEndpointCsrfFilter(), AuthorizationFilter.class)
                .addFilterBefore(new AuthRateLimitFilter(authProperties), AuthorizationFilter.class)
                .addFilterBefore(new AccountRateLimitFilter(intentosLogin, loginProteccionProperties),
                        AuthorizationFilter.class);

        return http.build();
    }

    /** Allowlist explícita de orígenes con credenciales permitidas (sin `*`). */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.origenesNormalizados());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT,
                "X-Requested-With", "Idempotency-Key"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /** Mapea el claim {@code rol} del JWT a autoridades {@code ROLE_*}. */
    private static JwtAuthenticationConverter jwtAuthoritiesConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String rol = jwt.getClaimAsString("rol");
            if (rol == null || rol.isBlank()) {
                return List.of();
            }
            return List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_" + rol));
        });
        return converter;
    }

    /** BCrypt para passwords; el decoder JWT reutiliza el servicio de firma. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtDecoder jwtDecoder(JwtService jwtService) {
        return jwtService.decoder();
    }
}
