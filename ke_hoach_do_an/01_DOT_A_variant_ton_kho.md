# ĐỢT A — CRUD variant + tồn kho  🔴 ƯU TIÊN 1

> **Gỡ chặn mục 4 (chi tiết sản phẩm), 5 (giỏ hàng), 6 (thanh toán).**
> Người làm đề xuất: **bro** (vì dính chặt vào `order` mà bro đã nắm).
> Phụ thuộc: không. Làm được ngay.

---

## A.0 Hiện trạng — cái gì đã có, cái gì thiếu

### Đã có sẵn, không phải làm lại

| Thứ | Ở đâu | Ghi chú |
|---|---|---|
| `ProductVariantEntity` | `features/product/entity/` | `product`, `size`, `stockQuantity`, `version` |
| `ProductSize` | `features/product/enums/` | chỉ **S, M, L** → tối đa 3 variant/sản phẩm |
| `ProductVariantResponse` | `features/product/dto/response/` | đã lộ ra `ProductDetailResponse.variants` |
| `ProductVariantRepository` | `features/product/repository/` | order đang dùng |
| **Trừ kho khi đặt** | `OrderServiceImpl:288` | `setStockQuantity(stock - quantity)` |
| **Hoàn kho khi huỷ** | `OrderServiceImpl:244` | `setStockQuantity(stock + quantity)` |
| **Chặn mua quá tồn** | `OrderServiceImpl:284` | `409 "Only N left"` |
| `@Version` chống đua | `ProductVariantEntity.version` | hai người mua cùng lúc không âm kho |

### Thiếu đúng một thứ

**Không có API nào tạo/sửa/xoá variant.** Toàn bộ tồn kho hiện chỉ đến từ seed.

```
Admin tạo sản phẩm mới
  → sản phẩm có 0 variant
  → FE hiện 0 nút size
  → khách không bấm mua được
  → mục 4, 5, 6 demo không trọn
```

---

## A.1 Nghiệp vụ cần đúng

### Quy tắc

1. **Một sản phẩm có tối đa 3 variant**, mỗi size một cái. Không được 2 variant cùng size.
2. **Tồn kho không bao giờ âm.** Đặt hàng đã chặn rồi; sửa tay cũng phải chặn.
3. **Sản phẩm ACTIVE nên có ít nhất 1 variant còn hàng** — nếu không thì khách vào xem rồi
   không mua được, trải nghiệm tệ. (Xem câu hỏi Q1 cuối file: ép hay chỉ cảnh báo.)
4. **Xoá variant phải an toàn với đơn hàng.** Đơn cũ đã lưu `size` trong `order_items`;
   nhưng đơn **đang chạy** (PENDING/CONFIRMED/SHIPPING) sẽ cần hoàn kho nếu bị huỷ —
   xoá mất variant thì hoàn kho vào đâu?
5. **Sửa tồn kho phải chống đua** giống Collection: hai admin cùng mở form, người sau không
   được ghi đè số của người trước mà không biết.

### Luồng đầy đủ: tạo một sản phẩm bán được

```
1. POST   /products/admin                      → tạo, status = HIDDEN (chưa ảnh)
2. POST   /products/admin/{id}/images          → ảnh đầu tự thành thumbnail
3. POST   /products/admin/{id}/variants        → size S, tồn 10      ← ĐỢT A
4. POST   /products/admin/{id}/variants        → size M, tồn 15      ← ĐỢT A
5. PATCH  /products/admin/{id}  {status:ACTIVE}→ lên kệ
6. GET    /products/slug/{slug}                → khách thấy 2 nút size
7. POST   /orders                              → mua, kho S còn 9
```

Bước 3–4 là thứ đang thiếu. Thêm xong là chuỗi này chạy liền mạch.

---

## A.2 API cần làm

| # | Method | URL | Quyền | Việc |
|---|---|---|---|---|
| A-1 | `POST` | `/products/admin/{id}/variants` | ADMIN | Thêm một size + tồn kho |
| A-2 | `PATCH` | `/products/admin/{id}/variants/{variantId}` | ADMIN | Sửa tồn kho |
| A-3 | `DELETE` | `/products/admin/{id}/variants/{variantId}` | ADMIN | Gỡ một size |
| A-4 | `GET` | `/products/admin/{id}/variants` | ADMIN | Liệt kê (gồm cả size hết hàng) |

