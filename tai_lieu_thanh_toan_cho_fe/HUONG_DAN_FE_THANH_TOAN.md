# Hướng dẫn FE — Thanh toán VNPay, COD & Hoàn tiền

> Bổ sung 30/10/2026 cho module `/payments` (VNPay + xác nhận COD + danh sách hoàn tiền).
> Base URL: `http://localhost:8088/api/v1`
>
> ⚠️ Tài liệu đơn hàng cũ (`tai_lieu_don_hang_cho_fe/`) §9 ghi *"không làm VNPay"* — **đã lỗi thời**.
> Giờ có VNPay thật. Phần này thay cho mục đó.

---

## 1. Bảng endpoint

| Method | URL | Quyền | Việc |
|---|---|---|---|
| `POST` | `/payments/me/{orderNumber}/vnpay` | đăng nhập | Mở link thanh toán VNPay cho đơn **chuyển khoản** của mình |
| `GET` | `/payments/me/{orderNumber}` | đăng nhập | Trạng thái thanh toán của một đơn của mình |
| `POST` | `/payments/cod/{orderNumber}/confirm` | ADMIN | Shop xác nhận **đã thu tiền mặt** đơn COD |
| `GET` | `/payments/admin/refunds?status=` | ADMIN | Danh sách khoản hoàn tiền (phân trang) |
| `GET` | `/payments/vnpay/ipn` | công khai | **VNPay server gọi** — không phải FE |
| `GET` | `/payments/vnpay/return` | công khai | **Trình duyệt redirect về** — trang HTML, không phải JSON |

---

## 2. 🔴 Hai phương thức thanh toán, hai luồng khác hẳn

Đơn có `paymentMethod` chốt từ lúc đặt (xem `tai_lieu_don_hang_cho_fe/`):

| | **COD** (trả khi nhận) | **BANK_TRANSFER** (VNPay) |
|---|---|---|
| Có bản ghi payment? | **Có** (từ lúc đặt đơn) | Có |
| Có hạn thanh toán? | **Không** | **Có — 15 phút** từ lúc đặt |
| FE làm gì để thu tiền? | Không gì — shop bấm khi giao hàng | Mở link VNPay (§3) |
| Gọi `POST .../vnpay`? | ❌ **409** — COD không qua cổng | ✅ |

> Payment của COD cũng tồn tại ngay từ lúc đặt, ở trạng thái `PENDING`, để shop có chỗ bấm
> "đã thu tiền" sau này (§6).

---

## 3. Mở link VNPay

```
POST /payments/me/{orderNumber}/vnpay
```
→ `200` + `VnpayPaymentUrlResponse`:
```json
{
  "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=…&vnp_SecureHash=…",
  "txnRef": "105df48f66fc4918a91ddd2f5309cf22",
  "amount": 530000,
  "status": "PENDING",
  "expiresAt": "2026-10-30T10:15:00Z"
}
```

FE chỉ việc **mở `paymentUrl`** (WebView trên mobile, redirect trên web). Khách nhập thẻ test ngay
trên trang VNPay.

### 3.1 🔴 Mở lại link KHÔNG gia hạn

`expiresAt` = lúc **đặt đơn** + 15 phút, cố định. Khách đặt 10:00, mở link 10:12 thì vẫn chỉ còn tới
10:15 — vì hàng đã bị giữ từ 10:00. Bấm "thanh toán lại" nhiều lần **không** kéo dài thêm.

Quy tắc khi bấm lại:

| Lần thử gần nhất | Bấm lại thì |
|---|---|
| `PENDING` | Trả lại **đúng link cũ** (cùng `txnRef`), không tạo mới |
| `FAILED` | Mở lần thử mới (`txnRef` mới), **hạn vẫn là hạn cũ** |
| `PAID` | ❌ `409` — đã trả tiền rồi |
| `EXPIRED` / `CANCELLED` | ❌ `409` — đặt đơn mới |

### 3.2 `amount` là số VND thật

`amount` trong response là số tiền gốc (530.000 đ). Trên URL VNPay bạn sẽ thấy `vnp_Amount=53000000`
(nhân 100 theo quy định VNPay) — **đừng hiển thị số đó cho khách**, dùng `amount` trong response.

---

## 4. 🔴 Sau khi khách thanh toán xong — ĐỌC KỸ

VNPay có **hai đường** báo kết quả. FE chỉ thấy một, và đó **không** phải đường đáng tin.

| | IPN | Return URL |
|---|---|---|
| Ai gọi | **Server VNPay → server mình** | Trình duyệt khách redirect về |
| FE thấy? | Không | Có |
| Là nguồn xác nhận? | **✅ Duy nhất** | ❌ Chỉ để hiển thị |

**Hệ quả bắt buộc cho FE:**

1. Sau khi khách trả tiền xong, VNPay redirect trình duyệt về `/payments/vnpay/return` (trang HTML do
   backend dựng). **Đừng tin chữ trên URL đó.** Khách có thể F5, sửa URL, mở lại tuần sau.
