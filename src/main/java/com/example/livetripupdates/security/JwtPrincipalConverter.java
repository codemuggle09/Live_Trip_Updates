package com.example.livetripupdates.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Component
public class JwtPrincipalConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, extractAuthorities(jwt), jwt.getSubject());
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        addRoles(jwt.getClaim("roles"), authorities);
        addScopes(jwt.getClaimAsString("scope"), authorities);
        return authorities;
    }

    private void addRoles(Object claim, Set<GrantedAuthority> authorities) {
        if (claim instanceof Collection<?> roles) {
            roles.stream()
                    .map(String::valueOf)
                    .map(this::roleAuthority)
                    .forEach(authorities::add);
        } else if (claim instanceof String role) {
            authorities.add(roleAuthority(role));
        }
    }

    private void addScopes(String scope, Set<GrantedAuthority> authorities) {
        if (scope == null || scope.isBlank()) {
            return;
        }
        for (String value : scope.split(" ")) {
            if (!value.isBlank()) {
                authorities.add(new SimpleGrantedAuthority("SCOPE_" + value));
            }
        }
    }

    private GrantedAuthority roleAuthority(String role) {
        String normalized = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return new SimpleGrantedAuthority(normalized);
    }
}
