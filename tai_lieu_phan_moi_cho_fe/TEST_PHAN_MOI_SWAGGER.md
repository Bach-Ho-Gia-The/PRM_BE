# Kịch bản test — Phần mới (Variant · Cửa hàng · Thông báo · Nhắn tin · Huỷ đơn)

> Cùng bộ với `tai_lieu_auth_cho_fe/TEST_AUTH_SWAGGER.md` và `tai_lieu_shop_cho_fe/TEST_SHOP_SWAGGER.md`.
> Swagger: **`http://localhost:8088/api/v1/swagger-ui.html`** · Cập nhật 17/10/2026

---

## §0 — Chuẩn bị

### 0.1 Không cần chạy SQL nào

`ddl-auto: update` tự tạo 4 bảng mới và 1 cột mới:

```
stores · notifications · conversations · chat_messages
orders.cancel_reason
```

Kiểm tra sau lần khởi động đầu:
```sql
SELECT table_name FROM information_schema.tables
WHERE table_name IN ('stores','notifications','conversations','chat_messages');

SELECT indexname FROM pg_indexes
WHERE tablename IN ('stores','notifications','conversations','chat_messages') ORDER BY 1;
-- idx_chat_messages_conversation · idx_conversations_last_message
-- idx_notifications_account · idx_notifications_unread · idx_stores_status
```

### 0.2 Seed tự chạy

4 chi nhánh (Q1, Q3, Thủ Đức, Tân Bình) được tạo lúc khởi động nếu chưa có. Chạy lại nhiều lần
không sinh bản trùng.

### 0.3 Tài khoản

| Vai | Email | Mật khẩu |
|---|---|---|
| Admin | `admin@example.com` | `Admin@12345` |
| Khách | `user@example.com` | `User@12345` |

Cần **2 tài khoản khách** cho các ca kiểm quyền riêng tư (§C7, §D11, §E8) — đăng ký thêm một cái.

### 0.4 Quy ước

✅ mong đợi thành công · ❌ mong đợi lỗi · Mọi URL đã bỏ tiền tố `/api/v1`

---

## §A — Phân quyền 26 endpoint mới (14 ca)

| # | Hành động | Token | Mong đợi |
|---|---|---|---|
| A1 | `GET /stores` | không | ✅ `200` |
| A2 | `GET /stores/id/{id}` | không | ✅ `200` |
| A3 | `GET /stores/admin` | không | ❌ `401` |
| A4 | `GET /stores/admin` | khách | ❌ `403` |
| A5 | `POST /stores/admin` | khách | ❌ `403` |
| A6 | `POST /products/admin/{id}/variants` | khách | ❌ `403` |
| A7 | `GET /notifications/me` | không | ❌ `401` |
| A8 | `GET /notifications/me` | khách | ✅ `200` |
| A9 | `GET /conversations/me` | không | ❌ `401` |
| A10 | `GET /conversations/admin` | khách | ❌ `403` |
| A11 | `POST /conversations/admin/{id}/messages` | khách | ❌ `403` |
| A12 | `POST /orders/me/{num}/cancel` | không | ❌ `401` |
| A13 | `GET /stores/admin` | admin | ✅ `200` |
| A14 | `GET /conversations/admin` | admin | ✅ `200` |

> **A3 và A4 phải khác nhau.** Cùng ra một mã nghĩa là chuỗi filter sai thứ tự.
> ⚠️ A3 là ca **chống hồi quy** cho lỗi whitelist: nếu ai đó đổi `/stores/id/**` thành `/stores/*`
> thì `/stores/admin` lọt vào whitelist và A3 sẽ ra `403` thay vì `401`.

---

## §B — Tồn kho theo size (24 ca)

### B.1 Thêm variant

| # | Hành động | Mong đợi |
|---|---|---|
| B1 | `POST .../variants` `{"size":"S","stockQuantity":10}` | ✅ `201`, `variants` có 1 phần tử |
| B2 | Lặp lại y hệt | ❌ `409` *"Variant already exists for size: S"* |
| B3 | `{"size":"M","stockQuantity":15}` | ✅ `201`, 2 phần tử |
| B4 | `{"size":"L","stockQuantity":0}` | ✅ `201` — 0 là hợp lệ, nghĩa là tạm hết |
| B5 | `{"size":"XL",...}` | ❌ `400` — chỉ có S/M/L |
| B6 | `stockQuantity = -1` | ❌ `400` |
| B7 | `stockQuantity = 100001` | ❌ `400` |
| B8 | Thiếu `size` | ❌ `400` |
| B9 | Sản phẩm đã xoá mềm | ❌ `404` |
| B10 | `id` sản phẩm không tồn tại | ❌ `404` |

### B.2 Sửa tồn kho

