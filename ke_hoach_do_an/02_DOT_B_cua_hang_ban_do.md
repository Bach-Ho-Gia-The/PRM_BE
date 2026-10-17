# ĐỢT B — Cửa hàng + bản đồ  (mục 8)

> **Rẻ nhất trên mỗi điểm.** Module mới hoàn toàn, không đụng file của ai.
> Người làm đề xuất: **bạn kia** (làm song song với Đợt A, không conflict).
> Phụ thuộc: không.

---

## B.0 Vì sao làm sớm

- Là **CRUD thuần**, không nghiệp vụ phức tạp, không dính module nào khác.
- Vẫn ăn trọn **10%** như mọi mục khác.
- Làm xong trong một buổi, giải phóng người để sang mục nặng hơn.

⚠️ **Cạm bẫy phải tránh:** hardcode danh sách cửa hàng thẳng trong Flutter cho nhanh.
Đề bài chấm "màn hình bản đồ" trong một đồ án **có backend** — toạ độ hardcode là tự bỏ phần
"có API" và rất dễ bị hỏi *"dữ liệu này lấy từ đâu?"*. Làm hẳn API, chỉ tốn thêm một buổi.

---

## B.1 Nghiệp vụ

Shop có vài chi nhánh. Khách mở màn bản đồ → thấy các ghim → bấm vào ghim → xem tên, địa chỉ,
số điện thoại, giờ mở cửa → bấm gọi hoặc bấm chỉ đường.

Admin quản lý danh sách chi nhánh như mọi danh mục khác: thêm, sửa, ẩn, xoá.

### Quy tắc

1. **Chỉ cửa hàng `ACTIVE` mới hiện với khách.** Dùng lại `RecordStatus{ACTIVE, HIDDEN, DELETED}`
   và `SoftDeletableEntity` sẵn có.
2. **Toạ độ bắt buộc và phải hợp lệ**: `latitude` ∈ [-90, 90], `longitude` ∈ [-180, 180].
   Thiếu toạ độ thì không cắm ghim được → không cho tạo.
3. **Xoá là xoá mềm.** Cửa hàng đóng cửa vẫn có thể bị nhắc tới ở đơn hàng cũ sau này.
4. **Giờ mở cửa để dạng chuỗi tự do** (`"08:00 - 22:00"`), không tách `openTime`/`closeTime`.
   Lý do: chi nhánh có thể nghỉ thứ 2, hoặc giờ cuối tuần khác ngày thường — mô hình hoá đầy đủ
   là một bảng riêng, quá mức cần thiết cho đồ án. Chuỗi hiển thị thẳng lên UI là đủ.

---

## B.2 Entity

```java
@Entity
@Table(
    name = "stores",
    indexes = @Index(name = "idx_stores_status", columnList = "status")
)
public class StoreEntity extends SoftDeletableEntity {
    private UUID id;
    private String storeName;      // @Column(nullable=false, length=150)
    private String address;        // @Column(nullable=false, length=300)
    private String phone;          // length=20, nullable — khớp regex ^0\d{9}$ như profile
    private String openingHours;   // length=100, nullable  "08:00 - 22:00"
    private Double latitude;       // @Column(nullable=false)
    private Double longitude;      // @Column(nullable=false)
    private String imageUrl;       // length=500, nullable — ảnh mặt tiền
    private String imagePublicId;  // length=255, nullable — nếu dùng Cloudinary
    // status, createdAt, updatedAt kế thừa từ SoftDeletableEntity
}
```

> Dùng `Double` chứ không `BigDecimal`: toạ độ GPS không cần chính xác tuyệt đối như tiền,
> và `Double` nối thẳng vào `LatLng` của thư viện bản đồ Flutter không phải đổi kiểu.

---

## B.3 API

### Công khai

| # | Method | URL | Việc |
|---|---|---|---|
| B-1 | `GET` | `/stores` | Danh sách cửa hàng ACTIVE (có phân trang) |
| B-2 | `GET` | `/stores/{id}` | Chi tiết một cửa hàng ACTIVE |

> Nhớ thêm `/stores/**` vào **whitelist** ở `application.yaml` dòng ~106 (khối chỉ cho GET).

### Quản trị

| # | Method | URL | Việc |
|---|---|---|---|
| B-3 | `GET` | `/stores/admin` | Danh sách gồm cả HIDDEN |
| B-4 | `POST` | `/stores/admin` | Tạo |
| B-5 | `GET` | `/stores/admin/{id}` | Chi tiết gồm cả HIDDEN |
| B-6 | `PATCH` | `/stores/admin/{id}` | Sửa từng phần |
| B-7 | `DELETE` | `/stores/admin/{id}` | Xoá mềm |

**7 endpoint.** Đúng khuôn `/categories` đang có — copy cấu trúc đó là nhanh nhất.

### DTO

```java
// request/StoreRequest.java
public record StoreRequest(
        @NotBlank @Size(max = 150)  String storeName,
        @NotBlank @Size(max = 300)  String address,
        @Pattern(regexp = "^0\\d{9}$", message = "Phone must be 10 digits starting with 0")
        String phone,
        @Size(max = 100)            String openingHours,
        @NotNull @DecimalMin("-90.0")  @DecimalMax("90.0")   Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")  Double longitude,
        @Size(max = 500)            String imageUrl
) {}
```

```java
// request/StoreUpdateRequest.java  — PATCH, mọi field nullable, KHÔNG @NotBlank/@NotNull
//   nhưng vẫn giữ @Size / @DecimalMin / @DecimalMax / @Pattern
//   + thêm  RecordStatus status  để ẩn/hiện
```

