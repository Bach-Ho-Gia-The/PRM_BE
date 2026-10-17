# Kịch bản test — Đơn hàng

> Bổ sung 17/10/2026 cho module `/orders` (8 endpoint) vốn chưa có tài liệu test.
> Swagger: **`http://localhost:8088/api/v1/swagger-ui.html`**

---

## §0 — Chuẩn bị

### 0.1 Cần có trước khi chạy

1. Một sản phẩm `ACTIVE`, có thumbnail, **có variant size M tồn kho ≥ 10**
   (chưa có variant thì xem `tai_lieu_phan_moi_cho_fe/TEST_PHAN_MOI_SWAGGER.md` §B.4).
2. **Hai** tài khoản khách (A và B) để kiểm quyền riêng tư.
3. Một tài khoản admin.

### 0.2 Ghi lại trước khi đặt

Mở `GET /products/slug/{slug}` và **chép ra số tồn kho từng size**. Cuối bài sẽ đối chiếu.

### 0.3 Quy ước

✅ mong đợi thành công · ❌ mong đợi lỗi · URL đã bỏ tiền tố `/api/v1`

---

## §A — Phân quyền (10 ca)

| # | Hành động | Token | Mong đợi |
|---|---|---|---|
| A1 | `POST /orders` | không | ❌ `401` |
| A2 | `GET /orders/me` | không | ❌ `401` |
| A3 | `GET /orders` | khách | ❌ `403` |
| A4 | `GET /orders/{id}` | khách | ❌ `403` |
| A5 | `GET /orders/number/{num}` | khách | ❌ `403` |
| A6 | `PATCH /orders/{id}/status` | khách | ❌ `403` |
| A7 | `GET /orders` | admin | ✅ `200` |
| A8 | `GET /orders/number/{num}` | admin | ✅ `200` |
| A9 | `GET /orders/me` | khách | ✅ `200` |
| A10 | `POST /orders/me/{num}/cancel` | khách khác | ❌ `404` (**không phải `403`**) |

> **A10 là ca bảo mật.** Ra `403` nghĩa là server đã xác nhận mã đơn đó có thật.

---

## §B — Dữ liệu vào (18 ca)

| # | Gửi gì | Mong đợi |
|---|---|---|
| B1 | Đầy đủ, hợp lệ, `COD` | ✅ `201`, `status = PENDING` |
| B2 | `items: []` | ❌ `400` *"Order must contain at least one item"* |
| B3 | 51 dòng khác nhau | ❌ `400` *"…more than 50 different items"* |
| B4 | `quantity: 0` | ❌ `400` |
| B5 | `quantity: 1000` | ❌ `400` |
| B6 | **Cùng** `(productId, size)` ba dòng, mỗi dòng `999` | ❌ `400` *"Quantity must not exceed 999 per product and size"* |
| B7 | Cùng `(productId, size)` hai dòng `500` + `499` | ✅ `201`, **một** dòng `quantity = 999` |
| B8 | Cùng `(productId, size)` hai dòng `2` + `3` | ✅ `201`, **một** dòng `quantity = 5` |
| B9 | `phone: "090123456"` (9 số) | ✅ `201` |
| B10 | `phone: "0901234567890"` (13 số) | ❌ `400` |
| B11 | `phone: "84901234567"` | ❌ `400` |
| B12 | Thiếu `receiverName` | ❌ `400` |
| B13 | Thiếu `address` | ❌ `400` |
| B14 | `email` sai định dạng | ❌ `400` |
| B15 | **Bỏ trống** `email` | ✅ `201` (mail xác nhận về email tài khoản) |
| B16 | `paymentMethod: "MOMO"` | ❌ `400` |
| B17 | Thiếu `paymentMethod` | ❌ `400` |
| B18 | `note` 501 ký tự | ❌ `400` |

> **B6 là ca chống hồi quy.** `@Max(999)` chạy trên từng phần tử client gửi, nên gộp dòng là lách
> được. Nếu B6 ra `201` thì cái chốt kiểm-lại-sau-khi-gộp đã bị gỡ.

---

## §C — Tồn kho và tiền (16 ca)

