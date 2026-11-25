package cl.grip.productos.stock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import cl.grip.productos.producto.Producto;
import cl.grip.productos.producto.ProductoRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class StockServiceTest {
    private Producto producto(String codigo, int stock, int descuento) {
        Producto p = new Producto();
        p.setCodigo(codigo);
        p.setNombre("GRIP Street 90");
        p.setPrecio(new BigDecimal("54990"));
        p.setStock(stock);
        p.setDescuento(descuento);
        return p;
    }

    @Test
    void descuentaStockYDevuelveDatosDelProducto() {
        ProductoRepository repo = mock(ProductoRepository.class);
        Producto p = producto("SNK-001", 17, 0);
        when(repo.findByCodigoForUpdate("SNK-001")).thenReturn(Optional.of(p));

        List<StockApi.Reserva> r = new StockService(repo).descontar(List.of(new StockApi.Linea("SNK-001", 1)));

        assertEquals(16, p.getStock());
        assertEquals(1, r.size());
        assertEquals(new BigDecimal("54990"), r.get(0).precio());
        verify(repo).saveAndFlush(p);
    }

    @Test
    void rechazaVentaMayorAlStockConConflicto() {
        ProductoRepository repo = mock(ProductoRepository.class);
        Producto p = producto("SNK-003", 4, 0);
        when(repo.findByCodigoForUpdate("SNK-003")).thenReturn(Optional.of(p));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> new StockService(repo).descontar(List.of(new StockApi.Linea("SNK-003", 6))));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(4, p.getStock());
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void rechazaProductoInexistente() {
        ProductoRepository repo = mock(ProductoRepository.class);
        when(repo.findByCodigoForUpdate("SNK-999")).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> new StockService(repo).descontar(List.of(new StockApi.Linea("SNK-999", 1))));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void rechazaCantidadCeroONegativa() {
        ProductoRepository repo = mock(ProductoRepository.class);
        assertThrows(ResponseStatusException.class,
                () -> new StockService(repo).descontar(List.of(new StockApi.Linea("SNK-001", 0))));
        verifyNoInteractions(repo);
    }

    @Test
    void reponerDevuelveElStock() {
        ProductoRepository repo = mock(ProductoRepository.class);
        Producto p = producto("SNK-001", 16, 0);
        when(repo.findByCodigoForUpdate("SNK-001")).thenReturn(Optional.of(p));
        new StockService(repo).reponer(List.of(new StockApi.Linea("SNK-001", 1)));
        assertEquals(17, p.getStock());
    }
}
