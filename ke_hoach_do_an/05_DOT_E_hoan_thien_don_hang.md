# ĐỢT E — Hoàn thiện đơn hàng

> **Không thuộc 10 mục chấm điểm**, nhưng E-1 gần như chắc chắn bị hỏi lúc bảo vệ.
> Người làm đề xuất: **bro** (đang nắm `order`).
> Phụ thuộc: E-3 cần Đợt C nếu muốn thông báo khi thanh toán xong.

---

## E.0 Vì sao vẫn nên làm

Hiện tại `PATCH /orders/{id}/status` là **endpoint chỉ dành cho ADMIN**. Nghĩa là:

> Khách đặt nhầm → **không tự huỷ được** → phải nhắn shop nhờ huỷ hộ.

Câu hỏi *"khách đặt nhầm thì làm sao?"* là câu rất dễ bị hỏi, và trả lời "phải gọi shop" nghe
rất yếu trong khi phần còn lại của hệ thống khá chỉn chu. **E-1 chỉ tốn một buổi.**

E-2 và E-3 là điểm cộng, làm nếu còn thời gian.

---

## E.1 · Khách tự huỷ đơn  🔴 nên làm

### Nghiệp vụ

```
POST /orders/me/{orderNumber}/cancel
```

Dùng `orderNumber` chứ không `id`: khách chỉ nhìn thấy mã `LBVN-2610-A7K2` trên màn hình,
và `GET /orders/me/{orderNumber}` đang có cũng dùng mã này — **giữ nhất quán**.

### Quy tắc

1. **Chỉ huỷ được đơn của chính mình.** Tìm theo `orderNumber` **và** `accountId` lấy từ token.
   Không thấy → `404` (không phải `403`, để không lộ mã đơn của người khác có tồn tại).
2. **Chỉ huỷ được khi đơn đang `PENDING`.**
   - `CONFIRMED` trở đi → `409 "Order is already confirmed; contact the shop to cancel"`.
     (Shop đã gói hàng rồi, không cho tự huỷ nữa.)
   - `CANCELLED` → `409 "Order is already cancelled"`.
   - `COMPLETED` → `409 "A completed order cannot be cancelled"`.
3. **Phải hoàn kho.** Dùng lại đúng đoạn hoàn kho admin đang dùng (`OrderServiceImpl:244`)
   — **gọi lại hàm có sẵn, tuyệt đối không chép bản thứ hai**. Hai đường hoàn kho khác nhau là
   mầm mống lệch số liệu.
4. **Sinh thông báo** `Đơn hàng đã huỷ` nếu Đợt C đã xong.
5. **Ghi lại lý do huỷ** *(tuỳ chọn, xem Q1)*.

### Luồng

```
POST /orders/me/LBVN-2610-A7K2/cancel
  → tìm đơn theo orderNumber + accountId        → không thấy: 404
  → kiểm status == PENDING                      → không: 409
  → hoàn kho từng dòng hàng (hàm có sẵn)
  → status = CANCELLED
  → sinh thông báo (nếu có Đợt C)
  → trả OrderResponse đã cập nhật
```

### Test

| # | Ca | Mong đợi |
|---|---|---|
| 1 | Huỷ đơn `PENDING` của mình | `200`, status `CANCELLED` |
| 2 | Kho sau khi huỷ | **về đúng số trước khi đặt** |
| 3 | Huỷ lại lần nữa | `409` |
| 4 | Huỷ đơn `CONFIRMED` | `409` |
| 5 | Huỷ đơn `COMPLETED` | `409` |
| 6 | Huỷ đơn của người khác | `404` |
| 7 | `orderNumber` không tồn tại | `404` |
| 8 | Không có token | `401` |
| 9 | Huỷ đơn 3 dòng hàng khác size | cả 3 variant đều được hoàn |

**Ca 2 và ca 9 là hai ca quan trọng nhất** — hoàn kho sai là lỗi mất tiền thật.

---

## E.2 · Mail xác nhận đơn hàng

### Nghiệp vụ

Đặt hàng xong → khách nhận mail có: mã đơn, danh sách món, tổng tiền, địa chỉ giao.

Hạ tầng **đã sẵn sàng**: Brevo REST API qua `RestClient`, đang dùng cho OTP. Chỉ cần thêm một
template và một lời gọi.

### Phải cẩn thận hai thứ

