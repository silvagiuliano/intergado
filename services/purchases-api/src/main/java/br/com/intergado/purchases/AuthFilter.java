package br.com.intergado.purchases;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Requires a valid session cookie on every /api route except the login steps, and the role the route needs (Role.required). */
@Component
public class AuthFilter extends OncePerRequestFilter {
    private static final Set<String> PUBLIC = Set.of("/api/auth/request-code", "/api/auth/verify", "/api/auth/logout");
    private final JdbcTemplate jdbc;

    public AuthFilter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/") || PUBLIC.contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = Arrays.stream(request.getCookies() == null ? new Cookie[0] : request.getCookies())
            .filter(cookie -> AuthController.COOKIE.equals(cookie.getName())).map(Cookie::getValue)
            .findFirst().orElse(null);
        // The role is read on every request, so a change on the Users screen applies immediately.
        var user = token == null ? null : jdbc.query("""
            SELECT s.email, u.role FROM sessions s JOIN users u ON u.email = s.email AND u.active = 1
            WHERE s.token_hash = ? AND s.expires_at > %s
            """.formatted(AuthController.NOW), (rs, row) -> Map.entry(rs.getString(1), Role.valueOf(rs.getString(2))),
            AuthController.sha256(token)).stream().findFirst().orElse(null);
        if (user == null) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Sessão inválida ou expirada");
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Role required = Role.required(request.getMethod(), path);
        if (!user.getValue().includes(required)) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, "Seu perfil (" + user.getValue().label + ") não permite esta operação");
            return;
        }
        request.setAttribute(AuthController.EMAIL_ATTRIBUTE, user.getKey());
        request.setAttribute(AuthController.ROLE_ATTRIBUTE, user.getValue());
        chain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
