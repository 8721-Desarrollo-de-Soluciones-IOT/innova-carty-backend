# Innova Carty Platform · Backend (Spring Boot)

RESTful API shared by the three clients of Innova Carty:

- **Smart cart (ESP32)**: reports the RFID tags, the weight, the geofence and the card payments.
- **Web Console (Angular)**: store administrator. Fleet, alerts, catalog and unlock with PIN.
- **Mobile app (Flutter)**: one app with two views. The login decides the view: shopper ("Mi carrito", Yape / Plin / card payment) or admin.

Built with Spring Boot 3.5, Java 21, Spring Data JPA and PostgreSQL on **Supabase**. Deployed on **Render** with Docker.

## Bounded contexts

```
src/main/java/com/innovacarty/platform/
├── iam/          # Sign-in, shopper sign-up, supervisor PIN (JWT HS256)
├── catalog/      # Products (price, nominal weight, tolerance, RFID tag) and stores
├── monitoring/   # Carts, shopping sessions, alerts, events, audit logs, stats
│   └── application/CartLifecycleService   # Domain rules of the cart (weight, lock, unlock, exit)
├── payment/      # Simulated Yape / Plin / card payments
├── shopping/     # "Mi carrito": shopper endpoints of the mobile app (/me)
├── devices/      # Gateway for the ESP32 (X-Device-Key)
└── shared/       # Security, CORS, OpenAPI, errors and demo data
```

## Endpoints (`/api/v1`)

| Resource | Endpoints | Who |
| :--- | :--- | :--- |
| Authentication | `POST /authentication/sign-in`, `POST /authentication/sign-up`, `POST /authentication/verify-pin` | Public |
| Carts | `GET /carts`, `GET /carts/{id}`, `POST /carts`, `PATCH /carts/{id}`, `POST /carts/{id}/unlock`, `POST /carts/{id}/finish`, `DELETE /carts/{id}` | Admin |
| Shopping sessions | `GET /shopping-sessions?cartId=`, `GET /shopping-sessions/{id}`, `PATCH /shopping-sessions/{id}` | Admin |
| Alerts | `GET /alerts?cartId=&status=&_order=`, `GET /alerts/{id}`, `POST /alerts`, `PATCH /alerts/{id}` | Admin |
| Cart events | `GET /cart-events?cartId=&_order=&_limit=`, `POST /cart-events` | Admin |
| Audit logs | `GET /audit-logs?cartId=`, `POST /audit-logs` | Admin |
| Session stats | `GET /session-stats` | Admin |
| Products | `GET /products`, `GET /products/{id}` (any user), `POST`, `PATCH`, `DELETE` (admin) | Admin / Shopper |
| Stores | `GET /stores`, `GET /stores/{id}` | Admin |
| Payments | `POST /payments` `{cartId, method: YAPE \| PLIN \| CARD}`, `GET /payments?cartId=` | Shopper / Admin |
| My cart | `GET /me/cart`, `POST /me/cart` `{cartId}`, `PATCH /me/cart` `{budget}`, `DELETE /me/cart`, `GET /me/sessions`, `GET /me/payments` | Shopper |
| Devices | `GET /devices/carts/{id}`, `PUT /devices/carts/{id}/state`, `POST /devices/carts/{id}/events` | ESP32 (`X-Device-Key`) |

Interactive documentation: **`/swagger-ui.html`**.

### How the clients talk

```
ESP32 ──PUT state / POST events──▶ ┐
ESP32 ◀──GET status every 2 s───── │
Mobile app (shopper) ── /me, /payments ──▶  Spring Boot API ──JDBC──▶ Supabase (PostgreSQL)
Mobile app (admin) ──┐                   │
Web Console ─────────┴── /carts, /alerts…▶ ┘
```

1. The cart sends the tags it read (`PUT /devices/carts/CART-0427/state`). The API matches them with the catalog, opens the session and recalculates the total and the expected weight.
2. The shopper scans the QR on the cart screen. The app calls `POST /me/cart` and shows the items live.
3. The shopper pays with Yape / Plin / card (simulated, `POST /payments`). The cart reads `PAID` in its next poll and opens the exit.
4. If the cart reaches the exit without paying, it sends `GEOFENCE`. The API locks it and raises a critical alert. The supervisor unlocks it from the console, the app (PIN) or the key fob.

## Run locally (IntelliJ IDEA or terminal)

Requires Java 21. Without database variables it uses an in-memory H2 database with demo data.

```bash
mvn spring-boot:run        # or open the folder in IntelliJ and run PlatformApplication
# http://localhost:8080/swagger-ui.html
```

**Demo accounts** (created on the first start):

| Role | Email | Password | PIN |
| :--- | :--- | :--- | :--- |
| Admin | `lucia.medina@retailco.pe` | `demo1234` | `2468` |
| Shopper | `camila.rios@gmail.com` | `demo1234` | – |

The physical prototype is **CART-0427**, with the 10 NTAG215 products (`SKU-IC-001` to `SKU-IC-010`).

## Deploy on Render with Supabase

1. Render: **New → Web Service**, connect this repository, **Language: Docker**, plan **Free**, health check `/actuator/health`.
2. Environment variables (see `.env.example`):

| Variable | Value |
| :--- | :--- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://aws-1-us-east-1.pooler.supabase.com:5432/postgres?sslmode=require` (Supabase session pooler) |
| `SPRING_DATASOURCE_USERNAME` | `postgres.<project-ref>` |
| `SPRING_DATASOURCE_PASSWORD` | Supabase database password |
| `JWT_SECRET` | long random text |
| `DEVICE_API_KEY` | key of the ESP32 |

The tables are created on the first start in the `innovacarty` schema (not exposed by the Supabase Data API).

On the free plan Render sleeps the service after 15 min without requests; the first request after that takes about a minute.

## Conventions

Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:`), GitFlow (`main`, `develop`, `feature/*`).
