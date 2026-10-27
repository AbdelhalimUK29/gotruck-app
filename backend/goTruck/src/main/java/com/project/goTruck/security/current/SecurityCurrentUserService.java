package com.project.goTruck.security.current;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class SecurityCurrentUserService
        implements CurrentUserService {

    @Override
    public CurrentUserPrincipal currentUser() {

        CurrentUserAuthenticationToken authentication =
                (CurrentUserAuthenticationToken)
                        SecurityContextHolder
                                .getContext()
                                .getAuthentication();

        return (CurrentUserPrincipal)
                authentication.getPrincipal();

    }

}