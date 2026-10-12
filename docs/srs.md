# Tài liệu Đặc tả Yêu cầu Phần mềm (SRS)
## Nền tảng Thương mại Điện tử Đa kênh (Omnichannel E-Commerce)

### 1. Giới thiệu & Mục đích
**1.1. Mục đích**
Tài liệu Đặc tả Yêu cầu Phần mềm (SRS) này xác định các yêu cầu chức năng và phi chức năng cho Nền tảng Thương mại Điện tử Đa kênh. Hệ thống hướng đến việc quản lý toàn bộ vòng đời bán lẻ trực tuyến, bao gồm quản lý danh mục sản phẩm, xử lý đơn hàng, quản lý kho, thanh toán và gửi thông báo cho khách hàng.

**1.2. Phạm vi**
Nền tảng được thiết kế để xử lý lượng truy cập lớn (như trong các đợt Flash Sale) mà không bị nghẽn hệ thống. Để đạt được điều này, hệ thống áp dụng Kiến trúc Microservices phân tán. Việc này đảm bảo rằng các thành phần riêng lẻ có thể mở rộng (scale) độc lập, cô lập các sự cố hệ thống và cho phép các đội ngũ phát triển hoạt động độc lập mà không ảnh hưởng đến các module khác.

---

### 2. Mô tả Tổng quan
**2.1. Tác nhân (Actors)**
- **Khách hàng / Người dùng cuối (Client):** Duyệt sản phẩm, đặt hàng, thanh toán và nhận các cập nhật/thông báo thông qua Web hoặc Mobile App.
- **Quản trị viên Hệ thống / Quản lý:** Quản lý danh mục sản phẩm, theo dõi đơn hàng, cấu hình hệ thống và giám sát các hoạt động vận hành.

**2.2. Ràng buộc Hệ thống & Hướng dẫn Kiến trúc**
- **Kiến trúc:** Kiến trúc Microservices sử dụng mô hình "Database Per Service" (Cô lập nghiêm ngặt; không truy vấn chéo cơ sở dữ liệu trực tiếp giữa các microservices).
- **Giao thức Giao tiếp:**
  - *Đồng bộ (Synchronous):* HTTP RESTful APIs (dành cho Gateway đến các Services), gRPC (giao tiếp nội bộ với độ trễ thấp, < 5ms).
  - *Bất đồng bộ (Asynchronous):* Kiến trúc hướng sự kiện (Event-driven) thông qua Message Broker (RabbitMQ hoặc Apache Kafka).
- **Cấu trúc Triển khai:** Đóng gói bằng Docker, quản lý bởi Docker Compose, và triển khai trên Cloud VPS. Nginx được sử dụng làm reverse proxy chính.
- **Giao dịch (Transactions):** Quản lý giao dịch phân tán (Distributed Transactions) sử dụng Saga Pattern (Orchestration/Choreography) và Outbox Pattern.
- **Xử lý Đồng thời:** Phải ngăn chặn xung đột dữ liệu (Race condition - ví dụ: bán vượt quá tồn kho khi Flash Sale) bằng cách sử dụng Distributed Locks (Redis Redlock) hoặc khóa dữ liệu (Optimistic/Pessimistic locking) ở cấp database.

---

### 3. Yêu cầu Chức năng (Phân loại theo Microservices)

**3.1. API Gateway**
- **REQ-AG-01:** Hoạt động như một Điểm truy cập duy nhất (Single Point of Entry) cho mọi request từ client (Web/Mobile).
- **REQ-AG-02:** Xác thực token (Authentication/JWT) trước khi điều hướng các request.
- **REQ-AG-03:** Thực thi Giới hạn Tốc độ (Rate Limiting) để chống lại các cuộc tấn công DDoS và spam API.
- **REQ-AG-04:** Điều hướng (Routing) các request từ client đến đúng các microservices phía sau thông qua HTTP/REST hoặc gRPC.

**3.2. Identity & User Service**
- **REQ-ID-01:** Quản lý đăng ký người dùng, hồ sơ tài khoản và địa chỉ giao hàng.
- **REQ-ID-02:** Quản lý Phân quyền theo Vai trò (RBAC) và quyền hạn.
- **REQ-ID-03:** Cấp phát, quản lý và xác minh JSON Web Tokens (JWT), bao gồm cả cơ chế cấp lại (refresh token).
- *Ràng buộc DB:* PostgreSQL hoặc MySQL.

