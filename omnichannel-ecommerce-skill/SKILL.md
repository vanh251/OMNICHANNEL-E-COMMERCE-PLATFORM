# Omnichannel E-Commerce Microservices Development Skill

## 1. Identity

You are an AI coding agent specialized in the
"Omnichannel E-Commerce Platform" project.

Your responsibility is to help design, implement, debug, test,
document, containerize, and deploy this enterprise-style
microservices e-commerce system.

The system is designed around:

- Java 17
- Spring Boot
- Spring Security
- JWT
- Spring Cloud Gateway
- PostgreSQL
- MongoDB
- Redis
- RabbitMQ
- Docker / Docker Compose
- Nginx
- Prometheus
- Grafana
- Loki
- GitHub Actions
- OpenAPI / Swagger
- k6 / JMeter

---

# 2. Project Goal

Build an enterprise-style omnichannel e-commerce platform using
microservices.

The system must support:

- User registration and authentication
- JWT authentication
- RBAC authorization
- Product catalog
- Product categories
- Product SKU
- Product search
- Order creation
- Inventory management
- Stock reservation
- Payment processing
- Payment webhook
- Notification
- Event-driven communication
- Distributed transaction handling
- Flash Sale concurrency
- Docker deployment
- VPS deployment
- Monitoring and logging

The system must be designed so that services are independently
deployable and independently scalable.

---

# 3. Mandatory Technology Rules

## 3.1 Java

Use Java 17.

Never change the project to Java 21 unless the developer explicitly
requests it.

Recommended Gradle configuration:

java {
toolchain {
languageVersion = JavaLanguageVersion.of(17)
}
}

Docker runtime should use Java 17.

Example:

FROM eclipse-temurin:17-jre

---

# 4. Microservices

The system contains the following components:

1. API Gateway
2. Identity & User Service
3. Product Catalog Service
4. Order & Inventory Service
5. Payment & Billing Service
6. Notification Service

See:

references/services.md

for detailed responsibilities.

---

# 5. Architecture Rules

Follow these architectural rules strictly.

## 5.1 Database Per Service

Each business service owns its own database.

Never allow:

Service A -> directly query Service B database.

Correct:

Service A -> REST/gRPC/Event -> Service B

Incorrect:

Service A -> PostgreSQL database owned by Service B

---

# 6. Service Communication

Use:

Client
|
v
API Gateway
|
+--> Identity Service
|
+--> Product Service
|
+--> Order & Inventory Service
|
+--> Payment Service

For synchronous communication:

- REST/HTTP
- gRPC where explicitly useful

For asynchronous communication:

- RabbitMQ

Order-related domain events should be published through RabbitMQ.

See:

references/messaging.md

---

# 7. API Gateway

API Gateway is the single public application entry point.

Responsibilities:

- Routing
- JWT verification
- Authentication forwarding
- Rate limiting
- CORS
- Request filtering
- Central API entry point

Business services should not normally be directly exposed to
the public Internet.

Inside Docker Compose, use service names instead of localhost.

Example:

http://identity-service:8081
http://product-service:8082
http://order-inventory-service:8083
http://payment-service:8084

Never use:

http://localhost:8081

for container-to-container communication.

---

# 8. Identity & User Service

Responsibilities:

- User registration
- Login
- JWT access token
- Refresh token
- User profile
- Address management
- RBAC

Typical roles:

- USER
- ADMIN

Use Spring Security.

Password storage must use a secure password hashing mechanism.

Never store plaintext passwords.

---

# 9. Product Catalog Service

Responsibilities:

- Product
- Category
- Brand
- SKU
- Product image
- Product attributes
- Product search

Primary database:

MongoDB.

Optional search engine:

Elasticsearch.

If Elasticsearch is not implemented, do not introduce unnecessary
complexity.

Product Service owns product master data.

---

# 10. Order & Inventory Service

This is the CORE business service.

Responsibilities:

- Create order
- Order items
- Order status
- Inventory
- Stock reservation
- Stock release
- Stock deduction
- Concurrency control

Order states:

PENDING
CONFIRMED
SHIPPING
COMPLETED
CANCELLED

The service must prevent overselling.

Example:

Stock = 1

100 concurrent users attempt to buy.

Expected:

successful reservations <= 1

Final stock must never become negative.

---

# 11. Payment & Billing Service

Responsibilities:

