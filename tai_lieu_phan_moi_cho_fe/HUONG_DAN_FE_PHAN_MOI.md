# Hướng dẫn FE — Phần mới (Variant · Cửa hàng · Thông báo · Nhắn tin · Huỷ đơn)

> Bổ sung cho `tai_lieu_auth_cho_fe/` và `tai_lieu_shop_cho_fe/`.
> Cập nhật 17/10/2026 · **27 endpoint mới**, tổng dự án **95 endpoint**.
> Đặt hàng (mục 5 · 6) nằm riêng ở `tai_lieu_don_hang_cho_fe/`.
> Base URL: `http://localhost:8088/api/v1`

---

## 1. Sáu điều dễ vấp nhất

| # | Chuyện gì | Làm sao |
|---|---|---|
| 1 | **Sản phẩm mới tạo không mua được** | Phải thêm variant (size + tồn kho) thì nút mua mới hiện. Xem §2 |
| 2 | Sửa tồn kho mà **quên `expectedVersion`** → `400` | Lấy `version` từ `GET` ngay trước đó, gửi kèm. **Sai version ⇒ `409`, KHÔNG được tự thử lại** |
| 3 | Chi tiết cửa hàng là `/stores/id/{id}` **không phải** `/stores/{id}` | `/stores/*` sẽ nuốt luôn `/stores/admin`, nên đặt vậy cho an toàn |
| 4 | Lịch sử và tin mới là **hai URL khác nhau** | `/messages` = trang lịch sử · `/messages/new?since=` = mảng tin mới. Xem §5.3 |
| 5 | `GET /conversations/me` **tự tạo hội thoại** | Gọi một lần là có chỗ gõ, không cần `POST` tạo trước |
| 6 | Khách chỉ huỷ được đơn **`PENDING`** | `CONFIRMED` trở đi ⇒ `409`, hướng khách sang màn nhắn tin |

---

## 2. Tồn kho theo size (mục 4 · 5 · 6)

### 2.1 Vì sao phần này quan trọng

`ProductDetailResponse.variants` là thứ quyết định **khách bấm mua được hay không**. Đặt hàng
bắt buộc gửi `size`, mà size chỉ tồn tại nếu admin đã tạo variant.

**Sản phẩm 0 variant = sản phẩm trưng bày, không bán được.**

### 2.2 Luồng tạo một sản phẩm bán được (admin)

```
1. POST   /products/admin                        → tạo, status tự = HIDDEN
2. POST   /products/admin/{id}/images   + file   → ảnh đầu tự thành thumbnail
3. POST   /products/admin/{id}/variants          → {"size":"S","stockQuantity":10}
4. POST   /products/admin/{id}/variants          → {"size":"M","stockQuantity":15}
5. PATCH  /products/admin/{id}                   → {"status":"ACTIVE"}
```

Bỏ bước 3–4 là khách vào xem rồi **không chọn được size nào**.

### 2.3 Bảng endpoint

| Method | URL | Việc |
|---|---|---|
| `POST` | `/products/admin/{id}/variants` | Thêm một size |
| `PATCH` | `/products/admin/{id}/variants/{variantId}` | Sửa tồn kho |
| `DELETE` | `/products/admin/{id}/variants/{variantId}` | Gỡ một size |
| `GET` | `/products/admin/{id}/variants` | Liệt kê, gồm cả size hết hàng |

Ba cái đầu trả **nguyên `ProductDetailResponse`** → FE cập nhật cả màn bằng một response, không phải gọi lại.

### 2.4 Chỉ có 3 size: `S`, `M`, `L`

Nên tối đa 3 variant mỗi sản phẩm. Gửi `"XL"` ⇒ `400`.

### 2.5 `expectedVersion` khi sửa tồn kho

```json
PATCH /products/admin/{id}/variants/{variantId}
{ "stockQuantity": 25, "expectedVersion": 3 }
```

`version` lấy từ `ProductVariantResponse.version`.

Sai version ⇒ `409 "Variant has changed; refresh before retrying with the latest version"`.

> 🔴 **FE không được tự động thử lại.** Phải tải lại dữ liệu và hỏi người dùng. Tự retry nghĩa là
> ghi đè con số người khác vừa nhập — đúng cái mà `expectedVersion` sinh ra để chặn.

### 2.6 `stockQuantity = 0` là hợp lệ

Đó là cách chính thức để báo **tạm hết hàng**. Sản phẩm vẫn ACTIVE, vẫn xem được.