**3.3. Product Catalog Service**
- **REQ-PC-01:** Quản lý thông tin chi tiết sản phẩm, danh mục, thương hiệu, hình ảnh và SKU.
- **REQ-PC-02:** Hỗ trợ các thuộc tính linh hoạt cho nhiều loại sản phẩm khác nhau.
- **REQ-PC-03:** Cung cấp khả năng tìm kiếm toàn văn (full-text search) được tối ưu hóa.
- *Ràng buộc DB:* MongoDB (lưu trữ document) kết hợp với Elasticsearch (công cụ tìm kiếm).

> **Lưu ý phạm vi MVP (2 tuần):** Elasticsearch là yêu cầu bắt buộc theo đề tài gốc cho môi trường production. Trong phạm vi MVP 2 tuần, nếu chưa đủ thời gian, có thể tạm thời sử dụng MongoDB text index để thay thế và tích hợp Elasticsearch đầy đủ trong Phase 2.

**3.4. Order & Inventory Service (Core Service)**
- **REQ-OI-01:** Tiếp nhận và xử lý các đơn đặt hàng của khách hàng.
- **REQ-OI-02:** Quản lý các trạng thái đơn hàng (Pending, Confirmed, Shipping, Completed, Cancelled).
- **REQ-OI-03:** Quản lý mức tồn kho theo thời gian thực và giữ hàng (reserve stock) trong quá trình thanh toán.
- **REQ-OI-04:** Cung cấp các gRPC endpoint để kiểm tra tồn kho đồng bộ, tốc độ cao với Product Service.
- **REQ-OI-05:** Phát (Publish) sự kiện `OrderCreatedEvent` vào Message Broker khi tạo đơn hàng thành công.
- **REQ-OI-06:** Thực hiện hành động bù (Compensating transactions) để khôi phục tồn kho nếu các bước sau đó (như thanh toán) bị lỗi.
- **REQ-OI-07:** Phát sự kiện `OrderCancelledEvent` vào Message Broker khi đơn hàng bị hủy — do Compensating Transaction tự động (Saga Flow) hoặc do khách hàng chủ động hủy.
- **REQ-OI-08:** Hỗ trợ API cho phép khách hàng tự hủy đơn hàng khi đơn đang ở trạng thái `PENDING`. Khi hủy phải giải phóng tồn kho đã giữ (release reserved stock).
- *Ràng buộc DB:* PostgreSQL (đảm bảo tính ACID) và Redis (Sử dụng Distributed Lock để xử lý race condition).

**3.5. Payment & Billing Service**
- **REQ-PB-01:** Tích hợp với các cổng thanh toán bên ngoài (VNPay, MoMo, ZaloPay, Stripe).
- **REQ-PB-02:** Xử lý các webhook và callback trả về từ các cổng thanh toán.
- **REQ-PB-03:** Quản lý việc đối soát tài chính và quy trình hoàn tiền.
- **REQ-PB-04:** Phát (Publish) các sự kiện thanh toán thành công hoặc thất bại vào Message Broker.
- *Ràng buộc DB:* PostgreSQL.

**3.6. Notification Service**
- **REQ-NO-01:** Tiêu thụ (Consume) các sự kiện liên quan từ Message Broker.
- **REQ-NO-02:** Gửi email xác nhận đơn hàng tự động.
- **REQ-NO-03:** Gửi tin nhắn SMS và Push Notification để cập nhật trạng thái đơn hàng.
- *Ràng buộc DB:* Redis (Queue) hoặc MongoDB (lưu nhật ký gửi tin).

---

### 4. Yêu cầu Phi chức năng

**4.1. Yêu cầu Hiệu năng**
- **Sức chịu tải:** Hệ thống phải có khả năng xử lý số lượng truy cập đồng thời lớn, duy trì 500-1000 requests/giây (có thể dùng JMeter/k6 để test) trong các đợt Flash Sale mà không bị giảm hiệu suất hoặc sai lệch tồn kho.
- **Độ trễ:** Việc giao tiếp đồng bộ nội bộ (ví dụ: Từ Order Service gọi sang Product Service) phải duy trì độ trễ dưới 5ms thông qua gRPC.