> Khách **không cần** endpoint riêng: `GET /products/slug/{slug}` đã trả `variants` rồi.

Tất cả trả về `ProductDetailResponse` (giống nhóm ảnh đang làm) để FE **cập nhật nguyên màn
bằng một response**, không phải gọi thêm lần nữa. Riêng A-4 trả `List<ProductVariantResponse>`.

### DTO

```java
// dto/request/ProductVariantRequest.java   — dùng cho A-1
public record ProductVariantRequest(
        @NotNull(message = "Size is required")
        ProductSize size,

        @NotNull(message = "Stock quantity is required")
        @Min(value = 0, message = "Stock quantity must be zero or greater")
        @Max(value = 100000, message = "Stock quantity must not exceed 100000")
        Integer stockQuantity
) {}
```

```java
// dto/request/ProductVariantUpdateRequest.java   — dùng cho A-2
public record ProductVariantUpdateRequest(
        @NotNull(message = "Stock quantity is required")
        @Min(value = 0, message = "Stock quantity must be zero or greater")
        @Max(value = 100000, message = "Stock quantity must not exceed 100000")
        Integer stockQuantity,

        @NotNull(message = "Expected version is required")
        @PositiveOrZero(message = "Expected version must be zero or greater")
        Long expectedVersion
) {}
```

**Không cho sửa `size`.** Đổi size của một variant đang có tồn kho và đang nằm trong đơn là vô
nghĩa — muốn đổi thì xoá rồi tạo lại. Giữ `size` bất biến làm mọi thứ đơn giản hơn nhiều.

**`ProductVariantResponse` phải có thêm `version`** để FE gửi lại ở A-2. Kiểm xem record hiện tại
đã có chưa, chưa thì thêm.

---

## A.3 Chi tiết từng sub-task

### A-1 · Thêm variant

```
POST /products/admin/{id}/variants
{ "size": "S", "stockQuantity": 10 }
```

Các bước trong service:
1. `requireManageableForWrite(id)` — khoá bi quan, chặn sản phẩm DELETED (dùng lại hàm sẵn có).
2. Nếu sản phẩm đã có variant cùng `size` → `409 "Variant already exists for size: S"`.
3. Tạo entity, `product.getVariants().add(variant)`, `saveAndFlush`.
4. Trả `productMapper.toDetailResponse(product)`.

| Tình huống | Kết quả |
|---|---|
| Size hợp lệ, chưa tồn tại | `201` + chi tiết sản phẩm có variant mới |
| Size đã tồn tại | `409` |
| `size` sai chính tả (vd `"XL"`) | `400` — Jackson không parse được enum |
| `stockQuantity` âm | `400` |
| `stockQuantity = 0` | `201` ✅ hợp lệ — nghĩa là "có size này nhưng tạm hết" |
| `id` sản phẩm không tồn tại / đã xoá | `404` |
| Thiếu token / không phải ADMIN | `401` / `403` |

### A-2 · Sửa tồn kho

```
PATCH /products/admin/{id}/variants/{variantId}
{ "stockQuantity": 25, "expectedVersion": 3 }
```

1. `requireManageableForWrite(id)`.
2. Tìm variant trong `product.getVariants()` theo `variantId` → không có thì `404`.
   **Phải tìm trong danh sách của chính sản phẩm đó**, không `variantRepository.findById` trực tiếp
   — nếu không, truyền `variantId` của sản phẩm khác sẽ sửa nhầm.
3. So `expectedVersion` với `variant.getVersion()` → lệch thì `409 "Variant has changed; refresh
   before retrying with the latest version"` (y hệt câu chữ Collection đang dùng, để FE xử lý chung).
4. `setStockQuantity`, `saveAndFlush`.

| Tình huống | Kết quả |
|---|---|
| Version đúng | `200`, `version` tăng 1 |
| Version cũ | `409` — **FE không được tự thử lại**, phải tải lại rồi hỏi người dùng |
| `variantId` thuộc sản phẩm khác | `404` |
| Đặt về 0 | `200` ✅ — cách chính thức để "tạm hết hàng" |

