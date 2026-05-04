package id.ac.ui.cs.advprog.order.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import id.ac.ui.cs.advprog.order.security.RestAccessDeniedHandler;
import id.ac.ui.cs.advprog.order.security.RestAuthenticationEntryPoint;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final SecurityConfig securityConfig = new SecurityConfig(
            new RestAuthenticationEntryPoint(objectMapper),
            new RestAccessDeniedHandler(objectMapper)
    );

    @Test
    void jwtConverterShouldMapRolesClaimList() {
        Jwt jwt = buildJwt(Map.of("roles", java.util.List.of("ADMIN", "TITIPER")));

        AbstractAuthenticationToken token = securityConfig.jwtAuthenticationConverter().convert(jwt);
        Set<String> authorities = token.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertTrue(authorities.contains("ROLE_ADMIN"));
        assertTrue(authorities.contains("ROLE_TITIPER"));
    }

    @Test
    void jwtConverterShouldMapSingleRoleClaim() {
        Jwt jwt = buildJwt(Map.of("role", "JASTIPER"));

        AbstractAuthenticationToken token = securityConfig.jwtAuthenticationConverter().convert(jwt);
        Set<String> authorities = token.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertTrue(authorities.contains("ROLE_JASTIPER"));
    }

    private Jwt buildJwt(Map<String, Object> claims) {
        return new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                claims
        );
    }
}