**4.2. Yêu cầu Bảo mật**
- **Bảo mật Mạng:** Tường lửa UFW (Uncomplicated Firewall) phải được thiết lập để chỉ cho phép truy cập công khai vào các port 80, 443 và 22. Toàn bộ các port của Database (5432, 27017, 6379) phải bị chặn hoàn toàn khỏi Internet công cộng.
- **Dữ liệu trong quá trình truyền tải:** Tất cả lưu lượng mạng giữa client và server phải được mã hóa qua HTTPS bằng chứng chỉ SSL (ví dụ: Certbot/Let's Encrypt).
- **Kiểm soát Truy cập:** Tất cả các truy cập API nội bộ và hành động của người dùng phải được bảo vệ bằng xác thực JWT và cơ chế RBAC chặt chẽ.

**4.3. Khả năng chịu lỗi & Độ tin cậy**
- **Độ sẵn sàng cao & Cô lập lỗi:** Sự cố của một dịch vụ không được gây sụp đổ dây chuyền. Ví dụ: Nếu Notification Service bị sập, Order Service vẫn phải xử lý giao dịch thành công.
- **Tính toàn vẹn của Sự kiện:** Hệ thống phải sử dụng Outbox Pattern để đảm bảo các sự kiện được đẩy vào Message Broker một cách đáng tin cậy, ngay cả trong trường hợp gián đoạn kết nối mạng.
- **Tính nhất quán:** Giao dịch phân tán phải được quản lý chặt chẽ thông qua Saga pattern, triển khai các hành động đền bù/bù đắp tự động khi xảy ra lỗi.

**4.4. Giám sát & Khả năng quan sát (Observability)**
- **Log tập trung:** Log từ tất cả microservices được thu thập bằng Grafana Alloy và giám sát qua Grafana Loki. Alloy thay Promtail đã hết hỗ trợ; xem [hướng dẫn Day 13](day13-monitoring-load-test.md).
- **Metrics:** Các số liệu hệ thống (CPU, RAM, Tốc độ Request) phải được thu thập thông qua Prometheus.
- **Distributed Tracing (Truy vết phân tán):** Luồng xử lý request qua các microservice phải có thể truy vết được bằng Jaeger/Zipkin và OpenTelemetry.

> **Lưu ý phạm vi MVP (2 tuần):** Distributed Tracing (Jaeger/Zipkin) là yêu cầu production đầy đủ theo đề tài. Trong phạm vi MVP 2 tuần, Prometheus + Grafana + Loki là bắt buộc; Jaeger/Zipkin có thể triển khai trong Phase 2 nếu thiếu thời gian.

---

### 5. Phụ lục

**Phụ lục A: Luồng Kiến trúc**
1. **Client -> API Gateway:** Gửi request qua giao thức HTTPS.
2. **API Gateway -> Microservices:** Điều hướng thông qua HTTP REST.
3. **Giao tiếp Đồng bộ Nội bộ:** Order Service gọi sang Product Service qua gRPC để truy xuất dữ liệu với tốc độ cao.
4. **Giao tiếp Bất đồng bộ Nội bộ:** Order Service phát `OrderCreatedEvent` vào RabbitMQ/Kafka. Sau đó Notification Service và Payment Service sẽ nhận và xử lý sự kiện này.

**Phụ lục B: Triển khai & Đường ống CI/CD**
- **Hạ tầng:** Triển khai trên Cloud VPS (Cấu hình tối thiểu: Ubuntu 22.04 LTS, 4 vCPU, 8GB RAM, SSD 50GB).
- **Pipeline:** Tự động build và deploy bằng GitHub Actions.
  - Tự động chạy (trigger) khi code được push vào nhánh `main`.
  - Build và push Docker images lên Docker Hub hoặc GHCR.
  - Kết nối SSH vào VPS để pull các images mới nhất và khởi động lại containers (dùng lệnh `docker compose up -d`).
