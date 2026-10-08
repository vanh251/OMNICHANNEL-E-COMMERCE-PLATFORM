# CODING CONVENTION
## Omnichannel E-Commerce Platform

> Mục tiêu: thống nhất cách code giữa các member và AI Agent/Antigravity khi vibe code.
> Tài liệu này áp dụng cho tất cả microservice trong project.

---

## 1. Nguyên tắc bắt buộc

1. **Java 17 là phiên bản chuẩn**. Không tự ý nâng lên Java 21+.
2. Mỗi service là một **bounded context độc lập**.
3. **Database Per Service**: service chỉ được truy cập database của chính mình.
4. Không hard-code password, JWT secret, database URL, API key hoặc credential.
5. Không commit `.env`, secret, private key hoặc token.
6. Không tự ý thêm framework/library mới nếu chưa cần thiết.
7. Không refactor diện rộng khi task chỉ yêu cầu sửa một chức năng.
8. Không thay đổi API contract đã thống nhất nếu chưa có lý do và cập nhật tài liệu.
9. Mọi thay đổi business logic quan trọng phải có test.
10. Code phải build/test được trước khi push.

---

## 2. Kiến trúc service

Cấu trúc mặc định:

```text
service-name/
├── src/main/java/com/example/<service>/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   ├── dto/
│   ├── mapper/
│   ├── config/
│   ├── exception/
│   ├── client/
│   └── event/
├── src/main/resources/
│   ├── application.yml
│   └── application-<profile>.yml
├── src/test/
└── build.gradle
```

### Quy tắc dependency

```text
Controller
    ↓
Service
    ↓
Repository / Client
    ↓
Database / External Service
```

- Controller không chứa business logic.
- Repository không chứa business logic.
- Service xử lý business logic.
- DTO dùng cho API request/response.
- Entity chỉ dùng cho persistence.
- Không trả Entity trực tiếp từ REST API.
- External service communication đặt trong `client/`.
- Event/message đặt trong `event/`.
- Object mapping giữa Entity và DTO đặt trong `mapper/` (dùng MapStruct hoặc thủ công, không viết mapping logic trong Service/Controller).

---

## 3. Naming Convention

### Package

Dùng lowercase:

```text
controller
service
repository
entity
dto
config
exception
client
event
```

### Class

PascalCase:

```java
ProductController
ProductService
ProductRepository
CreateProductRequest
ProductResponse
GlobalExceptionHandler
```

### Method / variable

camelCase:

```java
createProduct()
findProductById()
productId
availableStock
```

### Constant

UPPER_SNAKE_CASE:

```java
MAX_RETRY_COUNT
DEFAULT_PAGE_SIZE
JWT_EXPIRATION_SECONDS
```

### Boolean

Ưu tiên:

```java
isActive
isEnabled
hasStock
```

---

## 4. Controller Convention

Controller chỉ chịu trách nhiệm:

- nhận request
- validate input
- gọi service
- trả response

Ví dụ:

```java
@PostMapping
public ResponseEntity<ApiResponse<ProductResponse>> create(
        @Valid @RequestBody CreateProductRequest request) {

    return ResponseEntity.ok(
        ApiResponse.success(productService.create(request))
    );
}
```

Không viết kiểu:

```java
// KHÔNG LÀM
@PostMapping
public ResponseEntity<?> create(...) {
    // query DB
    // tính stock
    // xử lý payment
    // publish event
    // ...
}
```

---

## 5. DTO Convention

Không expose Entity trực tiếp.

Request:

```text
CreateProductRequest
UpdateProductRequest
LoginRequest
CreateOrderRequest
```

Response:

```text
ProductResponse
UserResponse
OrderResponse
PaymentResponse
```

DTO nên chứa dữ liệu cần thiết cho API, không chứa logic persistence.

Validation dùng Jakarta Validation:

```java
@NotBlank
@Email
@Size
@Positive
@Min
@Max
```

---

## 6. Entity Convention

Entity chỉ đại diện dữ liệu persistence.

Ví dụ:

```java
@Entity
@Table(name = "products")
@Getter
@Setter
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal price;
}
```

Quy tắc:

- Có `@Table` khi tên table cần kiểm soát rõ ràng.
- Không nhúng business logic phức tạp vào Entity.
- Không expose Entity qua controller.
- Dùng `BigDecimal` cho tiền.
- Không dùng `double` cho tiền.

