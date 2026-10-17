# Hướng dẫn FE — Đơn hàng (rubric mục 5 · 6)

> Tài liệu còn thiếu trước đây: module `/orders` có **8 endpoint** mà không tài liệu nào mô tả.
> Bổ sung 17/10/2026. Phần **huỷ đơn** nằm ở `tai_lieu_phan_moi_cho_fe/` §6.
> Base URL: `http://localhost:8088/api/v1`

---

## 1. Bảng endpoint

| Method | URL | Quyền | Việc |
|---|---|---|---|
| `POST` | `/orders` | đăng nhập | Đặt đơn |
| `GET` | `/orders/me` | đăng nhập | Đơn của tôi (rút gọn, phân trang) |
| `GET` | `/orders/me/{orderNumber}` | đăng nhập | Chi tiết đơn của tôi |
| `POST` | `/orders/me/{orderNumber}/cancel` | đăng nhập | Khách tự huỷ |
| `GET` | `/orders?status=` | ADMIN | Tất cả đơn, lọc theo trạng thái |
| `GET` | `/orders/{id}` | ADMIN | Chi tiết theo UUID |
| `GET` | `/orders/number/{orderNumber}` | ADMIN | Chi tiết theo mã đơn |
| `PATCH` | `/orders/{id}/status` | ADMIN | Đổi trạng thái |

---

## 2. 🔴 Giỏ hàng nằm ở Flutter, không ở backend

Backend **không lưu giỏ**. Không có `/cart`, không có "thêm vào giỏ".

FE giữ giỏ trong state (Provider/Bloc) rồi **gửi nguyên giỏ một lần** khi bấm đặt hàng. Đây là
quyết định có chủ đích: mục 10 của rubric chấm state management, mà giỏ hàng là bài demo rõ nhất.

**Không sợ gian lận giá.** `OrderItemRequest` chỉ nhận `productId` + `size` + `quantity`; giá,
tên, ảnh đều do server tự tra từ database. Giỏ ở máy có bị sửa cũng không mua rẻ được.

---

## 3. Đặt đơn

```json
POST /orders
{
  "receiverName": "Nguyễn Văn A",
  "phone": "0901234567",
  "email": "a@example.com",
  "address": "12 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM",
  "note": "Giao giờ hành chính",
  "paymentMethod": "COD",
  "items": [
    { "productId": "…", "size": "M", "quantity": 2 },
    { "productId": "…", "size": "L", "quantity": 1 }
  ]
}
```
→ `201` + `OrderResponse`

### 3.1 Ràng buộc

| Field | Bắt buộc | Luật |
|---|---|---|
| `receiverName` | ✅ | ≤ 150, không để trống |
| `phone` | ✅ | `^0\d{8,10}$` — **9 đến 11 chữ số**, bắt đầu bằng 0 |
| `email` | ❌ | đúng định dạng email, ≤ 100. Bỏ trống ⇒ mail xác nhận gửi về email tài khoản |
| `address` | ✅ | ≤ 500 |
| `note` | ❌ | ≤ 500 |
| `paymentMethod` | ✅ | `COD` hoặc `BANK_TRANSFER` — **chỉ có 2 giá trị** |
| `items` | ✅ | **1 đến 50 dòng** |
| `items[].productId` | ✅ | UUID |
| `items[].size` | ✅ | `S` · `M` · `L` |
| `items[].quantity` | ✅ | 1 … 999 |

> ⚠️ `phone` của đơn hàng cho **9–11 số**, rộng hơn `phone` của hồ sơ (`^0\d{9}$` — đúng 10 số).
> Khác nhau có chủ đích: số nhận hàng có thể là số bàn.

### 3.2 Hai dòng trùng sẽ được gộp

Gửi cùng một cặp `(productId, size)` làm nhiều dòng thì backend **cộng số lượng lại thành một
dòng**. Tổng sau khi gộp vẫn phải ≤ 999, nếu không thì `400`.

FE nên tự gộp từ bên giỏ cho khớp hiển thị.

