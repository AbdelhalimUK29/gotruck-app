package com.project.goTruck.security.filter;

import com.project.goTruck.entity.user.User;
import com.project.goTruck.repository.UserRepository;
import com.project.goTruck.security.current.CurrentUserPrincipal;
import com.project.goTruck.security.current.CurrentUserService;
import com.project.goTruck.security.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {

    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            CurrentUserPrincipal currentUser = currentUserService.currentUser();
            UUID keycloakId = currentUser.keycloakId();

            if (keycloakId != null) {
                userRepository.findByKeycloakId(keycloakId)
                        .filter(user -> user.getCompany() != null)
                        .map(user -> user.getCompany().getId())
                        .ifPresent(TenantContext::setCurrentCompanyId);
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}