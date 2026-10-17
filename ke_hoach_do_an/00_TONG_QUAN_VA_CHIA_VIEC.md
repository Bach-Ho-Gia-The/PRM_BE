# Kế hoạch phần còn lại — Tổng quan & chia việc

> Viết ngày 17/10/2026. Đọc file này trước, rồi mới đọc 5 file đợt.
> Nguồn chuẩn: nhánh `main`, 8 module, 66 endpoint, build xanh 94 test.

---

## 0. Điều quan trọng nhất phải nói trước

**8 trên 10 mục chấm điểm là MÀN HÌNH, tức là Flutter.** Backend chỉ là bên cung cấp dữ liệu.

| Loại việc | Mục |
|---|---|
| Thuần Flutter | 2, 3, 4, 5, 6, 7, 8, 9, 10 (phần UI) |
| Thuần backend | 1 |
| Cần cả hai | 4, 5, 6, 7, 8, 9 (phần API) |

Backend hiện đã phục vụ được mục 1·2·3·6 và gần xong mục 4. Nghĩa là **nút thắt của đồ án không
nằm ở backend nữa, mà nằm ở Flutter**. Hai người mà cùng ngồi viết backend thì cuối kỳ sẽ có một
API rất đẹp và một cái app chưa chạy.

⚠️ Mình **chưa hề nhìn thấy repo Flutter**, nên toàn bộ kế hoạch dưới đây chỉ dám lên chi tiết cho
backend. Phần Flutter chỉ ước lượng thô. Muốn kế hoạch sát thì cho mình biết: app đã dựng tới đâu,
đã có màn nào chạy thật, đã chọn Provider hay Bloc chưa.

---

## 1. Đối chiếu 10 mục chấm điểm

| # | Mục | Backend | Flutter | Còn thiếu gì |
|---|---|---|---|---|
| 1 | Thiết kế DB / API | ✅ xong | — | không |
| 2 | Màn đăng nhập | ✅ xong | ? | UI + lưu token |
| 3 | Màn danh sách sản phẩm | ✅ xong | ? | UI + phân trang cuộn |
| 4 | Màn chi tiết sản phẩm | ⚠️ **thiếu CRUD variant** | ? | **Đợt A** + UI chọn size |
| 5 | Màn giỏ hàng | ✅ không cần BE | ? | UI + state (xem §3 câu 1) |
| 6 | Màn thanh toán | ✅ xong phần đặt đơn | ? | UI form + gọi `POST /orders` |
| 7 | Màn thông báo | ❌ trắng | ? | **Đợt C** + UI |
| 8 | Màn bản đồ cửa hàng | ❌ trắng | ? | **Đợt B** + UI bản đồ |
| 9 | Màn nhắn tin | ❌ trắng | ? | **Đợt D** + UI |
| 10 | State management | — | ? | thuần Flutter |

### 🔴 Lỗ hổng đề bài không ghi nhưng sẽ vỡ lúc demo

`product_variants` đã có entity, đã có `@Version`, đã lộ ra `ProductDetailResponse`, và
`OrderItemRequest` **bắt buộc** gửi `size`. `OrderServiceImpl` thậm chí đã trừ tồn kho khi đặt và
hoàn kho khi huỷ.

**Nhưng không có một API nào tạo được variant.**

Hệ quả thực tế: admin tạo sản phẩm mới → sản phẩm đó không có size nào → **khách bấm mua là lỗi**.
Chỉ vài món seed sẵn trong DB mới mua được. Demo mà thầy bảo "tạo thử một sản phẩm rồi mua" là lộ ngay.
Đây là lý do **Đợt A phải làm trước mọi thứ khác**.

---

## 2. Thứ tự đề xuất

```
ĐỢT A  variant + tồn kho      ████  gỡ chặn mục 4·5·6   ← làm ngay
ĐỢT B  cửa hàng + bản đồ      ██    mục 8, rẻ nhất
ĐỢT C  thông báo              ███   mục 7
ĐỢT D  nhắn tin               ██████ mục 9, nặng gấp đôi
ĐỢT E  hoàn thiện đơn hàng    ██    không tính điểm, nhưng hay bị hỏi
```

