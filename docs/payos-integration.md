# Tích hợp payOS

Payment dùng SDK chính thức `vn.payos:payos-java:2.0.1`, hỗ trợ hai chế độ:
`PAYMENT_PROVIDER=payos` cho nhận tiền thật; `mock` cho demo và test tải.
Thông tin kênh đã được đặt trong file local `.env` được gitignore.
Source, `.env.example`, báo cáo và log không chứa các khóa kênh.

Tham khảo: [SDK Java chính thức](https://github.com/payOSHQ/payos-lib-java),
[API payment requests](https://payos.vn/docs/api/),
[webhook và chữ ký](https://payos.vn/docs/tich-hop-webhook/kiem-tra-du-lieu-voi-signature/).

## Luồng thanh toán

1. Khách đăng nhập và tạo order qua `POST /api/orders`.
2. Payment nhận OrderCreatedEvent, lưu payment PENDING, customerId, tổng tiền và providerOrderCode trong transaction. Giá trị do Order cung cấp, không nhận số tiền từ client.
3. Worker tạo link payOS sau khi intent đã commit. GET theo providerOrderCode trước khi tạo giúp khôi phục link khi lần POST trước mất phản hồi.
4. Khách gọi `GET /api/payments/{orderId}` bằng JWT của chủ đơn hoặc ADMIN. Khi link sẵn sàng, response có checkoutUrl, qrCode, paymentLinkId và expiresAt.
5. Khách mở checkoutUrl hoặc quét QR để chuyển khoản.
6. Webhook được SDK xác minh chữ ký; Payment đối chiếu mã link, currency và số tiền trước khi ghi SUCCESS + PaymentSucceededEvent vào Outbox. Order xác nhận đơn và trừ tồn qua Saga hiện có.
7. Worker kiểm tra trạng thái định kỳ, dùng amountPaid để xử lý thanh toán cộng dồn hoặc webhook bị thất lạc. CANCELLED/EXPIRED/FAILED được chuyển thành PaymentFailedEvent để Order giải phóng tồn.

Payment providerOrderCode là số nguyên dương được lưu một lần, tối đa 53 bit, độc lập với ID đơn hàng. GET payment có thể trả PENDING với checkoutUrl chưa có trong thời gian worker chưa tạo xong; client đọc lại sau vài giây. Thời gian polling mặc định 15 giây, link hết hạn sau 900 giây.

payOS dùng VND nguyên; tổng tiền lẻ hoặc currency không hỗ trợ sẽ tạo payment FAILED và phát event bù trừ, không làm tròn giá. Mất kết nối/429/5xx giữ PENDING để retry, không suy đoán đã thanh toán hay thất bại.

API GET của payOS không trả QR như API tạo link. Khi phục hồi sau mất phản hồi POST, qrCode có thể rỗng; checkoutUrl vẫn dẫn đến trang thanh toán payOS.

## Địa chỉ đã cấu hình

| Mục đích | URL |
| --- | --- |
| Webhook | https://api-vanhdz.duckdns.org/api/payments/webhooks/payos |
| Quay lại | https://api-vanhdz.duckdns.org/api/payments/payos/return |
| Quay lại khi hủy checkout | https://api-vanhdz.duckdns.org/api/payments/payos/cancel |

Webhook là POST công khai nhưng bắt buộc chữ ký hợp lệ; không yêu cầu JWT.
Return/cancel chỉ trả hướng dẫn kiểm tra trạng thái trong ứng dụng. Query string
như status=PAID không xác nhận thanh toán và không hủy đơn hàng.

Mẫu Nginx có location riêng cho webhook, nằm ngoài allowlist của /api/ nếu demo
giới hạn IP. Gateway đã định tuyến /api/payments/** tới Payment.

## Cấu hình

Đặt các biến trong `.env` của máy chạy ứng dụng:

```dotenv
PAYMENT_PROVIDER=payos
PAYOS_CLIENT_ID=<client-id>
PAYOS_API_KEY=<api-key>
PAYOS_CHECKSUM_KEY=<checksum-key>
PAYOS_RETURN_URL=https://api-vanhdz.duckdns.org/api/payments/payos/return
PAYOS_CANCEL_URL=https://api-vanhdz.duckdns.org/api/payments/payos/cancel
PAYOS_WEBHOOK_URL=https://api-vanhdz.duckdns.org/api/payments/webhooks/payos
PAYOS_TIMEOUT_MS=10000
PAYOS_LINK_TTL_SECONDS=900
PAYOS_POLL_MS=15000
```

Hai file Compose đã truyền cấu hình payOS cho Payment. File `.env` local không
được đưa lên VPS qua git; operator cần đặt các biến tương ứng trong `.env` VPS.
Payment dùng Hibernate ddl-auto=update để bổ sung các cột provider và refundRequired,
giữ nguyên tập giá trị PaymentStatus.

Local, build rồi cập nhật stack:

```powershell
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 300
```

VPS: build/push image Payment mới, cập nhật `.env`, reload Nginx với các location
mới, rồi deploy image tương ứng theo [hướng dẫn VPS](day14-vps-deployment.md).
Chỉ xác nhận webhook sau khi endpoint HTTPS mới đã hoạt động.

Workflow Deploy Production đã nhận PAYOS_CLIENT_ID, PAYOS_API_KEY và
PAYOS_CHECKSUM_KEY từ GitHub Actions Secrets; ba secret đã được cấu hình.
Workflow ghi các biến vào .env VPS với quyền 600, giữ nguyên thông tin DB,
chạy đúng commit/image sha của lần build, chờ stack healthy, kiểm tra HTTPS
return endpoint rồi đăng ký webhook. Fail ở bước HTTPS/webhook nghĩa là chưa
hoàn tất nghiệm thu payOS dù container đã chạy.

## Xác nhận kênh và đăng ký webhook

Helper đọc khóa từ `.env` hoặc biến môi trường, không in khóa hay thông tin ngân hàng:

```powershell
python infrastructure/payos/verify-channel.py
python infrastructure/payos/verify-channel.py --smoke-link
python infrastructure/payos/verify-channel.py --confirm-webhook
```

Lệnh mặc định chỉ đọc để kiểm tra xác thực. --smoke-link tạo link 2000 VND,
xác minh chữ ký phản hồi rồi hủy ngay; không khởi tạo chuyển tiền.
--confirm-webhook gọi API đăng ký URL, payOS gửi dữ liệu mẫu có chữ ký tới endpoint.
Thông báo mẫu có orderCode không thuộc payment hiện hữu được ACK, không tạo đơn/payment.

## Hủy đơn và tiền đến muộn

Order vẫn chỉ cho khách hủy PENDING. Payment lưu dấu hủy; worker hủy link payOS
chưa thanh toán. Nếu tiền đã được nhận khi đơn hủy, payment giữ SUCCESS và
refundRequired=true để ADMIN đối soát.

Endpoint refund trả 409 cho payment PAYOS vì nhận tiền không đồng nghĩa đã có
dịch vụ chi hộ. Không đánh dấu REFUNDED khi chưa có bằng chứng hoàn tiền.
Mock vẫn giữ luồng bù trừ/hoàn tiền giả lập đã kiểm thử.

## Demo và kiểm thử

Runner Day 13/14 kiểm tra PAYMENT_PROVIDER=mock trước khi tạo fixture. Bộ demo
500 request không chạy trên kênh payOS thật. Để demo mock, đổi mode và restart
Payment trước khi chạy; mode trong Compose phải khớp mode của container đang chạy.

```powershell
python infrastructure/postgres/run-performance-tests.py --env-file .env.example --services payment-service
python -B -m unittest discover -s infrastructure/day14 -p "test_*.py"
python -B -m unittest discover -s infrastructure/k6 -p "test_*.py"
```

Test payOS dùng HTTP server và gateway giả lập, không dùng khóa thật: kiểm tra
headers/chữ ký request, chữ ký response/webhook, intent bền vững, mất phản hồi,
retry, thanh toán một phần, event trùng, hủy/tiền đến muộn và quyền đọc checkout.

## Bằng chứng kiểm thử ngày 13/10/2026

- Payment: 44 test PASS trên PostgreSQL tạm, không failure/error/skipped; Gradle test + bootJar PASS.
- Python: 6 test Day 14 và 12 test Day 13 PASS, gồm từ chối chạy demo tải trên kênh payOS thật.
- Compose local/production validate PASS; Nginx nginx -t PASS.
- Kiểm tra kênh thật: Client ID/API Key được chấp nhận; tạo link thử 2000 VND với providerOrderCode 1397694730237927 thành công, chữ ký phản hồi hợp lệ, hủy link trả CANCELLED. Không thực hiện chuyển tiền.
- Khóa kênh chỉ nằm trong .env được gitignore; quét file source không phát hiện các giá trị khóa được cung cấp.
- Kiểm tra HTTPS domain từ máy hiện tại bị timeout khi TLS handshake. API confirm-webhook trả HTTP 400/code 20; webhook chưa được xác nhận. Chưa chứng minh webhook thật tới ứng dụng hoặc thanh toán/CONFIRMED end-to-end trên VPS.
