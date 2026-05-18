package id.ac.ui.cs.advprog.order.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import id.ac.ui.cs.advprog.order.security.RestAccessDeniedHandler;
import id.ac.ui.cs.advprog.order.security.RestAuthenticationEntryPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void jwtConverterShouldReturnEmptyAuthoritiesWhenNoRoleClaims() {
        Jwt jwt = buildJwt(Map.of("sub", "user-1"));
        AbstractAuthenticationToken token = securityConfig.jwtAuthenticationConverter().convert(jwt);
        assertEquals(0, token.getAuthorities().size());
    }

    @Test
    void jwtConverterShouldNormalizeAndDeduplicateAuthorities() {
        Jwt jwt = buildJwt(Map.of(
                "roles", List.of("ADMIN", "ROLE_ADMIN", "  TITIPER  "),
                "role", "ROLE_TITIPER"
        ));

        AbstractAuthenticationToken token = securityConfig.jwtAuthenticationConverter().convert(jwt);
        Set<String> authorities = token.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertEquals(Set.of("ROLE_ADMIN", "ROLE_TITIPER"), authorities);
    }

    @Test
    void jwtDecoderShouldBeCreated() {
        assertNotNull(securityConfig.jwtDecoder("0123456789abcdef0123456789abcdef"));
    }

    @Test
    void jwtConverterShouldIgnoreBlankAndRolePrefixOnlyAuthorities() {
        Jwt jwt = buildJwt(Map.of(
                "roles", List.of("ADMIN", " ", "ROLE_")
        ));

        AbstractAuthenticationToken token = securityConfig.jwtAuthenticationConverter().convert(jwt);
        Set<String> authorities = token.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertEquals(Set.of("ROLE_ADMIN"), authorities);
    }

    @Test
    void jwtConverterShouldFallbackToSubWhenPrincipalClaimNullOrBlank() {
        Jwt jwt = buildJwt(Map.of("sub", "subject-user", "role", "ADMIN"));

        ReflectionTestUtils.setField(securityConfig, "principalClaimName", null);
        AbstractAuthenticationToken fromNull = securityConfig.jwtAuthenticationConverter().convert(jwt);
        assertEquals("subject-user", fromNull.getName());

        ReflectionTestUtils.setField(securityConfig, "principalClaimName", "   ");
        AbstractAuthenticationToken fromBlank = securityConfig.jwtAuthenticationConverter().convert(jwt);
        assertEquals("subject-user", fromBlank.getName());
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
