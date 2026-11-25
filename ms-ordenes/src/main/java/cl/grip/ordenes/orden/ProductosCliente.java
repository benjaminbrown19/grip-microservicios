package cl.grip.ordenes.orden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Cliente REST hacia ms-productos (comunicacion sincronica entre microservicios).
 * Usa el nombre del servicio en la red de Docker (http://ms-productos:8082).
 */
@Component
public class ProductosCliente {
    public record Linea(String codigo, int cantidad) {}
    public record Reserva(String codigo, String nombre, BigDecimal precio, int descuento, int cantidad) {}

    private final RestClient http;
    private final ObjectMapper json = new ObjectMapper();

    public ProductosCliente(@Value("${grip.productos.url}") String url) {
        this.http = RestClient.builder().baseUrl(url).build();
    }

    /** Descuenta el stock de todas las lineas (todo o nada) y devuelve precio y descuento vigentes. */
    public List<Reserva> descontar(List<Linea> lineas) {
        try {
            List<Reserva> r = http.post().uri("/internal/v1/stock/descontar")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(lineas)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Reserva>>() {});
            return r == null ? List.of() : r;
        } catch (RestClientResponseException ex) {
            // Se reenvia al frontend el mismo codigo y mensaje que entrego ms-productos (400, 409...).
            throw new ResponseStatusException(HttpStatus.valueOf(ex.getStatusCode().value()), mensaje(ex));
        } catch (ResourceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Servicio de productos no disponible");
        }
    }

    /** Compensacion: devuelve el stock si la orden no se pudo guardar. */
    public void reponer(List<Linea> lineas) {
        http.post().uri("/internal/v1/stock/reponer")
                .contentType(MediaType.APPLICATION_JSON)
                .body(lineas)
                .retrieve()
                .toBodilessEntity();
    }

    private String mensaje(RestClientResponseException ex) {
        try {
            JsonNode nodo = json.readTree(ex.getResponseBodyAsString());
            if (nodo.hasNonNull("message") && !nodo.get("message").asText().isBlank()) {
                return nodo.get("message").asText();
            }
        } catch (Exception ignorada) {
            // Cuerpo no JSON: se usa el texto de estado.
        }
        return ex.getStatusText();
    }
}
