# API đang triển khai

**Email giao dịch:** `POST /api/auth/register` trả `emailVerified=false` và gửi liên kết xác minh qua SMTP. `GET /api/auth/verify-email?token=...` xác minh liên kết một lần (24 giờ); `POST /api/auth/resend-verification` yêu cầu Bearer JWT, giới hạn một lần/phút và năm lần/ngày, trả 202 hoặc 429. `POST /api/auth/login` và `GET /api/users/me` trả thêm `emailVerified`. Tài khoản chưa xác minh vẫn được đặt hàng; thư đơn hàng chờ tối đa bảy ngày. Xem [cấu hình email](email-delivery.md).

Gateway local: `http://localhost:8080`. Prefix thực tế là `/api`, không có `/v1`.
Success trả DTO trực tiếp (hoặc list DTO), chưa dùng envelope `success/data` đồng nhất.
Tài liệu này ghi nhận implementation; không thay đổi API contract bằng cách thêm wrapper.

| Method | Path | Kết quả / xác thực hiện tại |
| --- | --- | --- |
| POST | `/api/auth/register` | 201 UserResponse; firstName, lastName, email, password |
| POST | `/api/auth/login` | 200 accessToken, refreshToken, tokenType, userId, email, role |
| GET | `/api/users/me` | 200 UserResponse; Bearer JWT |
| GET | `/api/admin/test` | ADMIN |
| GET, POST | `/api/products` | GET công khai; POST ADMIN, trả ProductResponse (201); chưa pagination |
| GET, PUT, DELETE | `/api/products/{id}` | GET công khai; PUT/DELETE ADMIN; DTO / DTO / 204 |
| GET | `/api/products/category/{categoryId}` | List ProductResponse |
| GET, POST | `/api/categories` | GET công khai; POST ADMIN, trả CategoryResponse (201) |
| GET, PUT, DELETE | `/api/categories/{id}` | GET công khai; PUT/DELETE ADMIN; DTO / DTO / 204 |
| GET | `/api/inventory?productId=<id>&page=0&size=20` | ADMIN; Page InventoryResponse; `productId` tùy chọn; sắp SKU tăng dần; page ≥ 0, size từ 1 đến 100 |
| GET | `/api/inventory/{sku}` | ADMIN; tồn hiện tại theo SKU; chưa có inventory trả 404 |
| POST | `/api/inventory/{sku}/adjustments` | ADMIN; body `{"quantityDelta":20}`; 200 InventoryResponse; số dương nhập thêm, số âm giảm tồn khả dụng |
| POST | `/api/orders` | 201 OrderResponse; JWT; giữ tồn và tạo PENDING |
| GET | `/api/orders/{id}` | JWT, kiểm tra chủ đơn; theo dõi CONFIRMED/CANCELLED |
| GET | `/api/orders/my-orders` | JWT, list đơn của user; chưa pagination |
| POST | `/api/orders/{id}/cancel` | JWT, chủ đơn, chỉ PENDING; trạng thái khác trả 409 |
| GET | `/api/orders/admin?status=CONFIRMED&page=0&size=20` | ADMIN; danh sách phân trang, lọc trạng thái tùy chọn, sắp `createdAt DESC, id DESC`; `size` từ 1 đến 100 |
| GET | `/api/orders/admin/{id}` | ADMIN; chi tiết đơn bất kỳ |
| POST | `/api/orders/{id}/ship` | ADMIN; chỉ CONFIRMED → SHIPPING; trả OrderResponse |
| POST | `/api/orders/{id}/confirm-receipt` | USER là chủ đơn; chỉ SHIPPING → COMPLETED; trả OrderResponse |

