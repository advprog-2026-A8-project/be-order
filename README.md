# C4 Model of the Current Architecture

## Context Diagram

![alt text](assets/ContextDiagram.png)

## Container Diagram

![alt text](assets/ContainerDiagram.png)

## Deployment Diagram

![alt text](assets/DeploymentDiagram.png)

## Risk Analysis & Architecture Modification

![alt text](assets/RiskStormingMatrix.png)
![alt text](assets/ModifiedArchitectureDiagram.png)

### Refleksi Risk Storming

Risk Storming diterapkan karena arsitektur saat ini tidak lagi cukup dinilai hanya dari sisi kelengkapan fitur, tetapi juga harus dilihat dari sisi risiko operasional jangka panjang. Ketika JSON berkembang dan digunakan dalam skala yang lebih besar, perhatian utama arsitektur bergeser ke aspek skalabilitas, konsistensi data, ketahanan sistem, dan observability pada banyak service yang saling terhubung.

Teknik ini membantu tim mengidentifikasi risiko secara sistematis berdasarkan kemungkinan terjadinya dan besar dampaknya, bukan hanya berdasarkan intuisi. Dengan memetakan risiko seperti overselling saat war, inkonsistensi antarservice, bottleneck pada Order Service, dan keterbatasan observability ke dalam matriks likelihood-impact, tim dapat memprioritaskan masalah yang paling penting untuk keberlanjutan sistem.

Risk Storming juga bermanfaat karena menghubungkan diskusi arsitektur dengan keputusan desain yang konkret. Hasil akhirnya bukan hanya daftar risiko, tetapi juga usulan future architecture yang lebih jelas, termasuk penambahan API Gateway, reservation cache untuk kontrol flash sale, event bus untuk menjaga konsistensi berbasis saga, serta dukungan observability dan audit yang lebih kuat.

# Individu (Derrick)
## Component Diagram

![alt text](assets/ComponentDiagram.png)

## Code Diagram

![alt text](assets/CodeDiagram.png)

## Profiling

- Tool profiling mengikuti `be-wallet-transaksi`: **Apache JMeter** + **HTML Report (APDEX)**.
- Test plan: `performance/jmeter/order-transaction.jmx`
- Data token: `performance/jmeter/data/admin_tokens.csv` dan `performance/jmeter/data/titiper_tokens.csv`
- Output APDEX before/after:
  - `performance/jmeter/results/report-before/index.html`
  - `performance/jmeter/results/report-after/index.html`

> Screenshot yang dibutuhkan:
> 1) tabel APDEX (`APDEX (Application Performance Index)`) before vs after,
> 2) grafik `Response Times Over Time` before vs after.

## Monitoring

![]()

![]()


# BE Order
PIC: Derrick - 2406351440

Backend service untuk orkestrasi transaksi order pada sistem JaStip Online Nasional (JSON): checkout, lifecycle status order, cancel + refund trigger, rating submission, history/monitoring, serta integrasi lintas service (inventory, wallet, voucher, profile).

## API Documentation
Saat aplikasi berjalan:

- Swagger UI: `http://localhost:5002/swagger-ui/index.html` (profile `dev`)
- OpenAPI JSON: `http://localhost:5002/v3/api-docs` (profile `dev`)

Untuk profile `main`, ganti port menjadi `5000`.

`Authorize` di Swagger menggunakan format:

```text
Bearer <JWT_TOKEN>
```

## Endpoint Summary
Base path: `/api/orders`

- `POST /api/orders/checkout`
- `GET /api/orders`
- `GET /api/orders/{id}`
- `PATCH /api/orders/{id}/status`
- `POST /api/orders/{id}/cancel`
- `POST /api/orders/{id}/rating`
- `GET /api/orders/titiper/{userId}/active`
- `GET /api/orders/titiper/{userId}/history`
- `GET /api/orders/jastiper/{jastiperId}/todo`
- `GET /api/orders/jastiper/{jastiperId}/processing`
- `GET /api/orders/jastiper/{jastiperId}/completed`
- `GET /api/orders/admin/active`
- `GET /api/orders/admin/active/paged`
- `GET /api/orders/admin/by-status`
- `GET /api/orders/admin/by-status/paged`
- `GET /api/orders/admin/summary`

## External Contract Integration
Service ini memakai kombinasi HTTP + gRPC:

- Inventory (HTTP): `${ORDER_INVENTORY_URL}`
  - `POST /{id}/reserve?quantity=...`
  - `POST /{id}/release?quantity=...` (untuk kompensasi release stock saat cancel/failure)
- Wallet Contract (gRPC): `${ORDER_WALLET_GRPC_HOST}:${ORDER_WALLET_GRPC_PORT}`
  - `checkBalance`
  - `deductBalance`
  - `refundBalance`
- Voucher API: `${ORDER_VOUCHER_URL}`
  - `POST /validate`
  - `POST /use`
  - `PATCH /admin/update/{code}` (restore quota untuk kompensasi)
- Profile: `${ORDER_PROFILE_URL}`

