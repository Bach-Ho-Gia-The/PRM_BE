# Kịch bản test — Thanh toán VNPay, COD & Hoàn tiền

> Bổ sung 30/10/2026 cho module `/payments`.
> Swagger: **`http://localhost:8088/api/v1/swagger-ui.html`**

---

## §0 — Chuẩn bị

### 0.1 Cần có trước khi chạy

1. `.env` đã set `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET` (sandbox thật), `VNPAY_PAYMENT_URL`,
   `VNPAY_API_URL`. Thiếu ⇒ app **không khởi động được** (không có default).
2. Một sản phẩm có variant tồn kho ≥ 5.
3. Hai tài khoản khách (A, B) + một admin.
4. Thẻ test VNPay sandbox (NCB): số thẻ `9704198526191432198`, tên `NGUYEN VAN A`, ngày `07/15`,
   OTP `123456`.

### 0.2 Quy ước

✅ mong đợi thành công · ❌ mong đợi lỗi · URL đã bỏ tiền tố `/api/v1`

> 🔴 **Nhắc lại luật quan trọng nhất:** IPN là nguồn xác nhận duy nhất. Sau khi trả tiền trên
> sandbox, trạng thái `PAID` chỉ xuất hiện khi **IPN về** (vài giây). Thấy `PENDING` ngay sau khi trả
> **không** phải lỗi — poll lại `GET /payments/me/{num}`.

---

## §A — Phân quyền (10 ca)

| # | Hành động | Token | Mong đợi |
|---|---|---|---|
| A1 | `POST /payments/me/{num}/vnpay` | không | ❌ `401` |
| A2 | `GET /payments/me/{num}` | không | ❌ `401` |
| A3 | `POST /payments/cod/{num}/confirm` | khách | ❌ `403` |
| A4 | `POST /payments/cod/{num}/confirm` | admin | ✅ (nếu đơn hợp lệ) |
| A5 | `GET /payments/admin/refunds` | khách | ❌ `403` |
| A6 | `GET /payments/admin/refunds` | admin | ✅ `200` |
| A7 | `GET /payments/me/{num đơn của A}` | khách B | ❌ `404` (**không `403`**) |
| A8 | `POST /payments/me/{num đơn của A}/vnpay` | khách B | ❌ `404` |
| A9 | `GET /payments/vnpay/ipn` (thiếu tham số) | không | ✅ `200` + body `{"RspCode":"97",…}` |
| A10 | `GET /payments/vnpay/return` (thiếu tham số) | không | ✅ `200` + trang HTML (không `401`) |

> **A9/A10 là ca bảo mật quan trọng:** hai endpoint `/payments/vnpay/**` công khai (VNPay gọi,
> không có token). Nhưng sai chữ ký ⇒ **không chạm database**: A9 trả `RspCode 97`, A10 ra trang
> "liên kết không hợp lệ" — **không** lộ thông tin đơn nào.

---

## §B — Mở link VNPay (12 ca)

| # | Chuẩn bị | Hành động | Mong đợi |
|---|---|---|---|
| B1 | Đơn `BANK_TRANSFER` vừa đặt, `PENDING` | `POST .../vnpay` | ✅ `200`, có `paymentUrl`, `status=PENDING` |
| B2 | Như B1 | Mở `paymentUrl` trên trình duyệt | ✅ ra trang VNPay sandbox |
| B3 | Đơn **COD** | `POST .../vnpay` | ❌ `409` *"…paid on delivery (COD)…"* |
| B4 | Đơn đã `CANCELLED` | `POST .../vnpay` | ❌ `409` *"…cancelled…"* |
| B5 | Đơn đã `CONFIRMED` | `POST .../vnpay` | ❌ `409` *"…already CONFIRMED…"* |
| B6 | Lần thử đang `PENDING` | `POST .../vnpay` lần hai | ✅ **cùng `txnRef`**, không tạo mới |
| B7 | Như B6 | So `expiresAt` hai lần | ✅ **y hệt** (không gia hạn) |
| B8 | Đơn đã `PAID` | `POST .../vnpay` | ❌ `409` *"…already been paid"* |
| B9 | — | Đọc `amount` trong response | ✅ số VND thật (vd 530000), **không** nhân 100 |
| B10 | — | Đọc `vnp_Amount` trên `paymentUrl` | ✅ = `amount × 100` (vd 53000000) |
| B11 | Đơn của người khác | `POST .../vnpay` | ❌ `404` |
| B12 | Đơn quá hạn 15 phút | `POST .../vnpay` | ❌ `409` *"Payment has expired"* |

> **B7 là ca chống hồi quy.** Nếu `expiresAt` dài thêm sau mỗi lần bấm ⇒ cơ chế "mở lại không gia hạn"
> đã hỏng, khách gia hạn vô hạn bằng cách bấm thanh toán lại.

---

## §C — Luồng thanh toán thật (sandbox) (8 ca)

| # | Hành động | Mong đợi |
|---|---|---|
| C1 | Trả tiền thành công trên VNPay | ✅ redirect về `/payments/vnpay/return`, trang báo thành công |
| C2 | Ngay sau C1, `GET /payments/me/{num}` | ✅ `status=PAID`, `paidAt` có giá trị (có thể chờ IPN vài giây) |
| C3 | Sau C2, xem `order.status` qua `GET /orders/me/{num}` | ✅ **vẫn `PENDING`** — trả tiền KHÔNG tự xác nhận đơn |
| C4 | Kiểm `paidAt` | ✅ = giờ giao dịch thật ở cổng (từ `vnp_PayDate`), không phải giờ IPN về |
| C5 | Trả tiền **thất bại** (bấm huỷ trên VNPay) | ✅ `GET /payments/me/{num}` ⇒ `status=FAILED` |
| C6 | Sau C5, `POST .../vnpay` lần nữa | ✅ `200`, **`txnRef` mới**, `expiresAt` vẫn cũ |
| C7 | SQL: `SELECT status, paid_at FROM payments WHERE txn_ref='…'` | ✅ khớp với response |
| C8 | Mở lại `/payments/vnpay/return` cũ sau nhiều phút (F5) | ✅ trang vẫn hiển thị đúng trạng thái đọc từ DB, không ghi gì thêm |

