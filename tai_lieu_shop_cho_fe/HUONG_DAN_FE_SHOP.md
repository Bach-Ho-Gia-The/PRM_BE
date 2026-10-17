# Hướng dẫn nối API — Product · Category · Collection · Upload ảnh

> Cùng bộ với `tai_lieu_auth_cho_fe/`. Tài liệu này **chỉ nói phần cửa hàng**;
> đăng nhập / token / cookie xem `tai_lieu_auth_cho_fe/HUONG_DAN_FE_FLUTTER.md`.
>
> Cập nhật: 17/10/2026 · Backend: Spring Boot 4.1.1 · Base URL: `http://localhost:8088/api/v1`

---

## 0. Đọc 7 dòng này trước, đỡ mất một buổi

| # | Điều dễ vấp nhất |
|---|---|
| 1 | **Mọi URL có tiền tố `/api/v1`.** Swagger ở `localhost:8088/api/v1/swagger-ui.html` |
| 2 | **`/products` và `/products/admin` là HAI thế giới khác nhau.** Nhánh công khai không cần token, nhánh `admin` bắt buộc ROLE_ADMIN |
| 3 | **Upload ảnh phải set `contentType`.** Không set thì backend vẫn nhận (đã nới), nhưng nên set cho đúng |
| 4 | **Mọi lệnh GHI của Collection bắt buộc có `expectedVersion`.** Gửi sai số là `409`, không phải lỗi mạng |
| 5 | **Muốn sửa BST đang ACTIVE phải `PATCH /hide` trước**, sửa xong `PATCH /publish` lại |
| 6 | **Sản phẩm tạo mới mà chưa có ảnh sẽ nằm `HIDDEN`**, không tự lên cửa hàng |
| 7 | **`?size` tối đa là `100`.** Gửi lớn hơn thì Spring tự cắt về 100, không báo lỗi |

---

## 1. Khuôn response — giống hệt phần auth

Mọi endpoint đều trả về cùng một vỏ:

```json
{
  "code": 200,
  "message": "Products retrieved successfully",
  "result": { },
  "path": "/api/v1/products",
  "timestamp": "2026-10-17T08:30:00Z"
}
```

Dữ liệu thật **luôn nằm trong `result`**. Danh sách thì `result` là object phân trang:

```json
"result": {
  "content": [ ],
  "page": 0,
  "size": 20,
  "totalElements": 134,
  "totalPages": 7,
  "last": false
}
```

---

## 2. Bảng endpoint

### 2.1 — Công khai (không cần token)

| Method | URL | Việc |
|---|---|---|
| GET | `/products` | Danh sách sản phẩm, có lọc + phân trang |
| GET | `/products/id/{id}` | Chi tiết theo id |
| GET | `/products/slug/{slug}` | Chi tiết theo slug |
| GET | `/categories` | Danh sách danh mục |
| GET | `/categories/id/{id}` | Chi tiết danh mục |
| GET | `/categories/slug/{slug}` | Chi tiết danh mục theo slug |
| GET | `/collections` | Danh sách BST đang đăng |
| GET | `/collections/slug/{slug}` | Chi tiết BST + trang sản phẩm bên trong |

> 🆕 **Tồn kho theo size** (`/products/admin/{id}/variants`, 4 endpoint) được bổ sung sau tài
> liệu này — xem `tai_lieu_phan_moi_cho_fe/HUONG_DAN_FE_PHAN_MOI.md` §2.
> 🔴 **Sản phẩm không có variant thì khách KHÔNG đặt mua được**, dù đang `ACTIVE`.
>
> Đặt hàng xem `tai_lieu_don_hang_cho_fe/`.

### 2.2 — Quản trị (bắt buộc `Authorization: Bearer <admin token>`)

| Method | URL | Việc |
|---|---|---|
| GET | `/products/admin` | Danh sách gồm cả HIDDEN |
| POST | `/products/admin` | Tạo sản phẩm |
| GET | `/products/admin/{id}` | Chi tiết gồm cả HIDDEN |
| PATCH | `/products/admin/{id}` | Sửa từng phần |
| DELETE | `/products/admin/{id}` | Xoá mềm |
| POST | `/products/admin/{id}/images` | Thêm ảnh vào gallery |
| PUT | `/products/admin/{id}/images/{imageId}` | Thay một ảnh trong gallery |
| DELETE | `/products/admin/{id}/images/{imageId}` | Xoá một ảnh |
| PUT | `/products/admin/{id}/thumbnail` | Upload ảnh mới và đặt làm ảnh đại diện |
| PATCH | `/products/admin/{id}/thumbnail` | Chọn một ảnh **đã có** trong gallery làm đại diện |
| GET/POST | `/categories/admin` | Danh sách / tạo danh mục |
| GET/PATCH/DELETE | `/categories/admin/{id}` | Chi tiết / sửa / xoá danh mục |
| GET/POST | `/collections/admin` | Danh sách / tạo BST |
| GET/PATCH/DELETE | `/collections/admin/{id}` | Chi tiết / sửa / xoá BST |
| PUT | `/collections/admin/{id}/cover` | Upload ảnh bìa BST |
| PUT | `/collections/admin/{id}/products` | Đặt lại danh sách + thứ tự sản phẩm |
| PATCH | `/collections/admin/{id}/publish` | Đăng BST lên cửa hàng |
| PATCH | `/collections/admin/{id}/hide` | Gỡ BST khỏi cửa hàng |

