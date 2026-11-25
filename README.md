# GRIP Sneaker Store — Microservicios

Backend de GRIP Sneaker Store dividido en **3 microservicios Spring Boot** con un **API Gateway Nginx**,
desplegado con **Docker Compose en AWS EC2** (AWS Academy Learner Lab), siguiendo el ejemplo de despliegue del curso
(`react-springboot4-docker-ec2`).

Evoluciona el monolito `grip-backend` (entrega de la Evaluación 3, etiqueta `ev3-entregada`), conservando la misma API
`/api/v1`, la misma lógica de negocio y las mismas reglas de seguridad. **El frontend no cambia**: solo apunta al gateway.

## Arquitectura

```text
Navegador (React en localhost:5173)
        │  http://IP_PUBLICA:8080/api/v1/...
        ▼
┌──────────────── EC2 · Docker Compose ─────────────────┐
│  gateway (Nginx :80 → publicado en :8080)              │
│   ├─ /api/v1/auth/**       → ms-usuarios  :8081        │
│   ├─ /api/v1/productos/**  → ms-productos :8082        │
│   └─ /api/v1/ordenes/**    → ms-ordenes   :8083 ──REST──► ms-productos /internal/v1/stock
│                                                        │
│  mysql:8.4 (sin puerto publico)                        │
│   grip_usuarios · grip_productos · grip_ordenes        │
└────────────────────────────────────────────────────────┘
```

| Servicio | Responsabilidad | Base de datos | Origen en el monolito |
|---|---|---|---|
| `ms-usuarios` | Login, registro, renovación de tokens (único que **emite** JWT), cuentas iniciales | `grip_usuarios` (`usuarios_auth`) | paquete `seguridad` |
| `ms-productos` | CRUD de productos, catálogo inicial, descuento de stock con bloqueo de fila | `grip_productos` (`productos`) | paquete `producto` |
| `ms-ordenes` | Compra online (checkout), venta en tienda, órdenes y boletas | `grip_ordenes` (`ordenes`, `orden_items`) | paquete `orden` |
| `gateway` | Punto de entrada único, enrutamiento y Swagger unificado | — | (nuevo) |

## Decisiones de diseño

- **Gateway Nginx**: un solo puerto público (8080). Los microservicios y MySQL no exponen puertos a Internet.
- **Descubrimiento de servicios con el DNS de Docker**: cada contenedor se llama por su nombre (`ms-productos:8082`); no se necesita Eureka.
- **Base de datos por servicio**: una instancia MySQL con un esquema por microservicio; ningún servicio lee tablas de otro.
- **Comunicación entre servicios**: REST síncrono. `ms-ordenes` llama a `POST /internal/v1/stock/descontar` de `ms-productos`, que valida y descuenta el stock de **toda la orden en una transacción** (409 si falta stock).
- **Compensación**: si `ms-ordenes` no puede guardar la orden, llama a `/internal/v1/stock/reponer` para devolver el stock.
- **JWT compartido**: `ms-usuarios` firma los tokens (HMAC, acceso 15 min, refresco 7 días). `ms-productos` y `ms-ordenes` solo los **validan** con la misma clave y obtienen el rol desde el claim `rol`.
- Los endpoints `/internal/**` no están en Swagger y el gateway no los publica.

## Seguridad por endpoint

| Endpoint (vía gateway) | Acceso |
|---|---|
| `POST /api/v1/auth/login`, `/registro`, `/refresh` | Público |
| `GET /api/v1/productos`, `GET /api/v1/productos/{codigo}` | Público |
| `POST/PUT/DELETE /api/v1/productos/**` | ADMINISTRADOR |
| `POST /api/v1/ordenes/checkout` | Público (canal forzado a `online`) |
| `POST /api/v1/ordenes` (venta en tienda) | ADMINISTRADOR, VENDEDOR |
| `GET /api/v1/ordenes`, `GET /api/v1/ordenes/{id}` | ADMINISTRADOR, VENDEDOR |

## Despliegue en EC2

Requisitos en la instancia (como en la guía del curso): Docker, plugin Compose, Buildx y Git.

```bash
git clone https://github.com/benjaminbrown19/grip-microservicios.git
cd grip-microservicios
cp .env.example .env        # completar con claves aleatorias (ver comentarios)
chmod 600 .env
# Construir de a uno (la instancia t3.medium tiene 4 GB; las pruebas unitarias se ejecutan al construir)
docker compose build ms-productos
docker compose build ms-usuarios
docker compose build ms-ordenes
docker compose up -d
docker compose ps
curl -s http://localhost:8080/salud
curl -s http://localhost:8080/api/v1/productos
```

Grupo de seguridad: SSH 22 (My IP y EC2 Instance Connect) y TCP **8080** (IP de quienes usarán el frontend).

- API: `http://IP_PUBLICA:8080/api/v1/...`
- Swagger unificado: `http://IP_PUBLICA:8080/swagger-ui/index.html` (selector arriba a la derecha: usuarios, productos, ordenes)

## Frontend

En el repositorio `sneaker-store`, archivo `.env.development.local` (no se versiona):

```dotenv
VITE_API_URL=http://IP_PUBLICA:8080/api/v1
```

Luego `npm ci` y `npm run dev` (puerto **5173**, el permitido por CORS).

## Pruebas

Las pruebas unitarias (JUnit 5 + Mockito) se ejecutan en cada `docker compose build` (`mvn package`).
Si una prueba falla, la imagen no se construye.

| Servicio | Pruebas |
|---|---|
| ms-usuarios | `JwtServiceTest`: emisión y validación, refresh rechazado como acceso, firma ajena, token vencido, secreto corto |
| ms-productos | `StockServiceTest`: descuento, stock insuficiente (409), producto inexistente, cantidad inválida, reposición; `ProductoServiceTest` (heredadas del monolito) |
| ms-ordenes | `OrdenServiceTest`: checkout fuerza canal online, venta con vendedor, descuento + cupón + envío, envío bajo $50.000, 409 sin guardar, compensación, cupón inválido |

## Comandos útiles

```bash
docker compose logs --tail=100 ms-ordenes     # registros de un servicio
docker compose restart ms-productos           # reiniciar un servicio
docker compose down                           # detener (los datos quedan en el volumen)
```