> **C3 là ca cốt lõi.** Tiền về mà đơn tự nhảy `CONFIRMED` là sai luật — đơn phải chờ shop duyệt.
> **C8:** Return URL chỉ đọc, F5 bao nhiêu lần cũng không được ghi nhận thanh toán thêm lần nào.

---

## §D — Xác nhận thu tiền COD (10 ca)

| # | Chuẩn bị | Hành động (admin) | Mong đợi |
|---|---|---|---|
| D1 | Đơn COD `SHIPPING` | `POST /payments/cod/{num}/confirm` | ✅ `200`, `status=PAID`, `paidAt` = bây giờ |
| D2 | Sau D1, xem `order.status` | — | ✅ **vẫn `SHIPPING`** — thu tiền không đổi trạng thái đơn |
| D3 | Đơn COD `COMPLETED` | confirm | ✅ `200`, `PAID` |
| D4 | Sau D1, confirm **lại** | — | ✅ `200`, `paidAt` **y hệt** (không dời) |
| D5 | Đơn COD `PENDING` | confirm | ❌ `409` *"…shipping or completed, not PENDING"* |
| D6 | Đơn COD `CONFIRMED` | confirm | ❌ `409` *"…not CONFIRMED"* |
| D7 | Đơn COD `CANCELLED` | confirm | ❌ `409` *"…cancelled…"* |
| D8 | Đơn **BANK_TRANSFER** `SHIPPING` | confirm | ❌ `409` *"…not cash-on-delivery…"* |
| D9 | `orderNumber` bịa | confirm | ❌ `404` |
| D10 | Đơn cũ không có payment | confirm | ❌ `404` *"…no payment record"* |

> **D4 là ca chống hồi quy.** Thu tiền là sự kiện một lần; bấm hai lần mà `paidAt` đổi ⇒ idempotent
> đã hỏng.

---

## §E — Hạn thanh toán & tự huỷ (6 ca)

| # | Chuẩn bị | Hành động | Mong đợi |
|---|---|---|---|
| E1 | Đơn BANK_TRANSFER, SQL kéo `payment_due_at` về quá khứ | Chờ 1 lượt scan (≤ 90s) | ✅ log `order expiry scan: checked=… cancelled=…` |
| E2 | Sau E1 | `GET /orders/me/{num}` | ✅ `status=CANCELLED` |
| E3 | Sau E1 | `GET /payments/me/{num}` | ✅ `status=EXPIRED` |
| E4 | Sau E1 | Kiểm tồn kho sản phẩm | ✅ **đã hoàn lại** đúng số |
| E5 | Đơn đã trả tiền (`PAID`) nhưng để quá hạn | Chờ scan | ✅ **KHÔNG bị huỷ** (có payment PAID) |
| E6 | Đơn **COD** để lâu | Chờ scan | ✅ **KHÔNG bị đụng** (COD không có hạn) |

> **E5/E6 là hai ca an toàn quan trọng.** Tác vụ quét chỉ huỷ đơn chuyển khoản quá hạn **chưa trả
> tiền**; đơn đã trả tiền và đơn COD đều được bỏ qua.

---

## §F — Hoàn tiền (8 ca)

| # | Chuẩn bị | Hành động | Mong đợi |
|---|---|---|---|
| F1 | Đơn đã `PAID`, admin/khách huỷ đơn | `GET /payments/admin/refunds` | ✅ có 1 khoản, `reason=ORDER_CANCELLED`, `status=PENDING` |
| F2 | Đơn **chưa** trả tiền, huỷ | `GET .../refunds` | ✅ **không** sinh khoản hoàn |
| F3 | Đơn đã huỷ rồi IPN báo trả tiền (ca về muộn) | `GET .../refunds` | ✅ `reason=PAID_AFTER_CANCEL` |
| F4 | Nhiều khoản hoàn | `?status=PENDING` | ✅ chỉ khoản `PENDING` |
| F5 | — | `?status=` bỏ trống | ✅ tất cả |
| F6 | — | Kiểm thứ tự | ✅ **mới nhất trước** |
| F7 | — | Đọc `amount` một khoản | ✅ = `payments.amount` (số thật đã thu), có thể khác `orders.total` |
| F8 | — | Đọc `customerName` | ✅ = tên người nhận trên đơn, không phải tên tài khoản |

> **F7 là ca kế toán.** Hoàn theo `orders.total` thay vì số tiền thật đã thu là hoàn sai (thiếu hoặc
> thừa) — không ai phát hiện tới khi đối soát.

---

## §G — Tổng kết

| Nhóm | Số ca |
|---|---|
| §A Phân quyền | 10 |
| §B Mở link VNPay | 12 |
| §C Luồng thật sandbox | 8 |
| §D Xác nhận COD | 10 |
| §E Hạn & tự huỷ | 6 |
| §F Hoàn tiền | 8 |
| **Tổng** | **54** |

Không đủ thời gian chạy hết thì ưu tiên:
**A7 · A9 · A10 · B3 · B7 · B8 · C2 · C3 · C8 · D2 · D4 · D5 · E3 · E5 · E6 · F1 · F3 · F7**.