| # | Hành động | Mong đợi |
|---|---|---|
| B11 | `GET .../variants` lấy `version` | ✅ `200`, có field `version` |
| B12 | `PATCH` với `expectedVersion` đúng | ✅ `200`, `version` **tăng 1** |
| B13 | `PATCH` lại với version **cũ** | ❌ `409` *"Variant has changed…"* |
| B14 | `PATCH` thiếu `expectedVersion` | ❌ `400` |
| B15 | `expectedVersion = -1` | ❌ `400` |
| B16 | `PATCH` với `variantId` của **sản phẩm khác** | ❌ `404` |
| B17 | `PATCH` `stockQuantity = 0` | ✅ `200` |

> **B16 là ca bảo mật.** Nếu ra `200` thì service đang tra thẳng repository thay vì tìm trong
> danh sách của chính sản phẩm đó.

### B.3 Xoá variant

| # | Chuẩn bị | Hành động | Mong đợi |
|---|---|---|---|
| B18 | sản phẩm 2 size, không đơn nào | xoá size S | ✅ `200`, còn 1 |
| B19 | đặt một đơn size M, để `PENDING` | xoá size M | ❌ `409` *"…still has open orders"* |
| B20 | huỷ đơn đó | xoá size M | ✅ `200` |
| B21 | đơn size M đã `COMPLETED` | xoá size M | ✅ `200` |
| B22 | sản phẩm ACTIVE còn đúng 1 size | xoá nốt | ❌ `409` *"…must keep at least one size…"* |
| B23 | ẩn sản phẩm đó rồi | xoá nốt | ✅ `200`, `variants` rỗng |
| B24 | — | `DELETE` với `variantId` lạ | ❌ `404` |

### B.4 Luồng đầu–cuối (bắt buộc chạy)

```
1.  POST  /products/admin           {name,sku,price,categoryId}   → status HIDDEN
2.  POST  /products/admin/{id}/images + file                      → có thumbnail
3.  GET   /products/admin/{id}                                    → variants = []
4.  POST  /products/admin/{id}/variants  {"size":"S","stockQuantity":3}
5.  PATCH /products/admin/{id}      {"status":"ACTIVE"}
6.  GET   /products/slug/{slug}     (KHÔNG token)                 → thấy size S, tồn 3
7.  POST  /orders                   {productId, size:"S", quantity:3}
8.  GET   /products/slug/{slug}                                   → tồn 0
9.  POST  /orders                   size S, quantity 1            → 409 "Only 0 left"
10. PATCH /orders/{id}/status       {"status":"CANCELLED"}
11. GET   /products/slug/{slug}                                   → tồn VỀ LẠI 3  ✅
```

**Bước 11 chứng minh vòng đời tồn kho khép kín.** Bước 6 chứng minh Đợt A đã gỡ được nút chặn.

---

## §C — Cửa hàng (20 ca)

| # | Hành động | Mong đợi |
|---|---|---|
| C1 | `GET /stores` ngay sau khi khởi động lần đầu | ✅ `200`, **4 chi nhánh seed** |
| C2 | Khởi động lại app rồi `GET /stores` | ✅ vẫn 4, **không nhân đôi** |
| C3 | `POST /stores/admin` đủ field | ✅ `201` |
| C4 | Trùng tên | ❌ `409` |
| C5 | Trùng tên khác hoa thường | ❌ `409` |
| C6 | Tên có khoảng trắng thừa hai đầu | ✅ `201`, đã cắt |
| C7 | Thiếu `latitude` | ❌ `400` |
| C8 | `latitude = 91` | ❌ `400` |
| C9 | `longitude = -181` | ❌ `400` |
| C10 | `phone = "84901234567"` | ❌ `400` |
| C11 | `phone = "0901234567"` | ✅ `201` |
| C12 | `imageUrl = "khong-phai-url"` | ❌ `400` |
| C13 | `imageUrl = "https://cdn.test/a.jpg"` | ✅ `201` |
| C14 | `PATCH` chỉ đổi `openingHours` | ✅ `200`, toạ độ + tên **giữ nguyên** |
| C15 | `PATCH {"status":"DELETED"}` | ❌ `400` — bắt dùng `DELETE` |
| C16 | `PATCH {"status":"HIDDEN"}` rồi `GET /stores` | ✅ không thấy |
| C17 | Sau C16, `GET /stores/admin` | ✅ vẫn thấy |
| C18 | Sau C16, `GET /stores/id/{id}` | ❌ `404` |
| C19 | `DELETE` rồi `GET /stores/admin/{id}` | ❌ `404` |
| C20 | `?sort=latitude` | ❌ `400` — whitelist chỉ `storeName`/`createdAt`/`updatedAt` |

---

## §D — Thông báo (18 ca)