FE xử lý: `variants.every(v => v.stockQuantity == 0)` ⇒ hiện "Hết hàng", làm mờ nút mua.
Từng size riêng lẻ hết thì làm mờ đúng nút size đó.

### 2.7 Xoá variant — hai điều kiện chặn

| Tình huống | Kết quả |
|---|---|
| Size đang nằm trong đơn `PENDING`/`CONFIRMED`/`SHIPPING` | `409 "Cannot remove a size that still has open orders"` |
| Size chỉ nằm trong đơn `COMPLETED`/`CANCELLED` | ✅ xoá được |
| Xoá size **cuối cùng** của sản phẩm đang `ACTIVE` | `409 "An ACTIVE product must keep at least one size; hide it first"` |
| Sản phẩm đang `HIDDEN` | ✅ xoá hết size cũng được |

---

## 3. Cửa hàng & bản đồ (mục 8)

### 3.1 Endpoint

| Method | URL | Quyền |
|---|---|---|
| `GET` | `/stores` | công khai, phân trang |
| `GET` | `/stores/id/{id}` | công khai |
| `GET` | `/stores/admin` | ADMIN, gồm cả HIDDEN |
| `POST` | `/stores/admin` | ADMIN |
| `GET` | `/stores/admin/{id}` | ADMIN |
| `PATCH` | `/stores/admin/{id}` | ADMIN |
| `DELETE` | `/stores/admin/{id}` | ADMIN, xoá mềm |

### 3.2 Response

```json
{
  "id": "…", "storeName": "Chi nhánh Quận 1",
  "address": "72 Lê Thánh Tôn, Bến Nghé, Quận 1",
  "phone": "0901234501", "openingHours": "08:00 - 22:00",
  "latitude": 10.77689, "longitude": 106.70098,
  "imageUrl": null, "status": "ACTIVE",
  "createdAt": "…", "updatedAt": "…"
}
```

`latitude`/`longitude` là `double` → cắm thẳng vào `LatLng(lat, lng)`.

### 3.3 Đã có sẵn 4 chi nhánh seed

Quận 1 · Quận 3 · Thủ Đức · Tân Bình — toạ độ thật ở TP.HCM, ghim hiện đúng chỗ ngay khi chạy app.

### 3.4 "Cửa hàng gần tôi nhất" làm ở FE

Chỉ có 4 cửa hàng — tải hết rồi tự tính bằng `Geolocator.distanceBetween()`. Backend **không có**
endpoint lọc theo khoảng cách, và cũng không cần.

### 3.5 Ràng buộc khi tạo/sửa

| Field | Luật |
|---|---|
| `storeName` | bắt buộc, ≤ 150, **không trùng** (không phân biệt hoa thường) |
| `address` | bắt buộc, ≤ 300 |
| `phone` | `^0\d{9}$` — 10 số bắt đầu bằng 0 |
| `latitude` | bắt buộc, −90 … 90 |
| `longitude` | bắt buộc, −180 … 180 |
| `imageUrl` | tuỳ chọn, phải `http`/`https`, ≤ 500 |
| `status` | chỉ `ACTIVE`/`HIDDEN`. Gửi `DELETED` ⇒ `400`, phải dùng `DELETE` |

---

## 4. Thông báo (mục 7)

### 4.1 Endpoint — tất cả đều cần đăng nhập

| Method | URL | Việc |
|---|---|---|
| `GET` | `/notifications/me?unreadOnly=&page=&size=` | Danh sách, **mới nhất trước** |
| `GET` | `/notifications/me/unread-count` | `{"unreadCount": 5}` cho chấm đỏ |
| `PATCH` | `/notifications/me/{id}/read` | Đánh dấu một cái |
| `PATCH` | `/notifications/me/read-all` | `{"updatedCount": 5}` |
| `DELETE` | `/notifications/me/{id}` | Xoá hẳn |

**Không nhận `sort`** — màn thông báo chỉ có một thứ tự hợp lý.

### 4.2 Response

```json
{
  "id": "…",
  "type": "ORDER_STATUS",
  "title": "Đơn hàng đã được xác nhận",
  "content": "Đơn LBVN-2610-A7K2 đã được xác nhận và đang được chuẩn bị.",
  "referenceId": "<id đơn hàng>",
  "isRead": false,
  "createdAt": "…"
}
```

👉 **`referenceId` là id đơn hàng** — bấm vào thông báo thì nhảy thẳng màn chi tiết đơn.

### 4.3 Khi nào có thông báo

