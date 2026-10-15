# Danh mục Sự kiện (Event Catalog)

## Event email đang chạy

Identity phát `EmailVerificationRequestedEvent` (`identity.email.verification.requested`) với `customerId`, `email`, `verificationUrl`, `expiresAt`; sau khi xác minh phát `CustomerEmailVerifiedEvent` (`identity.email.verified`) với `customerId`, `email`, `verifiedAt`. Order phát `OrderEmailRecipientRegisteredEvent` (`notification.order.recipient`) với `orderId`, `customerId`, `email` chụp tại thời điểm tạo đơn. Cả ba dùng envelope `eventId`, `eventType`, `timestamp`, `data` và Outbox. Notification tiêu thụ các event này cùng bảy event đơn hàng và thanh toán đã có để gửi SMTP; chi tiết tại [email giao dịch](email-delivery.md).
> Runtime hiện dùng ID số dạng chuỗi cho orderId/customerId (ví dụ "123", "42"); các ID có tiền tố trong ví dụ dưới đây thuộc thiết kế ban đầu. Payment hỗ trợ mock và payOS; xem [luồng payOS](payos-integration.md).

## Các Luồng Bất đồng bộ & Cấu trúc Sự kiện

Hệ thống dựa trên Kiến trúc Hướng Sự kiện (Event-Driven Architecture) sử dụng các mẫu Saga và Outbox để xử lý các giao dịch phân tán. Dưới đây là danh mục các sự kiện nghiệp vụ (domain events) cốt lõi di chuyển qua Message Broker.

---

### 1. `OrderCreatedEvent` (Sự kiện Tạo Đơn hàng)
- **Bên phát (Publisher):** Order & Inventory Service
- **Bên đăng ký (Subscribers):** 
  - Payment & Billing Service (để khởi tạo giao dịch thanh toán đang chờ xử lý)
  - Notification Service (để gửi email "Đã nhận đơn hàng")
- **Mô tả:** Được kích hoạt khi khách hàng đặt hàng thành công và hệ thống đã giữ kho thành công.

**Cấu trúc Dữ liệu (Payload):**
```json
{
  "eventId": "evt_987654321",
  "eventType": "OrderCreatedEvent",
  "timestamp": "2026-10-05T14:30:00Z",
  "data": {
    "orderId": "ord_12345",
    "customerId": "usr_999",
    "totalAmount": 150.50,
    "currency": "USD",
    "items": [
      {
        "productId": "prod_001",
        "sku": "SKU-ABC-123",
        "quantity": 2,
        "unitPrice": 75.25
      }
    ]
  }
}
```

---

### 2. `PaymentSucceededEvent` (Sự kiện Thanh toán Thành công)
- **Bên phát (Publisher):** Payment & Billing Service
- **Bên đăng ký (Subscribers):**
  - Order & Inventory Service (để cập nhật trạng thái đơn hàng thành `Confirmed` (Đã xác nhận) và hoàn tất trừ kho)
  - Notification Service (để gửi email "Thanh toán Thành công / Đang chuẩn bị giao hàng")
- **Mô tả:** Được kích hoạt khi webhook từ cổng thanh toán xác nhận đã thu tiền thành công.

**Cấu trúc Dữ liệu (Payload):**
```json
{
  "eventId": "evt_abc123",
  "eventType": "PaymentSucceededEvent",
  "timestamp": "2026-10-05T14:35:10Z",
  "data": {
    "transactionId": "txn_555666777",
    "orderId": "ord_12345",
    "paymentMethod": "STRIPE",
    "amountPaid": 150.50,
    "paidAt": "2026-10-05T14:35:08Z"
  }
}
```

---

### 3. `PaymentFailedEvent` (Sự kiện Thanh toán Thất bại)
- **Bên phát (Publisher):** Payment & Billing Service
- **Bên đăng ký (Subscribers):**
  - Order & Inventory Service (để kích hoạt **Giao dịch Bù đắp (Compensating Transaction)**: giải phóng kho đã giữ và đổi trạng thái đơn hàng thành `Cancelled` (Đã hủy))
  - Notification Service (để gửi thông báo "Thanh toán Thất bại")
- **Mô tả:** Được kích hoạt nếu thanh toán bị từ chối, hết thời gian chờ hoặc không xác minh được.

**Cấu trúc Dữ liệu (Payload):**
```json
{
  "eventId": "evt_xyz987",
  "eventType": "PaymentFailedEvent",
  "timestamp": "2026-10-05T14:36:00Z",
  "data": {
    "transactionId": "txn_888999000",
    "orderId": "ord_12345",
    "reason": "INSUFFICIENT_FUNDS",
    "failedAt": "2026-10-05T14:35:55Z"
  }
}
```

---

### 4. `OrderCancelledEvent` (Sự kiện Hủy Đơn hàng)
- **Bên phát (Publisher):** Order & Inventory Service
- **Bên đăng ký (Subscribers):**
  - Payment Service (lưu dấu hủy; ngăn thanh toán đến muộn hoặc hoàn tiền payment đã SUCCESS)
  - Notification Service (để gửi thông báo "Đơn hàng của bạn đã bị hủy")
- **Mô tả:** Được kích hoạt trong hai trường hợp: (1) Compensating Transaction tự động hủy đơn hàng khi thanh toán thất bại (Saga Flow), hoặc (2) khách hàng chủ động hủy đơn khi đơn hàng đang ở trạng thái `PENDING`. Sự kiện này đảm bảo kho hàng đã giữ (reserved stock) được giải phóng và khách hàng được thông báo kịp thời.

**Cấu trúc Dữ liệu (Payload):**
```json
{
  "eventId": "evt_cancel001",
  "eventType": "OrderCancelledEvent",
  "timestamp": "2026-10-05T14:37:00Z",
  "data": {
    "orderId": "ord_12345",
    "customerId": "usr_999",
    "reason": "PAYMENT_FAILED",
    "cancelledAt": "2026-10-05T14:36:58Z"
  }
}
```

### 5. `PaymentRefundedEvent` (Sự kiện Hoàn tiền)

Payment ghi trạng thái REFUNDED và Outbox trong cùng transaction, routing key
`payment.refunded`; Notification nhận qua notification.queue. Việc hoàn tiền
chỉ xảy ra một lần khi đơn đã hủy nhưng payment trước đó SUCCESS.

```json
{
  "eventId": "uuid",
  "eventType": "PaymentRefundedEvent",
  "timestamp": "2026-10-13T04:00:00Z",
  "data": {
    "orderId": "123",
    "transactionId": "TXN-uuid",
    "amountRefunded": 1000,
    "refundedAt": "2026-10-13T04:00:00Z"
  }
}
```