| # | Hành động | Mong đợi |
|---|---|---|
| D1 | Khách đặt đơn, chưa ai đổi trạng thái | ✅ `unreadCount = 0` — **không bắn lúc PENDING** |
| D2 | Admin đổi đơn sang `CONFIRMED` | ✅ khách có 1 thông báo |
| D3 | Kiểm `referenceId` của thông báo đó | ✅ bằng **id đơn hàng** |
| D4 | Kiểm `content` | ✅ có chứa mã đơn `LBVN-…` |
| D5 | Kiểm `isRead` | ✅ **`false`** |
| D6 | Admin đổi tiếp `SHIPPING` → `COMPLETED` | ✅ tổng 3 thông báo, **3 tiêu đề khác nhau** |
| D7 | `GET /notifications/me/unread-count` | ✅ `{"unreadCount": 3}` |
| D8 | `PATCH /me/{id}/read` | ✅ `200`, `isRead = true`, count còn 2 |
| D9 | `PATCH` lại chính nó | ✅ `200`, count **vẫn 2** |
| D10 | `?unreadOnly=true` | ✅ chỉ 2 cái chưa đọc |
| D11 | Khách **B** gọi `PATCH /me/{id}/read` với id của khách **A** | ❌ **`404`** (không phải `403`) |
| D12 | Khách B gọi `GET /notifications/me` | ✅ `200`, **danh sách rỗng** |
| D13 | `PATCH /me/read-all` | ✅ `{"updatedCount": 2}` |
| D14 | `PATCH /me/read-all` lần nữa | ✅ `{"updatedCount": 0}`, không lỗi |
| D15 | `unread-count` sau D13 | ✅ `0` |
| D16 | Thứ tự danh sách | ✅ **mới nhất đứng đầu** |
| D17 | `DELETE /me/{id}` | ✅ `200` |
| D18 | `DELETE /me/{id}` với id của người khác | ❌ `404` |

> **D5 là ca chống hồi quy quan trọng.** Lúc dựng, MapStruct từng âm thầm trả `isRead = false`
> cho **mọi** thông báo (Lombok sinh getter `isRead()` nên property tên là `read`, không khớp field
> `isRead` của record). Nếu D8 báo `isRead` vẫn `false` sau khi đánh dấu ⇒ lỗi đó quay lại.

---

## §E — Nhắn tin (22 ca)

| # | Hành động | Mong đợi |
|---|---|---|
| E1 | Khách `GET /conversations/me` lần đầu | ✅ `200`, hội thoại được **tạo ngầm** |
| E2 | Gọi lại lần 2 | ✅ **cùng `id`**, không tạo thêm |
| E3 | `unreadCount` lúc mới tạo | ✅ `0`, `lastMessagePreview` = `null` |
| E4 | Khách `POST /me/messages` `{"content":"Áo này còn size M không shop?"}` | ✅ `201`, `senderType = CUSTOMER` |
| E5 | Gửi `{"content":"   "}` | ❌ `400` |
| E6 | Gửi chuỗi 2001 ký tự | ❌ `400` |
| E7 | Gửi `{"content":"   chào shop   "}` | ✅ nội dung lưu là `"chào shop"` (đã cắt) |
| E8 | Admin `GET /conversations/admin` | ✅ thấy hội thoại, `unreadByAdmin = 1` |
| E9 | Kiểm `lastMessagePreview` | ✅ đúng câu khách vừa gửi |
| E10 | Kiểm `customerEmail` | ✅ đúng email khách |
| E11 | Khách chưa điền hồ sơ | ✅ `customerName = null`, **không lỗi** |
| E12 | `GET /conversations/admin/unread-count` | ✅ `{"unreadCount": 1}` (đếm **hội thoại**, không phải tin) |
| E13 | Admin `POST /admin/{id}/messages` | ✅ `201`, `senderType = ADMIN` |
| E14 | Sau E13, admin xem lại danh sách | ✅ `unreadByAdmin = 0` (tự gửi = đã đọc) |
| E15 | Khách `GET /conversations/me` | ✅ `unreadCount = 1` |
| E16 | Khách `GET /me/messages` | ✅ `PageResponse`, **mới → cũ** |
| E17 | Khách `GET /me/messages/new?since=<trước tin admin>` | ✅ **mảng**, chỉ tin admin, **cũ → mới** |
| E18 | `/messages/new?since=` thời điểm tương lai | ✅ `200`, mảng rỗng |
| E19 | `/messages/new?since=khong-phai-ngay` | ❌ `400` |
| E20 | Khách `PATCH /me/read` | ✅ `unreadCount = 0` |
| E21 | Gửi tin 500 ký tự rồi xem danh sách admin | ✅ `lastMessagePreview` **đúng 200 ký tự** |
| E22 | Admin `GET /admin/{id}/messages` với id lạ | ❌ `404` |
| E23 | Khách mới `GET /conversations/me` rồi **không gõ gì**; admin `GET /conversations/admin` | ✅ **KHÔNG thấy** hội thoại rỗng đó |
| E24 | Sau E23, khách gửi 1 tin; admin xem lại | ✅ giờ mới thấy |