> 💡 Nếu thấy `expectedVersion` phiền cho admin, vẫn **phải giữ**. Tình huống thật: hai admin cùng
> nhập hàng về, một người gõ 50, một người gõ 30, không có version thì kho thành 30 và mất 50 cái
> mà không ai biết.

### A-3 · Xoá variant

```
DELETE /products/admin/{id}/variants/{variantId}
```

Đây là chỗ dễ sai nhất. Quy tắc:

1. `requireManageableForWrite(id)`, tìm variant trong sản phẩm → `404` nếu không có.
2. **Chặn nếu size này đang nằm trong đơn chưa kết thúc** (`PENDING`, `CONFIRMED`, `SHIPPING`):
   `409 "Cannot remove a size that still has open orders"`.
   Lý do: đơn đó bị huỷ sẽ phải hoàn kho về variant này. Xoá mất thì hoàn vào đâu?
   Cần thêm vào `OrderItemRepository`:
   ```java
   boolean existsByProductIdAndSizeAndOrderStatusIn(UUID productId, ProductSize size,
                                                    Collection<OrderStatus> statuses);
   ```
3. Đơn **đã xong hoặc đã huỷ** thì không chặn — `order_items` đã lưu tên, giá, size thành bản chụp
   riêng, xoá variant không làm hỏng lịch sử.
4. **Xoá cứng**, không soft-delete. Variant không có `status` và không phải thứ cần giữ lịch sử.
5. Nếu sau khi xoá sản phẩm còn 0 variant và đang ACTIVE → xem Q1.

| Tình huống | Kết quả |
|---|---|
| Không dính đơn nào | `200` + chi tiết sản phẩm |
| Dính đơn PENDING/CONFIRMED/SHIPPING | `409` |
| Chỉ dính đơn COMPLETED/CANCELLED | `200` ✅ |
| Xoá cái cuối của sản phẩm ACTIVE | theo Q1 |

### A-4 · Liệt kê variant

```
GET /products/admin/{id}/variants
```
Đơn giản nhất, trả cả size tồn 0 (khác với màn khách). Không phân trang — tối đa 3 phần tử.

---

## A.4 Checklist test (viết vào `ProductVariantServiceTest`)

Bám khuôn `ProductImageServiceTest` đang có.

| # | Ca | Mong đợi |
|---|---|---|
| 1 | Thêm size S cho sản phẩm chưa có variant | `201`, `variants` có 1 phần tử |
| 2 | Thêm S lần nữa | `409` |
| 3 | Thêm M sau khi đã có S | `201`, 2 phần tử |
| 4 | Thêm với `stockQuantity = 0` | `201` |
| 5 | Thêm với `stockQuantity = -1` | `400` |
| 6 | Thêm vào sản phẩm đã xoá mềm | `404` |
| 7 | Sửa tồn kho, version đúng | `200`, version +1 |
| 8 | Sửa lại bằng version cũ | `409` |
| 9 | Sửa bằng `variantId` của sản phẩm khác | `404` |
| 10 | Xoá variant không dính đơn | `200` |
| 11 | Xoá variant đang trong đơn PENDING | `409` |
| 12 | Xoá variant chỉ dính đơn COMPLETED | `200` |
| 13 | Đặt hàng size vừa hết (`stock = 0`) | `409 "Only 0 left"` |
| 14 | Đặt hàng size không tồn tại | `404`/`409` — kiểm lại xem order đang trả mã nào |

**Ca 14 cần soát luôn**: hiện `buildItem` ném lỗi khi không tìm thấy variant — xem câu chữ có dễ
hiểu cho FE không, tiện thể sửa luôn nếu khó hiểu.

### Test tay trên Swagger

