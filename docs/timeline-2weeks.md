# TIMELINE 2 TUẦN — OMNICHANNEL E-COMMERCE PLATFORM

## 1. Mục tiêu dự án

Trong 14 ngày, hoàn thành một phiên bản **MVP có khả năng chạy end-to-end** của hệ thống thương mại điện tử đa kênh theo kiến trúc Microservices.

### Kiến trúc mục tiêu

```text
                         Web / Mobile
                              │
                            HTTPS
                              │
                           Nginx
                              │
                              ▼
                    Spring Cloud Gateway
                              │
        ┌─────────────┬───────┼──────────────┐
        ▼             ▼       ▼              ▼
    Identity       Product   Order         Payment
    Service        Service  Inventory      Service
        │             │       │              │
        ▼             ▼       ▼              ▼
    PostgreSQL     MongoDB PostgreSQL     PostgreSQL
                            │
                          Redis
                            │
                            ▼
                       RabbitMQ
                            │
                            ▼
                     Notification
                       Service
```

### Flow nghiệp vụ chính

```text
Register / Login
       ↓
      JWT
       ↓
Browse Product
       ↓
Create Order
       ↓
Reserve Stock
       ↓
OrderCreatedEvent
       ↓
Payment
       ↓
Payment Success
       ↓
Confirm Order
       ↓
Notification
```

---

# 2. Công nghệ sử dụng

| Thành phần               | Công nghệ               |
| ------------------------ | ----------------------- |
| Backend                  | Java 21 + Spring Boot   |
| Security                 | Spring Security + JWT   |
| API Gateway              | Spring Cloud Gateway    |
| User Database            | PostgreSQL              |
| Product Database         | MongoDB                 |
| Order Database           | PostgreSQL              |
| Payment Database         | PostgreSQL              |
| Cache / Distributed Lock | Redis                   |
| Message Broker           | RabbitMQ                |
| Container                | Docker + Docker Compose |
| Reverse Proxy            | Nginx                   |
| SSL                      | Let's Encrypt / Certbot |
| CI/CD                    | GitHub Actions          |
| Monitoring               | Prometheus + Grafana    |
| Logging                  | Loki                    |
| Load Test                | k6                      |
| API Documentation        | Swagger / OpenAPI       |

> **Phạm vi 2 tuần (MVP Scope):** Các thành phần sau đây là yêu cầu bắt buộc theo đề tài gốc cho môi trường production, nhưng được phép hoãn sang **Phase 2** nếu chức năng cốt lõi chưa hoàn thành trong 14 ngày:
> - **Elasticsearch** → Tạm dùng MongoDB text index thay thế
> - **Payment Gateway thật** (VNPay, MoMo, Stripe) → Dùng Mock Payment
> - **Jaeger/Zipkin** (Distributed Tracing) → Prometheus + Grafana + Loki là bắt buộc
> - **Kubernetes** → Docker Compose trên VPS là đủ cho MVP

---

# 3. Timeline tổng thể

| Ngày       | Nội dung chính          | Kết quả                                 |
| ---------- | ----------------------- | --------------------------------------- |
| **Day 1**  | Thiết kế kiến trúc      | Architecture + ERD + API + Event        |
| **Day 2**  | Infrastructure          | PostgreSQL + MongoDB + Redis + RabbitMQ |
| **Day 3**  | Identity Service        | Register + Login + JWT + RBAC           |
| **Day 4**  | Product Service         | Product + Category + SKU API            |
| **Day 5**  | Order Service           | Order + Order Status                    |
| **Day 6**  | Inventory               | Stock + Reservation                     |
| **Day 7**  | Race Condition          | Redis Lock + Flash Sale Test            |
| **Day 8**  | RabbitMQ + Notification | Event-driven Notification               |
| **Day 9**  | Payment Service         | Mock Payment                            |
| **Day 10** | Saga + Outbox           | Distributed Transaction                 |
| **Day 11** | Gateway + Docker        | Full Docker Environment                 |
| **Day 12** | VPS + Nginx + HTTPS     | Production Deployment                   |
| **Day 13** | Monitoring + Load Test  | Grafana + Prometheus + k6               |
| **Day 14** | Integration + Demo      | Final Testing + Documentation           |

