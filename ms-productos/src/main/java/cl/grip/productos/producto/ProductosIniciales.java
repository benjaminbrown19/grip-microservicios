package cl.grip.productos.producto;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Carga el catalogo de demostracion una sola vez, cuando la tabla productos
 * esta vacia. No sobrescribe precios ni stock de una base que ya contiene datos.
 */
@Configuration
public class ProductosIniciales {

    @Bean
    CommandLineRunner cargarProductosIniciales(ProductoRepository repository) {
        return args -> {
            if (repository.count() > 0) {
                return;
            }

            repository.saveAll(List.of(
                crear("SNK-001", "GRIP Street 90",
                    "Zapatilla urbana de cana baja, silueta retro y entresuela acolchada.",
                    "54990", 18, 5, "urbano", 0,
                    "/assets/img/producto-snk-001.jpg"),
                crear("SNK-002", "GRIP Runner X1",
                    "Zapatilla de running liviana, malla transpirable.",
                    "64990", 12, 4, "running", 15,
                    "/assets/img/producto-snk-002.jpg"),
                crear("SNK-003", "GRIP Court Pro",
                    "Zapatilla de basketball de cana alta, soporte reforzado.",
                    "79990", 3, 5, "basketball", 0,
                    "/assets/img/producto-snk-003.jpg"),
                crear("SNK-004", "GRIP Slide Skate",
                    "Zapatilla de skate con puntera reforzada.",
                    "49990", 25, 6, "skate", 0,
                    "/assets/img/producto-snk-004.jpg"),
                crear("SNK-005", "GRIP Street Low",
                    "Version de cana baja de la linea urbana.",
                    "47990", 20, 5, "urbano", 10,
                    "/assets/img/producto-snk-005.jpg"),
                crear("SNK-006", "GRIP Velocity",
                    "Zapatilla de running de competencia.",
                    "69990", 9, 4, "running", 0,
                    "/assets/img/producto-snk-006.jpg"),
                crear("SNK-007", "GRIP Baseline",
                    "Zapatilla de basketball con amortiguacion.",
                    "84990", 14, 5, "basketball", 0,
                    "/assets/img/producto-snk-007.jpg"),
                crear("SNK-008", "GRIP Grind Deck",
                    "Zapatilla de skate de perfil bajo.",
                    "52990", 16, 5, "skate", 20,
                    "/assets/img/producto-snk-008.jpg")
            ));
        };
    }

    private Producto crear(
            String codigo,
            String nombre,
            String descripcion,
            String precio,
            int stock,
            int stockCritico,
            String categoria,
            int descuento,
            String imagen) {

        Producto producto = new Producto();
        producto.setCodigo(codigo);
        producto.setNombre(nombre);
        producto.setDescripcion(descripcion);
        producto.setPrecio(new BigDecimal(precio));
        producto.setStock(stock);
        producto.setStockCritico(stockCritico);
        producto.setCategoria(categoria);
        producto.setDescuento(descuento);
        producto.setImagen(imagen);
        return producto;
    }
}
