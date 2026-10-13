# Day 14 — kiểm thử tích hợp và final demo

## Cách chạy lại

Từ thư mục gốc, Docker Compose đang healthy và Python 3.10+. Bản hiện tại yêu cầu tài khoản ADMIN fixture qua FIXTURE_ADMIN_EMAIL/FIXTURE_ADMIN_PASSWORD; chuẩn bị theo [hướng dẫn bảo mật](security-and-cancellation.md).

```powershell
docker compose up -d --build --wait --wait-timeout 300
python infrastructure/day14/run-demo.py
```

Runner gọi Gateway, tạo user/category/product/SKU riêng có tiền tố `day14-<uuid>`, insert inventory fixture vào DB Order, giữ dữ liệu để đối chiếu. Không reset tồn hiện hữu, không xóa volume, không ghi token/password vào báo cáo. Yêu cầu user chạy script có quyền Docker. Môi trường phải dành riêng cho demo vì có 500 worker và bước tạm dừng Notification.

Tham số: `--env-file`, `--compose-file`, `--base-url` (chỉ loopback trên cùng Docker host), `--timeout` (thời gian chờ từng trạng thái, mặc định 120 giây). Nếu kiểm tra thất bại, process trả exit code 1 và ghi kết quả từng kịch bản đã hoàn thành. File JSON nằm trong `infrastructure/day14/results/` và được gitignore. Nếu preflight Docker/config thất bại trước khi có runId, sửa môi trường rồi chạy lại.

Flash sale dùng Python standard library: **500 thread chờ cùng barrier rồi mỗi thread gửi đúng một request**. Mạng và scheduler vẫn quyết định thời điểm request tới server. Đây là một đợt tranh chấp tồn, không chứng minh hệ thống đạt 500 RPS bền vững; dùng k6 Day 13 để đo throughput/p95.

## Kết quả local đã đo

Run ID: `day14-47544dc96797`. Thời điểm bắt đầu `2026-10-12T18:52:59.281433+00:00` (01:52:59 ngày 13/10/2026, Asia/Saigon). Stack Docker Compose local. File gốc: `infrastructure/day14/results/day14-47544dc96797.json`.

| Kịch bản | Bằng chứng | Kết quả |
| --- | --- | --- |
| E2E | Đăng ký/login/JWT `/users/me`, đọc product, order `126372`, payment SUCCESS, order CONFIRMED; đủ log created/payment; stock 0/reserved 0 | PASS |
| Compensation | SKU giá 10001, order `126373`, payment FAILED, order CANCELLED; tồn trả về 1, reserved 0; đủ log created/failed/cancelled | PASS |
| Flash sale | 500 requests; 10 HTTP 201, 490 HTTP 400 đúng lỗi thiếu tồn của SKU, 0 lỗi bất thường; 10 CONFIRMED; total/available/reserved đều 0; thời gian gửi và chờ Saga 3.297 giây | PASS |
| Notification dừng | Order `126874` CONFIRMED, payment SUCCESS khi consumer = 0; queue giữ 2 message | PASS |
| Notification phục hồi | Khởi động lại; đủ log created/payment của order `126874`; consumer = 1, ready/unacked = 0 | PASS |

Tính đúng tồn được kiểm tra bằng một SQL snapshot nhất quán: không âm; total = available + reserved; reserved = số đơn PENDING; tồn ban đầu - total = số CONFIRMED; HTTP 201 khớp số đơn trong DB. Mỗi fixture chỉ có một item, quantity = 1 nên có thể đối chiếu số đơn với số lượng tồn. HTTP 400 do validation, 401/403, 5xx và timeout đều được tính là lỗi bất thường, không tính vào 490 hết hàng.

## Trình tự thuyết trình

1. Mở [README](../README.md), giải thích bounded context, REST/gRPC/RabbitMQ, SQL giữ tồn và Outbox/Saga trên sequence diagram.
2. Chạy runner; đọc JSON `endToEnd` và `compensation`, tìm đúng orderId trong Grafana/Loki hoặc `docker compose logs notification-service`.
3. Trình bày `flashSale.http` và `flashSale.stock`, nhấn mạnh 10 đơn đã CONFIRMED và reserved về 0.
4. Trình bày `serviceFailure.queueDuringOutage` và `queueAfterRecovery`. Lệnh thủ công đúng là `docker compose stop notification-service` / `docker compose start notification-service` (container thực tế có tiền tố `ecommerce-`). Runner có `finally` khởi động lại khi bước kiểm tra bị lỗi.
5. Mở monitoring, đối chiếu [báo cáo k6 Day 13](day13-performance-report.md); không đánh đồng không overselling với đạt latency mục tiêu.
6. Với VPS thật, làm checklist trong [hướng dẫn triển khai](day14-vps-deployment.md), bổ sung domain, commit/image tag, kết quả HTTPS/firewall/renewal và workflow run.

## Phạm vi chưa nghiệm thu

- VPS/domain/HTTPS/firewall và GitHub Actions remote: chưa có bằng chứng trong phiên này.
- Mục tiêu tải Day 13: p95 và dropped iterations còn chưa đạt theo báo cáo đã đo; Day 14 flash sale không thay thế kết quả đó.
- Notification hiện là log mô phỏng email, chưa chứng minh SMTP delivery hay dedup theo eventId. Kết quả Day 14 trên dùng Payment mock; bản hiện tại có [payOS](payos-integration.md) và được kiểm thử riêng.
- Kết quả local bên trên được đo trước bản sửa JWT/RBAC và hủy đơn. Bản hiện tại đã bảo vệ Product/Payment và từ chối hủy CONFIRMED; xem [thay đổi và kiểm thử](security-and-cancellation.md). Refresh endpoint và pagination còn thiếu.

Các kết quả trên xác nhận bốn kịch bản local của Day 14. Chỉ đánh dấu toàn bộ Definition of Done khi có đủ bằng chứng cho những mục còn lại.

## Kiểm tra bổ sung khi bàn giao

- Order & Inventory: 48 test PASS trên PostgreSQL tạm; Payment: 20 test PASS; không failure/error/skipped. Gradle `test bootJar` của hai service PASS với Java 17.
- Python: 4 test cho bộ nghiệm thu/khôi phục Notification và 9 test đối chiếu báo cáo tồn đều PASS.
- Compose local/production validate PASS; các cổng publish của production đều bind `127.0.0.1`, Identity/Order nhận cùng JWT secret.
- Mẫu Nginx chạy `nginx -t` PASS trong container `nginx:stable-alpine`. Đây là kiểm tra cú pháp, chưa phải kiểm tra proxy/TLS trên VPS.
- Sau demo, cả 6 application services và PostgreSQL/MongoDB/Redis/RabbitMQ đều healthy. Notification đã chạy lại; monitoring containers đang running.
- Prometheus xác nhận 6 application targets UP (và target Prometheus UP); Grafana `/api/health` báo database `ok`; truy vấn Loki theo `service_name="notification-service"` tìm đủ 2 log của order `126874` sau phục hồi.
