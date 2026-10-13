# Day 14 — triển khai và nghiệm thu VPS

Hướng dẫn dành cho VPS demo Ubuntu 22.04/24.04, tối thiểu theo đề tài 4 vCPU, 8 GB RAM, 50 GB SSD. Chuẩn bị domain có bản ghi A trỏ đúng VPS (AAAA nếu dùng IPv6), tài khoản SSH có sudo và repository/image GHCR truy cập được. Các lệnh dưới đây chạy trên VPS, trừ SSH tunnel chạy trên máy cá nhân.

**Trạng thái:** cấu hình đã chuẩn bị; chưa có bằng chứng chạy trên VPS/domain thực tế. Product/Payment đã có JWT/RBAC; Payment hỗ trợ mock và [payOS](payos-integration.md), email vẫn giả lập. Dùng máy demo riêng; nếu cần trình bày qua Internet, giới hạn truy cập theo IP người chấm trước khi đưa API ra công khai, giữ webhook payOS công khai theo mẫu Nginx. Không dùng dữ liệu khách hàng thật trong demo.

## 1. Ubuntu, Docker và firewall

```bash
sudo apt update
sudo apt install -y ca-certificates curl git nginx ufw python3 snapd
```

Cài Docker Engine từ apt repository chính thức: làm bước **Set up Docker's apt repository** trong [Docker Engine for Ubuntu](https://docs.docker.com/engine/install/ubuntu/#install-using-the-repository), sau đó:

```bash
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker nginx
sudo docker run --rm hello-world
sudo usermod -aG docker "$USER"
```

Đăng xuất/đăng nhập lại rồi kiểm tra `docker version` và `docker compose version`. Nhóm docker có quyền quản trị host; chỉ cấp cho tài khoản triển khai tin cậy. Không cài Java trên VPS nếu chỉ chạy image.

Mở cổng SSH đang dùng **trước** khi bật firewall, giữ phiên SSH hiện tại và thử phiên SSH thứ hai. Nếu SSH không dùng 22, thay lệnh đầu bằng đúng port:

```bash
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw enable
sudo ufw status verbose
```

