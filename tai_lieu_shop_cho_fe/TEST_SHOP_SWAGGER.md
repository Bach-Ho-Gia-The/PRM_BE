# Kịch bản test — Product · Category · Collection · Upload ảnh

> Cùng bộ với `tai_lieu_auth_cho_fe/TEST_AUTH_SWAGGER.md`.
> Swagger: **`http://localhost:8088/api/v1/swagger-ui.html`**
> Cập nhật: 17/10/2026

---

## §0 — Chuẩn bị

### 0.1 Không cần chạy SQL nào thêm

Phần auth có 6 lệnh migration; **phần cửa hàng thì không**. Các bảng
`products`, `product_images`, `product_variants`, `categories`, `collections`,
`collection_products` đều do `ddl-auto: update` tự lo.

🆕 Bản 17/10 có thêm 5 index mới. `ddl-auto: update` tự tạo khi khởi động:

```
idx_products_category_id · idx_products_status
idx_product_images_product_id · idx_collections_status · idx_categories_status
```

Kiểm tra sau khi chạy app lần đầu:
```sql
SELECT indexname FROM pg_indexes
WHERE tablename IN ('products','product_images','collections','categories')
ORDER BY 1;
```

### 0.2 Bật Cloudinary nếu muốn test upload

```
CLOUDINARY_CLOUD_NAME=...
CLOUDINARY_API_KEY=...
CLOUDINARY_API_SECRET=...
```

Không khai ⇒ **app vẫn chạy bình thường**, nhưng mọi endpoint upload trả
`503 "Image storage is not configured"`. Các ca từ §D trở đi sẽ bỏ qua được.

### 0.3 Hai tài khoản

| Vai | Email | Mật khẩu |
|---|---|---|
| Admin | tài khoản seed | `Admin@12345` |
| Khách | tài khoản seed | `User@12345` |

Lấy token: `POST /auth/login` → copy `result.accessToken` → bấm **Authorize** trên Swagger.

### 0.4 Quy ước

- ✅ = mong đợi thành công · ❌ = mong đợi lỗi
- Mọi URL trong tài liệu này **đã bỏ tiền tố** `/api/v1`

---

## §A — Phân quyền (12 ca)

> Mục đích: chứng minh nhánh `admin` và nhánh công khai thực sự tách biệt.

| # | Hành động | Token | Mong đợi |
|---|---|---|---|
| A1 | `GET /products` | không | ✅ `200` |
| A2 | `GET /products/slug/{slug}` | không | ✅ `200` |
| A3 | `GET /categories` | không | ✅ `200` |
| A4 | `GET /collections` | không | ✅ `200` |
| A5 | `GET /products/admin` | không | ❌ `401` |
| A6 | `GET /products/admin` | khách | ❌ `403` |
| A7 | `POST /products/admin` | khách | ❌ `403` |
| A8 | `DELETE /products/admin/{id}` | khách | ❌ `403` |
| A9 | `POST /categories/admin` | khách | ❌ `403` |
| A10 | `PATCH /collections/admin/{id}/publish` | khách | ❌ `403` |
| A11 | `PUT /products/admin/{id}/thumbnail` | khách | ❌ `403` |
| A12 | `GET /products/admin` | admin | ✅ `200` |

> **A5 vs A6 phải khác nhau.** Cùng ra `403` nghĩa là chuỗi filter sai thứ tự.

---

## §B — Danh mục (18 ca)