| POST | `/api/payments` | Đã đóng, kể cả ADMIN; payment chỉ tạo qua OrderCreatedEvent |
| GET | `/api/payments/{orderId}` | JWT; chủ đơn hoặc ADMIN |
| POST | `/api/payments/{id}/refund` | ADMIN; mock yêu cầu dấu hủy và xử lý idempotent; PAYOS trả 409, cần đối soát hoàn tiền thật |
| POST | `/api/payments/webhooks/payos` | Chế độ payos; công khai, SDK xác minh chữ ký; ACK 200 |
| GET | `/api/payments/payos/return`, `/api/payments/payos/cancel` | Chế độ payos; hướng dẫn kiểm tra trạng thái; không đổi trạng thái từ query string |

Luồng giao hàng: thanh toán thành công đưa đơn từ PENDING sang CONFIRMED; ADMIN bắt đầu giao; khách hàng chủ đơn xác nhận đã nhận. Hai API chuyển trạng thái dùng transaction và khóa đơn, ghi lịch sử và Outbox cùng trạng thái. Gọi lặp lại hoặc sai trạng thái trả 409; sai quyền trả 403; không có đơn trả 404. Sự kiện `OrderShippedEvent` (`order.shipped`) và `OrderCompletedEvent` (`order.completed`) được Notification Service gửi email đến địa chỉ đã xác minh. Không tự động hoàn tất và không thay đổi tồn kho tại hai bước giao hàng.

Ví dụ request tạo order, dùng ID/SKU sản phẩm đã có inventory trong Order:

```json
{
  "items": [{"productId": "<product-id>", "sku": "<sku>", "quantity": 1}],
  "shippingAddress": "Demo address",
  "note": "Day 14"
}
```

Header `Authorization: Bearer <accessToken>`. Giá lấy từ Product qua gRPC, không nhận giá từ client. Không đủ tồn trả 400 với `message: "Insufficient stock for SKU: <sku>"`; request sai validation cũng có thể trả 400, vì thế test phải kiểm tra nội dung lỗi. Order không tồn tại trả 404, đọc đơn của user khác trả 403, thiếu JWT trả 401.

Khi tạo product, `active=false` cho đến khi Order/Inventory tạo inventory theo SKU và xác nhận qua RabbitMQ. Trường `stock` của catalog là tồn ban đầu, bắt buộc khi tạo SKU mới và không phản ánh tồn hiện tại. Khi PUT product, có thể bỏ qua `stock` của SKU đã có; nếu gửi thì phải bằng giá trị ban đầu trong catalog, thay đổi sẽ bị từ chối (400). Request PUT vẫn cần toàn bộ thông tin và danh sách variants muốn giữ. Runner Day 13/14 chờ đồng bộ hoàn tất rồi mới đặt đơn, không insert inventory trực tiếp. Category chỉ lưu ở Product vì Order/Inventory không có bảng category.

Product Service quản lý tên, giá, ảnh, danh mục và biến thể; Order/Inventory chỉ giữ `productId`, SKU và các số tồn trong bản ghi inventory. API inventory đọc PostgreSQL của Order, không đọc hoặc ghi tồn vào MongoDB. `totalStock` là tổng tồn còn lại (bao gồm phần đang giữ), `reservedStock` là phần giữ cho đơn PENDING, `availableStock` là phần còn có thể đặt. Luôn giữ `totalStock = reservedStock + availableStock` và các số không âm. Khi thanh toán được xác nhận, tổng tồn giảm theo số đã bán.

Ví dụ dùng Bearer token ADMIN qua Gateway:

```http
GET /api/inventory/SKU-001
Authorization: Bearer <admin-access-token>
```

```json
{
  "productId": "<product-id>",
  "sku": "SKU-001",
  "totalStock": 50,
  "reservedStock": 5,
  "availableStock": 45,
  "updatedAt": "2026-10-15T10:00:00"
}
```

```http
POST /api/inventory/SKU-001/adjustments
Authorization: Bearer <admin-access-token>
Content-Type: application/json

{"quantityDelta":20}
```

