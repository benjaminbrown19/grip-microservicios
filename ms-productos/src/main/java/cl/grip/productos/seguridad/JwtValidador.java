package cl.grip.productos.seguridad;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Valida los JWT emitidos por ms-usuarios. Este servicio no emite tokens:
 * solo comprueba la firma, la expiracion y que sea un token de acceso.
 */
@Component
public class JwtValidador {
    private final SecretKey key;

    public JwtValidador(@Value("${grip.jwt.secret}") String secret) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("GRIP_JWT_SECRET debe tener al menos 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Claims validarAcceso(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        if (!"access".equals(claims.get("tipo", String.class))) {
            throw new IllegalArgumentException("Tipo de token invalido");
        }
        return claims;
    }
}
