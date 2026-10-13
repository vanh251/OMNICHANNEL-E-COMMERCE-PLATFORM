# API đang triển khai

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
| POST | `/api/orders` | 201 OrderResponse; JWT; giữ tồn và tạo PENDING |
| GET | `/api/orders/{id}` | JWT, kiểm tra chủ đơn; theo dõi CONFIRMED/CANCELLED |
| GET | `/api/orders/my-orders` | JWT, list đơn của user; chưa pagination |
| POST | `/api/orders/{id}/cancel` | JWT, chủ đơn, chỉ PENDING; trạng thái khác trả 409 |
| POST | `/api/payments` | Đã đóng, kể cả ADMIN; payment chỉ tạo qua OrderCreatedEvent |
| GET | `/api/payments/{orderId}` | JWT; chủ đơn hoặc ADMIN |
| POST | `/api/payments/{id}/refund` | ADMIN; mock yêu cầu dấu hủy và xử lý idempotent; PAYOS trả 409, cần đối soát hoàn tiền thật |
| POST | `/api/payments/webhooks/payos` | Chế độ payos; công khai, SDK xác minh chữ ký; ACK 200 |
| GET | `/api/payments/payos/return`, `/api/payments/payos/cancel` | Chế độ payos; hướng dẫn kiểm tra trạng thái; không đổi trạng thái từ query string |

Ví dụ request tạo order, dùng ID/SKU sản phẩm đã có inventory trong Order:

```json
{
  "items": [{"productId": "<product-id>", "sku": "<sku>", "quantity": 1}],
  "shippingAddress": "Demo address",
  "note": "Day 14"
}
```

Header `Authorization: Bearer <accessToken>`. Giá lấy từ Product qua gRPC, không nhận giá từ client. Không đủ tồn trả 400 với `message: "Insufficient stock for SKU: <sku>"`; request sai validation cũng có thể trả 400, vì thế test phải kiểm tra nội dung lỗi. Order không tồn tại trả 404, đọc đơn của user khác trả 403, thiếu JWT trả 401.

Runner Day 14 tạo product bằng `{name, categoryId, variants:[{sku,name,price,stock}]}` rồi insert **fixture quản trị** vào database của Order vì chưa có inventory write API. Đây không phải cơ chế đồng bộ dữ liệu liên service của ứng dụng.

Luồng chính tự tạo payment qua `OrderCreatedEvent`; không gọi thêm POST payment để hoàn tất Saga. Chế độ mock: amount ≤ 10000 thành công, > 10000 thất bại. Chế độ payos: Payment giữ PENDING, trả checkoutUrl/qrCode/expiresAt và chờ tiền được xác minh. GET payment dùng ID số của order chuyển thành string, không dùng providerOrderCode. Notification chỉ ghi log, chưa có REST API dù Gateway đã có route `/api/notifications/**`.

Product/Payment tự kiểm tra JWT do Identity cấp, yêu cầu chữ ký HS256 hợp lệ, hạn dùng, userId và role. Thiếu/token sai trả 401; USER gọi thao tác ADMIN hoặc đọc payment người khác trả 403. Payment cũ chưa có customerId chỉ ADMIN được đọc.

Hủy trước khi tạo payment: lưu dấu hủy bền vững, payment đến muộn có trạng thái FAILED và không tạo giao dịch thu tiền. Hủy sau khi payment SUCCESS nhưng Order còn PENDING: tự hoàn tiền giả lập và phát PaymentRefundedEvent. Các bước dùng khóa theo orderId và Outbox; event trùng không thu/hoàn tiền thêm lần nữa.

Runner Day 13/14 cần tài khoản ADMIN qua FIXTURE_ADMIN_EMAIL/FIXTURE_ADMIN_PASSWORD để tạo catalog; tài khoản khách hàng riêng vẫn dùng để đặt đơn. Xem [hướng dẫn cập nhật bảo mật](security-and-cancellation.md).

payOS đã có webhook và reconciliation; xem [tích hợp payOS](payos-integration.md). Refund PAYOS trả 409 và tiền đến muộn có refundRequired=true; hệ thống chưa tích hợp chi hộ.

Chưa triển khai: `/api/auth/refresh`, payment methods, inventory REST, Elasticsearch và gRPC ValidateToken. Các ví dụ đó trong [API baseline](api_vi.md) là thiết kế mục tiêu.