> **E16 vs E17 là chỗ FE hay làm sai.** Hai URL khác nhau, hai hình dạng response khác nhau,
> hai chiều sắp xếp ngược nhau. Chạy cả hai rồi đọc kỹ JSON trước khi code màn chat.
>
> **E23 là ca chống hồi quy.** `last_message_at` để null ở hội thoại chưa có tin, mà PostgreSQL
> coi NULL là lớn nhất nên `ORDER BY ... DESC` đẩy chúng lên đầu. Nếu E23 thấy hội thoại rỗng
> thì bộ lọc `LastMessageAtNotNull` đã bị gỡ mất.

### E.25 — Thử polling thật

```
1. Mở 2 tab Swagger: tab KHÁCH, tab ADMIN
2. Khách:  POST /conversations/me/messages  → ghi lại createdAt
3. Khách:  GET  /me/messages/new?since=<createdAt vừa ghi>   → []  (chưa có gì mới)
4. Admin:  POST /admin/{id}/messages
5. Khách:  GET  /me/messages/new?since=<createdAt cũ>        → thấy tin admin ✅
```

---

## §F — Khách tự huỷ đơn (12 ca)

| # | Chuẩn bị | Hành động | Mong đợi |
|---|---|---|---|
| F1 | đơn `PENDING` của mình | `POST /orders/me/{num}/cancel` `{"reason":"Đặt nhầm size"}` | ✅ `200`, `status = CANCELLED` |
| F2 | như F1 | kiểm `cancelReason` | ✅ `"Đặt nhầm size"` |
| F3 | ghi tồn kho trước khi đặt | sau khi huỷ, `GET /products/slug/{slug}` | ✅ **kho về đúng số cũ** |
| F4 | đơn 3 dòng khác size | huỷ | ✅ **cả 3 size đều được hoàn** |
| F5 | — | gửi **không có body** | ✅ `200`, `cancelReason = null` |
| F6 | — | huỷ lại lần nữa | ❌ `409` *"Order is already cancelled"* |
| F7 | đơn `CONFIRMED` | huỷ | ❌ `409` *"…contact the shop to cancel"* |
| F8 | đơn `SHIPPING` | huỷ | ❌ `409` |
| F9 | đơn `COMPLETED` | huỷ | ❌ `409` |
| F10 | đơn của khách **A** | khách **B** huỷ | ❌ **`404`** (không phải `403`) |
| F11 | — | `orderNumber` bịa | ❌ `404` |
| F12 | sau F1 | `GET /notifications/me` | ✅ có thông báo *"Đơn hàng đã huỷ"* |
| F13 | đặt đơn mới | hộp thư của `email` trên form | ✅ nhận mail *"Xác nhận đơn hàng LBVN-…"* |
| F14 | đặt đơn, **bỏ trống** `email` | hộp thư của tài khoản | ✅ vẫn nhận được mail |

> **F3 và F4 là hai ca quan trọng nhất cả bộ** — hoàn kho sai là lỗi mất tiền thật.
> **F12** chứng minh Đợt C và Đợt E đã nối vào nhau.

---

## §G — Chạy test tự động

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./mvnw -B clean test
```

Mong đợi: **`Tests run: 166, Failures: 0, Errors: 0, Skipped: 11`**

| Bộ test mới | Số ca |
|---|---|
| `ProductVariantServiceTest` | 14 |
| `StoreServiceImplTest` | 10 |
| `NotificationServiceImplTest` | 14 |
| `ConversationServiceImplTest` | 19 |
| `OrderCancelServiceTest` | 10 |
| `OrderConfirmationMailTest` | 5 |
| **Cộng thêm lượt này** | **72** |

(11 ca skip là Testcontainers, cần Docker.)

---

## §H — Tổng kết

| Nhóm | Số ca |
|---|---|
| §A Phân quyền | 14 |
| §B Tồn kho | 24 |
| §C Cửa hàng | 20 |
| §D Thông báo | 18 |
| §E Nhắn tin | 24 |
| §F Huỷ đơn + mail | 14 |
| **Tổng** | **114** |

Không đủ thời gian chạy hết thì ưu tiên: **§A toàn bộ · B.4 (luồng đầu–cuối) · B13 · B16 · B19 ·
B22 · C2 · C15 · D3 · D5 · D11 · E1 · E2 · E16 · E17 · E23 · F3 · F4 · F10**.
