# Báo cáo hiệu năng Day 13

Đã đo trên máy local ngày **12/10/2026 (Asia/Saigon)** bằng k6 qua API Gateway.
Monitoring và smoke test hoạt động; hai mức tải mục tiêu 500/1000 request mỗi
giây **chưa đạt** ngưỡng p95 và dropped iterations. Các snapshot được ghi nhận
đều có tồn kho không âm và đối chiếu đúng với trạng thái đơn hàng.

## Môi trường và phương pháp

- Docker Desktop, Linux containers: 16 CPU được cấp, RAM 7.898.492.928 bytes (~7,36 GiB).
- Sáu service build từ source bằng Java 17; PostgreSQL 16, MongoDB 7, Redis 7, RabbitMQ 3.13.
- Prometheus 2.55.1, Grafana 11.2.0, Loki 3.7.0, Alloy 1.18.0, k6 1.0.0.
- Generator, services và monitoring dùng cùng Docker host. Test Java chạy trên host
  trong khoảng đo mức 500; đây là phép đo local với tài nguyên dùng chung.
- Mỗi mức tạo một SKU mới, một item quantity 1 mỗi đơn, giá 1000 VND để mock payment success.
- Executor `constant-arrival-rate`; tải cao chạy 30 giây/mức, graceful stop tối đa 30 giây.
- VU preallocate/max: 500/1000 ở mức 500; 1000/2000 ở mức 1000.
- Ngưỡng: p95 < 2000 ms, error rate < 1%, dropped iterations = 0; chờ Saga tối đa 120 giây.
- Mức 1000 chạy sau mức 500; backlog Outbox của mức 500 vẫn đang được xử lý.

Lệnh đã chạy:

```bash
python infrastructure/k6/run-load-test.py --rates 5 --duration 5 --settle-timeout 30
python infrastructure/k6/run-load-test.py --rates 500 1000 --duration 30 --settle-timeout 120
```

## HTTP và throughput

| Chỉ số | Smoke 5 RPS | Mục tiêu 500 RPS | Mục tiêu 1000 RPS |
| --- | ---: | ---: | ---: |
| Thời gian phát tải (s) | 5 | 30 | 30 |
| RPS: POST hoàn tất / thời gian phát tải | 5,20 | 348,73 | 511,63 |
| p95 latency (ms) | 630,19 | 3475,26 | 4653,51 |
| Error rate HTTP | 0% | 0,00956% | 0% |
| POST hoàn tất | 26 | 10462 | 15349 |
| Đơn tạo thành công (HTTP 201) | 26 | 10461 | 15349 |
| Dropped iterations | 0 | 4538 | 14652 |
| k6 thresholds | PASS | FAIL | FAIL |

RPS trong bảng dùng số POST hoàn tất chia cho thời gian phát tải cấu hình, gồm
các POST được hoàn tất trong graceful stop. Login và preflight không tính vào
chỉ số này. Đây không phải số request được xác nhận thanh toán mỗi giây hoặc
bằng chứng hệ thống duy trì ổn định cùng tốc độ đó. Lần chạy ngắn có thể phát
thêm một iteration ở ranh giới thời gian.

Cả hai mức cao đều chạm max VUs và có cảnh báo `Insufficient VUs`. Generator
không phát đủ lượng tải yêu cầu khi request kéo dài; vì vậy không kết luận
service đã nhận đủ 500/1000 request mỗi giây. Tăng VU cần được đo lại trên máy
tạo tải đủ tài nguyên.

## Tồn kho tại thời điểm báo cáo

| Chỉ số | Smoke | Mức 500 | Mức 1000 |
| --- | ---: | ---: | ---: |
| Stock ban đầu | 125 | 15100 | 30100 |
| Total stock | 99 | 12420 | 30100 |
| Reserved stock | 0 | 7781 | 15349 |
| Available stock | 99 | 4639 | 14751 |
| Đơn CONFIRMED | 26 | 2680 | 0 |
| Đơn PENDING | 0 | 7781 | 15349 |
| Đơn CANCELLED | 0 | 0 | 0 |
| Stock checks | PASS | PASS | PASS |
| Saga xử lý hết trong timeout | Có | Chưa | Chưa |