### 3.3 Phí ship

| Điều kiện | Phí |
|---|---|
| `paymentMethod = BANK_TRANSFER` | **0 đ** |
| `subtotal ≥ 1.000.000 đ` | **0 đ** |
| còn lại | **30.000 đ** |

`total = subtotal + shippingFee − discount`. **`discount` hiện luôn bằng 0** (chưa làm mã giảm giá).

Ngưỡng và mức phí đọc từ `app.order.*` nên có thể đổi mà không build lại — FE **đừng hardcode**,
cứ lấy `shippingFee` trong response.

### 3.4 Mã đơn

Dạng `LBVN-2610-A7K2` = `LBVN` + `yyMM` + 4 ký tự.
Bảng chữ `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` — **cố ý bỏ `I`, `O`, `0`, `1`** để khách đọc qua điện
thoại không nhầm. Hiện cho khách đọc chữ hoa, FE nên hiện bằng font đều nét.

---

## 4. Response

### 4.1 `OrderResponse` (chi tiết)

```json
{
  "id": "…", "orderNumber": "LBVN-2610-A7K2", "accountId": "…",
  "status": "PENDING", "paymentMethod": "COD",
  "receiverName": "Nguyễn Văn A", "phone": "0901234567",
  "email": "a@example.com",
  "address": "12 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM",
  "note": "Giao giờ hành chính",
  "cancelReason": null,
  "subtotal": 500000, "shippingFee": 30000, "discount": 0, "total": 530000,
  "items": [
    {
      "id": "…", "productId": "…",
      "productName": "Váy hoa nhí", "productSlug": "vay-hoa-nhi",
      "imageUrl": "https://…", "size": "M",
      "unitPrice": 250000, "quantity": 2, "lineTotal": 500000
    }
  ],
  "createdAt": "…", "updatedAt": "…"
}
```

### 4.2 `OrderSummaryResponse` (danh sách — **không có `items`**)

```json
{
  "id": "…", "orderNumber": "LBVN-2610-A7K2",
  "status": "PENDING", "paymentMethod": "COD",
  "receiverName": "Nguyễn Văn A",
  "total": 530000, "totalItems": 3,
  "createdAt": "…"
}
```

`totalItems` là **tổng số sản phẩm** (2 + 1 = 3), không phải số dòng.

### 4.3 🔴 Dòng hàng là BẢN CHỤP, không phải con trỏ

`productName`, `productSlug`, `imageUrl`, `unitPrice` được **chụp lại tại thời điểm đặt**.

Shop đổi giá, đổi ảnh, đổi tên, thậm chí xoá sản phẩm thì **đơn cũ không đổi** — hoá đơn phải giữ
nguyên như lúc khách bấm mua.

👉 Hệ quả cho FE: **đừng gọi lại `GET /products/id/{productId}` để lấy tên/ảnh trong màn chi tiết
đơn.** Dùng đúng dữ liệu trong `items`. `productId` chỉ để bấm sang trang sản phẩm (và sản phẩm đó
có thể đã bị ẩn — nhận `404` là bình thường, nên bắt lỗi).

---

## 5. Vòng đời trạng thái

```
PENDING ──→ CONFIRMED ──→ SHIPPING ──→ COMPLETED
   │            │
   └────────────┴──→ CANCELLED
```

| Từ | Được sang | Ghi chú |
|---|---|---|
| `PENDING` | `CONFIRMED` · `CANCELLED` | Khách **tự huỷ được** ở bước này |
| `CONFIRMED` | `SHIPPING` · `CANCELLED` | Chỉ admin huỷ được |
| `SHIPPING` | `COMPLETED` | **Không huỷ được nữa** |
| `COMPLETED` | — | điểm cuối |
| `CANCELLED` | — | điểm cuối |

Bước sai ⇒ `409 "Cannot change order status from X to Y"`.

**Huỷ (bất kể ai huỷ) thì kho được hoàn lại ngay** cho đúng từng size.

---

## 6. Màn "Đơn hàng của tôi"