---

# 4. Chi tiết từng ngày

## DAY 1 — Thiết kế kiến trúc

### Mục tiêu

Chốt kiến trúc hệ thống trước khi bắt đầu code.

### Công việc

Tạo repository:

```text
omnichannel-ecommerce/
├── api-gateway/
├── identity-service/
├── product-service/
├── order-service/
├── payment-service/
├── notification-service/
├── infrastructure/
│   ├── postgres/
│   ├── mongodb/
│   ├── redis/
│   └── rabbitmq/
├── docs/
├── docker-compose.yml
└── README.md
```

### Thiết kế

* [ ] System Architecture
* [ ] Microservices Architecture
* [ ] Database Architecture
* [ ] ERD
* [ ] Sequence Diagram
* [ ] Order Flow
* [ ] Payment Flow
* [ ] Event Flow
* [ ] API List
* [ ] Event List

### Output

```text
✅ Architecture Diagram
✅ Database ERD
✅ API Specification
✅ Event Specification
✅ Git Repository
```

---

# DAY 2 — Infrastructure

### Mục tiêu

Dựng môi trường infrastructure dùng chung.

### Services

```text
PostgreSQL
MongoDB
Redis
RabbitMQ
```

### Docker Compose

```bash
docker compose up -d
```

### Kiểm tra

```text
PostgreSQL → OK
MongoDB    → OK
Redis      → OK
RabbitMQ   → OK
```

### RabbitMQ

Exchange:

```text
ecommerce.exchange
```

Queues:

```text
payment.queue
notification.queue
order.queue
```

Events:

```text
OrderCreatedEvent
PaymentSucceededEvent
PaymentFailedEvent
OrderCancelledEvent
```

### Output

```text
✅ docker-compose.yml
✅ .env
✅ Infrastructure configuration
✅ RabbitMQ Exchange / Queue
```

---

# DAY 3 — Identity Service

### Chức năng

```text
POST /auth/register
POST /auth/login
POST /auth/refresh

GET /users/me
```

### Database

```text
users
roles
refresh_tokens
addresses
```

### Security

```text
Spring Security
JWT
BCrypt
RBAC
```

### Roles

```text
CUSTOMER
ADMIN
```

### Demo

```text
Register
   ↓
Login
   ↓
JWT
   ↓
GET /users/me
   ↓
200 OK
```

### Output

```text
✅ Register
✅ Login
✅ JWT
✅ Refresh Token
✅ RBAC
```

---

# DAY 4 — Product Service

### Entity

```text
Category
Product
ProductVariant / SKU
```

### API

```http
POST   /products
GET    /products
GET    /products/{id}
PUT    /products/{id}
DELETE /products/{id}

POST   /categories
GET    /categories
```

### Product mẫu

```json
{
  "name": "iPhone 17",
  "sku": "IP17-BLK-256",
  "price": 25000000,
  "stock": 100,
  "category": "PHONE"
}
```

### Output

```text
✅ Product CRUD
✅ Category CRUD
✅ SKU
✅ Product database
✅ Swagger API
```

---

# DAY 5 — Order Service

### Entity

```text
orders
order_items
order_status_history
```

### API

```http
POST /orders
GET  /orders/{id}
GET  /orders/my-orders
POST /orders/{id}/cancel
```

### Order Status

```text
PENDING
   ↓
CONFIRMED
   ↓
SHIPPING
   ↓
COMPLETED
```

Có thể chuyển sang:

```text
PENDING
   ↓
CANCELLED
```

hoặc:

```text
CONFIRMED
   ↓
CANCELLED
```

### Output

```text
✅ Create Order
✅ Get Order
✅ Order History
✅ Cancel Order
✅ Order State
```

