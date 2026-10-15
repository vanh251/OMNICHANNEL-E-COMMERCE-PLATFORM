# Gửi email giao dịch

## Chạy local

`docker compose up -d --build --wait` khởi động Mailpit cùng các service. SMTP nội bộ dùng `mailpit:1025`; hộp thư thử nghiệm tại `http://127.0.0.1:8025`. Mailpit chỉ giữ thư cho việc kiểm thử, không gửi ra Internet. Không dùng stack local để gửi thư cho khách thật.

Đăng ký qua `POST /api/auth/register`, mở email xác minh trong Mailpit rồi truy cập liên kết một lần. `GET /api/users/me` và phản hồi đăng nhập có `emailVerified`. Người dùng cũ bắt đầu ở trạng thái chưa xác minh; sau khi đăng nhập gọi `POST /api/auth/resend-verification` với Bearer token. Có thể đặt hàng trước khi xác minh, nhưng thư về đơn hàng sẽ chờ tối đa bảy ngày.

Runner `python infrastructure/day14/run-demo.py` tự lấy liên kết xác minh từ Mailpit và kiểm tra thư được SMTP chấp nhận. Mailpit là bằng chứng giao tới hộp thư thử nghiệm, không chứng minh khả năng giao tới hộp thư người nhận ngoài Internet.

## Cấu hình production

Điền các giá trị sau trong `.env` trên server, không commit mật khẩu:

```dotenv
EMAIL_VERIFICATION_BASE_URL=https://api.example.com
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=your-smtp-account
MAIL_PASSWORD=your-smtp-password
MAIL_FROM=no-reply@example.com
```

`EMAIL_VERIFICATION_BASE_URL` là URL công khai của Gateway, không thêm dấu `/` cuối. Production Compose yêu cầu đủ các giá trị trên; mặc định bật SMTP AUTH và STARTTLS, và không mở cổng Mailpit. Nhà cung cấp dùng cổng 465 có thể đặt `MAIL_PORT=465`, `MAIL_STARTTLS=false`, `MAIL_STARTTLS_REQUIRED=false`, `MAIL_SSL_ENABLE=true`. Tài khoản SMTP phải được phép dùng địa chỉ `MAIL_FROM`; cấu hình SPF, DKIM và DMARC cho domain gửi theo hướng dẫn của nhà cung cấp. Kiểm tra gửi và nhận bằng địa chỉ thật trước khi mở cho khách hàng.

## Luồng dữ liệu

- Identity lưu token xác minh đã băm SHA-256, hạn 24 giờ và chỉ dùng một lần. Mỗi tài khoản được yêu cầu lại tối đa một lần/phút và năm lần/ngày. Identity ghi event vào Outbox cùng transaction với user/token, rồi phát qua RabbitMQ.
- Order chụp email từ JWT lúc tạo đơn và ghi `OrderEmailRecipientRegisteredEvent` vào Outbox cùng đơn hàng. Email này chỉ nằm trong event riêng cho Notification; các event thanh toán không mang địa chỉ email.
- Notification lưu job trong MongoDB với unique `eventId`, xử lý bảy loại event: tạo đơn, thanh toán thành công/thất bại/hoàn tiền, hủy đơn, bắt đầu giao và nhận hàng. Job chờ event email đơn hàng hoặc xác minh đến trễ. Đơn cũ không có email snapshot sẽ không được gửi thư.
- Worker gửi thư văn bản UTF-8. Lỗi SMTP tạm thời thử lại tối đa năm lần; lỗi xác thực dừng ở `PAUSED_CONFIG`; job thiếu người nhận sau 24 giờ thành `UNDELIVERABLE`; email chưa xác minh sau bảy ngày thành `SUPPRESSED`. Event sai định dạng vào `notification.dead.queue`.

Các trạng thái job trong collection MongoDB `email_deliveries`: `PENDING`, `WAITING`, `SENDING`, `RETRY`, `SENT`, `SKIPPED`, `SUPPRESSED`, `UNDELIVERABLE`, `PAUSED_CONFIG`, `DEAD`. Metric Prometheus `notification_email_jobs{status="..."}` đếm job theo từng trạng thái. Kiểm tra queue `notification.queue`, `notification.dead.queue` và metric này khi xử lý sự cố. Sau khi sửa cấu hình SMTP, chuyển job `PAUSED_CONFIG` về `RETRY` và đặt `nextAttemptAt` về thời điểm hiện tại để chạy lại; kiểm tra người nhận và số lần gửi trước khi thao tác.

SMTP trả về thành công nghĩa là máy chủ gửi đã nhận thư, chưa bảo đảm thư đến inbox. Khi SMTP đã nhận nhưng service lỗi trước khi ghi `SENT`, có thể gửi trùng khi lease hết hạn; header `X-Notification-Event-ID` giúp truy vết, không bảo đảm nhà cung cấp khử trùng lặp.