---

## 3. Lọc danh sách sản phẩm

`GET /products` nhận các tham số sau, **tất cả đều tuỳ chọn**:

| Tham số | Kiểu | Ghi chú |
|---|---|---|
| `category` | slug | Danh mục phải đang ACTIVE, nếu không trả trang rỗng |
| `collection` | slug | Chỉ nhận slug hợp lệ `a-z0-9-`; BST phải đang đăng |
| `search` | chuỗi | Tìm trong **tên** và **tag**, không phân biệt hoa thường |
| `minPrice` / `maxPrice` | số | `min > max` ⇒ `400` |
| `featured` | boolean | |
| `page` / `size` | số | `size` mặc định 20, **trần 100** |
| `sort` | chuỗi | Chỉ được `name`, `price`, `createdAt`, `updatedAt` |

Gửi `sort` ngoài danh sách sẽ nhận `400` kèm **danh sách field hợp lệ ngay trong message** — đọc message là sửa được, không cần tra tài liệu.

```
GET /api/v1/products?category=ao-so-mi&search=lua&minPrice=200000&sort=price,asc&size=24
```

---

## 4. Upload ảnh

### 4.1 — Quy tắc chung

- Định dạng nhận: **jpg · png · webp**
- Dung lượng tối đa **5 MB** mỗi ảnh
- Backend **đọc nội dung file** để xác định định dạng, không tin phần mở rộng. Đổi tên `virus.exe` thành `anh.jpg` vẫn bị chặn.
- Tên field trong form-data luôn là **`file`**

### 4.2 — Flutter

Backend đã được chỉnh để **chấp nhận `application/octet-stream`**, nên code tối giản vẫn chạy:

```dart
final request = http.MultipartRequest('POST', uri)
  ..headers['Authorization'] = 'Bearer $token'
  ..files.add(await http.MultipartFile.fromPath('file', path));
```

Nhưng **nên set `contentType` cho đúng chuẩn**:

```dart
import 'package:http_parser/http_parser.dart';

..files.add(await http.MultipartFile.fromPath(
    'file', path,
    contentType: MediaType('image', 'jpeg'),   // hoặc 'png' / 'webp'
));
```

> ⚠️ Nếu bạn khai **sai** loại — ví dụ khai `image/png` mà ruột là JPEG — backend vẫn trả `415`.
> Khai đúng, hoặc không khai gì cả. Đừng khai bừa.

### 4.3 — Upload ảnh bìa BST (trường hợp đặc biệt)

`PUT /collections/admin/{id}/cover` nhận **hai phần** trong cùng một form-data:

| Phần | Kiểu | Nội dung |
|---|---|---|
| `metadata` | JSON | `{"expectedVersion": 3}` |
| `file` | file | ảnh |

```dart
final request = http.MultipartRequest('PUT', uri)
  ..headers['Authorization'] = 'Bearer $token'
  ..files.add(http.MultipartFile.fromString(
      'metadata', jsonEncode({'expectedVersion': version}),
      contentType: MediaType('application', 'json')))
  ..files.add(await http.MultipartFile.fromPath('file', path));
```

### 4.4 — Hai lỗi của dịch vụ ảnh, phân biệt cho đúng

| Mã | Message | Nghĩa thật |
|---|---|---|
| `503` | `Image storage is not configured` | Backend **chưa khai** `CLOUDINARY_*` trong `.env`. Lỗi cấu hình, không phải lỗi FE |
| `503` | `...usage limit has been reached` | Đã dùng quá **75%** hạn mức Cloudinary. Chặn chủ động, chỉnh `CLOUDINARY_QUOTA_BLOCK_AT_PERCENT` |
| `503` | `...usage could not be verified` | Không gọi được Cloudinary để đo hạn mức. Chặn để an toàn, tự mở lại sau 30 giây |
| `502` | `Image upload failed` | Cloudinary nhận nhưng lỗi |
| `415` | `Unsupported image format` | Không phải jpg/png/webp (đọc từ nội dung file) |
| `400` | `Image exceeds the configured upload size limit` | Quá 5 MB |

---

## 5. Vòng đời Bộ sưu tập — phần FE hay hiểu sai nhất

### 5.1 — `expectedVersion` là gì