---

# DAY 6 — Inventory Service

### Mục tiêu

Xử lý tồn kho và reservation.

### Database

```text
inventory
inventory_reservation
```

### Logic

```text
Create Order
     ↓
Check Stock
     ↓
Reserve Stock
     ↓
Create Order
```

### Theo dõi

```text
total_stock
reserved_stock
available_stock
```

### Ví dụ

```text
total_stock     = 100
reserved_stock  = 20
available_stock = 80
```

### Output

```text
✅ Check Stock
✅ Reserve Stock
✅ Release Stock
✅ Available Stock
```

---

# DAY 7 — Race Condition & Flash Sale

## Mục tiêu

Giải quyết bài toán nhiều người cùng mua một sản phẩm.

### Scenario

```text
Stock = 1

100 concurrent requests
        ↓
   POST /orders
```

### Kết quả mong muốn

```text
SUCCESS = 1
FAILED  = 99
STOCK   = 0
```

### Không được xảy ra

```text
SUCCESS > 1
STOCK < 0
```

### Giải pháp

```text
Redis Distributed Lock
```

hoặc:

```text
Optimistic Lock
Pessimistic Lock
```

### Output

```text
✅ Concurrency Control
✅ Redis Lock
✅ Flash Sale Test
✅ No Overselling
```

> **Đây là milestone quan trọng nhất của tuần 1.**

---

# DAY 8 — RabbitMQ + Notification

### Flow

```text
Order Service
      │
      │ OrderCreatedEvent
      ▼
RabbitMQ
      │
      ▼
Notification Service
```

### Notification

Mock Email:

```text
[EMAIL]

Your order #ORD001 has been created.
```

### Consumer

```text
notification.queue
```

### Output

```text
✅ RabbitMQ Producer
✅ RabbitMQ Consumer
✅ OrderCreatedEvent
✅ Notification Service
```

---

# DAY 9 — Payment Service

### API

```http
POST /payments
GET  /payments/{orderId}
POST /payments/{id}/refund
```

### Payment Status

```text
PENDING
SUCCESS
FAILED
REFUNDED
```

### Mock Payment

```text
Create Payment
      ↓
   ┌──┴──┐
   ↓     ↓
SUCCESS FAILED
```

### Output

```text
✅ Payment Service
✅ Payment Transaction
✅ Payment Status
✅ Mock Payment
```

> **Lưu ý:** Đây là Mock Payment cho phiên bản MVP 2 tuần. Theo yêu cầu đề tài gốc, môi trường production cần tích hợp đầy đủ các cổng thanh toán thật (VNPay, MoMo, ZaloPay, Stripe). Tích hợp Payment Gateway thật sẽ được thực hiện trong **Phase 2** sau khi core flow ổn định.

---

# DAY 10 — Saga + Outbox

## Saga

### Success

```text
Create Order
     ↓
Reserve Stock
     ↓
Create Payment
     ↓
Payment SUCCESS
     ↓
Order CONFIRMED
```

### Failure

```text
Create Order
     ↓
Reserve Stock
     ↓
Create Payment
     ↓
Payment FAILED
     ↓
Release Stock
     ↓
Cancel Order
```

### Outbox

Database:

```text
orders
outbox_events
```

Transaction:

```text
BEGIN

INSERT order

INSERT outbox_event

COMMIT
```

Sau đó:

```text
Outbox Publisher
      ↓
RabbitMQ
```

### Output

```text
✅ Saga Flow
✅ Compensation
✅ Outbox Table
✅ Event Publisher
```

> **Milestone 3:** End-to-end Order Flow phải chạy được.

---

# DAY 11 — API Gateway + Docker

## Gateway

Routing:

```text
/api/auth/**       → identity-service
/api/products/**   → product-service
/api/orders/**     → order-service
/api/payments/**   → payment-service
```

Architecture:

```text
Client
  ↓
API Gateway
  ↓
Microservices
```

## Dockerize

