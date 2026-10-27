package com.project.goTruck.security.current;

import org.springframework.security.authentication.AbstractAuthenticationToken;

public class CurrentUserAuthenticationToken
        extends AbstractAuthenticationToken {

    private final CurrentUserPrincipal principal;

    public CurrentUserAuthenticationToken(
            CurrentUserPrincipal principal) {

        super(
                principal.authorities()
                        .stream()
                        .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                        .toList()
        );

        this.principal = principal;

        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

}