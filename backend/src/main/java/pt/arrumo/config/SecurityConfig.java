package pt.arrumo.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import pt.arrumo.domain.AppUserRepo;
import pt.arrumo.security.Encryptor;
import pt.arrumo.security.SessionCheckFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Only resources from this server: blocks injected scripts and pages that try to embed the app. */
    static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
            + "font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'";

    /** BCrypt by default; the {bcrypt} prefix in the hash lets us switch algorithms later without migrations. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    Encryptor encryptor(AppProperties props) {
        return new Encryptor(props.encryptionKey());
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, SecurityContextRepository context,
                                  AppUserRepo users, AppProperties props) throws Exception {
        // CSRF token in a cookie the app's own JavaScript can read; it sends it back in the X-XSRF-TOKEN header.
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookieCustomizer(c -> c.sameSite("Strict").secure(props.secureCookie()));

        http
            .securityContext(c -> c.securityContextRepository(context))
            .csrf(c -> c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .authorizeHttpRequests(a -> a
                .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                // AuthController checks the half-finished login itself (password accepted, 2FA still missing, etc.)
                .requestMatchers("/api/auth/csrf", "/api/auth/login", "/api/auth/mfa", "/api/auth/mfa/start",
                                 "/api/auth/mfa/confirm", "/api/auth/password").permitAll()
                .requestMatchers("/api/**").authenticated()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/**").denyAll()
                .anyRequest().permitAll()) // static UI files
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .logout(l -> l.logoutUrl("/api/auth/logout")
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                .deleteCookies("arrumo_session"))
            .headers(h -> h
                .contentSecurityPolicy(c -> c.policyDirectives(CSP))
                .frameOptions(f -> f.deny())
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000)))
            .addFilterBefore(new SessionCheckFilter(users, props.maxSessionDuration()), AuthorizationFilter.class);
        return http.build();
    }
}