**Vì sao A trước:** nó đang làm hỏng 3 mục mà mình tưởng đã xong. Sửa 1 đợt, cứu 30%.

**Vì sao B trước C:** cùng 10% nhưng B là CRUD thuần, không dính module nào khác, làm xong trong
một buổi. C phải nối vào luồng đổi trạng thái đơn nên đụng `OrderServiceImpl`.

**Vì sao D cuối:** chat là đợt duy nhất có hai chiều đọc–ghi liên tục, phải nghĩ về phân trang
ngược, con trỏ tin mới, và trạng thái đã đọc. Tốn gấp đôi B hoặc C.

**E không tính điểm** nhưng "khách không tự huỷ được đơn" là câu hỏi gần như chắc chắn bị hỏi lúc bảo vệ.

---

## 3. Trả lời 4 câu đã hỏi — ý kiến của mình

### Câu 1 — Giỏ hàng: **để ở máy (Flutter), không làm module cart ở backend**

Lý do:
1. **Mục 10 chấm state management.** Giỏ hàng là ví dụ kinh điển nhất để demo Provider/Bloc —
   thêm/bớt/đổi số lượng/tính tổng, tất cả là state thay đổi liên tục. Đẩy lên server là tự bỏ đi
   bài demo đẹp nhất của chính mục 10.
2. **Backend đã chống gian lận rồi.** `OrderItemRequest` chỉ nhận `productId + size + quantity`,
   giá do server tự tra. Nên giỏ ở máy có bị sửa cũng không ảnh hưởng tiền.
3. **Giá đổi thì sao?** Lúc bấm thanh toán, server tính lại giá từ DB. FE chỉ cần đối chiếu tổng
   tiền server trả về với tổng tiền đang hiện, lệch thì báo "giá vừa thay đổi".
4. Tiết kiệm nguyên một module — thời gian đó đổ vào Flutter.

Đánh đổi chấp nhận: gỡ app ra cài lại thì mất giỏ. Khắc phục rẻ bằng `shared_preferences` hoặc `Hive`.

### Câu 2 — Thông báo: **chỉ làm trong app, KHÔNG làm push FCM**

Lý do:
1. Đề bài ghi "màn hình thông báo" — tức là **một màn hình có danh sách**. Không ghi chữ "push".
2. Push thật tốn nguyên một đợt mà **không thêm điểm nào**: dựng Firebase project, cấu hình APNs
   cho iOS (phải có tài khoản Apple Developer), quản lý device token, xử lý handler lúc app chạy nền,
   xin quyền thông báo. Rủi ro cao, điểm bằng 0.
3. Thiết kế của Đợt C đã chừa sẵn đường: khi nào cần push chỉ việc thêm bảng `device_tokens` và
   bắn thêm một lời gọi ở đúng chỗ đang ghi notification. **Không phải đập đi làm lại.**

👉 Nếu dư thời gian ở tuần cuối thì bổ sung sau, đừng làm bây giờ.

### Câu 3 — Nhắn tin: **khách ↔ admin, dùng polling, KHÔNG dùng WebSocket**

Lý do:
1. Đề bài ghi "màn hình nhắn tin", không ghi "thời gian thực".
2. WebSocket kéo theo: xác thực trên kênh socket (JWT không tự đi theo WS handshake), xử lý mất
   kết nối và nối lại, quản lý session khi có nhiều instance. Khối `socket` trong `application.yaml`
   đang bị comment — bật lên là mở ra một mớ việc mới.
3. **Polling 3 giây trông y hệt real-time trên màn demo.** Không ai phân biệt được.
4. API của Đợt D đã thiết kế sẵn tham số `since` (chỉ lấy tin mới hơn mốc thời gian) nên polling
   rất nhẹ — mỗi lần chỉ trả về mảng rỗng. Và **mô hình dữ liệu không phải đổi** nếu sau này gắn WS.

👉 Nếu muốn ăn điểm "wow", làm WS **sau khi** cả 10 mục đã chạy, không phải trước.

### Câu 4 — Bắt đầu từ đâu: **Đợt A, ngay bây giờ**

Nhưng mình **chưa gõ code** vì trong Đợt A có 4 quyết định nghiệp vụ cần bro gật trước
(liệt kê ở cuối file `01_DOT_A...`). Đọc xong gật phát là làm.