Snapshot cao tải được lấy sau tối đa 120 giây chờ Saga. Đây là tồn kho tại thời
điểm báo cáo, gồm các reservation còn chờ thanh toán; không được hiểu là tồn
kho sau khi tất cả Saga đã hoàn tất. Mức 1000 chưa có đơn CONFIRMED trong
snapshot vì event còn chờ sau backlog của mức 500.

Các kiểm tra `total = available + reserved`, `reserved = pending`,
`initial - total = confirmed`, `confirmed + pending <= initial`, tồn kho không
âm và số HTTP 201 bằng số đơn ghi trong DB đều pass. Không phát hiện overselling
trong các snapshot này; đây không thay thế chứng minh cho mọi trường hợp concurrency.

## Giới hạn được phát hiện

1. **Outbox có backlog:** cả Order và Payment lấy tối đa 20 event mỗi chu kỳ
   `fixedDelay` 1000 ms. Throughput lý thuyết tối đa khoảng 20 event/s trước khi
   tính thời gian publish, thấp hơn nhiều tải tạo đơn. Đơn HTTP 201 không đồng
   nghĩa với Saga đã hoàn tất.
2. **Tranh chấp một SKU:** Inventory hiện dùng SQL pessimistic lock. Độ trễ
   tăng khi nhiều request cùng giữ kho một SKU; đây là nguyên nhân cần kiểm tra
   tiếp bằng profiling, chưa được cô lập thành kết luận duy nhất của lần đo.
3. **Một lỗi trùng mã đơn:** log mức 500 có PostgreSQL unique violation trên
   `order_code` (`Key (order_code) already exists`), dẫn đến một response lỗi.
   Cách tạo mã hiện tại kiểm tra tồn tại trước insert nên vẫn có cửa sổ tranh
   chấp. Báo cáo ghi nhận lỗi để xử lý trong nhiệm vụ cải thiện hiệu năng.
4. **Môi trường dùng chung:** k6 cùng host với backend, monitoring và một phần
   test Java; kết quả không suy rộng thành hiệu năng VPS/production.

## Xác minh triển khai

| Kiểm tra | Kết quả |
| --- | --- |
| Compose local và production | Config validation PASS |
| Prometheus / Loki / Alloy | Native config validation PASS |
| Prometheus | 6 application targets UP |
| HTTP metrics | Histogram bucket hiện diện và PromQL dashboard hợp lệ |
| Grafana | Dashboard `ecommerce-overview`, 11 panel, datasource Prometheus + Loki |
| Loki | Có log của cả 6 service, query `service_name` trả đủ tên |
| Python reconciliation | 5 tests PASS |
| Java tests + bootJar, Java 17 | 90 tests PASS, 0 failures, 0 errors |
| Docker build | 6 service images PASS |
| Smoke test | PASS, Saga hoàn tất, tồn kho khớp |
| Load test 500/1000 | Đã chạy; threshold FAIL, Saga còn backlog, tồn kho khớp |

Test Java gồm Identity 21, Product 21, Order & Inventory 33, Payment 13,
Notification 1 và Gateway 1. Product test ban đầu thiếu MongoDB, sau đó xung
đột gRPC 9090 với Prometheus; lần xác minh cuối đã dùng MongoDB test database
và `GRPC_SERVER_PORT=0`, toàn bộ test pass.

## Dữ liệu gốc

Các file JSON/Markdown được giữ local trong `infrastructure/k6/results/`
(Git ignore, không chứa password hoặc JWT trong report). Run IDs:

- Smoke: `day13-5-d5014e522bcc`, snapshot `2026-10-11T18:03:59.995935+00:00`.
- 500 RPS: `day13-500-6dd2f8f15a4f`, snapshot `2026-10-11T18:07:41.796734+00:00`.
- 1000 RPS: `day13-1000-3a9e78dc1ec4`, snapshot `2026-10-11T18:10:18.655883+00:00`.

Mỗi run có `*-k6.json`, `*-report.json`, `*-report.md`; report chứa snapshot
tồn kho cùng từng stock check. Fixtures và stack Docker được giữ để xem
dashboard/log và điều tra backlog. Triển khai và cách chạy lại nằm trong
[hướng dẫn Day 13](day13-monitoring-load-test.md).