| # | Hành động | Mong đợi |
|---|---|---|
| B1 | `POST /categories/admin` `{"categoryName":"Áo sơ mi"}` | ✅ `201`, slug tự sinh `ao-so-mi` |
| B2 | Lặp lại B1 y hệt | ❌ `409` trùng tên |
| B3 | `{"categoryName":"ÁO SƠ MI"}` | ❌ `409` — so tên **không phân biệt hoa thường** |
| B4 | `{"categoryName":"Quần", "slug":"quan-dai"}` | ✅ slug tường minh thắng |
| B5 | `{"categoryName":"X", "slug":"Quan Dai"}` | ❌ `400` sai regex slug |
| B6 | `{"categoryName":""}` | ❌ `400` |
| B7 | `categoryName` 101 ký tự | ❌ `400` |
| B8 | `{"categoryName":"!!!"}` (không sinh nổi slug) | ❌ `400` |
| B9 | `PATCH` đổi tên, không gửi slug | ✅ slug **sinh lại** theo tên mới |
| B10 | `PATCH` `{"slug":""}` kèm tên mới | ✅ slug sinh lại |
| B11 | `PATCH` `{"slug":""}` **không** đổi tên | ❌ `400` |
| B12 | `PATCH` `{"status":"DELETED"}` | ❌ `400` — phải dùng `DELETE` |
| B13 | `PATCH` `{"status":"HIDDEN"}` | ✅ `200` |
| B14 | `GET /categories` (công khai) sau B13 | ✅ danh mục HIDDEN **không xuất hiện** |
| B15 | `GET /categories/admin` sau B13 | ✅ **vẫn thấy** |
| B16 | `DELETE` danh mục còn sản phẩm | ❌ `409` |
| B17 | Xoá hết sản phẩm rồi `DELETE` lại | ✅ `204` |
| B18 | `GET /categories/id/{id}` sau khi xoá | ❌ `404` |

---

## §C — Sản phẩm, phần không cần ảnh (28 ca)

### C.1 Tạo

| # | Hành động | Mong đợi |
|---|---|---|
| C1 | Tạo đủ `name`,`sku`,`price`,`categoryId`, **không** `thumbnail` | ✅ `201`, **`status = "HIDDEN"`** 🆕 |
| C2 | Như C1 nhưng kèm `"thumbnail":"https://cdn.ngoai/a.jpg"` | ✅ `201`, `status = "ACTIVE"` |
| C3 | Như C2 nhưng `"status":"HIDDEN"` | ✅ `201`, `HIDDEN` |
| C4 | `"status":"DELETED"` | ❌ `400` |
| C5 | Trùng `sku` | ❌ `409` |
| C6 | Thiếu `name` | ❌ `400` |
| C7 | `price = 0` | ❌ `400` — phải lớn hơn 0 |
| C8 | `price = -1` | ❌ `400` |
| C9 | `categoryId` không tồn tại | ❌ `404` |
| C10 | `categoryId` trỏ danh mục HIDDEN | ❌ `409` |
| C11 | `slug` sai regex (có hoa, có khoảng trắng) | ❌ `400` |
| C12 | `thumbnail` là URL chứa `/prm/dev/` | ❌ `400` — URL thuộc vùng quản lý |
| C12b | `"thumbnail":"khong-phai-url"` | ❌ `400` 🆕 *"Image URL must use http or https"* — trước đây **nhận và lưu thẳng vào DB** |
| C12c | `"thumbnail":"ftp://host/a.png"` | ❌ `400` 🆕 |

> **C1 là ca quan trọng nhất của bản 17/10.** Trước đây ra `ACTIVE`.

### C.2 Sửa

| # | Hành động | Mong đợi |
|---|---|---|
| C13 | `PATCH` chỉ gửi `{"name":"Tên mới"}` | ✅ slug **giữ nguyên** |
| C14 | `PATCH` gửi `{"slug":"ten-moi"}` | ✅ slug đổi |
| C15 | `PATCH` `sku` trùng sản phẩm khác | ❌ `409` |
| C16 | `PATCH` `sku` bằng chính sku cũ của nó | ✅ `200` |
| C17 | `PATCH` đổi sang danh mục HIDDEN | ❌ `409` |
| C18 | Sản phẩm đang ở danh mục HIDDEN, `PATCH` sửa **tên** | ✅ `200` — không bắt đổi danh mục |
| C19 | `PATCH` `{"thumbnail":""}` khi sản phẩm **có** gallery | ✅ thumbnail lùi về ảnh đầu gallery |
| C20 | `PATCH` `{"thumbnail":""}` khi **không** có ảnh nào, sản phẩm ACTIVE | ❌ `409` |
| C21 | `PATCH` đổi `thumbnail` khi ảnh hiện tại do hệ thống quản lý | ❌ `400` — phải dùng API gallery |

### C.3 Đọc và xoá