---

## 4. Chia việc cho 2 người

Giả định cả hai đều làm được cả backend lẫn Flutter. Nguyên tắc chia: **không ai sửa chung một file**,
để khỏi dính conflict.

### Phương án đề xuất — chia theo chiều dọc

| | **Bro** | **Bạn kia** |
|---|---|---|
| Đã quen | auth, account, order, profile | product, category, collection, cloudinary |
| **Backend** | Đợt A (variant — dính `order`) · Đợt E | Đợt B (cửa hàng) · Đợt C (thông báo) |
| **Flutter** | Màn 2 đăng nhập · Màn 5 giỏ · Màn 6 thanh toán · Mục 10 state | Màn 3 danh sách · Màn 4 chi tiết · Màn 8 bản đồ · Màn 7 thông báo |
| **Chung** | Đợt D nhắn tin + Màn 9 — làm đôi, người BE người FE | |

Vì sao chia vậy:
- **Variant dính chặt vào order** (trừ kho, hoàn kho) — người đã nắm `OrderServiceImpl` làm sẽ nhanh
  và an toàn hơn nhiều.
- **Cửa hàng và thông báo là module mới toanh**, không đụng file ai cả → bạn kia làm song song
  không sợ conflict.
- Mỗi người ôm luôn phần Flutter của đúng cái API mình viết → không phải giải thích qua lại.

### Mốc đồng bộ

| Mốc | Điều kiện để đi tiếp |
|---|---|
| **M1** | Đợt A xong + test xanh → mục 4·5·6 mới demo được trọn vẹn |
| **M2** | Đợt B + C xong → mục 7·8 có API, Flutter bắt đầu được |
| **M3** | Cả 2 ghép app, chạy thử end-to-end: đăng nhập → xem → thêm giỏ → đặt → nhận thông báo |
| **M4** | Đợt D + màn 9 |
| **M5** | Đợt E + dọn, viết tài liệu nộp |

**M3 là mốc quan trọng nhất.** Ghép sớm, đừng để tuần cuối mới ghép.

---

## 5. Luật áp dụng cho mọi đợt

Giữ nguyên cách đang làm:

1. **Bàn trước, code sau.** Mỗi đợt có mục "cần chốt" ở cuối — gật xong mới gõ.
2. **Xong mỗi sub-task thì dừng, báo cáo, hướng dẫn test, chờ nghiệm thu.**
3. **Bám golden standard `features/product`**: Controller → Service → Repository → Entity,
   `ApiResponse.builder()`, `PageResponse.from()`, MapStruct, DTO 1 file 1 record phẳng.
4. **Service kết thúc bằng mapper**, không `new` DTO bằng tay.
5. **Làm xong là phải có test** và chạy `./mvnw clean test` xanh trước khi báo.
6. Lệch chuẩn thì **hỏi trước khi gõ**, không làm rồi báo sau.

### Khuôn thư mục cho mọi module mới

```
features/<ten>/
├── controller/      <Ten>Controller.java
├── dto/
│   ├── request/     <Ten>Request.java, <Ten>UpdateRequest.java
│   └── response/    <Ten>Response.java
├── entity/          <Ten>Entity.java
├── enums/           (nếu có)
├── mapper/          <Ten>Mapper.java
├── repository/      <Ten>Repository.java
├── service/         <Ten>Service.java
│   └── impl/        <Ten>ServiceImpl.java
└── specification/   <Ten>Specifications.java  (nếu cần lọc)
```

---

## 6. Danh sách file trong thư mục này

| File | Nội dung |
|---|---|
| `00_TONG_QUAN_VA_CHIA_VIEC.md` | file này |
| `01_DOT_A_variant_ton_kho.md` | 🔴 làm trước — gỡ chặn mục 4·5·6 |
| `02_DOT_B_cua_hang_ban_do.md` | mục 8 |
| `03_DOT_C_thong_bao.md` | mục 7 |
| `04_DOT_D_nhan_tin.md` | mục 9 |
| `05_DOT_E_hoan_thien_don_hang.md` | huỷ đơn, mail, thanh toán |