BST có một cột `version` tăng mỗi lần bị sửa. **Mọi lệnh ghi đều phải gửi kèm số version mà bạn đang cầm trên tay.**

```json
PATCH /collections/admin/{id}
{ "expectedVersion": 3, "collectionName": "Thu Đông 2026" }
```

- Số khớp ⇒ cho sửa, version tăng lên 4
- Số **không khớp** ⇒ `409 "Collection has changed; refresh before retrying with the latest version"`

Nghĩa là: **có người khác vừa sửa trước bạn.** FE phải `GET` lại, hiện dữ liệu mới, rồi để người dùng quyết định làm lại. **Đừng tự động retry** — làm vậy là ghi đè mất thay đổi của người kia.

Số version luôn có trong `result.version` của mọi response BST.

### 5.2 — Sơ đồ trạng thái

```
       tạo mới
          │
          ▼
      ┌────────┐   publish   ┌────────┐
      │ HIDDEN │ ──────────► │ ACTIVE │
      │        │ ◄────────── │        │
      └───┬────┘    hide     └────────┘
          │                    (khách nhìn thấy)
          │ sửa / đổi ảnh / đổi danh sách sản phẩm
          │ ↻ CHỈ làm được ở trạng thái này
          ▼
       delete → DELETED
```

**BST mới tạo luôn là `HIDDEN`.** Phải gọi `publish` mới lên cửa hàng.

**Muốn sửa BST đang `ACTIVE`:** `hide` → sửa → `publish`. Không có đường tắt. Gọi thẳng là `409 "Hide collection before editing or publishing"`.

> FE nên gói ba bước này vào một nút "Sửa" và cảnh báo người dùng: *"BST sẽ tạm ẩn khỏi cửa hàng trong lúc bạn chỉnh sửa."*

### 5.3 — Điều kiện để `publish` thành công

BST phải có **ít nhất một sản phẩm đang hiển thị**. Toàn sản phẩm ẩn/xoá thì:
`409 "Collection must contain at least one visible product before publishing"`

### 5.4 — Đặt lại danh sách sản phẩm

`PUT /collections/admin/{id}/products` — gửi **toàn bộ** danh sách, không phải thêm/bớt từng cái:

```json
{ "expectedVersion": 4, "productIds": ["id-C", "id-A", "id-B"] }
```

**Thứ tự trong mảng chính là thứ tự hiển thị.** Kéo thả xong thì gửi lại cả mảng.

Luật:
- ID trùng nhau trong mảng ⇒ `400`
- Mảng rỗng ⇒ `400`, BST không được rỗng
- **Thêm** sản phẩm mới thì sản phẩm đó phải đang hiển thị, nếu không `409`
- Sản phẩm **đã nằm sẵn** trong BST từ trước mà nay bị ẩn thì **vẫn giữ được** và sắp lại thứ tự bình thường

### 5.5 — Trang BST công khai

`GET /collections/slug/{slug}` trả về chi tiết BST **kèm một trang sản phẩm**:

```json
"result": {
  "id": "...", "collectionName": "Thu Đông 2026", "slug": "thu-dong-2026",
  "coverImageUrl": "https://...",
  "products": { "content": [...], "page": 0, "totalElements": 12 }
}
```

- Nhận `page` và `size`, **không nhận `sort`** — thứ tự do admin đặt là cố định. Gửi `sort` khác là `400`.
- Xin `page` vượt quá số trang ⇒ vẫn trả thông tin BST, `content` rỗng. **Không phải 404.**
- `404` chỉ xảy ra khi BST không tồn tại, chưa publish, hoặc không còn sản phẩm nào hiển thị.

---

## 6. Vòng đời Sản phẩm

### 6.1 — Tạo sản phẩm: thứ tự bắt buộc

```
1. POST /products/admin                      → nhận id, sản phẩm ở trạng thái HIDDEN
2. POST /products/admin/{id}/images          → upload ảnh (ảnh đầu tiên tự thành thumbnail)
3. PATCH /products/admin/{id} {"status":"ACTIVE"}  → đẩy lên cửa hàng
```

> 🔴 **Đổi hành vi từ 17/10:** trước đây bước 1 tạo ra sản phẩm `ACTIVE` ngay, khiến nó hiện trên
> cửa hàng với ô ảnh rỗng trong lúc admin đi chọn file. Nay **không có `thumbnail` thì tự `HIDDEN`**.
>
> Nếu bạn gửi sẵn `thumbnail` là URL ảnh ngoài ở bước 1 thì sản phẩm vẫn `ACTIVE` như cũ.

### 6.2 — Quy tắc ảnh đại diện