| # | Hành động | Mong đợi |
|---|---|---|
| C22 | `GET /products/id/{id}` với sản phẩm HIDDEN | ❌ `404` |
| C23 | `GET /products/admin/{id}` với sản phẩm HIDDEN | ✅ `200` |
| C24 | `DELETE /products/admin/{id}` | ✅ `204` |
| C25 | `GET /products/admin/{id}` sau khi xoá | ❌ `404` |
| C26 | `DELETE` lại lần nữa | ❌ `404` |

---

## §D — Upload ảnh (22 ca)

> Cần `CLOUDINARY_*`. Chuẩn bị sẵn: 1 ảnh jpg nhỏ, 1 ảnh png, 1 file `.txt`, 1 ảnh > 5 MB.

| # | Hành động | Mong đợi |
|---|---|---|
| D1 | `POST /products/admin/{id}/images` + jpg | ✅ `200`, gallery có 1 ảnh, **tự thành thumbnail** |
| D2 | Thêm ảnh thứ 2 | ✅ gallery 2 ảnh, thumbnail **vẫn là ảnh 1** |
| D3 | Thêm ảnh thứ 3 với `asThumbnail=true` | ✅ thumbnail đổi sang ảnh 3 |
| D4 | Upload file `.txt` đổi đuôi thành `.jpg` | ❌ `415` |
| D5 | Upload ảnh > 5 MB | ❌ `400` hoặc `413` |
| D6 | Upload file rỗng | ❌ `400` |
| D7 | Không gửi phần `file` | ❌ `400` |
| D8 | Upload **không set `contentType`** (octet-stream) | ✅ `200` 🆕 — trước đây `415` |
| D9 | Upload jpg nhưng **khai** `image/png` | ❌ `415` |
| D10 | Upload webp | ✅ `200` |
| D11 | `PUT .../images/{imageId}` thay ảnh | ✅ `200`, `id` của ảnh **không đổi** |
| D12 | `PUT .../images/{imageId}` với imageId của sản phẩm khác | ❌ `404` |
| D13 | `PATCH .../thumbnail` `{"imageId": <ảnh 2>}` | ✅ thumbnail = ảnh 2, gallery **không đổi số lượng** |
| D14 | `PATCH .../thumbnail` với imageId lạ | ❌ `404` |
| D15 | `PUT .../thumbnail` + file mới, sản phẩm **đã có** thumbnail trong gallery | ✅ thay ảnh đó tại chỗ, gallery giữ nguyên số lượng |
| D16 | `PUT .../thumbnail` + file mới, thumbnail là **URL ngoài** | ✅ thêm ảnh mới vào gallery |
| D17 | `DELETE .../images/{id}` xoá ảnh đang là thumbnail | ✅ thumbnail nhảy sang ảnh kế tiếp |
| D18 | Xoá ảnh **cuối cùng** của sản phẩm ACTIVE | ❌ `409` *"An ACTIVE product must keep a thumbnail..."* |
| D19 | Ẩn sản phẩm rồi xoá ảnh cuối | ✅ `200`, thumbnail về `null` |
| D20 | `POST .../images` với id sản phẩm đã xoá mềm | ❌ `404` |

### D.21 — Ca khó: ảnh đang nằm trong đơn hàng

| # | Hành động | Mong đợi |
|---|---|---|
| D21 | Đặt một đơn chứa sản phẩm → `PUT` thay ảnh thumbnail của nó | ✅ `200`, **file cũ trên Cloudinary KHÔNG bị xoá**, đơn cũ vẫn hiện ảnh |
| D22 | Sản phẩm ACTIVE có 3 ảnh nhưng `thumbnail` đang NULL (sửa tay trong DB) → xoá ảnh **thứ 2** | ❌ `409` cùng message D18 🆕 — trước đây cũng 409 nhưng báo *"last image"* dù không phải ảnh cuối |

---

## §E — Bộ sưu tập (30 ca)

### E.1 Tạo