- Create payment transaction
- Payment status
- Payment callback/webhook
- Refund
- Reconciliation

Possible providers:

- VNPay
- MoMo
- ZaloPay
- Stripe

For student/demo implementation, a simulated payment provider
is acceptable unless real payment integration is explicitly required.

Payment must be idempotent.

The same payment callback must not create duplicate transactions.

---

# 12. Notification Service

Responsibilities:

- Email notification
- SMS notification
- Push notification
- Notification log

Notification should normally be asynchronous.

Example:

OrderCreatedEvent
|
v
RabbitMQ
|
v
Notification Service

The Order Service must not depend synchronously on Notification
Service to complete an order.

Therefore:

Notification Service failure should not prevent order creation.

---

# 13. Inventory Concurrency

Inventory operations are critical.

Never implement stock deduction using unsafe logic such as:

read stock
if stock > 0
stock = stock - 1
save stock

without concurrency control.

Possible strategies:

- Redis distributed locking
- Redis atomic operations
- Optimistic locking
- Pessimistic locking
- Database conditional update

Choose the simplest reliable mechanism appropriate to the existing
codebase.

For the project demo, Redis-based locking or SQL optimistic/pessimistic
locking can be used.

Do not introduce complex distributed locking unless required.

---

# 14. Saga Pattern

Distributed business transactions must not rely on a global database
transaction.

Example flow:

Create Order
|
v
Reserve Inventory
|
v
Create Payment
|
v
Payment Success
|
v
Confirm Order

Failure example:

Create Order
|
v
Reserve Inventory
|
v
Payment Failed
|
v
Release Inventory
|
v
Cancel Order

Every forward operation that changes distributed business state should
have a corresponding compensation strategy when necessary.

---

# 15. Outbox Pattern

When a service needs to update its database and publish an event,
avoid:

DB transaction
|
+--> save database
|
+--> publish RabbitMQ

because the broker can fail after the DB transaction succeeds.

Preferred:

DB Transaction
|
+--> business data
|
+--> outbox event

Then:

Outbox Publisher
|
v
RabbitMQ

The Outbox Pattern must be used when event delivery consistency is
important.

---

# 16. API Design

Use API-first principles.

Document APIs using:

- OpenAPI
- Swagger

Standard response structure should preferably be:

{
"code": 200,
"message": "Success",
"data": {}
}

Error responses should be consistent.

Use:

- DTO
- Validation
- Global exception handler
- Meaningful HTTP status codes

Do not expose JPA entities directly from REST controllers.

---

# 17. Spring Project Structure

Prefer:

src/main/java/.../
├── controller/
├── service/
├── repository/
├── entity/
├── dto/
│   ├── request/
│   └── response/
├── mapper/
├── config/
├── security/
├── exception/
├── event/
├── messaging/
└── util/

Do not create unnecessary abstractions.

Follow the existing project structure when extending code.

---

# 18. Security

For authenticated APIs:

Client
|
v
API Gateway
|
v
JWT
|
v
Service

Use:

- Spring Security
- JWT
- RBAC
- Password hashing
- Input validation

Never:

- hardcode JWT secret
- hardcode database password
- hardcode RabbitMQ password
- commit production credentials

Use environment variables.

---

# 19. Docker Rules

Every service should be containerizable.

Use:

- Dockerfile
- Docker Compose
- Internal Docker network
- Health checks
- Persistent volumes for databases

Inside Compose:

POSTGRES_HOST=postgres
MONGO_HOST=mongodb
REDIS_HOST=redis
RABBITMQ_HOST=rabbitmq

Never:

POSTGRES_HOST=localhost

See:

references/docker.md

---

# 20. Environment Configuration

Configuration must be externalized.

Use:

.env

and:

.env.example

Example:

POSTGRES_USER=postgres
POSTGRES_PASSWORD=change_me

JWT_SECRET=change_me

RABBITMQ_USER=guest
RABBITMQ_PASSWORD=guest

Never commit:

.env

to Git.

Commit:

.env.example

instead.

---

# 21. Observability

Services should expose Actuator endpoints.

Recommended:

/actuator/health
/actuator/prometheus

Monitoring stack:

Prometheus
|
v
Grafana

Logging:

Application
|
v
Loki
|
v
Grafana

Distributed tracing can use:

OpenTelemetry
+
Jaeger/Zipkin

if required by the project scope.

---

# 22. Testing Rules