Áp dụng cùng chính sách trên firewall của nhà cung cấp VPS. Docker published ports có thể vượt qua rule UFW; vì vậy Compose production bind cổng DB, broker, Gateway và monitoring vào loopback. Xem [giới hạn firewall của Docker](https://docs.docker.com/engine/install/ubuntu/#firewall-limitations). Kiểm tra từ máy ngoài, không suy luận an toàn chỉ từ `ufw status`.

## 2. Repository, secrets và images

```bash
sudo install -d -o "$USER" -g "$(id -gn)" /opt/omnichannel-ecommerce
git clone <repository-url> /opt/omnichannel-ecommerce
cd /opt/omnichannel-ecommerce
cp .env.example .env
chmod 600 .env
```

Chỉ copy `.env` khi chưa có. Thay toàn bộ password mẫu của PostgreSQL, MongoDB, Redis, RabbitMQ, Grafana và `JWT_SECRET`; có thể tạo từng secret bằng `openssl rand -hex 32`. Không đưa secret vào git/log/chat. MongoDB URI hiện ghép password trực tiếp, nên dùng chuỗi hex để không phải xử lý ký tự URI đặc biệt.

Giữ hostname nội bộ `postgres`, `mongodb`, `redis`, `rabbitmq` và cổng nội bộ mặc định. Giữ `IDENTITY_DB=identity_db`, `ORDER_DB=order_db`, `PAYMENT_DB=payment_db`: init SQL hiện tạo các tên này. Nếu đổi tên DB, cần cập nhật quy trình tạo DB tương ứng trước khi chạy. Init SQL chỉ chạy lần đầu trên volume rỗng; chỉnh `.env` không đổi credential của dữ liệu đã tồn tại.

Thêm vào `.env` (owner GHCR viết thường):

```dotenv
IMAGE_PREFIX=ghcr.io/<github-owner>/omnichannel
IMAGE_TAG=latest
```

Chạy workflow **Docker Build & Push** để có đủ 6 images. Với package private, đăng nhập GHCR bằng tài khoản có `read:packages` qua `docker login ghcr.io -u <github-user>`; nhập token tại prompt, không ghi token vào file hướng dẫn hay câu lệnh. Deploy key đọc repository phải được cấu hình riêng nếu repository private.

```bash
docker compose -f docker-compose.production.yml config --quiet
docker compose -f docker-compose.production.yml pull
docker compose -f docker-compose.production.yml up -d --wait --wait-timeout 300
docker compose -f docker-compose.production.yml ps
curl -fsS http://127.0.0.1:8080/actuator/health
```

Nếu unhealthy, xem `docker compose -f docker-compose.production.yml logs --tail=100 <service>`. Chỉ dùng file production độc lập, không merge với file local. Không dùng `down -v` để sửa lỗi: volume chứa dữ liệu cần giữ. Cấu hình JVM mỗi service tối đa 512 MB chưa bao gồm bộ nhớ ngoài heap và các dịch vụ hạ tầng; theo dõi RAM thực tế.

## 3. Nginx, DNS và HTTPS

```bash
sudo cp infrastructure/nginx/omnichannel.conf /etc/nginx/sites-available/omnichannel
sudo nano /etc/nginx/sites-available/omnichannel
```

Đổi `api.example.com` thành domain thật. Trong `location /api/`, với VPS demo hạn chế người truy cập, thêm `allow <IP-public-nguoi-cham>;` (mỗi IP một dòng) rồi `deny all;`. Cấp quyền cho IP public của VPS nếu sẽ chạy kiểm tra HTTPS từ chính VPS. Không thêm allowlist vào location thử thách ACME của Certbot.

```bash
sudo ln -s /etc/nginx/sites-available/omnichannel /etc/nginx/sites-enabled/omnichannel
sudo nginx -t
sudo systemctl reload nginx
curl -f http://api.example.com/api/products
sudo snap install --classic certbot
sudo /snap/bin/certbot --nginx -d api.example.com --redirect
sudo /snap/bin/certbot renew --dry-run
curl -f https://api.example.com/api/products
```

Chỉ tạo symlink nếu chưa tồn tại. Certbot cần domain phân giải đúng và HTTP port 80 tới VPS; lệnh nginx plugin thêm TLS vào server block tương ứng. Dùng địa chỉ email quản trị thật khi Certbot hỏi. Hướng dẫn gốc: [Ubuntu — Obtain TLS certificates](https://ubuntu.com/server/docs/how-to/security/obtain-tls-certificates/).

Mẫu Nginx chỉ proxy `/api/`, giữ nguyên URI. Truy cập `/actuator/prometheus` qua domain phải trả 404; Prometheus scrape bên trong Docker vẫn hoạt động. Nếu đổi `GATEWAY_PORT`, đồng bộ upstream Nginx và kiểm tra cấu hình server/healthcheck; cấu hình demo nên giữ 8080.

## 4. GitHub Actions và rollback

Repo có ba workflow: CI kiểm thử; Docker Build & Push tạo 6 images `latest` và `sha-<7 ký tự>`; Deploy Production SSH vào `/opt/omnichannel-ecommerce` sau build thành công trên main hoặc workflow_dispatch.

Thiết lập repository Actions secrets:

| Secret | Giá trị |
| --- | --- |
| `VPS_HOST` | IP/domain của VPS |
| `VPS_USER` | User triển khai đã có quyền Docker |
| `VPS_SSH_KEY` | Private key riêng cho deploy; public key nằm trong authorized_keys của user |
| `VPS_PORT` | Cổng SSH thực tế, thường 22 |

Thiết lập protected branch main yêu cầu CI thành công. Build workflow hiện chạy độc lập với CI: trigger build thành công **không tự chứng minh** test đã đạt. SSH user phải đọc được repository/GHCR mà không cần nhập tương tác.

Deploy dùng `git pull --ff-only`, validate Compose, pull images và `up --wait --wait-timeout 300`. Workflow fail nếu stack chưa healthy; không tự rollback, không prune container/image toàn host. Các lần deploy được xếp hàng qua concurrency group. Workflow mặc định dùng tag trong `.env` (`latest` nếu chưa đổi), chưa ràng buộc immutable image SHA với commit cấu hình; lưu SHA/tag của bản đã nghiệm thu trước mỗi lần cập nhật.

Rollback ứng dụng: đổi `IMAGE_TAG` trong `.env` về `sha-<7 ký tự bản đã đạt>` còn trong GHCR, sau đó chạy lại `pull` và `up --wait`. Nếu cấu hình Compose cũng thay đổi, dùng bản cấu hình tương thích đã lưu. Sao lưu PostgreSQL/MongoDB trước nâng cấp; rollback image không hoàn tác schema/data tự động. Không xóa image cũ khi chưa có phương án rollback.

## 5. Monitoring và nghiệm thu

Prometheus đã scrape Actuator của 6 service; Grafana provision Prometheus/Loki datasource; Alloy đọc container logs và gửi Loki. Mở SSH tunnel từ máy cá nhân:

```bash
ssh -p <ssh-port> -L 3000:127.0.0.1:3000 -L 9090:127.0.0.1:9090 -L 15672:127.0.0.1:15672 <user>@<vps>
```

Vào Grafana `http://localhost:3000`, Prometheus `http://localhost:9090/targets`, RabbitMQ `http://localhost:15672`. Xem thêm [dashboard, PromQL và Loki Day 13](day13-monitoring-load-test.md). Xác nhận 6 service targets UP, RPS/latency xuất hiện khi tạo đơn, Loki tìm được orderId từ demo; chụp bằng chứng đã che credentials.

Trên **VPS demo riêng**, cấu hình tài khoản ADMIN fixture qua FIXTURE_ADMIN_EMAIL/FIXTURE_ADMIN_PASSWORD theo [hướng dẫn bảo mật](security-and-cancellation.md), rồi chạy lại bộ nghiệm thu (sẽ tạo fixture và tạm dừng Notification):

```bash
python3 infrastructure/day14/run-demo.py --compose-file docker-compose.production.yml --base-url http://127.0.0.1:8080
```

Runner dùng loopback và Docker trên cùng host; HTTPS được kiểm tra riêng bằng register/login/order qua domain từ máy ngoài. Không dùng runner này trên môi trường có khách hàng đang hoạt động.

Checklist chỉ đánh dấu khi có bằng chứng:

- [ ] Tất cả container healthy; JSON Day 14 có `passed: true` trên VPS.
- [ ] Register/login/get product/create order qua domain HTTPS, xác nhận CONFIRMED và notification.
- [ ] TLS hợp lệ, HTTP chuyển sang HTTPS, `certbot renew --dry-run` thành công.
- [ ] Từ máy ngoài không truy cập được 5432/27017/6379/5672/15672/8080–8085/3000/9090.
- [ ] Prometheus targets UP, Grafana có metric, Loki có log đơn demo.
- [ ] CI, build/push và deploy thành công; ghi commit/image tag, phần cứng, thời gian và kết quả k6.
- [ ] Backup/restore và rollback được diễn tập trước khi coi hệ thống sẵn sàng vận hành thật.