| # | Chuẩn bị | Hành động | Mong đợi |
|---|---|---|---|
| C1 | size M tồn 10 | đặt 3 | ✅ `201`; `GET /products/slug/…` ⇒ **tồn 7** |
| C2 | size M tồn 7 | đặt 8 | ❌ `409` *"Only 7 item(s) left…"* |
| C3 | — | sau C2, kiểm tồn | ✅ **vẫn 7** (thất bại không được trừ) |
| C4 | size M tồn 7 | đặt 7 | ✅ `201`, tồn **0** |
| C5 | tồn 0 | đặt 1 | ❌ `409` *"Only 0 item(s) left…"* |
| C6 | sản phẩm chưa có variant size L | đặt size L | ❌ `404` *"Size L is not available…"* |
| C7 | sản phẩm `HIDDEN` | đặt | ❌ `404` *"Product not available…"* |
| C8 | sản phẩm đã xoá mềm | đặt | ❌ `404` |
| C9 | `productId` bịa | đặt | ❌ `404` |
| C10 | đơn 2 size khác nhau | đặt | ✅ `201`, **cả hai size đều bị trừ đúng** |
| C11 | sản phẩm 250.000, mua 2, `COD` | đặt | ✅ `subtotal 500.000` · `shippingFee 30.000` · `total 530.000` |
| C12 | như C11 nhưng `BANK_TRANSFER` | đặt | ✅ `shippingFee 0` · `total 500.000` |
| C13 | `subtotal` đúng 1.000.000, `COD` | đặt | ✅ `shippingFee 0` (ngưỡng tính **≥**) |
| C14 | `subtotal` 999.999, `COD` | đặt | ✅ `shippingFee 30.000` |
| C15 | bất kỳ | kiểm `discount` | ✅ luôn `0` |
| C16 | bất kỳ | kiểm `totalItems` | ✅ = **tổng số lượng**, không phải số dòng |

### C.17 — Hai khách cùng mua món cuối (phải thử)

```
1. Đặt tồn size M = 1
2. Mở 2 tab Swagger, mỗi tab một tài khoản khách, soạn sẵn cùng một request
3. Bấm Execute hai tab gần như cùng lúc
```
Mong đợi: **một tab `201`, tab kia `409`**.
Có thể là *"Only 0 item(s) left"* hoặc *"This item was just updated by someone else, please try
again"* — cả hai đều đúng, tuỳ thời điểm va nhau.
🔴 **Sai nếu cả hai cùng `201`** ⇒ `@Version` trên `product_variants` đã mất tác dụng.

---

## §D — Bản chụp dòng hàng (8 ca)

| # | Hành động | Mong đợi |
|---|---|---|
| D1 | Đặt đơn, ghi lại `items[0].unitPrice` | — |
| D2 | Admin `PATCH /products/admin/{id}` đổi giá | ✅ `200` |
| D3 | `GET /orders/me/{num}` | ✅ `unitPrice` **giữ giá cũ** |
| D4 | Admin đổi **tên** sản phẩm, xem lại đơn | ✅ `productName` **giữ tên cũ** |
| D5 | Admin đổi **thumbnail**, xem lại đơn | ✅ `imageUrl` **giữ ảnh cũ** |
| D6 | Admin **xoá mềm** sản phẩm, xem lại đơn | ✅ đơn vẫn đầy đủ, không `404` |
| D7 | Sau D6, `GET /products/id/{productId}` | ❌ `404` — đúng, FE phải bắt lỗi này |
| D8 | Kiểm `lineTotal` | ✅ = `unitPrice × quantity` |

> **D3–D6 là nhóm quan trọng nhất của bài này.** Hoá đơn bị viết lại theo sản phẩm hiện tại là lỗi
> kế toán, không phải lỗi hiển thị.

---

## §E — Vòng đời trạng thái (16 ca)