Request trên tăng `totalStock` lên 70 và `availableStock` lên 65, giữ `reservedStock=5`. Giảm tồn dùng số âm, ví dụ `{"quantityDelta":-3}`. Điều chỉnh dùng SQL UPDATE có điều kiện trong transaction, không ghi đè số lượng đọc trước đó, nên phối hợp được với giữ, trừ và hoàn tồn đồng thời. Không tạo SKU qua API điều chỉnh: SKU phải được khởi tạo từ luồng đồng bộ catalog; chưa đồng bộ xong trả 404. Thiếu JWT/token sai trả 401; USER trả 403; thiếu/null/0 hoặc số ngoài phạm vi integer trả 400; giảm quá khả dụng hoặc tăng tổng quá 2.147.483.647 trả 409 và không đổi tồn. Mỗi POST thành công là một điều chỉnh mới, vì vậy không tự động gửi lại request khi chưa biết kết quả; đọc lại tồn để đối soát. Thao tác ghi log người thực hiện và lượng thay đổi; chưa có sổ lịch sử điều chỉnh bền vững hoặc khóa chống gửi lặp.

Product lưu sự kiện đồng bộ ngay trong document MongoDB, publisher gửi lại khi RabbitMQ gián đoạn. Consumer của Order/Inventory chỉ insert SKU chưa có, không ghi đè tồn của SKU đã giao dịch; xác nhận được ghi vào outbox PostgreSQL. Queue lỗi cần đối soát: `catalog.inventory.sync.dlq` và `catalog.inventory.synced.dlq`. Với product đã tồn tại trước thay đổi này, bật `CATALOG_INVENTORY_BACKFILL_ENABLED=true` khi triển khai để xếp chúng vào luồng đồng bộ; các product đó tạm `active=false` đến khi nhận xác nhận. Index `unique_variant_sku` chỉ áp dụng cho product mới (`skuIndexEligible=true`) để dữ liệu cũ có SKU trùng không chặn khởi động; vẫn cần đối soát SKU trùng cũ trước khi mở rộng ràng buộc cho toàn bộ catalog.

Luồng chính tự tạo payment qua `OrderCreatedEvent`; không gọi thêm POST payment để hoàn tất Saga. Chế độ mock: amount ≤ 10000 thành công, > 10000 thất bại. Chế độ payos: Payment giữ PENDING, trả checkoutUrl/qrCode/expiresAt và chờ tiền được xác minh. GET payment dùng ID số của order chuyển thành string, không dùng providerOrderCode. Notification gửi SMTP và lưu trạng thái job trong MongoDB; chưa có REST API dù Gateway đã có route `/api/notifications/**`.

Product/Payment tự kiểm tra JWT do Identity cấp, yêu cầu chữ ký HS256 hợp lệ, hạn dùng, userId và role. Thiếu/token sai trả 401; USER gọi thao tác ADMIN hoặc đọc payment người khác trả 403. Payment cũ chưa có customerId chỉ ADMIN được đọc.

Hủy trước khi tạo payment: lưu dấu hủy bền vững, payment đến muộn có trạng thái FAILED và không tạo giao dịch thu tiền. Hủy sau khi payment SUCCESS nhưng Order còn PENDING: tự hoàn tiền giả lập và phát PaymentRefundedEvent. Các bước dùng khóa theo orderId và Outbox; event trùng không thu/hoàn tiền thêm lần nữa.

Runner Day 13/14 cần tài khoản ADMIN qua FIXTURE_ADMIN_EMAIL/FIXTURE_ADMIN_PASSWORD để tạo catalog; tài khoản khách hàng riêng vẫn dùng để đặt đơn. Xem [hướng dẫn cập nhật bảo mật](security-and-cancellation.md).

payOS đã có webhook và reconciliation; xem [tích hợp payOS](payos-integration.md). Refund PAYOS trả 409 và tiền đến muộn có refundRequired=true; hệ thống chưa tích hợp chi hộ.

Chưa triển khai: `/api/auth/refresh`, payment methods, Elasticsearch và gRPC ValidateToken. Các ví dụ đó trong [API baseline](api_vi.md) là thiết kế mục tiêu.