2. Để biết thật sự đã trả tiền chưa, FE **gọi `GET /payments/me/{orderNumber}`** và đọc `status` từ
   database. Đó mới là sự thật.
3. IPN có thể về **muộn vài giây**. Nên nếu vừa thanh toán xong mà `status` còn `PENDING`, **không có
   nghĩa là thất bại** — nghĩa là IPN chưa tới. FE nên **poll lại vài lần** (ví dụ mỗi 2s, tối đa ~30s)
   trước khi kết luận.

### 4.1 🔴 Trả tiền xong KHÔNG tự xác nhận đơn

Thanh toán thành công thì `payment.status = PAID` **nhưng `order.status` vẫn `PENDING`** — chờ shop
xác nhận. Tiền về ≠ đơn được duyệt. FE đừng hiện "đơn đã xác nhận" chỉ vì đã trả tiền.

---

## 5. Trạng thái thanh toán

```
GET /payments/me/{orderNumber}
```
→ `200` + `PaymentResponse`:
```json
{
  "id": "…",
  "orderNumber": "LBVN-2610-A7K2",
  "txnRef": "105df48f66fc4918a91ddd2f5309cf22",
  "attemptNo": 1,
  "status": "PAID",
  "amount": 530000,
  "expiresAt": "2026-10-30T10:15:00Z",
  "paidAt": "2026-10-30T10:07:32Z",
  "bankCode": "NCB",
  "createdAt": "…",
  "updatedAt": "…"
}
```

`PaymentStatus` có **5 giá trị**:

| Status | Nghĩa | FE hiển thị |
|---|---|---|
| `PENDING` | Chưa có kết quả (chưa trả, hoặc IPN chưa về) | "Chờ thanh toán" / "Đang xác nhận" |
| `PAID` | Đã thu tiền (`paidAt` có giá trị) | "Đã thanh toán" |
| `FAILED` | Lần thử thất bại — **vẫn mở lại link được** | "Thất bại — thử lại" |
| `EXPIRED` | Quá hạn, đơn đã bị huỷ, hàng đã trả kho | "Hết hạn" |
| `CANCELLED` | Khách/shop huỷ đơn | "Đã huỷ" |

- `expiresAt`, `paidAt`, `bankCode` để `null` với COD (chưa thu) hoặc khi chưa có dữ liệu.
- Khách và shop **cùng** đọc được endpoint này (cho đơn của chính khách).

---

## 6. Shop xác nhận thu tiền COD (ADMIN)

```
POST /payments/cod/{orderNumber}/confirm
```
→ `200` + `PaymentResponse` (`status = PAID`, `paidAt` = lúc bấm).

| Luật | Chi tiết |
|---|---|
| Chỉ đơn **COD** | Đơn chuyển khoản dùng VNPay, gọi đây ⇒ `409` |
| Chỉ khi đơn đã **SHIPPING** hoặc **COMPLETED** | Tiền mặt chỉ thu được khi hàng đã tới tay khách |
| Đơn đã **CANCELLED** ⇒ `409` | Đã huỷ thì không còn gì để thu |
| **Không đổi trạng thái đơn** | Thu tiền ≠ xác nhận/hoàn tất đơn. Đơn đi tiếp theo luồng giao hàng |
| **Idempotent** | Bấm lại trên đơn đã `PAID` ⇒ trả kết quả hiện tại, **không** dời `paidAt` |

---

## 7. Danh sách hoàn tiền (ADMIN)

```
GET /payments/admin/refunds?status=PENDING&page=0&size=20   ← bỏ status thì lấy tất cả
```
→ `200` + trang `RefundAdminResponse`:
```json
{
  "content": [
    {
      "id": "…",
      "orderNumber": "LBVN-2610-A7K2",
      "customerName": "Nguyễn Văn A",
      "amount": 530000,
      "reason": "ORDER_CANCELLED",
      "status": "PENDING",
      "createdAt": "2026-10-30T09:00:00Z"
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1,
  "first": true, "last": true
}
```

Mặc định **mới nhất trước** (`createdAt` giảm dần).

### 7.1 Hai lý do hoàn — hiển thị khác nhau

| `reason` | Khi nào | Shop cần làm |
|---|---|---|
| `ORDER_CANCELLED` | Huỷ một đơn **đã thanh toán** | Chuyển tiền hoàn cho khách |
| `PAID_AFTER_CANCEL` | Đơn **đã huỷ** rồi tiền mới về (VNPay báo muộn) | **Kiểm lại với cổng** trước khi hoàn — khách có thể chưa biết mình bị trừ |

### 7.2 `status` của khoản hoàn

| `status` | Nghĩa |
|---|---|
| `PENDING` | **Cần hoàn** — chưa chuyển tiền. Đây là trạng thái duy nhất hệ thống tự sinh |
| `REFUNDED` | Shop đã chuyển tiền (thao tác tay, đợt sau) |
| `REJECTED` | Shop từ chối hoàn (đợt sau) |

