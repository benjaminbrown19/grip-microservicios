package cl.grip.productos.seguridad;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lee "Authorization: Bearer ..." y, si el JWT es valido, registra al usuario con el rol
 * del claim "rol" (ROLE_ADMINISTRADOR, ROLE_VENDEDOR o ROLE_CLIENTE).
 */
public class JwtFilter extends OncePerRequestFilter {
    private final JwtValidador jwt;

    public JwtFilter(JwtValidador jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                Claims claims = jwt.validarAcceso(auth.substring(7));
                String rol = claims.get("rol", String.class);
                if (rol != null && !rol.isBlank()) {
                    var token = new UsernamePasswordAuthenticationToken(
                            claims.getSubject(), null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + rol.toUpperCase(Locale.ROOT))));
                    SecurityContextHolder.getContext().setAuthentication(token);
                }
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, res);
    }
}
