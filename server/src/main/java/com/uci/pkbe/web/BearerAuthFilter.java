package com.uci.pkbe.web;

import com.uci.pkbe.store.SessionStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class BearerAuthFilter extends OncePerRequestFilter {

    public static final String USERNAME_ATTR = "pkbe.username";
    public static final String TOKEN_ATTR = "pkbe.token";

    private final SessionStore sessionStore;

    public BearerAuthFilter(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return path.equals("/v1/login")
                || path.equals("/v1/public-config")
                || path.startsWith("/.well-known/")
                || path.equals("/health")
                || path.equals("/webview")
                || path.startsWith("/webview/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.equals("/swagger-ui.html");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            unauthorized(response);
            return;
        }
        String token = header.substring("Bearer ".length()).trim();
        var username = sessionStore.findUsername(token);
        if (username.isEmpty()) {
            unauthorized(response);
            return;
        }
        request.setAttribute(USERNAME_ATTR, username.get());
        request.setAttribute(TOKEN_ATTR, token);
        filterChain.doFilter(request, response);
    }

    private static void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid session\"}");
    }
}
