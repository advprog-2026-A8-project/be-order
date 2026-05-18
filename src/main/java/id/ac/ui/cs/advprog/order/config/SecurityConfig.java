package id.ac.ui.cs.advprog.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import id.ac.ui.cs.advprog.order.security.RestAccessDeniedHandler;
import id.ac.ui.cs.advprog.order.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private static final String ROLE_PREFIX = "ROLE_";
    private static final String EMPTY = "";

    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    @Value("${order.security.principal-claim:sub}")
    private String principalClaimName;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Safe for this service: API is stateless and uses JWT bearer auth, not cookie-based sessions.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers("/api/orders/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/orders/titiper/**").hasRole("TITIPER")
                        .requestMatchers("/api/orders/jastiper/**").hasRole("JASTIPER")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );

        return http.build();
    }

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthoritiesFromRolesClaim);
        converter.setPrincipalClaimName(resolvePrincipalClaimName());
        return converter;
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${jwt.secret}") String jwtSecret) {
        SecretKey key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private Collection<GrantedAuthority> extractAuthoritiesFromRolesClaim(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        String singleRole = jwt.getClaimAsString("role");

        if (roles == null && singleRole == null) {
            return Collections.emptyList();
        }

        List<String> allRoles = new ArrayList<>();
        if (roles != null) {
            allRoles.addAll(roles);
        }
        if (singleRole != null && !singleRole.isBlank()) {
            allRoles.add(singleRole);
        }

        return allRoles.stream()
                .map(this::normalizeRoleName)
                .filter(role -> !role.isBlank())
                .filter(role -> !role.equals(ROLE_PREFIX))
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    private String normalizeRoleName(String role) {
        if (role == null) {
            return EMPTY;
        }
        String trimmed = role.trim();
        if (trimmed.isEmpty()) {
            return EMPTY;
        }
        return trimmed.startsWith(ROLE_PREFIX) ? trimmed : ROLE_PREFIX + trimmed;
    }

    private String resolvePrincipalClaimName() {
        if (principalClaimName == null) {
            return "sub";
        }
        String normalized = principalClaimName.trim();
        return normalized.isEmpty() ? "sub" : normalized;
    }
}
