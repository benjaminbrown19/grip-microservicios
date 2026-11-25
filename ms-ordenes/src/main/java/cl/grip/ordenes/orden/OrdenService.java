package cl.grip.ordenes.orden;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Logica de ordenes (misma que el monolito grip-backend). Diferencia: el stock ya no se lee
 * de la base local, sino que se descuenta llamando a ms-productos. Si la orden no se puede
 * guardar, se repone el stock (compensacion).
 */
@Service
public class OrdenService {
    private static final Logger log = LoggerFactory.getLogger(OrdenService.class);
    private static final BigDecimal ENVIO = new BigDecimal("3990");
    private static final BigDecimal ENVIO_GRATIS_DESDE = new BigDecimal("50000");
    private static final BigDecimal CIEN = new BigDecimal("100");

    private final OrdenRepository ordenes;
    private final ProductosCliente productos;

    public OrdenService(OrdenRepository ordenes, ProductosCliente productos) {
        this.ordenes = ordenes;
        this.productos = productos;
    }

    @Transactional(readOnly = true)
    public List<OrdenApi.Respuesta> listar() {
        return ordenes.findAll().stream()
                .sorted(Comparator.comparing(Orden::getFecha).reversed())
                .map(OrdenApi::respuesta)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrdenApi.Respuesta buscar(String id) {
        return OrdenApi.respuesta(ordenes.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada")));
    }

    /** Venta interna (en tienda): endpoint protegido para administrador o vendedor. */
    @Transactional
    public OrdenApi.Respuesta crear(OrdenApi.Crear entrada) {
        return crearInterno(entrada, true);
    }

    /** Compra de la tienda publica: fuerza canal online y no acepta datos de vendedor. */
    @Transactional
    public OrdenApi.Respuesta crearCheckout(OrdenApi.Crear entrada) {
        return crearInterno(entrada, false);
    }

    private OrdenApi.Respuesta crearInterno(OrdenApi.Crear entrada, boolean ventaInterna) {
        if (entrada == null || entrada.cliente() == null
                || entrada.items() == null || entrada.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Datos de la orden incompletos");
        }

        String nombre = texto(entrada.cliente().nombre());
        if (nombre.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre requerido");
        }
        String apellidos = texto(entrada.cliente().apellidos());
        String correo = textoONull(entrada.cliente().correo());

        String canal = ventaInterna
                ? valorPorDefecto(entrada.canal(), "online").toLowerCase(Locale.ROOT)
                : "online";
        if (!"online".equals(canal) && !"vendedor".equals(canal)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Canal de venta inválido");
        }

        OrdenApi.Entrega entregaEntrada = entrada.entrega();
        String tipoEntrega = entregaEntrada == null
                ? "retiro"
                : valorPorDefecto(entregaEntrada.tipo(), "retiro").toLowerCase(Locale.ROOT);
        if (!"retiro".equals(tipoEntrega) && !"domicilio".equals(tipoEntrega)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de entrega inválido");
        }
        String region = entregaEntrada == null ? null : textoONull(entregaEntrada.region());
        String comuna = entregaEntrada == null ? null : textoONull(entregaEntrada.comuna());
        String direccion = entregaEntrada == null ? null : textoONull(entregaEntrada.direccion());
        if ("domicilio".equals(tipoEntrega) && (region == null || comuna == null || direccion == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Para despacho a domicilio debes indicar región, comuna y dirección");
        }

        OrdenApi.Pago pagoEntrada = entrada.pago();
        String metodoPago = pagoEntrada == null
                ? "no_informado"
                : valorPorDefecto(pagoEntrada.metodo(), "no_informado").toLowerCase(Locale.ROOT);
        if (!Set.of("tarjeta", "debito", "credito", "efectivo", "presencial", "no_informado").contains(metodoPago)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Método de pago inválido");
        }
        String ultimos4 = pagoEntrada == null ? null : soloDigitos(pagoEntrada.ultimos4(), 4);
        String codigoAutorizacion = pagoEntrada == null ? null : soloDigitos(pagoEntrada.codigoAutorizacion(), 8);
        Integer cuotas = pagoEntrada == null ? null : pagoEntrada.cuotas();
        if (cuotas != null && (cuotas < 1 || cuotas > 12)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cantidad de cuotas inválida");
        }

        String cupon = textoONull(entrada.cupon());
        BigDecimal porcentajeCupon = BigDecimal.ZERO;
        if (cupon != null) {
            cupon = cupon.toUpperCase(Locale.ROOT);
            porcentajeCupon = switch (cupon) {
                case "GRIP10" -> new BigDecimal("0.10");
                case "GRIP20" -> new BigDecimal("0.20");
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cupón no válido");
            };
        }

        String vendedorNombre = null;
        String vendedorRun = null;
        String vendedorCorreo = null;
        if (ventaInterna && "vendedor".equals(canal)) {
            if (entrada.vendedor() == null
                    || textoONull(entrada.vendedor().nombre()) == null
                    || textoONull(entrada.vendedor().correo()) == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Faltan los datos del vendedor que registra la venta");
            }
            vendedorNombre = textoONull(entrada.vendedor().nombre());
            vendedorRun = textoONull(entrada.vendedor().run());
            vendedorCorreo = textoONull(entrada.vendedor().correo());
        }

        // Validacion de lineas (antes de tocar el stock).
        List<ProductosCliente.Linea> lineas = new ArrayList<>();
        Set<String> codigos = new HashSet<>();
        for (OrdenApi.Linea linea : entrada.items()) {
            if (linea == null || linea.codigo() == null || linea.codigo().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código de producto requerido");
            }
            String codigo = linea.codigo().trim();
            if (!codigos.add(codigo)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Producto duplicado en la orden: " + codigo);
            }
            if (linea.cantidad() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cantidad inválida");
            }
            lineas.add(new ProductosCliente.Linea(codigo, linea.cantidad()));
        }

        // Llamada a ms-productos: descuenta todo el stock o nada (409 si falta stock).
        List<ProductosCliente.Reserva> reservas = productos.descontar(lineas);
        Map<String, ProductosCliente.Reserva> porCodigo = new HashMap<>();
        for (ProductosCliente.Reserva r : reservas) {
            porCodigo.put(r.codigo(), r);
        }

        try {
            List<ItemOrden> items = new ArrayList<>();
            BigDecimal subtotal = BigDecimal.ZERO;
            for (ProductosCliente.Linea linea : lineas) {
                ProductosCliente.Reserva reserva = porCodigo.get(linea.codigo());
                if (reserva == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "Respuesta incompleta del servicio de productos");
                }
                BigDecimal precioUnitario = reserva.precio()
                        .multiply(CIEN.subtract(BigDecimal.valueOf(reserva.descuento())))
                        .divide(CIEN, 0, RoundingMode.HALF_UP);
                ItemOrden item = new ItemOrden(reserva.codigo(), reserva.nombre(), linea.cantidad(), precioUnitario);
                items.add(item);
                subtotal = subtotal.add(item.getSubtotal());
            }

            BigDecimal descuento = subtotal.multiply(porcentajeCupon).setScale(0, RoundingMode.HALF_UP);
            BigDecimal costoEnvio = BigDecimal.ZERO;
            if ("domicilio".equals(tipoEntrega) && subtotal.compareTo(ENVIO_GRATIS_DESDE) < 0) {
                costoEnvio = ENVIO;
            }
            BigDecimal total = subtotal.subtract(descuento).add(costoEnvio);
            String estado = "no_informado".equals(metodoPago) ? "registrada" : "pagada";

            Orden orden = new Orden(
                    "ORD-" + UUID.randomUUID(), Instant.now(),
                    nombre, apellidos, correo, estado, total, canal,
                    vendedorNombre, vendedorRun, vendedorCorreo,
                    tipoEntrega, region, comuna, direccion,
                    metodoPago, ultimos4, codigoAutorizacion, cuotas,
                    cupon, subtotal, descuento, costoEnvio);
            items.forEach(orden::agregarItem);

            return OrdenApi.respuesta(ordenes.saveAndFlush(orden));
        } catch (RuntimeException ex) {
            compensar(lineas);
            throw ex;
        }
    }

    private void compensar(List<ProductosCliente.Linea> lineas) {
        try {
            productos.reponer(lineas);
            log.warn("Orden no guardada: se repuso el stock de {} producto(s)", lineas.size());
        } catch (RuntimeException ex) {
            log.error("No se pudo reponer el stock tras un error al guardar la orden: {}", lineas, ex);
        }
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private static String textoONull(String valor) {
        String limpio = texto(valor);
        return limpio.isBlank() ? null : limpio;
    }

    private static String valorPorDefecto(String valor, String defecto) {
        return valor == null || valor.isBlank() ? defecto : valor.trim();
    }

    private static String soloDigitos(String valor, int maximo) {
        if (valor == null) return null;
        String limpio = valor.replaceAll("\\D", "");
        if (limpio.isBlank()) return null;
        return limpio.length() <= maximo ? limpio : limpio.substring(limpio.length() - maximo);
    }
}