---

## 7. Service Convention

Business logic nằm ở Service.

Ví dụ:

```java
@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;

    public OrderResponse createOrder(CreateOrderRequest request) {
        // validate
        // reserve stock
        // create order
        // publish event
        // return response
    }
}
```

Tên method nên thể hiện business action:

```text
createOrder()
cancelOrder()
reserveStock()
releaseStock()
confirmPayment()
```

Tránh tên quá chung:

```text
process()
handle()
execute()
doSomething()
```

trừ khi context thực sự rõ ràng.

---

## 8. Repository Convention

Repository chỉ phụ trách persistence.

Ví dụ:

```java
public interface ProductRepository
        extends JpaRepository<Product, Long> {

    Optional<Product> findById(Long id);

    boolean existsBySku(String sku);
}
```

Không đặt business rule vào Repository.

---

## 9. API Convention

Base path:

```text
/api/v1/...
```

Ví dụ:

```text
POST   /api/v1/auth/register
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh

GET    /api/v1/users/me

GET    /api/v1/products
GET    /api/v1/products/{id}
POST   /api/v1/products           (ADMIN)
PUT    /api/v1/products/{id}      (ADMIN)
DELETE /api/v1/products/{id}      (ADMIN)
GET    /api/v1/categories
POST   /api/v1/categories         (ADMIN)

POST   /api/v1/orders
GET    /api/v1/orders/{id}
GET    /api/v1/orders/my-orders
POST   /api/v1/orders/{id}/cancel

POST   /api/v1/payments
GET    /api/v1/payments/{orderId}
GET    /api/v1/payments/methods
POST   /api/v1/payments/{id}/refund   (ADMIN)
POST   /api/v1/payments/webhook/{provider}
```

HTTP method:

```text
GET     → đọc
POST    → tạo / action
PUT     → cập nhật toàn bộ
PATCH   → cập nhật một phần
DELETE  → xoá
```

Không dùng `POST` cho mọi endpoint nếu không cần thiết.

### Pagination

Các endpoint trả danh sách phải hỗ trợ pagination:

```text
GET /api/v1/products?page=0&size=20&sort=createdAt,desc
```

Response có pagination phải bọc trong `Page<T>` hoặc trả thêm metadata:

```json
{
  "success": true,
  "data": {
    "content": [...],
    "totalElements": 100,
    "totalPages": 5,
    "currentPage": 0,
    "pageSize": 20
  }
}
```

Default: `page=0`, `size=20`. Không cho phép `size > 100`.

### Webhook Handler

Endpoint nhận webhook từ cổng thanh toán (`/api/v1/payments/webhook/{provider}`) phải:

- Xác minh **chữ ký (signature)** từ provider trước khi xử lý.
- Trả HTTP `200 OK` ngay lập tức — xử lý bất đồng bộ qua internal queue.
- Là **idempotent**: cùng một webhook gọi nhiều lần phải cho kết quả giống nhau.
- Không expose trực tiếp ra Internet nếu có thể; đi qua API Gateway.

---

## 10. Response Convention

Thống nhất response:

```json
{
  "success": true,
  "message": "Product created successfully",
  "data": {},
  "timestamp": "2026-10-01T12:00:00Z"
}
```

Error:

```json
{
  "success": false,
  "message": "Product not found",
  "data": null,
  "timestamp": "2026-10-01T12:00:00Z"
}
```

Không trả stack trace cho client.

---

## 11. Exception Convention

Mỗi service có:

```text
exception/
├── GlobalExceptionHandler.java
├── ResourceNotFoundException.java
├── BusinessException.java
└── ErrorCode.java
```

Ví dụ:

```java
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException
        extends RuntimeException {
}
```

Business error phải có message rõ ràng.

Ví dụ:

```text
PRODUCT_NOT_FOUND
INSUFFICIENT_STOCK
ORDER_NOT_FOUND
PAYMENT_FAILED
UNAUTHORIZED
FORBIDDEN
```

---

## 12. Database Convention

### PostgreSQL

Dùng cho:

```text
Identity
Order / Inventory
Payment
```

### MongoDB

Dùng cho:

```text
Product Catalog
```