**1. Không được nhét lời gọi mạng vào `@Transactional`.**
Đây là cái bẫy đã gặp và đã ghi vào sổ. Gửi mail trong transaction = giam connection DB suốt
thời gian chờ Brevo trả lời. Làm giống Cloudinary: **gửi sau khi commit**, hoặc bọc try/catch
nuốt lỗi như thông báo ở Đợt C.

**2. Quota Brevo là 300 mail/ngày cho CẢ TÀI KHOẢN.**
Đang dùng chung tài khoản với dự án cũ. OTP đăng ký đã ăn vào quota này rồi. Thêm mail đơn hàng
là ăn thêm. Hết quota thì **OTP đăng ký cũng chết theo** — mà đăng nhập là mục 2, mất điểm nặng hơn nhiều.

👉 **Đề xuất: ưu tiên OTP.** Mail đơn hàng lỗi thì nuốt, chỉ log. Đừng để mail đơn hàng làm hỏng
luồng đăng ký.

### Test

| # | Ca | Mong đợi |
|---|---|---|
| 1 | Đặt đơn thành công | mail được gửi (mock, kiểm có gọi) |
| 2 | **Brevo trả lỗi** | **đơn vẫn tạo thành công** |
| 3 | Khách vãng lai không có email | không gửi, không lỗi |
| 4 | Nội dung mail | có mã đơn + tổng tiền đúng |

---

## E.3 · Thanh toán thật (PayOS)

> ⚠️ **Chỉ làm nếu đã xong cả 10 mục và còn dư thời gian.**

Khối `payos` trong `application.yaml` **đang bị comment** (dòng ~206–228). Hiện `PaymentMethod`
chỉ có `BANK_TRANSFER` (ship 0đ) và tiền mặt.

Việc phải làm nếu động vào:
1. Bật lại khối config, thêm biến vào `.env-example`.
2. `POST /orders/{id}/payment-link` → tạo link thanh toán.
3. **Webhook nhận kết quả** — đây là phần khó: phải **xác thực chữ ký** của PayOS, nếu không ai
   cũng giả được "đã thanh toán". Và webhook phải **idempotent** (PayOS gửi lại nhiều lần).
4. Đối soát trạng thái khi webhook không tới.

👉 **Thành thật mà nói: không nên làm cho đồ án.** Webhook cần URL công khai (ngrok hoặc deploy),
chữ ký phải đúng, và sai một bước là đơn ở trạng thái lơ lửng. Rủi ro cao, **không thêm điểm nào**
vì đề bài không yêu cầu. Demo bằng `BANK_TRANSFER` + ảnh QR tĩnh là đủ thuyết phục.

---

## E.4 Thứ tự trong đợt

| Bước | Nội dung | Đánh giá |
|---|---|---|
| E-1 | Khách tự huỷ đơn | 🔴 **nên làm** — rẻ, bịt câu hỏi khó |
| E-2 | Mail xác nhận đơn | 🟡 điểm cộng, coi chừng quota |
| E-3 | PayOS | ⚪ **đề xuất bỏ** |

---

## E.5 ❓ Cần chốt

**Q1 — Có lưu lý do huỷ không?**
- 👉 **Mình đề xuất CÓ, dạng tuỳ chọn.** Thêm `cancelReason` (length 300, nullable) vào
  `OrderEntity`, nhận từ body `{"reason": "Đặt nhầm size"}`. Rẻ, mà admin xem báo cáo thì biết
  khách hay huỷ vì lý do gì. Không bắt buộc nhập.

**Q2 — `CONFIRMED` rồi có cho khách huỷ không?**
- 👉 **Mình đề xuất KHÔNG.** `CONFIRMED` nghĩa là shop đã xác nhận và đang gói hàng. Cho huỷ tự do
  sau bước đó là mở đường cho khách phá. Muốn huỷ thì nhắn shop — mà Đợt D vừa làm xong chỗ để nhắn.
  👉 Hai đợt nối vào nhau rất tự nhiên, nói được điều này lúc bảo vệ nghe rất hợp lý.

**Q3 — Admin có cần xem được ai huỷ đơn, khách hay admin?**
- 👉 **Mình đề xuất CHƯA CẦN.** Muốn đủ thì phải thêm `cancelledBy`. Nhưng hiện chưa có màn báo cáo
  nào dùng tới. Ghi vào sổ nợ.
