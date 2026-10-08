# Đường cơ sở API (API Baseline)
## Các Luồng Giao tiếp Đồng bộ

Dưới đây là đường cơ sở của các REST API hướng ra bên ngoài được cung cấp qua API Gateway, và các kênh gRPC nội bộ được sử dụng cho giao tiếp đồng bộ nghiêm ngặt.

---

### 1. REST API Bên ngoài (Client gọi Gateway)

#### **Identity & User APIs (API Người dùng & Định danh)**
| HTTP Method | Path (Đường dẫn) | Mục đích | Dịch vụ Đích |
|-------------|------|---------|----------------|
| `POST` | `/api/v1/auth/register` | Đăng ký tài khoản khách hàng mới. | Identity Service |
| `POST` | `/api/v1/auth/login` | Xác thực người dùng và cấp phát JWT. | Identity Service |
| `POST` | `/api/v1/auth/refresh` | Cấp lại Access Token mới bằng Refresh Token hợp lệ. | Identity Service |
| `GET` | `/api/v1/users/me` | Lấy thông tin hồ sơ của người dùng đã xác thực. | Identity Service |

#### **Product Catalog APIs (API Danh mục Sản phẩm)**
| HTTP Method | Path (Đường dẫn) | Mục đích | Dịch vụ Đích |
|-------------|------|---------|----------------|
| `GET` | `/api/v1/products` | Tìm kiếm/liệt kê sản phẩm (được hỗ trợ bởi Elasticsearch). | Product Catalog Service |
| `GET` | `/api/v1/products/{id}` | Lấy thông tin chi tiết sản phẩm & các SKU. | Product Catalog Service |
| `POST` | `/api/v1/products` | Tạo sản phẩm mới. *(Yêu cầu quyền ADMIN)* | Product Catalog Service |
| `PUT` | `/api/v1/products/{id}` | Cập nhật thông tin sản phẩm. *(Yêu cầu quyền ADMIN)* | Product Catalog Service |
| `DELETE` | `/api/v1/products/{id}` | Xóa sản phẩm. *(Yêu cầu quyền ADMIN)* | Product Catalog Service |
| `GET` | `/api/v1/categories` | Lấy danh sách tất cả danh mục sản phẩm. | Product Catalog Service |
| `POST` | `/api/v1/categories` | Tạo danh mục sản phẩm mới. *(Yêu cầu quyền ADMIN)* | Product Catalog Service |

#### **Order & Inventory APIs (API Đơn hàng & Tồn kho)**
| HTTP Method | Path (Đường dẫn) | Mục đích | Dịch vụ Đích |
|-------------|------|---------|----------------|
| `POST` | `/api/v1/orders` | Gửi yêu cầu đặt hàng mới (khởi tạo luồng Saga). | Order & Inventory Service |
| `GET` | `/api/v1/orders/{id}` | Kiểm tra trạng thái của một đơn hàng cụ thể. | Order & Inventory Service |
| `GET` | `/api/v1/orders/my-orders` | Lấy danh sách tất cả đơn hàng của người dùng đang đăng nhập. | Order & Inventory Service |
| `POST` | `/api/v1/orders/{id}/cancel` | Yêu cầu hủy đơn hàng (chỉ áp dụng khi đơn ở trạng thái PENDING). | Order & Inventory Service |

#### **Payment APIs (API Thanh toán)**
| HTTP Method | Path (Đường dẫn) | Mục đích | Dịch vụ Đích |
|-------------|------|---------|----------------|
| `POST` | `/api/v1/payments` | Khởi tạo giao dịch thanh toán cho một đơn hàng. | Payment & Billing Service |
| `GET` | `/api/v1/payments/{orderId}` | Kiểm tra trạng thái thanh toán của một đơn hàng. | Payment & Billing Service |
| `GET` | `/api/v1/payments/methods` | Lấy danh sách các nhà cung cấp thanh toán khả dụng. | Payment & Billing Service |
| `POST` | `/api/v1/payments/{id}/refund` | Khởi tạo yêu cầu hoàn tiền cho một giao dịch. *(Yêu cầu quyền ADMIN)* | Payment & Billing Service |
| `POST` | `/api/v1/payments/webhook/{provider}` | Endpoint Webhook cho các cổng thanh toán bên ngoài (VNPay, MoMo, Stripe, v.v.). | Payment & Billing Service |

---

### 2. API Đồng bộ Nội bộ (Giao tiếp Giữa các Dịch vụ)

Mặc dù phần lớn giao tiếp nội bộ là bất đồng bộ, các hoạt động kiểm tra dữ liệu nghiêm ngặt vẫn yêu cầu các lệnh gọi đồng bộ với hiệu suất cao.

#### **Kênh gRPC**
| Giao thức | Tên Method / RPC | Bên gọi (Caller) | Bên cung cấp (Target) | Mục đích |
|----------|--------------|--------|-------------------|---------| 
| `gRPC` | `VerifyProductDetails` | Order Service | Product Catalog | Lấy giá chính xác, dữ liệu snapshot của sản phẩm và xác thực SKU ngay tại thời điểm thanh toán. Độ trễ < 5ms. |
| `gRPC` | `ValidateToken` | API Gateway | Identity Service | Xác minh nghiêm ngặt các claim của JWT (nếu Gateway không thể tự xác thực chữ ký). |