> 🔴 `PENDING` nghĩa là **"cần hoàn"**, KHÔNG phải "đã hoàn". Đừng hiện cho khách là "đã hoàn tiền".

`amount` lấy từ **số tiền thật đã thu** (`payments.amount`), có thể khác `orders.total` (phí ship,
khuyến mãi). Luôn hiển thị `amount` của khoản hoàn.

---

## 8. Hạn thanh toán & tự huỷ

- Đơn chuyển khoản `PENDING` quá **15 phút** mà chưa trả tiền sẽ bị **tác vụ nền tự huỷ** (chạy mỗi
  phút), hoàn hàng về kho, payment chuyển `EXPIRED`.
- Nhưng tác vụ **hỏi VNPay trước khi huỷ**: nếu khách đã trả tiền mà IPN về muộn, đơn **không** bị huỷ.
- 👉 FE: nếu khách để quá hạn, màn chi tiết đơn sẽ thấy `order.status = CANCELLED` và
  `payment.status = EXPIRED`. Cho khách đặt đơn mới.

---

## 9. Bảng lỗi

### 9.1 `POST /payments/me/{orderNumber}/vnpay`

| Mã | Message | Khi nào |
|---|---|---|
| `404` | `Order not found with number: …` | Đơn không phải của mình / không tồn tại |
| `409` | `This order is paid on delivery (COD); VNPay is not available` | Gọi VNPay cho đơn COD |
| `409` | `This order is cancelled and can no longer be paid` | Đơn đã huỷ |
| `409` | `This order is already {STATUS} and can no longer be paid` | Đơn không còn `PENDING` |
| `409` | `Payment has expired` | Quá hạn 15 phút |
| `409` | `This order has already been paid` | Đã trả tiền rồi |
| `409` | `This payment was cancelled; place a new order to pay again` | Lần thử trước bị huỷ |
| `409` | `Payment has expired; place a new order to pay again` | Lần thử trước đã hết hạn |

### 9.2 `GET /payments/me/{orderNumber}`

| Mã | Message | Khi nào |
|---|---|---|
| `404` | `Order not found with number: …` | Không phải đơn của mình |
| `404` | `This order has no payment record: …` | Đơn cũ tạo trước khi có module payment |

### 9.3 `POST /payments/cod/{orderNumber}/confirm` (ADMIN)

| Mã | Message | Khi nào |
|---|---|---|
| `403` | — | Không phải ADMIN |
| `404` | `Order not found with number: …` | Đơn không tồn tại |
| `404` | `This order has no payment record: …` | Đơn cũ không có payment |
| `409` | `This order is not cash-on-delivery; cash collection does not apply` | Đơn chuyển khoản |
| `409` | `This order is cancelled; cash can no longer be collected` | Đơn đã huỷ |
| `409` | `Cash can only be collected once the order is shipping or completed, not {STATUS}` | Đơn chưa giao |
| `409` | `This payment is {STATUS} and cannot be marked as paid` | Payment đã đóng (EXPIRED/…) |

---

## 10. Trang Return (tham khảo — FE web thường không tự render)

Backend dựng sẵn trang HTML tại `/payments/vnpay/return` cho **7 trạng thái** (`VnpayReturnState`).
FE **mobile** dùng WebView thì chỉ cần bắt redirect về URL này rồi đóng WebView và gọi
`GET /payments/me/{orderNumber}` (§4). Bảy trạng thái để tham khảo nếu cần đọc:

| State | Khi nào | Ý nghĩa |
|---|---|---|
| `SUCCESS` | payment `PAID`, đơn còn sống | Thành công |
| `PAID_AFTER_CANCEL` | payment `PAID`, đơn `CANCELLED` | Đã nhận tiền → sẽ hoàn lại |
| `PENDING_CONFIRMATION` | payment `PENDING` + URL báo `00` | Đang xác nhận — **"vui lòng không thanh toán lại"** |
| `FAILED` | payment `PENDING` + URL ≠ `00`, hoặc payment đã đóng | Không thành công |
| `TXN_REF_NOT_FOUND` | không có giao dịch với `txnRef` | Không tìm thấy |
| `INVALID_LINK` | sai chữ ký | Liên kết không hợp lệ |
| `SYSTEM_ERROR` | trang tự lỗi | Không hiển thị được |

> `PENDING_CONFIRMATION` **không phải** thất bại — IPN chưa về thôi. Hiện "thất bại" ở đây là mời
> khách trả tiền hai lần.

---

## 11. Thứ backend **không** có

- **Hoàn tiền tự động** — hệ thống chỉ ghi nhận "cần hoàn" (`PENDING`). Chuyển tiền thật là shop làm tay.
- **Thẻ lưu sẵn / thanh toán 1 chạm** — mỗi lần là một phiên VNPay mới.
- **Phương thức khác** (Momo, ZaloPay) — chỉ COD và VNPay.
- **Webhook cho FE** — FE phải tự poll `GET /payments/me/{orderNumber}`, không có push.
