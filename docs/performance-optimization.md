# Tối ưu hiệu năng sau Day 13

Triển khai ngày 12/10/2026, dựa trên các giới hạn đã đo trong
[báo cáo Day 13](day13-performance-report.md). Giữ Java 17, PostgreSQL,
Saga Choreography và các payload/API hiện có. Mã đơn mới có dạng
`ORD-yyyyMMdd-<32 ký tự UUID hex>`; mã đơn cũ vẫn đọc được.

## Các thay đổi

- Order và Payment lấy lô Outbox có kích thước cấu hình được, mặc định 200 event,
  nghỉ 50 ms giữa các lần poll. Các message vẫn độc lập, JSON và persistent.
  Gửi trên cùng channel và chờ broker confirm một lần cho cả lô theo
  [scoped operations của Spring AMQP](https://docs.spring.io/spring-amqp/reference/amqp/template.html).
- Chỉ đánh dấu published khi toàn bộ lô được xác nhận. Lỗi gửi một phần hoặc timeout
  giữ cả lô pending để retry. Crash sau broker confirm nhưng trước DB commit có thể
  gửi trùng; consumer tiếp tục dùng order ID/trạng thái và unique constraint để
  xử lý idempotency. Không cam kết exactly-once hoặc thứ tự toàn cục giữa nhiều publisher.
- Poll dùng `FOR UPDATE SKIP LOCKED`, cho phép các replica lấy lô khác nhau theo
  [cơ chế khóa PostgreSQL](https://www.postgresql.org/docs/16/sql-select.html).
  Khóa được giữ trong transaction đến khi broker confirm và cập nhật DB hoàn tất.
- Thêm index Outbox `(published, id)` ở cả hai DB và reservation `(order_id)` ở Order.
  Bật JDBC batching cho các UPDATE; các INSERT dùng identity vẫn không được batch.
- Mỗi service Order/Payment có 4 RabbitMQ consumer, prefetch 50, cấu hình qua biến môi trường.
- Giữ kho, xác nhận và giải phóng kho dùng SQL UPDATE có điều kiện nguyên tử.
  Cơ chế vẫn là khóa hàng PostgreSQL; mỗi SKU nóng vẫn phải tuần tự hóa việc ghi.
  Guard kiểm tra tồn khả dụng khi reserve, tồn đã giữ khi release/confirm và tổng tồn khi confirm.
- Ghi đơn, lịch sử, Outbox và reservation trước khi cập nhật SKU, trong cùng transaction.
  UPDATE tồn kho nằm cuối luồng để rút ngắn thời gian giữ khóa. Thiếu hàng rollback toàn bộ,
  kể cả Outbox đã INSERT. Reserve/release/confirm khóa SKU theo thứ tự để giảm deadlock.
- Cancel dùng khóa hàng đơn giống các callback Payment, tránh cùng xử lý reservation
  khi khách hủy đơn đồng thời với sự kiện thanh toán.
- Mã đơn dùng UUID đầy đủ thay cho phần dư `nanoTime` sáu chữ số và query
  kiểm tra tồn tại trước INSERT. Unique constraint DB vẫn là lớp kiểm tra cuối cùng.
- Log gRPC lookup và thay đổi reservation chuyển sang DEBUG; log vòng đời đơn/thanh toán vẫn ở INFO.
- Runner chờ các fixture `DAY13-*` PENDING và toàn bộ Order Outbox pending được xử lý hết
  trước mỗi mức tải. Đơn PENDING lịch sử ngoài fixture không chặn phép đo.
  Báo cáo thêm thời gian chờ Saga sau khi k6 kết thúc.

## Cấu hình

| Biến môi trường | Mặc định | Ý nghĩa |
| --- | ---: | --- |
| `OUTBOX_POLL_MS` | 50 | Thời gian nghỉ giữa các poll, sau khi xử lý lô |
| `OUTBOX_BATCH_SIZE` | 200 | Event tối đa mỗi lô; chấp nhận 1–1000 |
| `OUTBOX_CONFIRM_TIMEOUT_MS` | 5000 | Timeout broker confirm cho cả lô |
| `RABBITMQ_CONSUMERS` | 4 | Số consumer cố định trên mỗi service Order/Payment |
| `RABBITMQ_PREFETCH` | 50 | Message chưa ack tối đa mỗi consumer |

Compose local/production và `.env.example` đã khai báo các biến này.
Giảm khoảng nghỉ không đảm bảo throughput theo phép chia batch/poll: phải tính cả
publish, broker confirm, transaction commit và khả năng consumer/DB.

## Triển khai và rollback

Với DB đang có dữ liệu và traffic, chạy các script index trước khi deploy.
`CREATE INDEX CONCURRENTLY` phải chạy ngoài transaction:

- Order DB: [performance-order.sql](../infrastructure/postgres/performance-order.sql).
- Payment DB: [performance-payment.sql](../infrastructure/postgres/performance-payment.sql).

Service local dùng Hibernate `ddl-auto: update` sẽ tạo index nếu chưa tồn tại.
Không đặt các script này vào thư mục init: init chỉ chạy khi tạo volume mới.

```powershell
docker compose build order-inventory-service payment-service
docker compose up -d --no-build --wait order-inventory-service payment-service
```

Nếu cần rollback, dùng lại image trước thay đổi. Các index bổ sung tương thích với
code cũ; mã đơn mới là chuỗi trong cột hiện tại. Có thể giảm consumer và đưa batch/poll
về 20/1000 bằng environment để điều chỉnh tải lên DB/MQ, nhưng code mới vẫn confirm theo lô.

## Kiểm thử

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.15.6-hotspot"
python infrastructure/postgres/run-performance-tests.py
python -m unittest discover -s infrastructure/k6 -p "test_*.py"
docker compose --env-file .env.example config --quiet
docker compose --env-file .env.example -f docker-compose.production.yml config --quiet
```

Runner PostgreSQL dùng image khai báo trong Compose để tạo container tạm có cổng
host ngẫu nhiên, credential test ngẫu nhiên và database `performance_test_*` riêng.
Sau test, database và container được xóa; không thay dữ liệu của stack ứng dụng.
Log test nằm trong `infrastructure/k6/results/` được Git ignore.

48 test Order và 20 test Payment đã PASS, build `bootJar` PASS, không skip.
Hai suite kiểm tra repository Outbox và concurrency Inventory chạy trên PostgreSQL 16 thật;
các suite khác giữ H2/mock. Nếu chỉ chạy Gradle bình thường, test khóa Outbox đồng thời
được skip vì H2 không mô phỏng đầy đủ khóa PostgreSQL; cần runner trên để xác minh.
9 test Python và kiểm tra cấu hình hai Compose đã PASS. Docker build hai service PASS.
CI Order/Payment đã chuyển sang runner PostgreSQL; báo cáo test được lưu thành artifact.

Các tình huống bổ sung: confirm theo lô; timeout/partial send và retry; lô giới hạn;
publisher bỏ qua hàng bị khóa; rollback trả hàng về pending; 100 request tranh một tồn;
hai đơn khóa SKU theo thứ tự ngược nhau; rollback đơn/Outbox/reservation khi SKU sau thiếu;
quantity không dương; confirm/release lặp lại; guard rollback khi tồn không khớp;
payment success/failure đồng thời chỉ tạo một trạng thái cuối.

## Phép đo local

Đo qua Gateway ngày 12/10/2026, cùng thời lượng 30 giây và cùng VU preallocate/max
như baseline (500/1000 ở mức 500; 1000/2000 ở mức 1000). Mỗi mức dùng SKU mới,
quantity 1 và mock payment success. Smoke 5 RPS chạy 5 giây. Cấu hình cuối là 4 consumer.
Generator, backend và monitoring dùng chung Docker Desktop (~7,36 GiB); kết quả này
không đại diện cho production. Test Java đã hoàn tất trước các phép đo cuối.

| Chỉ số | Smoke | 500 RPS | 1000 RPS |
| --- | ---: | ---: | ---: |
| POST hoàn tất / thời gian phát tải (RPS) | 5,20 | 364,37 | 470,80 |
| p95 (ms) | 322,26 | 3293,32 | 5082,11 |
| Error rate | 0% | 0% | 0% |
| Đơn HTTP 201 | 26 | 10931 | 14124 |
| Dropped iterations | 0 | 4070 | 15876 |
| Đơn CONFIRMED khi báo cáo | 26 | 10931 | 14124 |
| Đơn PENDING khi báo cáo | 0 | 0 | 0 |
| Reserved stock khi báo cáo | 0 | 0 | 0 |
| Chờ Saga sau k6 (s) | 0,215 | 11,695 | 18,697 |
| Stock checks | PASS | PASS | PASS |
| k6 thresholds | PASS | FAIL | FAIL |

Ở mức 500, baseline có p95 3475,26 ms và 348,73 RPS; sau tối ưu là 3293,32 ms
và 364,37 RPS. Ở mức 1000, baseline là 4653,51 ms/511,63 RPS; sau tối ưu là
5082,11 ms/470,80 RPS, tức HTTP ở mức này chưa cải thiện. Hai phép đo cao đều chạm
max VUs; không kết luận hệ thống đã nhận đủ tải mục tiêu.

Kết quả đạt được rõ nhất là xử lý hết Saga: baseline còn 7781/15349 đơn PENDING sau
120 giây chờ; bản mới xác nhận toàn bộ 10931/14124 đơn và giải phóng hết reservation
trong timeout. Tất cả kiểm tra stock equation, số reservation, deduction,
overselling và số HTTP 201 đối chiếu DB đều pass; không gặp lỗi trùng mã đơn.
RPS trong bảng vẫn bao gồm POST hoàn tất trong graceful stop, không phải throughput
thanh toán ổn định. Baseline và bản mới cũng không phải phép đo A/B được cô lập:
baseline có test Java dùng chung host và backlog giữa hai mức, còn bản mới hoàn tất
test trước khi đo và chờ backlog cũ; bản mới xử lý payment/confirm đồng thời với HTTP.

Trong lần đo điều tra, PostgreSQL ghi nhận 7/10 kết nối Order chờ khóa (`transactionid`
hoặc `tuple`), cùng một kết nối chờ `WALSync`. Rút ngắn khóa đã cải thiện so với lần
triển khai trung gian (500 RPS: p95 5308,12 ms; 1000 RPS: 6311,09 ms). Đây là quan sát
cục bộ, chưa phải profiling đầy đủ hoặc chứng minh mọi giới hạn nằm ở tồn kho.

Đã thử thêm cấu hình 2 consumer ở mức 500: 377,77 RPS, p95 3988,06 ms, chờ Saga
31,932 giây, tồn kho khớp, threshold vẫn FAIL. Lần này chạy ngay sau restart service
và không có smoke warmup như phép đo 4 consumer; không xem là A/B cô lập. Giữ mặc định
4 consumer và đã đưa stack local về cấu hình này.

Ngưỡng p95 < 2000 ms và dropped iterations = 0 ở 500/1000 RPS vẫn chưa đạt.
Bước tiếp theo cần profiling thời gian chờ pool/khóa/commit và đo generator trên host
riêng, thêm bài đo nhiều SKU để phân biệt giới hạn của một hàng tồn kho nóng với tải
catalog thông thường. Không tắt durability hoặc thay cơ chế tồn kho để làm đẹp số đo.

Dữ liệu gốc nằm trong `infrastructure/k6/results/` (Git ignore):

- Smoke cuối: `day13-5-d82fda1743e6`.
- 500 cuối: `day13-500-20736cab73c1`.
- 1000 cuối: `day13-1000-b8a173342428`.
- Thử 2 consumer: `day13-500-5862d7f9b413`.

```powershell
python infrastructure/k6/run-load-test.py --rates 5 --duration 5 --settle-timeout 30
python infrastructure/k6/run-load-test.py --rates 500 1000 --duration 30 --settle-timeout 120
```