```text
identity-service
product-service
order-service
payment-service
notification-service
api-gateway
```

### Test

```bash
docker compose up -d
```

### Output

```text
✅ All services containerized
✅ Gateway routing
✅ Docker Compose
✅ One-command startup
```

---

# DAY 12 — VPS + Nginx + HTTPS

## VPS

```text
Ubuntu 22.04
4 vCPU
8 GB RAM
50 GB SSD
```

## Architecture

```text
Internet
    │
  HTTPS
    │
    ▼
  Nginx
    │
    ▼
API Gateway
    │
    ▼
Microservices
```

## Firewall

Chỉ mở:

```text
22
80
443
```

Không expose:

```text
5432
27017
6379
```

## SSL

```bash
sudo apt install certbot python3-certbot-nginx

sudo certbot --nginx -d api.example.com
```

### Output

```text
✅ VPS
✅ Docker
✅ Nginx
✅ HTTPS
✅ Firewall
```

---

# DAY 13 — Monitoring + Load Test

## Monitoring

```text
Prometheus
     ↓
Grafana
```

Dashboard:

```text
CPU
RAM
Request/s
HTTP 2xx
HTTP 4xx
HTTP 5xx
Response Time
Service UP/DOWN
```

## Logging

```text
Services
    ↓
   Loki
    ↓
 Grafana
```

## Load Test

Sử dụng:

```text
k6
```

Scenario:

```text
500 requests/s
      ↓
POST /orders
```

Sau đó:

```text
1000 requests/s
```

### Thu thập

```text
RPS
p95 latency
error rate
successful orders
final stock
```

### Output

```text
✅ Prometheus
✅ Grafana
✅ Loki
✅ k6 Test
✅ Performance Report
```

Triển khai trong repository: [hướng dẫn Day 13](day13-monitoring-load-test.md).
Kết quả đo thực tế và các giới hạn: [báo cáo hiệu năng](day13-performance-report.md).

---

# DAY 14 — Integration + Final Demo

## 1. End-to-End Test

```text
Register
   ↓
Login
   ↓
Get Product
   ↓
Create Order
   ↓
Reserve Stock
   ↓
Payment
   ↓
Confirm Order
   ↓
RabbitMQ
   ↓
Notification
```

---

## 2. Flash Sale Demo

```text
Stock = 10

500 concurrent requests
        ↓
    Order Service
        ↓
    Redis Lock
        ↓
     Inventory
```

Expected:

```text
Initial Stock      = 10
Successful Orders  = 10
Failed Orders      = 490+
Final Stock        = 0
Overselling        = 0
```

---

## 3. Service Failure Demo

Dừng Notification:

```bash
docker stop notification-service
```

Sau đó tạo đơn:

```text
Customer
    ↓
Order
    ↓
Payment
    ↓
SUCCESS
```

Notification có thể thất bại nhưng Order vẫn hoạt động.

---

## 4. Documentation

README cần có:

```text
1. Tổng quan về Đề tài: Tên đề tài, mô tả và lý do bài toán mang tính thực tế tại các doanh nghiệp.
2. Phân tích Kiến trúc Microservices: Danh sách các services, sơ đồ Mermaid về luồng dữ liệu và cơ chế giao tiếp (REST, gRPC, Event-Driven Queue).
3. Hướng dẫn Tìm hiểu & Phân tích Hệ thống: Các bước phân tích Domain Driven Design (DDD), xử lý dữ liệu và thiết kế Database/API Contract.
4. Hướng dẫn Triển khai lên VPS Thực tế: Các bước chuẩn bị VPS (Ubuntu), Docker/Docker Compose, Nginx Reverse Proxy, SSL Certbot, CI/CD GitHub Actions, và Monitoring (Prometheus/Grafana/Loki).
5. Tiêu chí Đánh giá & Yêu cầu Bài làm: Các chỉ số đo lường sản phẩm và kịch bản Demo bắt buộc dành cho sinh viên.

```

---

# 5. Milestones