| # | Từ | `PATCH` sang | Mong đợi |
|---|---|---|---|
| E1 | `PENDING` | `CONFIRMED` | ✅ `200` |
| E2 | `CONFIRMED` | `SHIPPING` | ✅ `200` |
| E3 | `SHIPPING` | `COMPLETED` | ✅ `200` |
| E4 | `COMPLETED` | `SHIPPING` | ❌ `409` |
| E5 | `COMPLETED` | `CANCELLED` | ❌ `409` |
| E6 | `PENDING` | `SHIPPING` | ❌ `409` (nhảy cóc) |
| E7 | `PENDING` | `COMPLETED` | ❌ `409` |
| E8 | `SHIPPING` | `CANCELLED` | ❌ `409` |
| E9 | `PENDING` | `PENDING` | ❌ `409` |
| E10 | `CANCELLED` | bất kỳ | ❌ `409` |
| E11 | `PENDING` | `CANCELLED` | ✅ `200`, **kho hoàn lại đủ** |
| E12 | `CONFIRMED` | `CANCELLED` | ✅ `200`, **kho hoàn lại đủ** |
| E13 | — | `status` không có trong enum | ❌ `400` |
| E14 | — | thiếu `status` | ❌ `400` |
| E15 | — | `id` bịa | ❌ `404` |
| E16 | sau E1 | khách `GET /notifications/me` | ✅ có thông báo *"Đơn hàng đã được xác nhận"* |

### E.17 — Hoàn kho khép kín (bắt buộc chạy)

```
1. Ghi tồn size M = X
2. Đặt 3 món size M        → tồn X-3
3. PATCH status CONFIRMED  → tồn X-3  (xác nhận KHÔNG đụng kho)
4. PATCH status CANCELLED  → tồn X    ✅ về đúng số ban đầu
```
Bước 3 quan trọng: chỉ **đặt** mới trừ, chỉ **huỷ** mới cộng. Trạng thái trung gian không đụng kho.

---

## §F — Màn của khách và của admin (12 ca)

| # | Hành động | Mong đợi |
|---|---|---|
| F1 | Khách A `GET /orders/me` | ✅ chỉ đơn của A |
| F2 | Khách B `GET /orders/me` | ✅ **không thấy** đơn của A |
| F3 | `GET /orders/me` | ✅ **không có** `items` (bản rút gọn) |
| F4 | `GET /orders/me/{num}` | ✅ **có** `items` |
| F5 | Khách B mở `/orders/me/{num đơn của A}` | ❌ `404` |
| F6 | `GET /orders/me` thứ tự | ✅ **mới nhất trước** |
| F7 | `?size=200` | ✅ `200` nhưng trả **tối đa 100** |
| F8 | Admin `GET /orders` | ✅ thấy đơn của **mọi** khách |
| F9 | Admin `?status=PENDING` | ✅ chỉ đơn `PENDING` |
| F10 | Admin `?status=` bỏ trống | ✅ tất cả |
| F11 | Admin `GET /orders/number/LBVN-…` | ✅ đúng đơn đó, có `items` |
| F12 | Admin `GET /orders/number/LBVN-9999-ZZZZ` | ❌ `404` |

---

## §G — Mail xác nhận (4 ca)

| # | Hành động | Mong đợi |
|---|---|---|
| G1 | Đặt đơn có `email` | ✅ hộp thư đó nhận *"Xác nhận đơn hàng LBVN-…"* |
| G2 | Đặt đơn **bỏ trống** `email` | ✅ email **tài khoản** nhận |
| G3 | Kiểm nội dung mail | ✅ đúng mã đơn · đúng tổng tiền · đúng địa chỉ |
| G4 | Đo thời gian trả lời `POST /orders` | ✅ **không chậm** vì mail (gửi nền sau khi commit) |

> ⚠️ Brevo Free **300 mail/ngày dùng chung với OTP đăng ký**. Hết quota thì mail đơn im lặng bỏ
> qua — **đơn vẫn đặt được bình thường**, đó là hành vi đúng. Đừng spam đăng ký trước demo.

---

## §H — Tổng kết

| Nhóm | Số ca |
|---|---|
| §A Phân quyền | 10 |
| §B Dữ liệu vào | 18 |
| §C Tồn kho & tiền | 16 + 1 |
| §D Bản chụp | 8 |
| §E Trạng thái | 16 + 1 |
| §F Danh sách | 12 |
| §G Mail | 4 |
| **Tổng** | **86** |

Không đủ thời gian chạy hết thì ưu tiên:
**A10 · B6 · B8 · C1 · C2 · C3 · C6 · C11 · C12 · C.17 · D3 · D6 · E6 · E11 · E.17 · F2 · F5 · F11**.
