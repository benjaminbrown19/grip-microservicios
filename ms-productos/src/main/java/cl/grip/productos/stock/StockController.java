package cl.grip.productos.stock;

import io.swagger.v3.oas.annotations.Hidden;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API interna entre microservicios. La consume ms-ordenes en la red de Docker.
 * No aparece en Swagger y el gateway no la publica.
 */
@Hidden
@RestController
@RequestMapping("/internal/v1/stock")
public class StockController {
    private final StockService service;

    public StockController(StockService service) {
        this.service = service;
    }

    @PostMapping("/descontar")
    public List<StockApi.Reserva> descontar(@RequestBody List<StockApi.Linea> lineas) {
        return service.descontar(lineas);
    }

    @PostMapping("/reponer")
    public ResponseEntity<Void> reponer(@RequestBody List<StockApi.Linea> lineas) {
        service.reponer(lineas);
        return ResponseEntity.noContent().build();
    }
}
