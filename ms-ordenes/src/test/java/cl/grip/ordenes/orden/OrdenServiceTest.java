package cl.grip.ordenes.orden;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class OrdenServiceTest {
    private OrdenRepository repo;
    private ProductosCliente productos;
    private OrdenService service;

    @BeforeEach
    void preparar() {
        repo = mock(OrdenRepository.class);
        productos = mock(ProductosCliente.class);
        service = new OrdenService(repo, productos);
        when(repo.saveAndFlush(any(Orden.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private OrdenApi.Crear orden(String canal, OrdenApi.Entrega entrega, String cupon, OrdenApi.Vendedor vendedor) {
        return new OrdenApi.Crear(
                new OrdenApi.Cliente("Juan", "Perez", "cliente.prueba@gmail.com"),
                List.of(new OrdenApi.Linea("SNK-001", 1)),
                canal, entrega, new OrdenApi.Pago("efectivo", null, null, null), vendedor, cupon);
    }

    private void stockDisponible(String precio, int descuento) {
        when(productos.descontar(anyList())).thenReturn(List.of(
                new ProductosCliente.Reserva("SNK-001", "GRIP Street 90", new BigDecimal(precio), descuento, 1)));
    }

    @Test
    void checkoutFuerzaCanalOnlineYCalculaTotal() {
        stockDisponible("54990", 0);
        OrdenApi.Respuesta r = service.crearCheckout(orden("vendedor", null, null,
                new OrdenApi.Vendedor("Intruso", "1", "x@gmail.com")));
        assertEquals("online", r.canal());
        assertNull(r.vendedor());
        assertEquals(0, new BigDecimal("54990").compareTo(r.total()));
        assertEquals("pagada", r.estado());
    }

    @Test
    void ventaEnTiendaGuardaDatosDelVendedor() {
        stockDisponible("54990", 0);
        OrdenApi.Respuesta r = service.crear(orden("vendedor", null, null,
                new OrdenApi.Vendedor("Carlos Soto", "176543213", "carlos.soto@duoc.cl")));
        assertEquals("vendedor", r.canal());
        assertEquals("Carlos Soto", r.vendedor().nombre());
    }

    @Test
    void aplicaDescuentoDelProductoCuponYEnvio() {
        // 64990 con 15% = 55242 (redondeo); GRIP10 = 5524; envio domicilio 0 porque supera 50000.
        stockDisponible("64990", 15);
        OrdenApi.Respuesta r = service.crearCheckout(orden("online",
                new OrdenApi.Entrega("domicilio", "Tarapaca", "Alto Hospicio", "Calle 1"), "grip10", null));
        assertEquals(0, new BigDecimal("55242").compareTo(r.subtotal()));
        assertEquals(0, new BigDecimal("5524").compareTo(r.descuento()));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.envio()));
        assertEquals(0, new BigDecimal("49718").compareTo(r.total()));
    }

    @Test
    void cobraEnvioBajo50000() {
        stockDisponible("47990", 0);
        OrdenApi.Respuesta r = service.crearCheckout(orden("online",
                new OrdenApi.Entrega("domicilio", "RM", "Santiago", "Calle 1"), null, null));
        assertEquals(0, new BigDecimal("3990").compareTo(r.envio()));
        assertEquals(0, new BigDecimal("51980").compareTo(r.total()));
    }

    @Test
    void propagaStockInsuficienteSinGuardarOrden() {
        when(productos.descontar(anyList()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuficiente para: GRIP Court Pro"));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.crearCheckout(orden("online", null, null, null)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void reponeStockSiNoSePuedeGuardarLaOrden() {
        stockDisponible("54990", 0);
        when(repo.saveAndFlush(any(Orden.class))).thenThrow(new RuntimeException("BD caida"));
        assertThrows(RuntimeException.class, () -> service.crearCheckout(orden("online", null, null, null)));
        verify(productos).reponer(anyList());
    }

    @Test
    void rechazaCuponInvalidoSinTocarStock() {
        assertThrows(ResponseStatusException.class,
                () -> service.crearCheckout(orden("online", null, "FALSO", null)));
        verifyNoInteractions(productos);
    }
}
