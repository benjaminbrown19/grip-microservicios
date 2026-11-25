package cl.grip.usuarios.seguridad;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private static final String SECRETO = "clave-de-prueba-con-mas-de-32-caracteres-123";

    private Usuario usuario() {
        Usuario u = new Usuario();
        u.setCorreo("ana.perez@duoc.cl");
        u.setNombre("Ana");
        u.setRol("administrador");
        return u;
    }

    @Test
    void emiteTokenDeAccesoConCorreoYRol() {
        JwtService jwt = new JwtService(SECRETO, 900000, 604800000);
        var claims = jwt.validar(jwt.emitir(usuario(), false), "access");
        assertEquals("ana.perez@duoc.cl", claims.getSubject());
        assertEquals("administrador", claims.get("rol", String.class));
    }

    @Test
    void rechazaRefreshTokenUsadoComoAcceso() {
        JwtService jwt = new JwtService(SECRETO, 900000, 604800000);
        String refresh = jwt.emitir(usuario(), true);
        assertThrows(IllegalArgumentException.class, () -> jwt.validar(refresh, "access"));
    }

    @Test
    void rechazaTokenFirmadoConOtraClave() {
        JwtService emisor = new JwtService(SECRETO, 900000, 604800000);
        JwtService otro = new JwtService("otra-clave-distinta-con-mas-de-32-caracteres", 900000, 604800000);
        String token = emisor.emitir(usuario(), false);
        assertThrows(Exception.class, () -> otro.validar(token, "access"));
    }

    @Test
    void rechazaTokenVencido() {
        JwtService jwt = new JwtService(SECRETO, -1000, -1000);
        String token = jwt.emitir(usuario(), false);
        assertThrows(Exception.class, () -> jwt.validar(token, "access"));
    }

    @Test
    void exigeSecretoDeAlMenos32Bytes() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService("corta", 1, 1));
    }
}