### Redis

Dùng cho:

```text
cache
stock reservation / concurrency control
temporary data
```

Quy tắc quan trọng:

```text
Identity Service  → identity DB
Product Service   → product DB
Order Service     → order DB
Payment Service   → payment DB
```

Không được:

```text
Order Service → query trực tiếp Product DB
Payment Service → query trực tiếp Order DB
```

Nếu cần dữ liệu của service khác:

```text
REST API
hoặc
RabbitMQ Event
```

---

## 13. Microservice Communication

### Synchronous

Dùng REST khi cần response ngay:

```text
Gateway → Service
Order → Product
```

### Asynchronous

Dùng RabbitMQ cho event:

```text
OrderCreatedEvent
PaymentSucceededEvent
PaymentFailedEvent
OrderCancelledEvent
```

Event name dùng PascalCase + `Event`.

Payload phải có các field cần thiết. Cấu trúc chuẩn của mọi event:

```json
{
  "eventId": "<uuid>",
  "eventType": "<EventClassName>",
  "timestamp": "<ISO-8601>",
  "data": { ... }
}
```

- `eventId`: UUID, dùng để **idempotency** — consumer kiểm tra xem event đã xử lý chưa.
- `eventType`: tên class event, dùng để routing.
- `timestamp`: thời điểm phát event, không phải thời điểm nhận.
- `data`: payload nghiệp vụ, phải khớp với định nghĩa trong `docs/event_vi.md`.

Không được thêm/xóa field của event đã publish mà không cập nhật `docs/event_vi.md` và thông báo cho toàn nhóm.

### gRPC Internal Communication

Chỉ dùng gRPC cho giao tiếp **nội bộ giữa các service** khi yêu cầu độ trễ thấp (< 5ms):

```text
Order Service → Product Catalog Service  (VerifyProductDetails)
API Gateway   → Identity Service         (ValidateToken)
```

- Proto file đặt trong `src/main/proto/`.
- Tên method theo PascalCase: `VerifyProductDetails`, `ValidateToken`.
- Không expose gRPC endpoint ra bên ngoài API Gateway.
- Không thêm gRPC channel mới mà không cập nhật `docs/api_vi.md`.

---

## 14. Inventory / Concurrency

Đây là phần critical của project.

Không được viết logic kiểu:

```text
read stock
→ if stock > 0
→ decrease stock
```

mà không có concurrency control.

Phải đảm bảo:

```text
Concurrent Requests
        ↓
Concurrency Control
        ↓
Atomic Reservation
        ↓
No Overselling
```

Các cơ chế được project sử dụng phải được thống nhất trước khi code:

```text
Redis Lock / SQL Lock / Optimistic Lock
```

Không tự ý đổi cơ chế giữa các member.

---

## 15. Transaction / Saga / Outbox

### Transaction

Database transaction dùng cho thao tác atomic trong một service.

Dùng `@Transactional` ở Service layer, **không** dùng ở Controller hay Repository.

Chỉ dùng `@Transactional(readOnly = true)` cho các query không ghi dữ liệu để tối ưu performance.

### Saga (Choreography)

Project dùng **Saga Choreography** (hướng sự kiện). Không dùng Orchestrator tập trung.

Happy Path:

```text
Create Order
    ↓
Reserve Stock (Redis Lock)
    ↓
Publish OrderCreatedEvent
    ↓
Payment Service consume → Tạo giao dịch thanh toán
    ↓
Publish PaymentSucceededEvent
    ↓
Order Service consume → CONFIRMED + Trừ kho thật
```

Compensation Path (khi Payment thất bại):

```text
Publish PaymentFailedEvent
    ↓
Order Service consume → Release Stock + Cancel Order
    ↓
Publish OrderCancelledEvent
    ↓
Notification Service consume → Gửi email hủy đơn
```

Quy tắc:
- Mỗi service **chỉ lắng nghe event liên quan đến mình**.
- Compensation phải **idempotent** — có thể chạy lại nhiều lần mà không sai kết quả.
- Không gọi service khác bằng REST trong bước Compensation; phải dùng event.

### Outbox

Khi vừa thay đổi DB vừa publish event, **bắt buộc** dùng Outbox Pattern:

```text
BEGIN TRANSACTION
  INSERT INTO orders (...)
  INSERT INTO outbox_events (eventType, payload, status='PENDING')
COMMIT

Outbox Publisher (Spring @Scheduled)
  → Poll outbox_events WHERE status='PENDING'
  → Publish to RabbitMQ
  → UPDATE status='PUBLISHED'
```

Không giả định:

```text
DB commit → publish RabbitMQ  ← KHÔNG ATOMIC, có thể mất event
```

Quy tắc Outbox:
- Bảng `outbox_events` nằm trong cùng DB với bảng nghiệp vụ.
- Outbox Publisher retry khi MQ không khả dụng.
- Consumer phải xử lý **idempotency** bằng `eventId`.

---

## 16. Configuration

Không hard-code:

```java
String password = "123456";
String jwtSecret = "...";
```

Dùng environment variable:

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

Các secret bắt buộc nằm trong:

```text
.env
GitHub Secrets
VPS environment
```

Không commit secret thật.

---

## 17. Logging

Dùng log level phù hợp:

```text
ERROR → lỗi cần xử lý
WARN  → tình trạng bất thường
INFO  → business/system event quan trọng
DEBUG → thông tin debug
```

Ví dụ:

```java
log.info("Creating order for userId={}", userId);
log.warn("Insufficient stock for productId={}", productId);
log.error("Payment failed for orderId={}", orderId, exception);
```

Không log:

```text
password
JWT token
credit card
secret
private key
```

---

## 18. Testing

Mỗi feature quan trọng phải có test.

Ưu tiên:

```text
Service Unit Test
Controller/API Test
Repository Test khi cần
Integration Test cho flow quan trọng
Concurrency Test cho Inventory
```

Critical cases:

```text
Identity Service
  register success
  duplicate email
  login success
  invalid password
  JWT authentication
  refresh token

Product Service
  create product (ADMIN)
  update product (ADMIN)
  get product list with pagination
  get product by id

Order & Inventory Service
  create order success
  insufficient stock → reject
  concurrent stock reservation (concurrency test)
  cancel order khi PENDING
  cancel order khi CONFIRMED → reject
  Saga compensation khi payment fail → release stock + cancel order

Payment Service
  payment success → publish PaymentSucceededEvent
  payment failure → publish PaymentFailedEvent
  refund flow
  webhook idempotency (cùng webhook gọi 2 lần → không duplicate)

Notification Service
  consume OrderCreatedEvent → gửi notification
  consume PaymentSucceededEvent → gửi notification
  consume OrderCancelledEvent → gửi notification
  service down → không ảnh hưởng Order Service
```

Không xoá test chỉ vì test đang fail. Phải tìm nguyên nhân.

---

## 19. Git Convention

Branch:

```text
main
develop

feature/identity-service
feature/product-service
feature/order-inventory
feature/payment
feature/notification

bugfix/<short-description>
hotfix/<short-description>
```

Commit:

```text
feat(identity): implement JWT authentication
feat(product): add product CRUD
feat(order): implement stock reservation
fix(order): prevent stock overselling
test(order): add concurrent reservation test
refactor(product): simplify product mapper
docs(api): update OpenAPI specification
build(docker): add product service image
ci: update GitHub Actions workflow
```

Không commit:

```text
update
fix
test
abc
final
final-final
done
```

---

## 20. Vibe Coding Rules cho AI Agent

### Trước khi code

AI Agent BẮT BUỘC:

1. Đọc `CODING_CONVENTION.md`.
2. Đọc `README.md`.
3. Đọc cấu trúc service hiện tại.
4. Kiểm tra Java/Spring Boot version.
5. Kiểm tra dependency hiện có.
6. Tìm code tương tự trước khi tạo class mới.
7. Xác định API/database/event contract liên quan.

### Trong khi code

AI Agent phải:

- ưu tiên sửa code hiện có thay vì tạo duplicate;
- giữ nguyên naming convention;
- giữ nguyên architecture;
- không tự ý thêm dependency;
- không tự ý đổi database;
- không tự ý đổi API contract;
- không refactor unrelated code;
- viết test cho business logic mới;
- chạy build/test sau khi hoàn thành.

### Sau khi code

AI Agent phải báo cáo:

```text
## Implementation Summary

### Changed
- ...

### Added
- ...

### Tests
- ...

### Build
- PASS / FAIL

### Potential Issues
- ...

### Files Changed
- ...
```

