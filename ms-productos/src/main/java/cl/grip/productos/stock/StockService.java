package cl.grip.productos.stock;

import cl.grip.productos.producto.Producto;
import cl.grip.productos.producto.ProductoRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Descuenta y repone stock. Todo ocurre en UNA transaccion con bloqueo de fila:
 * si un producto no tiene stock suficiente, no se descuenta ninguno.
 */
@Service
public class StockService {
    private final ProductoRepository productos;

    public StockService(ProductoRepository productos) {
        this.productos = productos;
    }

    @Transactional
    public List<StockApi.Reserva> descontar(List<StockApi.Linea> lineas) {
        if (lineas == null || lineas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay productos para descontar");
        }
        List<StockApi.Reserva> reservas = new ArrayList<>();
        for (StockApi.Linea linea : lineas) {
            if (linea == null || linea.codigo() == null || linea.codigo().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código de producto requerido");
            }
            if (linea.cantidad() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cantidad inválida");
            }
            String codigo = linea.codigo().trim();
            Producto producto = productos.findByCodigoForUpdate(codigo)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Producto inexistente: " + codigo));
            if (producto.getStock() == null || producto.getStock() < linea.cantidad()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Stock insuficiente para: " + producto.getNombre());
            }
            producto.setStock(producto.getStock() - linea.cantidad());
            productos.saveAndFlush(producto);
            int descuento = producto.getDescuento() == null ? 0 : producto.getDescuento();
            reservas.add(new StockApi.Reserva(producto.getCodigo(), producto.getNombre(),
                    producto.getPrecio(), descuento, linea.cantidad()));
        }
        return reservas;
    }

    /** Compensacion: devuelve el stock si ms-ordenes no pudo guardar la orden. */
    @Transactional
    public void reponer(List<StockApi.Linea> lineas) {
        if (lineas == null) return;
        for (StockApi.Linea linea : lineas) {
            if (linea == null || linea.codigo() == null || linea.cantidad() <= 0) continue;
            productos.findByCodigoForUpdate(linea.codigo().trim()).ifPresent(p -> {
                p.setStock((p.getStock() == null ? 0 : p.getStock()) + linea.cantidad());
                productos.saveAndFlush(p);
            });
        }
    }
}
