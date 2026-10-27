package com.project.goTruck.security.converter;

import com.project.goTruck.security.current.CurrentUserAuthenticationToken;
import com.project.goTruck.security.current.CurrentUserPrincipal;
import com.project.goTruck.security.current.CurrentUserPrincipalMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GoTruckJwtAuthenticationConverter
        implements Converter<Jwt, CurrentUserAuthenticationToken> {

    private final RealmRoleConverter realmRoleConverter;

    private final CurrentUserPrincipalMapper principalMapper;

    @Override
    public CurrentUserAuthenticationToken convert(Jwt jwt) {

        Set<String> authorities =
                realmRoleConverter.convert(jwt)
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet());

        CurrentUserPrincipal principal =
                principalMapper.fromJwt(jwt, authorities);

        return new CurrentUserAuthenticationToken(principal);

    }

}