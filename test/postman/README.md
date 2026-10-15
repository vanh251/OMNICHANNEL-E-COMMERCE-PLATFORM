# Hướng dẫn sử dụng Postman Collections

Thư mục này chứa các bộ Postman Collection phục vụ kiểm thử thủ công và tự động cho toàn bộ hệ thống **Omnichannel E-Commerce Platform**.

---

## 1. Danh sách các file trong thư mục

* **`Omnichannel_ECommerce_API.postman_collection.json`**: Bộ API Collection đầy đủ nhất của toàn bộ hệ sinh thái (Authentication có xác minh email, Categories & Products, Inventory Management theo SKU, Orders & Fulfillment, Payments Mock & payOS, Actuator Health/Metrics).
* **`Seed_Data.postman_collection.json`**: Collection chuyên dụng để khởi tạo dữ liệu mẫu (Categories, Products, Biến thể SKU) hàng loạt thông qua Postman Collection Runner hoặc Newman CLI.
* **`data/sample_products.json`**: File chứa 10 bộ dữ liệu sản phẩm mẫu phong phú (Điện thoại, Laptop, Thiết bị âm thanh, Đồng hồ thông minh).

---

## 2. Các biến trong Collection (Variables)

Khi import `Omnichannel_ECommerce_API.postman_collection.json`, các biến sau được thiết lập sẵn:

| Tên biến | Giá trị mặc định | Mô tả |
| :--- | :--- | :--- |
| `baseUrl` | `http://localhost:8080` | URL Gateway (`http://localhost:8080` trên local hoặc `https://api-vanhdz.duckdns.org` trên production). |
| `adminEmail` | `admin@example.test` | Tài khoản quản trị viên mặc định (`ADMIN`). |
| `adminPassword` | `Password123!` | Mật khẩu tài khoản quản trị viên. |
| `userEmail` | `customer@example.test` | Tài khoản khách hàng mẫu (`USER`). |
| `userPassword` | `Password123!` | Mật khẩu tài khoản khách hàng. |
| `accessToken` | *(Tự động gán)* | Bearer Token của khách hàng sau khi gọi Login Customer. |
| `adminToken` | *(Tự động gán)* | Bearer Token của quản trị viên sau khi gọi Login Admin. |
| `categoryId` | *(Tự động gán)* | ID danh mục được lưu tự động khi tạo danh mục. |
| `productId` | *(Tự động gán)* | ID sản phẩm được lưu tự động khi tạo sản phẩm. |
| `inventorySku` | `DEMO-PAYOS-2K` | SKU mẫu dùng để test các thao tác điều chỉnh tồn kho. |
| `verificationToken` | `""` | Token xác minh email (lấy từ hộp thư người nhận hoặc Mailpit). |
| `orderId` | `1` | ID đơn hàng để test hủy/xem đơn và thanh toán. |
| `paymentId` | `1` | ID thanh toán để test hoàn tiền (`POST /api/payments/{id}/refund`). |

---

## 3. Cấu trúc các thư mục API

### `01. Authentication & Users`
* `Register User`: Đăng ký tài khoản khách hàng mới (`emailVerified = false`).
* `Resend Verification Email`: Gửi lại email xác minh (Bearer JWT, giới hạn 1 lần/phút, tối đa 5 lần/ngày).
* `Verify Email (One-time link)`: Xác minh địa chỉ email qua liên kết một lần (24h).
* `Login User (Customer)`: Đăng nhập lấy `accessToken`.
* `Login Admin`: Đăng nhập lấy `adminToken`.
* `Get Profile (Me)`: Lấy thông tin user hiện tại (trả về cả trường `emailVerified`).
* `Test Admin Access`: Kiểm tra quyền truy cập ADMIN (`/api/admin/test`).

### `02. Categories & Products`
* `Get All Categories` / `Create Category [ADMIN]` / `Get Category by ID` / `Update Category [ADMIN]` / `Delete Category [ADMIN]`
* `Get All Products` / `Create Product [ADMIN]` / `Get Product by ID` / `Update Product [ADMIN]` / `Delete Product [ADMIN]`
* `Get Products by Category ID`

> [!NOTE]
> Khi tạo sản phẩm mới, trường `stock` của biến thể là tồn ban đầu. Khi cập nhật sản phẩm (`PUT /api/products/{id}`), số lượng tồn do **Inventory Service** quản lý nên có thể bỏ qua `stock`; nếu gửi khác số tồn ban đầu sẽ bị từ chối `400`.

### `02b. Inventory [ADMIN]`
* `List Inventory [ADMIN]`: Lấy danh sách tồn kho có phân trang (`page`, `size`), sắp xếp SKU tăng dần.
* `Filter Inventory by Product ID [ADMIN]`: Lọc tồn kho theo mã `productId`.
* `Get Current Inventory by SKU [ADMIN]`: Xem chi tiết số lượng thực tế (`availableStock`, `reservedStock`, `totalStock`).
* `Adjust Inventory [ADMIN]`: Điều chỉnh nguyên tử tồn kho (`quantityDelta > 0` nhập thêm, `< 0` giảm tồn).
* `Reject Zero Inventory Adjustment [ADMIN]`: Test lỗi `400` khi truyền delta = 0.
* `Reject Removal Beyond Integer Stock [ADMIN]`: Test lỗi `409` khi giảm quá số lượng khả dụng.

### `03. Orders & Inventory`
* `Create Order (Dat hang)`: Đặt hàng và tạm giữ tồn kho.
* `Get Order by ID` / `Get My Orders`
* `Cancel Order (Chu don huy PENDING)`: Hủy đơn và hoàn trả tồn kho (Compensating).
* `Admin List Orders (Loc trang thai)`: Phân trang danh sách đơn hàng cho admin.
* `Admin Get Order by ID`: Xem chi tiết đơn hàng bất kỳ.
* `Admin Start Shipping`: Chuyển trạng thái sang `SHIPPING`.
* `Customer Confirm Receipt`: Khách hàng xác nhận đã nhận hàng (`COMPLETED`).

### `04. Payments & PayOS`
* `Get Payment Details & VietQR Link`: Lấy mã giao dịch, link thanh toán payOS hoặc mã Mock.
* `Refund Payment [ADMIN]`: Hoàn tiền (yêu cầu đơn đã hủy).
* `PayOS Webhook (Mock Callback)` / `PayOS Return URL` / `PayOS Cancel URL`

### `05. Monitoring & Health`
* `Actuator Health Check`: Kiểm tra sức khỏe toàn hệ thống (`/actuator/health`).
* `Actuator Prometheus Metrics`: Xem các metric đo lường hiệu năng (`/actuator/prometheus`).

---

## 4. Hướng dẫn chạy Seed dữ liệu tự động với Newman

Để tự động nạp toàn bộ danh mục và sản phẩm từ `sample_products.json` vào database:

```bash
# Cài đặt Newman (nếu chưa có)
npm install -g newman

# Chạy Seed Data Runner
newman run test/postman/Seed_Data.postman_collection.json \
  -d test/postman/data/sample_products.json \
  --env-var "baseUrl=http://localhost:8080"
```