## Database & Migration
- DB: PostgreSQL
- ORM validation mode: `spring.jpa.hibernate.ddl-auto=validate`
- Migration: Flyway (`src/main/resources/db/migration`)
  - `V1__init_order_schema.sql`
  - `V2__add_owner_status_indexes.sql`
  - `V3__add_idempotency_order_fk.sql`
  - `V4__add_voucher_code_column.sql`
  - `V5__add_voucher_applied_column.sql`
  - `V6__add_rating_sync_tasks.sql`
  - `V7__add_order_compensation_tasks.sql`

## Configuration
Copy `env.example` menjadi `.env`, lalu isi value sesuai environment.

Variabel utama:

- `ORDER_APP_PORT`
- `POSTGRES_HOST`
- `POSTGRES_PORT`
- `POSTGRES_DB`
- `POSTGRES_DB_DEV`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `ORDER_INVENTORY_URL`
- `ORDER_INVENTORY_INTERNAL_AUTHORIZATION`
- `ORDER_WALLET_GRPC_HOST`
- `ORDER_WALLET_GRPC_PORT`
- `GRPC_SERVER_INTERNAL_TOKEN`
- `ORDER_VOUCHER_URL`
- `ORDER_PROFILE_URL`
- `ORDER_PROFILE_INTERNAL_AUTHORIZATION`
- `ORDER_SECURITY_PRINCIPAL_CLAIM`
- `ORDER_RATING_SYNC_WORKER_DELAY_MS`
- `ORDER_RATING_SYNC_BATCH_SIZE`
- `ORDER_RATING_SYNC_RETRY_MAX_ATTEMPTS`
- `ORDER_RATING_SYNC_RETRY_BASE_DELAY_MS`
- `ORDER_COMPENSATION_WORKER_DELAY_MS`
- `ORDER_COMPENSATION_BATCH_SIZE`
- `ORDER_COMPENSATION_RETRY_MAX_ATTEMPTS`
- `ORDER_COMPENSATION_RETRY_BASE_DELAY_MS`
- `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE`
- `MANAGEMENT_ENDPOINT_PROMETHEUS_ACCESS`
- `MANAGEMENT_PROMETHEUS_METRICS_EXPORT_ENABLED`

## Run with Docker Compose
Menjalankan app + PostgreSQL untuk local testing dengan profile:

- `main` profile:
  - App: `5000`
  - Postgres: `5001`
- `dev` profile:
  - App: `5002`
  - Postgres: `5003`

Jalankan `dev`:

```powershell
docker compose --profile dev up --build
```

Jalankan `main`:

```powershell
docker compose --profile main up --build
```

## Monitoring (Prometheus + Grafana + Loki + Promtail + Alertmanager)
Untuk enable metrics, set di `.env`:

```text
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,prometheus
MANAGEMENT_ENDPOINT_PROMETHEUS_ACCESS=unrestricted
MANAGEMENT_PROMETHEUS_METRICS_EXPORT_ENABLED=true
```

Jalankan service order terlebih dulu (`main` atau `dev`), lalu jalankan stack monitoring:

```powershell
docker compose -f docker-compose.monitoring.yml up -d
```

Akses:

- Order metrics: `http://localhost:5002/actuator/prometheus` (dev) atau `http://localhost:5000/actuator/prometheus` (main)
- Prometheus UI: `http://localhost:5005`
- Grafana UI: `http://localhost:5004` (default `admin/admin`)
- Loki API: `http://localhost:5006`
- Alertmanager UI: `http://localhost:5007`

## Performance Test (JMeter + APDEX)
1. Isi token valid:
   - `performance/jmeter/data/admin_tokens.csv`
   - `performance/jmeter/data/titiper_tokens.csv`
2. Jalankan baseline (before):
```powershell
jmeter -n -t performance/jmeter/order-transaction.jmx -l performance/jmeter/results/order-before.jtl -e -o performance/jmeter/results/report-before
```
3. Jalankan lagi setelah perubahan (after):
```powershell
jmeter -n -t performance/jmeter/order-transaction.jmx -l performance/jmeter/results/order-after.jtl -e -o performance/jmeter/results/report-after
```
4. Buka report:
   - `performance/jmeter/results/report-before/index.html`
   - `performance/jmeter/results/report-after/index.html`
5. Ambil screenshot bagian:
   - `APDEX (Application Performance Index)`
   - `Response Times Over Time`

### Phase 2 (Business Flow Endpoint)
Untuk profiling endpoint inti order (`checkout`, `status`, `cancel`, `rating`), gunakan:

```powershell
jmeter -n -t performance/jmeter/order-business-flow.jmx -l performance/jmeter/results/order-business-before.jtl -e -o performance/jmeter/results/report-business-before
```

```powershell
jmeter -n -t performance/jmeter/order-business-flow.jmx -l performance/jmeter/results/order-business-after.jtl -e -o performance/jmeter/results/report-business-after
```

Sebelum menjalankan Phase 2, isi dulu:
- `performance/jmeter/data/jastiper_tokens.csv`
- `performance/jmeter/data/checkout_payloads.csv`
- `performance/jmeter/data/business_order_ids.csv`