```
GET /orders/me?page=0&size=10&sort=createdAt,desc
```
Mặc định đã là `createdAt` giảm dần, không truyền `sort` cũng được.

Chi tiết: `GET /orders/me/{orderNumber}` — dùng **mã đơn**, không phải UUID.

> Đơn của người khác trả `404` chứ không `403` — cố ý, để không lộ rằng mã đơn đó có thật.

---

## 7. Màn quản trị

```
GET /orders?status=PENDING&page=0&size=20     ← bỏ status thì lấy tất cả
GET /orders/number/LBVN-2610-A7K2             ← khách gọi điện đọc mã
GET /orders/{id}                              ← UUID
PATCH /orders/{id}/status   { "status": "CONFIRMED" }
```

`PATCH .../status` dùng **UUID**, còn tra cứu dùng **mã đơn**. Lấy `id` từ response của
`/orders/number/{orderNumber}`.

Đổi trạng thái thành công ⇒ khách **tự động nhận thông báo trong app**
(xem `tai_lieu_phan_moi_cho_fe/` §4).

---

## 8. Bảng lỗi

| Mã | Message | Khi nào |
|---|---|---|
| `400` | `Order must contain at least one item` | `items` rỗng |
| `400` | `Order must not contain more than 50 different items` | quá 50 dòng |
| `400` | `Quantity must not exceed 999 per product and size` | tổng sau khi gộp vượt trần |
| `400` | `Phone must start with 0 and contain 9 to 11 digits` | sai định dạng |
| `400` | `Payment method is required` | thiếu, hoặc gửi giá trị lạ |
| `401` | `Account no longer exists` | token còn hạn nhưng tài khoản đã bị xoá |
| `404` | `Product not available: {id}` | sản phẩm không tồn tại / đã ẩn / đã xoá |
| `404` | `Size M is not available for product: {tên}` | sản phẩm **chưa có variant size đó** |
| `404` | `Order not found: {orderNumber}` | đơn không phải của mình |
| `409` | `Only N item(s) left in stock for {tên} size M` | không đủ hàng |
| `409` | `This item was just updated by someone else, please try again` | hai khách cùng mua món cuối |
| `409` | `Cannot change order status from X to Y` | bước chuyển sai |
| `409` | `Order is already cancelled` | huỷ hai lần |
| `409` | `Order is already confirmed; contact the shop to cancel` | khách huỷ đơn đã xác nhận |

### 8.1 Hai lỗi FE bắt buộc xử lý riêng

**`404 "Size … is not available"`** — sản phẩm đó admin chưa tạo variant. Đừng hiện "sản phẩm
không tồn tại", hãy hiện "size này tạm không bán" và cho chọn size khác.

**`409 "This item was just updated by someone else"`** — khoá lạc quan, hai người cùng mua món
cuối. **Cho bấm thử lại được**, đây là lỗi thoáng qua, khác hẳn `409 "Only N left"` (thật sự hết hàng).

---

## 9. Thứ backend **không** có

- **Giỏ hàng phía server** — cố ý, xem §2.
- **Mã giảm giá** — `discount` luôn `0`.
- **Thanh toán online** (PayOS/VNPay) — không làm. `BANK_TRANSFER` là chuyển khoản tay, shop
  đối soát thủ công; đơn vẫn vào `PENDING` như `COD`.
- **Sổ địa chỉ** — khách gõ lại mỗi lần. FE có thể lấy sẵn từ `GET /profiles/me`
  (`fullName`, `phone`, `address`) để điền trước.
- **Theo dõi vận đơn / mã vận đơn** — chỉ có 5 trạng thái.
- **Đổi trả hàng** — không có.
- **Đặt hàng không cần tài khoản** — bắt buộc đăng nhập.
- **Tự huỷ đơn treo** — đơn `PENDING` bị bỏ quên sẽ **giữ tồn kho vô thời hạn**. Không có tác vụ
  nền dọn dẹp; admin phải tự huỷ. Xem cảnh báo ở `GHI_NHO_DU_AN.md` §7.8.