| # | Hành động | Mong đợi |
|---|---|---|
| E1 | `POST /collections/admin` với 2 productId hợp lệ | ✅ `201`, **`status = HIDDEN`**, `version = 0` |
| E2 | Mảng `productIds` rỗng | ❌ `400` |
| E3 | `productIds` có 2 phần tử trùng nhau | ❌ `400` |
| E4 | `productIds` chứa `null` | ❌ `400` |
| E5 | `productIds` chứa id không tồn tại | ❌ `404` |
| E6 | `productIds` chứa sản phẩm HIDDEN | ❌ `409` |
| E7 | `collectionName` rỗng / > 100 ký tự | ❌ `400` |
| E8 | Trùng slug với BST khác | ❌ `409` |
| E9 | `coverImageUrl` = `"abc"` (không có scheme) | ❌ `400` |
| E10 | `coverImageUrl` = `"ftp://host/a.png"` | ❌ `400` — chỉ http/https |
| E11 | `coverImageUrl` chứa `/prm/dev/` | ❌ `400` |

### E.2 `expectedVersion`

| # | Hành động | Mong đợi |
|---|---|---|
| E12 | `PATCH` với `expectedVersion` đúng | ✅ `200`, version **tăng 1** |
| E13 | `PATCH` lại với version **cũ** | ❌ `409` |
| E14 | `PATCH` thiếu hẳn `expectedVersion` | ❌ `400` |
| E15 | `expectedVersion = -1` | ❌ `400` |
| E16 | `PUT /products` với version đúng | ✅ `200`, version **tăng 1** |

> E16 quan trọng: đổi bảng liên kết **vẫn phải** làm version tăng. Không tăng thì hai admin ghi đè nhau.

### E.3 Vòng đời

| # | Hành động | Mong đợi |
|---|---|---|
| E17 | `PATCH /publish` khi BST có sản phẩm hiển thị | ✅ `200`, `ACTIVE` |
| E18 | `GET /collections` (công khai) | ✅ BST xuất hiện |
| E19 | `PATCH` sửa tên khi đang `ACTIVE` | ❌ `409` *"Hide collection before editing"* |
| E20 | `PUT /cover` khi đang `ACTIVE` | ❌ `409` |
| E21 | `PATCH /hide` → sửa → `PATCH /publish` | ✅ cả ba bước `200` |
| E22 | `PATCH /publish` khi BST chỉ còn sản phẩm ẩn | ❌ `409` |
| E23 | `PATCH /hide` khi đang `HIDDEN` | ❌ `409` *"Only an ACTIVE collection can be hidden"* |
| E24 | `DELETE` BST | ✅ `204`, liên kết sản phẩm bị xoá thật, BST còn lại ở trạng thái `DELETED` |
| E25 | `GET /collections/admin/{id}` sau khi xoá | ❌ `404` |

### E.4 Danh sách sản phẩm trong BST

| # | Hành động | Mong đợi |
|---|---|---|
| E26 | `PUT /products` đảo thứ tự `[C,A,B]` | ✅ `200`, `sortOrder` lần lượt 0,1,2 đúng thứ tự gửi |
| E27 | `PUT /products` bỏ bớt 1 sản phẩm | ✅ liên kết đó bị xoá |
| E28 | Ẩn một sản phẩm **đã nằm sẵn** trong BST rồi `PUT` lại kèm nó | ✅ `200` — được giữ |
| E29 | `PUT` **thêm mới** một sản phẩm đang ẩn | ❌ `409` |
| E30 | `GET /collections/slug/{slug}` công khai | ✅ chỉ thấy sản phẩm đang hiển thị, đúng thứ tự admin đặt |

---

## §F — Lọc, sắp xếp, phân trang (16 ca)

| # | Hành động | Mong đợi |
|---|---|---|
| F1 | `GET /products?size=5` | ✅ `content` 5 phần tử, `totalElements` đúng tổng |
| F2 | `?size=2000` | ✅ `200` nhưng **`size` trả về là `100`** 🆕 |
| F3 | `?size=0` | ✅ rơi về mặc định |
| F4 | `?page=9999` | ✅ `200`, `content` rỗng — **không phải 404** |
| F5 | `?sort=price,asc` | ✅ |
| F6 | `?sort=description` | ❌ `400`, message liệt kê field hợp lệ |
| F7 | `?sort=category.name` | ❌ `400` — chặn join ngầm |
| F8 | `?category=<slug ACTIVE>` | ✅ chỉ sản phẩm của danh mục đó |
| F9 | `?category=<slug HIDDEN>` | ✅ `200`, **trang rỗng** |
| F10 | `?category=khong-ton-tai` | ✅ `200`, trang rỗng |
| F11 | `?search=SUNSET` chữ hoa | ✅ vẫn khớp `Sunset` |
| F12 | `?search=` chuỗi rỗng | ✅ bỏ qua bộ lọc |
| F13 | `?minPrice=500000&maxPrice=100000` | ❌ `400` |
| F14 | `?minPrice=-1` | ❌ `400` |
| F15 | `?collection=<slug BST đang publish>` | ✅ đúng danh sách |
| F16 | `?collection=<slug BST đang HIDDEN>` | ✅ `200`, trang rỗng |

