# JWT, quyền truy cập và hủy đơn

> Payment hiện có cả mock và payOS. Quy tắc hoàn tiền tự động dưới đây áp dụng cho mock; payOS nhận tiền thật đến muộn sẽ có refundRequired=true và cần đối soát. Xem [tích hợp payOS](payos-integration.md).

Product cho phép đọc catalog công khai; mọi thao tác ghi sản phẩm/danh mục yêu cầu ADMIN. Payment chỉ tạo từ OrderCreatedEvent, lấy customerId và totalAmount do Order phát. GET payment yêu cầu chủ đơn hoặc ADMIN. Endpoint refund yêu cầu ADMIN và dấu hủy đã được Payment lưu; refund lặp lại trả cùng kết quả.

Order chỉ cho chủ đơn hủy PENDING. CONFIRMED, SHIPPING, COMPLETED hoặc CANCELLED trả HTTP 409. Khóa Order trong PostgreSQL quyết định kết quả khi hủy và xác nhận chạy đồng thời: nếu xác nhận thắng, hủy bị từ chối; nếu hủy thắng, tồn được hoàn và payment thành công đến muộn được bù trừ.

## Dấu hủy và xử lý event

Payment dùng bảng payment_order_state với khóa order_id. INSERT ON CONFLICT DO NOTHING và SELECT FOR UPDATE chạy cùng transaction với payment và Outbox. Dấu hủy tồn tại kể cả khi OrderCancelledEvent tới trước OrderCreatedEvent; không xóa dấu này khi còn khả năng phát lại event.

| Thứ tự xử lý | Kết quả |
| --- | --- |
| Hủy → tạo payment | Lưu payment FAILED, không có transactionId, không thu tiền |
| Tạo payment SUCCESS → hủy | Payment REFUNDED, phát đúng một PaymentRefundedEvent |
| Tạo payment FAILED → hủy | Giữ FAILED, không hoàn tiền |
| Event tạo/hủy/refund bị lặp | Không tạo thêm payment hay hoàn tiền lần hai |

PaymentRefundedEvent dùng routing key payment.refunded, tới notification.queue. Notification ghi log thông báo hoàn tiền. Thanh toán/hoàn tiền vẫn là mock; khi tích hợp provider thật cần idempotency key ở provider và trạng thái xử lý bất đồng bộ phù hợp.

## Cấu hình và dữ liệu cũ

Identity, Order, Product và Payment dùng cùng JWT_SECRET. Product/Payment yêu cầu secret ít nhất 32 byte, không có giá trị mặc định trong code. Hai file Compose đã truyền biến này. Nếu đổi secret, cần đăng nhập lại để nhận token mới.

Với cấu hình Hibernate ddl-auto=update hiện có, Payment bổ sung cột nullable payments.customer_id và bảng payment_order_state khi khởi động. Các giá trị PaymentStatus được giữ nguyên để tương thích CHECK constraint của database cũ. Payment cũ thiếu customer_id chỉ ADMIN được đọc; muốn phục hồi quyền chủ đơn, cần đối chiếu dữ liệu xuất từ Order rồi cập nhật qua quy trình quản trị, không đoán owner từ client.

Các đơn CONFIRMED đã bị hủy bởi bản cũ cần đối soát riêng về tồn kho và payment. Code mới không tự sửa dữ liệu lịch sử hoặc phát lại toàn bộ Outbox cũ.

Khi cập nhật môi trường đang chạy, build và cập nhật Notification trước Payment để consumer hiểu PaymentRefundedEvent; cập nhật Product/Order và cấu hình JWT cùng đợt. Với VPS dùng image GHCR, build/push đủ image tương ứng trước deploy.

## Tài khoản ADMIN cho fixture Day 13/14

1. Đăng ký một tài khoản fixture qua Identity bằng mật khẩu riêng. API đăng ký luôn tạo USER.
2. Operator cấp ADMIN cho đúng tài khoản đó trong identity_db, ví dụ câu SQL bên dưới; thay email mẫu bằng email đã đăng ký.
3. Đăng nhập lại để lấy quyền mới. Runner tự đăng nhập từ hai biến môi trường và không ghi token/password vào báo cáo.

```sql
UPDATE users SET role = 'ADMIN'
WHERE email = 'fixture-admin@example.test' AND enabled = true;
```

PowerShell, nhập mật khẩu qua prompt:

```powershell
$env:FIXTURE_ADMIN_EMAIL = Read-Host 'Email ADMIN fixture'
$fixtureCredential = Get-Credential -UserName $env:FIXTURE_ADMIN_EMAIL -Message 'ADMIN fixture'
$env:FIXTURE_ADMIN_PASSWORD = $fixtureCredential.GetNetworkCredential().Password
try {
    python infrastructure/day14/run-demo.py
    # Hoặc: python infrastructure/k6/run-load-test.py --rates 500
} finally {
    Remove-Item Env:FIXTURE_ADMIN_PASSWORD
    Remove-Variable fixtureCredential
}
```

Bash:

```bash
read -r -p 'Email ADMIN fixture: ' FIXTURE_ADMIN_EMAIL
read -r -s -p 'Password: ' FIXTURE_ADMIN_PASSWORD
printf '\n'
export FIXTURE_ADMIN_EMAIL FIXTURE_ADMIN_PASSWORD
python3 infrastructure/day14/run-demo.py
unset FIXTURE_ADMIN_PASSWORD
```

## Kiểm thử

- Product: test JWT thiếu/sai chữ ký/hết hạn/refresh, USER không được ghi, ADMIN ghi được, GET công khai.
- Payment: kiểm tra chủ đơn/ADMIN, từ chối tạo payment qua HTTP, refund cần dấu hủy và quyền ADMIN.
- PostgreSQL: event đảo thứ tự, event trùng, tạo/hủy/refund đồng thời, rollback payment/dấu hủy/Outbox.
- Order: từ chối hủy CONFIRMED; hủy và xác nhận đồng thời giữ tồn nhất quán.
- Runner: fixture catalog dùng token ADMIN, đặt đơn vẫn dùng tài khoản khách.

Chạy lại:

```powershell
python infrastructure/postgres/run-performance-tests.py --env-file .env.example --services order-inventory-service payment-service
python -B -m unittest discover -s infrastructure/day14 -p "test_*.py"
python -B -m unittest discover -s infrastructure/k6 -p "test_*.py"
```

Product dùng Gradle test bootJar với SPRING_MONGODB_URI trỏ vào MongoDB test riêng; không dùng database catalog đang hoạt động.

Kết quả kiểm thử local ngày 13/10/2026:

| Phạm vi | Kết quả |
| --- | --- |
| Order, PostgreSQL tạm, test + bootJar | 49 PASS |
| Payment, PostgreSQL tạm, test + bootJar | 28 PASS |
| Product, MongoDB tạm, test + bootJar | 25 PASS |
| Notification, test + bootJar | 1 PASS |
| Python Day 14 / Day 13 | 5 / 11 PASS |
| Compose local/production | config --quiet PASS |

Không có test failure/error/skipped trong các bộ Java trên. Các container database
kiểm thử đã được dọn. Chưa chạy lại demo toàn stack hoặc deploy VPS với bản sửa này;
kết quả E2E Day 14 cũ không thay thế nghiệm thu bản mới.
