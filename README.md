# Omnichannel E-Commerce

Nền tảng thương mại điện tử gồm API Gateway và năm service: Identity, Product,
Order & Inventory, Payment, Notification. Dự án dùng Java 17, Spring Boot,
PostgreSQL, MongoDB, Redis, RabbitMQ và Docker Compose.

Đọc [quy ước code](CODING_CONVENTION.md) trước khi sửa dự án.
Thiết kế và phạm vi nằm trong [kiến trúc](docs/architecture_vi.md),
[API baseline](docs/api_vi.md), [event catalog](docs/event_vi.md),
[SRS](docs/srs.md) và [timeline](docs/timeline-2weeks.md).
API hiện tại trong source dùng `/api/...`; ví dụ `POST /api/orders`.

## Chạy local

Tạo `.env` từ `.env.example`, thay các giá trị mẫu và giữ file này ngoài Git.
Các tên host trong `.env.example` dành cho mạng Docker.

```powershell
Copy-Item .env.example .env
docker compose up -d --build
docker compose ps
```

Gateway: `http://localhost:8080`. Grafana: `http://localhost:3000`.
Prometheus: `http://localhost:9090`. Đăng nhập Grafana bằng cấu hình trong `.env`.
Dashboard **E-Commerce — Monitoring & Logs** được tạo tự động trong folder E-Commerce.

## Monitoring và load test — Day 13

[Hướng dẫn Day 13](docs/day13-monitoring-load-test.md) trình bày Prometheus,
Grafana, Loki, Alloy và cách chạy k6 ở hai mức 500/1000 request mỗi giây.

```powershell
python infrastructure/k6/run-load-test.py
```

Runner tạo tài khoản, sản phẩm và SKU mới cho mỗi mức tải; khởi tạo tồn kho trong
database của Order bằng thao tác fixture dành cho người vận hành. Kết quả JSON
và Markdown nằm trong `infrastructure/k6/results/` (được Git ignore).
Chạy trên môi trường local hoặc staging dành cho thử tải.

[Báo cáo hiệu năng Day 13](docs/day13-performance-report.md) ghi các kết quả đã đo
và giới hạn của môi trường kiểm tra.

[Tối ưu sau Day 13](docs/performance-optimization.md) mô tả Outbox theo lô,
các UPDATE tồn kho nguyên tử, cấu hình consumer và kiểm thử concurrency trên PostgreSQL.

## Build và test

Dùng JDK 17; mỗi service có Gradle Wrapper riêng. Chạy trong thư mục service:

```powershell
.\gradlew.bat test bootJar
```

Nếu test Product chạy cùng lúc với Prometheus, đặt `$env:GRPC_SERVER_PORT = "0"`
trong PowerShell (hoặc `GRPC_SERVER_PORT=0` trên Linux) để test chọn cổng gRPC
ngẫu nhiên và tránh xung đột cổng 9090. Test Product cần MongoDB ở localhost và
dùng database `product_test_db` riêng.

Kiểm tra cấu hình Compose và kiểm tra logic báo cáo:

```powershell
docker compose --env-file .env.example config --quiet
docker compose --env-file .env.example -f docker-compose.production.yml config --quiet
python -m unittest discover -s infrastructure/k6 -p "test_*.py"
```

Compose production dùng image GHCR trong `docker-compose.production.yml`.
Các file provisioning/config monitoring phải được triển khai cùng repository.
Hướng dẫn truy cập monitoring qua SSH tunnel nằm trong tài liệu Day 13.