```java
// response/StoreResponse.java  — phẳng, không nested
public record StoreResponse(
        UUID id, String storeName, String address, String phone,
        String openingHours, Double latitude, Double longitude,
        String imageUrl, RecordStatus status,
        Instant createdAt, Instant updatedAt
) {}
```

> ⚠️ `imagePublicId` **không bao giờ** lộ ra response — giống `thumbnailPublicId` của product.

---

## B.4 Edge case phải xử

| Tình huống | Kết quả |
|---|---|
| `latitude = 91` | `400` |
| `longitude = -181` | `400` |
| Thiếu `latitude` khi tạo | `400` |
| `PATCH` chỉ đổi `phone` | `200`, toạ độ giữ nguyên |
| `PATCH {"status":"DELETED"}` | `400` — bắt dùng `DELETE`, giống Category |
| `GET /stores` khi cửa hàng đó HIDDEN | không xuất hiện trong danh sách |
| `GET /stores/{id}` với cửa hàng HIDDEN | `404` |
| `GET /stores/admin/{id}` với cửa hàng HIDDEN | `200` ✅ |
| `?size=2000` | cắt còn 100 (đã có cấu hình chung) |
| `?sort=latitude` | `400` — whitelist chỉ cho `storeName`, `createdAt`, `updatedAt` |
| `phone = "0123456789"` | `200` ✅ |
| `phone = "84901234567"` | `400` — sai regex |

### Ảnh cửa hàng

Hai lựa chọn, **chọn (a) trước**:
- **(a) Chỉ nhận URL ngoài** qua field `imageUrl`. Dùng `managedImageService.requireExternalImageUrl()`
  vừa viết ở vòng audit 3 — một dòng là xong, nhất quán với product/collection.
- (b) Upload qua Cloudinary như product. Thêm `PUT /stores/admin/{id}/image`. Chỉ làm nếu dư thời gian.

---

## B.5 Seed dữ liệu

**Phải có**, nếu không Flutter không có gì để cắm ghim lúc dựng UI.

Thêm vào chỗ seed đang chạy (cùng nơi seed `Admin@12345`), 3–4 chi nhánh **toạ độ thật ở TP.HCM**
để bản đồ trông hợp lý khi demo:

| Tên | Địa chỉ gợi ý | Khu |
|---|---|---|
| Chi nhánh Quận 1 | gần Nguyễn Huệ | ~10.776, 106.701 |
| Chi nhánh Quận 3 | gần Võ Văn Tần | ~10.779, 106.690 |
| Chi nhánh Thủ Đức | gần Vincom Thủ Đức | ~10.850, 106.760 |
| Chi nhánh Tân Bình | gần Trường Chinh | ~10.800, 106.650 |

> Toạ độ trên là **xấp xỉ, phải tra lại Google Maps lấy số thật** trước khi seed. Đừng chép thẳng.

---

## B.6 Checklist test

| # | Ca | Mong đợi |
|---|---|---|
| 1 | Tạo đủ field hợp lệ | `201` |
| 2 | Thiếu `latitude` | `400` |
| 3 | `latitude = 91` / `longitude = 181` | `400` |
| 4 | `phone` sai định dạng | `400` |
| 5 | `PATCH` chỉ đổi `openingHours` | `200`, field khác nguyên |
| 6 | `PATCH {"status":"HIDDEN"}` rồi `GET /stores` | không thấy |
| 7 | `GET /stores/admin` sau khi ẩn | vẫn thấy |
| 8 | `DELETE` rồi `GET /stores/admin/{id}` | `404` |
| 9 | Khách gọi `/stores/admin` | `401` |
| 10 | Khách (non-admin) gọi `/stores/admin` | `403` |
| 11 | `GET /stores` không token | `200` |
| 12 | `?sort=latitude` | `400` |

---

## B.7 File sẽ tạo

```
features/store/
├── controller/StoreController.java
├── dto/request/StoreRequest.java
├── dto/request/StoreUpdateRequest.java
├── dto/response/StoreResponse.java
├── entity/StoreEntity.java
├── mapper/StoreMapper.java
├── repository/StoreRepository.java
├── service/StoreService.java
└── service/impl/StoreServiceImpl.java

src/test/.../store/service/StoreServiceImplTest.java
```

**Sửa:** `application.yaml` (whitelist `/stores/**`), chỗ seed dữ liệu.

**Cách nhanh nhất:** copy nguyên module `features/category` rồi đổi tên và đổi field. Category
cũng là CRUD + soft delete + ẩn/hiện, cấu trúc giống hệt. Tiết kiệm được nửa thời gian và
**đảm bảo bám đúng golden standard**.

---

## B.8 ❓ Cần chốt

**Q1 — Khách có cần lọc cửa hàng theo khoảng cách ("gần tôi nhất") không?**
- 👉 **Mình đề xuất KHÔNG làm ở backend.** Số cửa hàng chỉ có 3–4 cái, Flutter tải hết về rồi tự
  tính khoảng cách tới vị trí người dùng bằng `Geolocator.distanceBetween()` — vài dòng, không cần
  truy vấn không gian ở DB. Làm `ST_Distance` trong Postgres cho 4 bản ghi là dùng dao mổ trâu.

**Q2 — Ảnh cửa hàng: URL ngoài hay upload Cloudinary?**
- 👉 **URL ngoài trước** (phương án a ở §B.4). Upload thêm được bất cứ lúc nào, mà hiện tại chỉ cần
  có ảnh để UI đỡ trống.

**Q3 — Có cần phân trang cho `/stores` không, khi chỉ có 4 cửa hàng?**
- 👉 **Vẫn nên có**, vì `PageResponse` là chuẩn chung của dự án, FE xử lý đồng nhất mọi danh sách.
  Chi phí bằng 0 vì dùng lại `PageResponse.from()`.
