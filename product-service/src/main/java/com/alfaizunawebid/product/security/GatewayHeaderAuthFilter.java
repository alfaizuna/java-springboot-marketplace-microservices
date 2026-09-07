package com.alfaizunawebid.product.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads the X-User-Email and X-User-Role headers injected by the API Gateway
 * and stores them as request attributes for downstream use by controllers.
 * <p>
 * Product Service does NOT validate JWT directly — trust is delegated to the Gateway.
 * </p>
 */
@Slf4j
@Component
public class GatewayHeaderAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_USER_ROLE = "X-User-Role";

    public static final String ATTR_USER_EMAIL = "userEmail";
    public static final String ATTR_USER_ROLE = "userRole";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String userEmail = request.getHeader(HEADER_USER_EMAIL);
        String userRole = request.getHeader(HEADER_USER_ROLE);

        if (userEmail != null) {
            request.setAttribute(ATTR_USER_EMAIL, userEmail);
            log.debug("Authenticated user from gateway header: email={}, role={}", userEmail, userRole);
        }

        if (userRole != null) {
            request.setAttribute(ATTR_USER_ROLE, userRole);
        }

        filterChain.doFilter(request, response);
    }
}