| Đơn chuyển sang | Tiêu đề |
|---|---|
| `CONFIRMED` | Đơn hàng đã được xác nhận |
| `SHIPPING` | Đơn hàng đang giao |
| `COMPLETED` | Giao hàng thành công |
| `CANCELLED` | Đơn hàng đã huỷ |

⚠️ **Không có thông báo lúc vừa đặt (`PENDING`)** — khách đang nhìn màn kết quả rồi, bắn thêm là thừa.

### 4.4 Những điều FE nên biết

- `PATCH .../read` gọi lại nhiều lần vẫn `200`, không đổi gì → cứ gọi thoải mái.
- Thông báo của người khác trả **`404`** (không phải `403`) — cố ý, để không lộ nó tồn tại.
- Chưa có push về điện thoại. Badge cập nhật khi mở app và khi kéo làm mới.

---

## 5. Nhắn tin (mục 9)

### 5.1 Mô hình

Mỗi khách có **đúng một** hội thoại với shop. Không có chat khách–khách, không có nhóm.

### 5.2 Endpoint

**Phía khách** (cần đăng nhập)

| Method | URL |
|---|---|
| `GET` | `/conversations/me` |
| `GET` | `/conversations/me/messages?page=&size=` |
| `GET` | `/conversations/me/messages/new?since=` |
| `POST` | `/conversations/me/messages` |
| `PATCH` | `/conversations/me/read` |

**Phía admin**

| Method | URL |
|---|---|
| `GET` | `/conversations/admin` |
| `GET` | `/conversations/admin/unread-count` |
| `GET` | `/conversations/admin/{id}/messages?page=&size=` |
| `GET` | `/conversations/admin/{id}/messages/new?since=` |
| `POST` | `/conversations/admin/{id}/messages` |
| `PATCH` | `/conversations/admin/{id}/read` |

### 5.3 Hai URL, đừng nhầm

| URL | Trả về | Thứ tự | Phân trang | Dùng khi |
|---|---|---|---|---|
| `/messages` | **`PageResponse`** `{content, totalElements, …}` | **mới → cũ** | có | mở màn chat, cuộn ngược lên |
| `/messages/new?since=` | **mảng** `[...]` | **cũ → mới** | không | vòng hỏi lại mỗi 3 giây |

- **Mở màn chat** → gọi `/messages`, rồi **đảo mảng** để hiện đúng chiều.
- **Đang mở màn chat** → cứ 3 giây gọi `/messages/new?since=<createdAt của tin cuối đang có>`.
  Không có tin mới thì trả `[]`, rất nhẹ. **Đóng màn là dừng gọi.**
- `/messages/new` **không có `since`** ⇒ trả toàn bộ tin từ đầu. Dùng được nhưng đừng gọi
  trong vòng lặp.

> Ban đầu đây là một endpoint với tham số `since` tuỳ chọn. Đã tách ra vì một URL mà trả hai
> kiểu dữ liệu khác nhau thì Swagger sinh tài liệu sai và FE không sinh được model.

### 5.4 Không dùng WebSocket

Cố ý. Polling 3 giây nhìn y hệt real-time trên màn demo, mà không phải xử lý xác thực kênh
socket, mất kết nối, nối lại.

### 5.5 Gửi tin

```json
POST /conversations/me/messages
{ "content": "Áo này còn size M không shop?" }
```
→ `201` + `ChatMessageResponse` `{id, senderType, content, createdAt}`

`senderType` là `CUSTOMER` hoặc `ADMIN` → quyết định bong bóng chat trái hay phải.

Nội dung được **cắt khoảng trắng hai đầu**, tối đa **2000 ký tự**, rỗng ⇒ `400`.

### 5.6 Số chưa đọc

- Khách: `ConversationResponse.unreadCount`
- Admin: `ConversationSummaryResponse.unreadByAdmin`, và `GET /conversations/admin/unread-count`
  trả **số hội thoại** đang chờ trả lời (không phải số tin).

Quy tắc: **tự gửi tin thì bộ đếm của mình tự về 0** (vừa gõ nghĩa là đã đọc).

### 5.7 Danh sách hội thoại của admin

```json
{
  "id": "…", "accountId": "…",
  "customerName": "Nguyễn Văn A",     // null nếu khách chưa điền hồ sơ
  "customerEmail": "khach@example.com",
  "lastMessageAt": "…",
  "lastMessagePreview": "Áo này còn size M không shop?",   // cắt 200 ký tự
  "unreadByAdmin": 3
}
```
Sắp xếp **hội thoại có tin mới nhất lên đầu**.

