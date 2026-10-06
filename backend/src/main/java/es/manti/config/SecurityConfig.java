package es.manti.config;

import es.manti.usuarios.api.GoogleOAuth2FailureHandler;
import es.manti.usuarios.api.GoogleOAuth2SuccessHandler;
import es.manti.usuarios.application.AuthProperties;
import es.manti.usuarios.application.JwtService;
import es.manti.usuarios.infra.filter.AuthRateLimitFilter;
import es.manti.usuarios.infra.filter.CookieEndpointCsrfFilter;
import es.manti.shared.error.ProblemAccessDeniedHandler;
import es.manti.shared.error.ProblemAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
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
 *   <li>Abiertos: rutas de autenticación, {@code /api/v1/public/**},
 *       {@code /actuator/health} y springdoc.</li>
 *   <li>Todo lo demás autenticado con Bearer JWT (resource server) — sin
 *       {@code httpBasic} ni {@code formLogin}.</li>
 *   <li>CORS: allowlist desde {@code ALLOWED_ORIGINS} con credenciales
 *       (nunca {@code *}).</li>
 *   <li>CSRF: deshabilitado SÓLO porque los endpoints con cookie exigen la
 *       cabecera {@code X-Requested-With: XMLHttpRequest}
 *       ({@link CookieEndpointCsrfFilter}); se documenta en el PR.</li>
 *   <li>Rate limit en memoria por IP para login/registro
 *       ({@link AuthRateLimitFilter}).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class})
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthProperties authProperties;
    private final CorsProperties corsProperties;

    // Los handlers se inyectan como parámetros del @Bean (no en el constructor
    // de la configuración) para evitar un ciclo: SecurityConfig → handler →
    // AuthService → PasswordEncoder (@Bean de esta misma clase).
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            GoogleOAuth2SuccessHandler googleSuccessHandler,
                                            GoogleOAuth2FailureHandler googleFailureHandler)
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
                                "/api/v1/auth/oauth2/token").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers("/api/v1/public/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
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
                // rate limit por IP y CSRF por cabecera en los endpoints con cookie.
                .addFilterBefore(new CookieEndpointCsrfFilter(), AuthorizationFilter.class)
                .addFilterBefore(new AuthRateLimitFilter(authProperties), AuthorizationFilter.class);

        return http.build();
    }

    /** Allowlist explícita de orígenes con credenciales permitidas (sin `*`). */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins().stream()
                .map(String::trim)
                .filter(origen -> !origen.isEmpty())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT, "X-Requested-With"));
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