> **F2 là ca mới của bản 17/10.** Trước đây trả về tới 2000 bản ghi.

---

## §G — Dữ liệu cũ / trường hợp hiểm (8 ca)

> Cần sửa tay trong DB. Nhớ khôi phục sau khi test.

| # | Chuẩn bị | Hành động | Mong đợi |
|---|---|---|---|
| G1 | `UPDATE products SET status = NULL WHERE id = ...` | `GET /products` | ✅ sản phẩm **vẫn hiện** — null coi như ACTIVE |
| G2 | `UPDATE categories SET status = NULL WHERE id = ...` | `GET /categories` | ✅ danh mục **KHÔNG hiện** — khác với sản phẩm, cố ý |
| G3 | `UPDATE product_images SET sort_order = NULL WHERE id = ...` | `DELETE` một ảnh khác của cùng sản phẩm | ✅ `200`, **không NPE** 🆕 |
| G4 | như G3 | `PATCH` sản phẩm `{"thumbnail":""}` | ✅ `200`, không NPE 🆕 |
| G5 | Ảnh có `public_id = NULL` (ảnh seed) | `DELETE` ảnh đó | ✅ `200`, **không gọi Cloudinary** |
| G6 | Tắt mạng / khai sai `CLOUDINARY_API_KEY` | `POST .../images` | ❌ `502` hoặc `503`, **không tạo bản ghi rác trong DB** |
| G7 | như G6 | `PUT .../images/{id}` thay ảnh | ❌ lỗi, và ảnh cũ trong DB **giữ nguyên** |
| G8 | Hai tab cùng mở một BST | Tab 1 `PATCH` xong, tab 2 `PATCH` với version cũ | ❌ `409` ở tab 2 |

> **G3 và G4 là ca mới.** Trước bản 17/10 hai ca này ném `500` do so sánh `sortOrder` null.

---

## §H — Kiểm tra sau khi vá (bản 17/10)

Bảng đối chiếu nhanh những gì **đã đổi hành vi**:

| Ca | Trước 17/10 | Sau 17/10 |
|---|---|---|
| C1 — tạo sản phẩm không ảnh | `status = ACTIVE`, hiện trên shop với ô ảnh vỡ | **`status = HIDDEN`** |
| D8 — upload không set contentType | `415` | **`200`** |
| F2 — `?size=2000` | trả về tới 2000 bản ghi | **cắt còn 100** |
| G3/G4 — `sort_order` null | `500` NPE | **`200`** |
| C12b — thumbnail không phải URL | nhận, lưu vào DB | **`400`** |
| D22 — ảnh làm mất thumbnail | `409` báo nhầm *"last image"* | `409` **message đúng sự thật** |
| E20 — upload cover sai version | `409`, không tốn lượt upload | `409`, **có tốn một lượt upload** (ảnh được dọn tự động) |

### Chạy test tự động

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./mvnw -B clean test
```
Mong đợi: **`Tests run: 94, Failures: 0, Errors: 0, Skipped: 11`**
(11 ca skip là Testcontainers, cần Docker mới chạy.)

---

## §I — Tổng kết

| Nhóm | Số ca |
|---|---|
| §A Phân quyền | 12 |
| §B Danh mục | 18 |
| §C Sản phẩm | 28 |
| §D Upload ảnh | 22 |
| §E Bộ sưu tập | 30 |
| §F Lọc & phân trang | 16 |
| §G Dữ liệu cũ | 8 |
| **Tổng** | **134** |

Ưu tiên nếu không đủ thời gian chạy hết: **§A toàn bộ · C1 · C20 · D4 · D8 · D18 · E13 · E19 · E22 · F2 · F6 · G3**.