Every important business operation should have tests.

Minimum:

- Unit tests
- Controller tests where appropriate
- Repository/integration tests
- API integration tests
- Concurrency tests

Critical test:

Stock = 10

Send many concurrent purchase requests.

Expected:

successful orders <= 10
final stock = 0
stock never < 0

---

# 23. Demo Requirements

The system should support these demonstrations.

## Demo 1: Flash Sale

Initial stock:

10

Send:

500-1000 concurrent requests

Expected:

- no negative stock
- no overselling
- successful reservations <= 10
- system remains operational

---

## Demo 2: Notification Failure

Stop Notification Service.

Then create an order.

Expected:

- order creation still succeeds
- Order Service does not crash
- event remains available for later processing

---

# 24. VPS Deployment

Production-like deployment:

Internet
|
v
Nginx
|
HTTPS
|
v
API Gateway
|
v
Microservices
|
+--> PostgreSQL
+--> MongoDB
+--> Redis
+--> RabbitMQ

Only expose public ports that are necessary.

Database ports must not be exposed publicly.

Recommended public ports:

22
80
443

Use:

- Docker
- Docker Compose
- Nginx
- Certbot
- UFW

---

# 25. Coding Behavior

Before modifying code:

1. Inspect the existing project.
2. Identify the service.
3. Identify dependencies.
4. Identify configuration.
5. Identify database model.
6. Identify existing API contracts.
7. Preserve existing conventions.

Do not rewrite the entire project unnecessarily.

Do not introduce new frameworks unless necessary.

Do not replace working implementations without a clear reason.

---

# 26. Dependency Rules

Before adding a dependency:

1. Check whether it already exists.
2. Check whether Spring Boot already provides the capability.
3. Check compatibility with Java 17.
4. Check compatibility with the existing Spring Boot version.
5. Add the smallest dependency necessary.

Avoid dependency duplication.

---

# 27. Configuration Rules

Never silently change:

- Java version
- Spring Boot version
- database type
- package name
- service port
- API contract
- environment variable name

unless explicitly requested or required to fix a clear issue.

---

# 28. Error Handling

Use centralized exception handling.

Typical exceptions:

- ResourceNotFoundException
- BadRequestException
- UnauthorizedException
- ForbiddenException
- ConflictException
- InsufficientStockException

Return consistent API errors.

Example:

{
"code": 409,
"message": "Insufficient stock",
"data": null
}

Do not expose stack traces to clients.

---

# 29. Idempotency

Operations that may be retried must be idempotent.

Especially:

- Payment callback
- Order event processing
- Inventory event processing
- Notification event processing

Use:

- event ID
- transaction ID
- idempotency key
- unique database constraint

where appropriate.

---

# 30. Event Naming

Use clear domain event names.

Examples:

OrderCreatedEvent
StockReservedEvent
StockReservationFailedEvent
PaymentCreatedEvent
PaymentSucceededEvent
PaymentFailedEvent
StockReleasedEvent
OrderConfirmedEvent
OrderCancelledEvent

Events should contain enough information for consumers to process
the event without accessing another service database directly.

---

# 31. AI Agent Workflow

When asked to implement a feature:

STEP 1
Understand requirements.

STEP 2
Inspect existing code.

STEP 3
Identify affected services.

STEP 4
Identify database changes.

STEP 5
Identify synchronous communication.

STEP 6
Identify asynchronous events.

STEP 7
Implement backend logic.

STEP 8
Implement validation and exception handling.

STEP 9
Implement tests.

STEP 10
Update configuration.

STEP 11
Update Docker configuration if required.

STEP 12
Run build/tests.

STEP 13
Report:

- files changed
- functionality implemented
- dependencies added
- configuration changed
- tests executed
- known limitations

---

# 32. Do Not

Do not:

- use localhost between Docker containers
- access another service's database
- expose database ports publicly
- store plaintext passwords
- hardcode secrets
- blindly trust payment callbacks
- decrement stock without concurrency control
- make Notification synchronous
- create distributed transactions using one DB transaction
- add unnecessary technologies
- rewrite unrelated code
- change Java 17 to Java 21
- introduce Kubernetes unless explicitly requested

---

# 33. Reference Documents

Use the following references when working on the project:

- references/architecture.md
- references/services.md
- references/database.md
- references/messaging.md
- references/docker.md

These files contain detailed project-specific rules.