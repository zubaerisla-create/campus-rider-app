package campusrider.security;

import campusrider.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Spring Security setup.
 *
 * - Authentication: session cookie (CR_SESSION, HttpOnly, SameSite=Lax) holding an
 *   {@link AuthUser}; created by AuthController after BCrypt password verification.
 * - CSRF: cookie-to-header token for every POST / PUT / DELETE.
 * - Authorization (role-based): passenger-only and driver-only URL groups below, plus
 *   ownership checks inside the services (a passenger only sees their own rides,
 *   a driver only rides offered or assigned to their rickshaw).
 * - Errors are JSON (401 / 403), never redirects to a login page.
 */
@Configuration
public class SecurityConfig {

    private static final String PASSENGER = "PASSENGER";
    private static final String DRIVER = "DRIVER";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repo.setCookiePath("/");
        repo.setCookieCustomizer(c -> c.sameSite("Lax"));
        return repo;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper mapper,
                                           SecurityContextRepository contextRepository,
                                           CookieCsrfTokenRepository csrfRepository) throws Exception {
        http
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .cors(cors -> {
                })
                .securityContext(sc -> sc.securityContextRepository(contextRepository))
                .sessionManagement(s -> s.sessionFixation(f -> f.changeSessionId()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(h -> h
                        .frameOptions(f -> f.deny())
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health", "/api/map", "/api/locations", "/api/locations/**",
                                "/api/routes/**").permitAll()
                        .requestMatchers("/api/passengers/**").hasRole(PASSENGER)
                        .requestMatchers("/api/drivers/**").hasRole(DRIVER)
                        .requestMatchers(HttpMethod.GET, "/api/rides/available").hasRole(PASSENGER)
                        .requestMatchers(HttpMethod.POST, "/api/rides/request", "/api/rides/*/cancel").hasRole(PASSENGER)
                        .requestMatchers(HttpMethod.POST, "/api/rides/*/accept", "/api/rides/*/reject",
                                "/api/rides/*/start", "/api/rides/*/complete").hasRole(DRIVER)
                        .requestMatchers(HttpMethod.GET, "/api/rides/*", "/api/tracking/**").authenticated()
                        .requestMatchers("/ws/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                writeError(res, mapper, HttpServletResponse.SC_UNAUTHORIZED, "Please sign in to continue."))
                        .accessDeniedHandler((req, res, ex) ->
                                writeError(res, mapper, HttpServletResponse.SC_FORBIDDEN,
                                        "You are not allowed to do that. (If you just signed in, refresh the page.)")));
        return http.build();
    }

    /** Only needed when the frontend is served from a different origin than the API (no dev proxy). */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private static void writeError(HttpServletResponse res, ObjectMapper mapper, int status, String message)
            throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), Map.of("status", status, "message", message));
    }
}
