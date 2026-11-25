package cl.grip.productos.stock;

import java.math.BigDecimal;

/** Mensajes que intercambian ms-ordenes y ms-productos por REST. */
public final class StockApi {
    private StockApi() {}

    /** Producto y cantidad que se quiere descontar o reponer. */
    public record Linea(String codigo, int cantidad) {}

    /** Datos del producto al momento de la venta (para calcular precios en ms-ordenes). */
    public record Reserva(String codigo, String nombre, BigDecimal precio, int descuento, int cantidad) {}
}
