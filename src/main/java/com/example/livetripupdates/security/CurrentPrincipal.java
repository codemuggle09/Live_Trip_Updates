package com.example.livetripupdates.security;

import com.example.livetripupdates.domain.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.security.Principal;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record CurrentPrincipal(String userId, String tenantId, Set<UserRole> roles) {

    public static CurrentPrincipal from(Principal principal) {
        if (principal instanceof JwtAuthenticationToken jwtAuthentication) {
            Jwt jwt = jwtAuthentication.getToken();
            String userId = firstNonBlank(jwt.getClaimAsString("user_id"), jwt.getSubject());
            String tenantId = jwt.getClaimAsString("tenant_id");
            Set<UserRole> roles = jwtAuthentication.getAuthorities().stream()
                    .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                    .map(CurrentPrincipal::parseRole)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toUnmodifiableSet());
            return new CurrentPrincipal(userId, tenantId, roles);
        }
        if (principal instanceof Authentication authentication) {
            Set<UserRole> roles = authentication.getAuthorities().stream()
                    .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                    .map(CurrentPrincipal::parseRole)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toUnmodifiableSet());
            return new CurrentPrincipal(authentication.getName(), null, roles);
        }
        throw new IllegalArgumentException("Unsupported principal type");
    }

    public boolean hasRole(UserRole role) {
        return roles.contains(role);
    }

    public boolean isComplete() {
        return userId != null && !userId.isBlank()
                && tenantId != null && !tenantId.isBlank()
                && !roles.isEmpty();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private static UserRole parseRole(String value) {
        return Arrays.stream(UserRole.values())
                .filter(role -> role.name().equals(value))
                .findFirst()
                .orElse(null);
    }
}