```
1. POST /products/admin            {name, sku, price, categoryId}   → HIDDEN
2. POST /products/admin/{id}/images + file                          → có thumbnail
3. GET  /products/admin/{id}                                        → variants = []
4. POST /products/admin/{id}/variants  {"size":"S","stockQuantity":3}
5. PATCH /products/admin/{id}      {"status":"ACTIVE"}
6. GET  /products/slug/{slug}      (không token)                    → thấy size S, tồn 3
7. POST /orders                    {productId, size:"S", quantity:3}
8. GET  /products/slug/{slug}                                       → tồn 0
9. POST /orders                    size S, quantity 1               → 409 "Only 0 left"
10. PATCH /orders/{id}/status      {"status":"CANCELLED"}
11. GET /products/slug/{slug}                                       → tồn về 3  ✅ hoàn kho
```

Bước 11 là bước chứng minh vòng đời tồn kho khép kín.

---

## A.5 File sẽ đụng

**Tạo mới**
```
features/product/dto/request/ProductVariantRequest.java
features/product/dto/request/ProductVariantUpdateRequest.java
src/test/.../product/service/ProductVariantServiceTest.java
```

**Sửa**
```
features/product/controller/ProductController.java      + 4 endpoint
features/product/service/ProductService.java            + 4 method
features/product/service/impl/ProductServiceImpl.java   + 4 method + helper requireVariant()
features/product/mapper/ProductMapper.java              + toVariantResponse nếu chưa có
features/product/dto/response/ProductVariantResponse.java  + field version nếu chưa có
features/order/repository/OrderItemRepository.java      + existsByProductIdAndSizeAndOrderStatusIn
```

⚠️ `ProductServiceImpl` đang ~430 dòng, thêm 4 method nữa là ~550. **Vẫn nên để chung**, đừng tách
`ProductVariantService` riêng — variant không sống độc lập khỏi product, và mọi thao tác đều bắt đầu
bằng `requireManageableForWrite(productId)` đã nằm sẵn ở đây. Tách ra là phải nhân bản helper, đúng
cái bẫy đã gặp hồi làm auth.

---

## A.6 ❓ Bốn thứ cần bro chốt trước khi gõ

**Q1 — Sản phẩm ACTIVE mà không còn variant nào thì xử sao?**
- (a) **Ép HIDDEN tự động**, giống luật "không thumbnail thì không lên kệ" vừa làm.
- (b) **Chặn `409`**, bắt admin ẩn sản phẩm trước rồi mới xoá variant cuối.
- (c) Kệ, cho ACTIVE với 0 size.
- 👉 **Mình nghiêng (b).** Nhất quán với `removeImage` vừa sửa (xoá ảnh cuối của sản phẩm ACTIVE
  thì `409`). Admin hiểu ngay mình đang làm gì. (a) thì sản phẩm tự biến mất khỏi shop mà admin
  không hay. (c) thì khách vào xem rồi không mua được.

**Q2 — Sản phẩm ACTIVE mà tất cả variant đều tồn 0?**
- 👉 **Mình đề xuất CHO PHÉP, không chặn.** Đây là "tạm hết hàng" — chuyện bình thường của shop.
  Khách vẫn nên xem được sản phẩm, chỉ là nút mua bị mờ. Chặn cái này là bắt admin ẩn sản phẩm mỗi
  lần cháy hàng, rất phiền. FE tự xử: `variants.every(v => v.stockQuantity == 0)` → hiện "Hết hàng".

**Q3 — Có cần endpoint cộng/trừ kho (`+10`, `-3`) thay vì gán thẳng không?**
- 👉 **Mình đề xuất KHÔNG làm bây giờ.** Gán thẳng + `expectedVersion` đã đủ an toàn và đủ demo.
  Cộng/trừ là nghiệp vụ nhập kho thật, chưa cần ở phạm vi đồ án. Ghi vào sổ nợ, cần thì thêm sau.

**Q4 — Tạo sản phẩm kèm luôn variant trong một lần gọi (`POST /products/admin` nhận mảng variants)?**
- 👉 **Mình đề xuất KHÔNG.** Lý do: `ProductRequest` đang phẳng đúng chuẩn, nhét mảng vào là phá
  quy ước DTO. Mà luồng thật vẫn phải tạo sản phẩm trước rồi mới upload ảnh được, nên admin dù sao
  cũng phải gọi nhiều lần. Thêm 1 lần gọi nữa không làm FE khổ hơn.