Nếu build/test fail, **không được báo PASS**.

---

## 21. Quy tắc khi AI không chắc chắn

Nếu thiếu thông tin quan trọng:

```text
KHÔNG ĐƯỢC TỰ ĐOÁN
```

AI phải:

1. kiểm tra code hiện tại;
2. kiểm tra README/docs;
3. tìm implementation tương tự;
4. nếu vẫn chưa đủ → hỏi member/leader.

Ví dụ không được tự đoán:

```text
RabbitMQ exchange name
API response format
Database schema
JWT secret format
service port
event payload
```

---

## 22. Không được tự ý thay đổi

AI Agent không được tự ý:

- đổi Java 17 → Java 21;
- đổi PostgreSQL → MySQL;
- đổi MongoDB → PostgreSQL;
- đổi RabbitMQ → Kafka;
- thêm Kubernetes;
- thêm Elasticsearch nếu task không yêu cầu;
- thay REST bằng gRPC;
- đổi package structure;
- đổi port của service;
- đổi API contract;
- xoá code của member khác;
- reset/rewrite Git history.

---

## 23. Definition of Done cho một feature

Feature chỉ được xem là DONE khi:

```text
[ ] Code đúng architecture
[ ] Đúng naming convention
[ ] Không hard-code secret
[ ] Không vi phạm Database Per Service
[ ] API/documentation được cập nhật
[ ] Test đã được thêm/cập nhật
[ ] Build PASS
[ ] Test PASS
[ ] Không có unrelated changes
[ ] Commit message đúng convention
[ ] PR mô tả rõ thay đổi
```

---

## 24. Checklist trước khi Merge

```text
Architecture
[ ] Đúng service ownership
[ ] Không truy cập DB service khác
[ ] Không tạo circular dependency

Code
[ ] Controller mỏng
[ ] Business logic nằm ở Service
[ ] DTO không expose Entity
[ ] Exception được xử lý tập trung
[ ] Naming đúng convention

Security
[ ] Không commit secret
[ ] Không log sensitive data
[ ] JWT/RBAC đúng contract

Testing
[ ] Unit test
[ ] Integration/API test nếu cần
[ ] Concurrency test nếu liên quan Inventory

Git
[ ] Branch đúng
[ ] Commit message đúng
[ ] Không có file rác
[ ] Không có unrelated changes

CI
[ ] Build PASS
[ ] Test PASS

Event & Async
[ ] Event payload đúng với docs/event_vi.md
[ ] Outbox được dùng khi publish event (không publish trực tiếp sau DB commit)
[ ] Consumer có xử lý idempotency (kiểm tra eventId)
[ ] Không tạo circular event dependency
```

---

# AI AGENT QUICK PROMPT

Dán prompt này vào đầu mỗi task vibe coding:

```text
You are working on the Omnichannel E-Commerce Platform.

Before coding:
1. Read CODING_CONVENTION.md.
2. Read README.md and relevant service documentation.
3. Inspect the existing code structure.
4. Identify existing patterns before creating new code.
5. Preserve Java 17 and the existing Spring Boot architecture.
6. Do not change API contracts, database ownership, ports, dependencies,
   messaging contracts, or architecture unless explicitly requested.

While coding:
- Follow CODING_CONVENTION.md exactly.
- Keep Controller thin.
- Put business logic in Service.
- Use DTO for API input/output.
- Do not expose Entity directly.
- Respect Database Per Service.
- Do not access another service's database directly.
- Do not hard-code secrets.
- Do not refactor unrelated code.
- Add/update tests for new business logic.

After coding:
1. Run tests.
2. Run build.
3. Fix failures caused by your changes.
4. Report changed files.
5. Report test/build result honestly.
6. List any remaining risks or assumptions.

If important information is missing, inspect the repository first.
Do not invent architecture, API contracts, database schema, event payloads,
service ports, or dependencies.
```

---

## 25. Golden Rule

> **Consistency > Cleverness**

Một đoạn code đơn giản nhưng giống convention của cả team tốt hơn một đoạn code "hay" nhưng tạo ra pattern mới.

> **AI được phép viết code nhanh, nhưng không được tự ý thay đổi kiến trúc của team.**