⚠️ Danh sách này **chỉ gồm hội thoại đã có ít nhất một tin**. Khách mở màn chat rồi thoát mà
chưa gõ gì thì admin không thấy — đúng ý đồ, vì không có gì để đọc cũng không có gì để trả lời.

---

## 6. Khách tự huỷ đơn

```json
POST /orders/me/{orderNumber}/cancel
{ "reason": "Đặt nhầm size" }        // body TUỲ CHỌN, gửi rỗng cũng được
```

Dùng **`orderNumber`** (`LBVN-2610-A7K2`) chứ không phải id — giống `GET /orders/me/{orderNumber}`.

| Trạng thái đơn | Kết quả |
|---|---|
| `PENDING` | ✅ `200`, kho được hoàn lại ngay |
| `CONFIRMED` / `SHIPPING` | `409 "Order is already confirmed; contact the shop to cancel"` |
| `COMPLETED` | `409` |
| `CANCELLED` | `409 "Order is already cancelled"` |
| Đơn của người khác | `404` (cố ý, không phải `403`) |

👉 Gặp `409` ở đơn `CONFIRMED`, FE nên hiện nút **"Nhắn shop"** đưa thẳng sang màn chat — hai
tính năng nối vào nhau rất tự nhiên.

`OrderResponse` nay có thêm **`cancelReason`** (null nếu admin huỷ hoặc chưa huỷ).

---

## 7. Bảng mã lỗi phần mới

| Mã | Message | Nghĩa |
|---|---|---|
| `400` | `Expected version is required` | Thiếu `expectedVersion` khi sửa tồn kho |
| `400` | `Stock quantity must be zero or greater` | Tồn kho âm |
| `400` | `Variant ID is required` | Thiếu id variant |
| `400` | `Image URL must use http or https` | URL ảnh cửa hàng không hợp lệ |
| `400` | `Use DELETE to remove a store` | Gửi `status: DELETED` qua PATCH |
| `400` | `Message content is required` | Tin nhắn rỗng |
| `404` | `Variant not found for product: …` | Variant không thuộc sản phẩm đó |
| `404` | `Notification not found with id: …` | Không phải của mình, hoặc không tồn tại |
| `404` | `Conversation not found with id: …` | Admin mở hội thoại không có |
| `404` | `Order not found with number: …` | Đơn không phải của mình |
| `409` | `Variant already exists for size: S` | Trùng size |
| `409` | `Variant has changed; refresh…` | Sai `expectedVersion` — **không tự retry** |
| `409` | `Cannot remove a size that still has open orders` | Size đang vướng đơn chưa xong |
| `409` | `An ACTIVE product must keep at least one size; hide it first` | Xoá size cuối |
| `409` | `Store already exists: …` | Trùng tên cửa hàng |
| `409` | `Order is already confirmed; contact the shop to cancel` | Hết cửa tự huỷ |

---

## 8. Thứ FE **không** lấy được từ backend

- **Giỏ hàng** — cố ý để ở máy (Flutter state). Backend không lưu giỏ.
- **Push notification** — chưa có FCM, chỉ có danh sách trong app.
- **Trạng thái "đang gõ…" / "đã xem"** — không có.
- **Lọc cửa hàng theo khoảng cách** — tự tính ở FE.
- **Gửi ảnh trong chat** — chưa hỗ trợ, chỉ văn bản.
- **Thanh toán online (PayOS/VNPay)** — không làm. Chỉ có `COD` và `BANK_TRANSFER` (chuyển khoản
  thủ công, shop đối soát tay). Xem §9.

---

## 9. Mail xác nhận đơn hàng

Đặt đơn thành công thì backend tự gửi mail kèm **mã đơn · tổng tiền · địa chỉ giao**.

- Gửi tới `email` khai trên form đặt hàng; bỏ trống thì lùi về email tài khoản.
- Gửi **sau khi** đơn đã ghi xong vào database, nên không có chuyện nhận mail cho đơn không tồn tại.
- Gửi nền, **không làm chậm** response của `POST /orders`.
- ⚠️ Dùng chung hạn mức Brevo Free **300 mail/ngày** với OTP đăng ký. Hết quota thì mail này im
  lặng bỏ qua (đơn vẫn đặt được bình thường), còn `/auth/register` sẽ trả `503`.
  **Lúc demo đừng spam đăng ký tài khoản mới.**
- **Đổi `size` của một variant** — không có. Muốn đổi thì xoá rồi tạo lại.