## 🟢 Milestone 1 — Day 4

```text
Gateway
Identity
Product

        ↓

Login + Product API hoạt động
```

---

## 🟡 Milestone 2 — Day 7

```text
Order
Inventory
Redis

        ↓

Flash Sale Test PASS
```

> Đây là **mốc sống còn**.

---

## 🟠 Milestone 3 — Day 10

```text
RabbitMQ
Payment
Notification
Saga
Outbox

        ↓

End-to-End Order Flow PASS
```

---

## 🔴 Milestone 4 — Day 14

```text
Docker
VPS
Nginx
HTTPS
CI/CD
Grafana
Load Test

        ↓

FINAL DEMO
```

---

# 6. Phân chia nhóm 4 người

| Thành viên   | Phụ trách chính                            |
| ------------ | ------------------------------------------ |
| **Member 1** | Identity + JWT + Gateway                   |
| **Member 2** | Product + MongoDB                          |
| **Member 3** | Order + Inventory + Redis                  |
| **Member 4** | Payment + RabbitMQ + Notification + DevOps |

### Phối hợp chung

```text
Order + Inventory
       ↓
  RabbitMQ
       ↓
Payment + Notification
       ↓
Saga + Outbox
       ↓
Docker + VPS
```

---

# 7. Ưu tiên khi thiếu thời gian

Nếu bị chậm tiến độ, ưu tiên theo thứ tự:

```text
1. ⭐⭐⭐⭐⭐ Order + Inventory
2. ⭐⭐⭐⭐⭐ Race Condition
3. ⭐⭐⭐⭐⭐ RabbitMQ
4. ⭐⭐⭐⭐ Payment
5. ⭐⭐⭐⭐ Saga
6. ⭐⭐⭐⭐ Docker + VPS
7. ⭐⭐⭐⭐ HTTPS
8. ⭐⭐⭐ Monitoring
9. ⭐⭐ Loki
10. ⭐ Elasticsearch / Distributed Tracing
```

**Không được hy sinh Order + Inventory + RabbitMQ để lấy các phần monitoring nâng cao.**

---

# 8. Definition of Done — Ngày 14

Dự án được xem là hoàn thành khi:

* [ ] Có ít nhất 4 microservices chạy độc lập
* [ ] Login bằng JWT
* [ ] Product CRUD hoạt động
* [ ] Create Order hoạt động
* [ ] Inventory Reservation hoạt động
* [ ] Không overselling khi concurrent request
* [ ] Redis Lock/Concurrency Control hoạt động
* [ ] RabbitMQ hoạt động
* [ ] Notification Consumer hoạt động
* [ ] Payment Service hoạt động
* [ ] Saga compensation hoạt động
* [ ] Outbox hoạt động
* [ ] Docker Compose chạy toàn hệ thống
* [ ] Deploy thành công lên VPS
* [ ] Nginx Reverse Proxy
* [ ] HTTPS/SSL
* [ ] Firewall
* [ ] Prometheus + Grafana
* [ ] Load Test bằng k6
* [ ] Demo Service Failure
* [ ] README hoàn chỉnh
* [ ] Có tài liệu kiến trúc và sequence diagram

---

# 9. Kết quả cuối cùng

```text
                   OMNICHANNEL E-COMMERCE
                            │
       ┌────────────────────┼────────────────────┐
       ▼                    ▼                    ▼
 Microservices       Distributed Transaction    DevOps
       │                    │                    │
       ▼                    ▼                    ▼
 6 Services          Saga + Outbox         Docker + VPS
       │                    │                    │
       └────────────────────┼────────────────────┘
                            ▼
                     Flash Sale Demo
                            +
                     Failure Demo
```

**Mục tiêu của 14 ngày không phải xây một Shopee hoàn chỉnh**, mà là xây một hệ thống đủ để chứng minh 4 năng lực cốt lõi: **Microservices Architecture → Distributed Transaction → Event-driven Architecture → Production Deployment**.