| Tình huống | Kết quả |
|---|---|
| Upload ảnh đầu tiên vào gallery | Tự động thành ảnh đại diện |
| `POST .../images` với `asThumbnail=true` | Ảnh mới thành đại diện |
| `PATCH .../thumbnail` + `{"imageId": "..."}` | Chọn một ảnh **đã có** làm đại diện |
| `PUT .../thumbnail` + file | Upload ảnh mới, thay luôn ảnh đại diện |
| Xoá ảnh đang là đại diện | Tự chuyển sang ảnh kế tiếp trong gallery |
| Xoá ảnh làm sản phẩm **ACTIVE** mất thumbnail | `409 "An ACTIVE product must keep a thumbnail; hide it or pick another image first"` — ẩn sản phẩm trước, hoặc chọn ảnh khác làm thumbnail |

### 6.3 — Ảnh nằm trong đơn hàng cũ sẽ không bị xoá

Khi thay/xoá một ảnh, backend kiểm tra xem URL đó có đang bị một `order_item` tham chiếu không.
Nếu có, **file trên Cloudinary được giữ lại** để lịch sử đơn hàng không vỡ ảnh — dù ảnh đã biến khỏi gallery sản phẩm.

### 6.4 — Slug không tự đổi theo tên

Đổi tên sản phẩm **không** làm slug đổi theo, vì link cũ đã được chia sẻ ra ngoài phải còn sống.
Muốn đổi slug thì gửi field `slug` một cách tường minh.

### 6.5 — Xoá là xoá mềm

`DELETE /products/admin/{id}` chỉ đổi `status` thành `DELETED`. Sản phẩm biến khỏi mọi danh sách
(kể cả danh sách admin) và trả `404` khi truy cập — nhưng **ảnh và lịch sử đơn hàng vẫn còn nguyên**.

---

## 7. Danh mục

| Luật | Chi tiết |
|---|---|
| Chỉ gán được danh mục **ACTIVE** cho sản phẩm | Chọn danh mục HIDDEN ⇒ `409` |
| Sửa field khác của sản phẩm thì giữ nguyên danh mục HIDDEN cũ | Chỉ khi **đổi sang** danh mục mới mới bắt buộc ACTIVE |
| Không xoá được danh mục còn sản phẩm | `409 "Cannot delete category that still contains products"`. Sản phẩm đã `DELETED` thì không tính |
| Danh mục có `status = null` (dữ liệu cũ) **không** hiện ở cửa hàng | Khác với sản phẩm: sản phẩm `status = null` vẫn được coi là đang bán |
| Đổi tên mà không gửi `slug` ⇒ slug tự sinh lại | Gửi `slug: ""` kèm tên mới cũng cho kết quả tương tự |
| Gửi `slug: ""` mà **không** đổi tên ⇒ `400` | Không biết lấy gì để sinh slug |
| `PATCH` với `status: "DELETED"` ⇒ `400` | Phải dùng `DELETE` |

---

## 8. Bảng mã lỗi hay gặp

| Mã | Khi nào | Xử lý ở FE |
|---|---|---|
| `400` | Sai định dạng, sort không hợp lệ, khoảng giá ngược | Hiện message trả về, nó đã đủ rõ |
| `401` | Thiếu/hết hạn token | Gọi `/auth/refresh`, hỏng nữa thì về màn đăng nhập |
| `403` | Token hợp lệ nhưng không phải ADMIN | Ẩn nút quản trị ngay từ đầu |
| `404` | Không tồn tại, hoặc đã xoá mềm, hoặc chưa publish | Không phân biệt được — **cố ý**, để không lộ dữ liệu ẩn |
| `409` | Trùng SKU/slug/tên · sai `expectedVersion` · sửa BST đang ACTIVE · xoá ảnh cuối · xoá danh mục còn hàng | Mỗi message là một tình huống khác nhau, **phải đọc message** |
| `413` | File vượt trần của tầng servlet (10 MB) | Nén ảnh trước khi gửi |
| `415` | Không phải ảnh, hoặc khai sai MIME | Xem mục 4.2 |
| `502` | Cloudinary lỗi | Cho người dùng thử lại |
| `503` | Chưa cấu hình Cloudinary, hoặc chạm hạn mức | Báo admin, FE không tự sửa được |

---

## 9. Thứ FE **không** lấy được, đừng đi tìm

- **`publicId` của ảnh không bao giờ xuất hiện trong response.** Nó là mã nội bộ để backend dọn file. FE chỉ dùng `imageUrl`.
- **Không có API xoá một ảnh khỏi Cloudinary trực tiếp.** Ảnh chỉ bị dọn khi bạn xoá/thay nó qua API sản phẩm.
- **Không gán được `coverImageUrl` là URL thuộc thư mục quản lý của hệ thống.** Muốn đặt ảnh bìa thì upload qua `PUT /cover`, còn field `coverImageUrl` chỉ dành cho ảnh ngoài. Cố tình dán vào sẽ nhận `400 "Managed image URLs must be set through the image upload or gallery API"`.
