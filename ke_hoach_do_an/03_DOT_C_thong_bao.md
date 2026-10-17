# ĐỢT C — Thông báo  (mục 7)

> Người làm đề xuất: **bạn kia** (sau khi xong Đợt B).
> Phụ thuộc: **cần Đợt A xong** nếu muốn demo trọn luồng *đặt hàng → nhận thông báo*.
> Quyết định đã chốt: **chỉ thông báo trong app, KHÔNG làm push FCM** (lý do ở file `00`).

---

## C.0 Phạm vi

Làm:
- Bảng thông báo trong DB, mỗi dòng thuộc về một tài khoản.
- API cho khách: xem danh sách, đếm số chưa đọc, đánh dấu đã đọc.
- Tự sinh thông báo khi **trạng thái đơn hàng đổi**.

Không làm (bây giờ):
- Push về điện thoại khi app đóng (FCM/APNs).
- Thông báo khuyến mãi gửi hàng loạt.
- Trung tâm cấu hình "tắt/bật từng loại thông báo".

> Thiết kế dưới đây **chừa sẵn đường cho FCM**: khi nào cần chỉ thêm bảng `device_tokens` và
> bắn thêm một lời gọi ngay sau chỗ ghi notification. Không phải đập đi làm lại.

---

## C.1 Nghiệp vụ

### Khi nào sinh thông báo

| Sự kiện | Tiêu đề | Nội dung |
|---|---|---|
| Đơn chuyển `CONFIRMED` | `Đơn hàng đã được xác nhận` | `Đơn {orderNumber} đã được xác nhận và đang được chuẩn bị.` |
| Đơn chuyển `SHIPPING` | `Đơn hàng đang giao` | `Đơn {orderNumber} đã được giao cho đơn vị vận chuyển.` |
| Đơn chuyển `COMPLETED` | `Giao hàng thành công` | `Đơn {orderNumber} đã giao thành công. Cảm ơn bạn!` |
| Đơn chuyển `CANCELLED` | `Đơn hàng đã huỷ` | `Đơn {orderNumber} đã được huỷ.` |

> **Không** sinh thông báo lúc vừa đặt (`PENDING`) — khách vừa bấm đặt xong, đang nhìn màn hình
> kết quả rồi, bắn thông báo ngay là thừa và gây cảm giác spam.

### Quy tắc

1. **Thông báo chỉ thuộc về một tài khoản.** Không có thông báo toàn hệ thống (chưa cần).
2. **Chỉ chủ nhân đọc được của mình.** Mọi endpoint đều là `/notifications/me/**`, lấy `accountId`
   từ token — **không bao giờ nhận `accountId` từ client**. Đây là lỗ hổng kinh điển.
3. **Đã đọc là một chiều.** Đánh dấu đã đọc rồi thì không có đường quay lại chưa đọc.
4. **Ghi thông báo không được làm hỏng việc chính.** Đổi trạng thái đơn mà ghi thông báo lỗi thì
   **đơn vẫn phải đổi trạng thái thành công**. Xem §C.4 — đây là điểm kỹ thuật quan trọng nhất của đợt này.
5. **`referenceId` trỏ tới thứ liên quan** (ở đây là `orderId`) để FE bấm vào thông báo là nhảy
   thẳng tới màn chi tiết đơn.

---

## C.2 Entity

```java
@Entity
@Table(
    name = "notifications",
    indexes = {
        // Màn thông báo luôn lọc theo chủ nhân rồi sắp mới nhất trước.
        @Index(name = "idx_notifications_account", columnList = "account_id, created_at"),
        // Badge đếm chưa đọc chạy rất thường xuyên, phải có index riêng.
        @Index(name = "idx_notifications_unread", columnList = "account_id, is_read")
    }
)
public class NotificationEntity extends BaseEntity {   // KHÔNG cần SoftDeletable
    private UUID id;
    private AccountEntity account;   // @ManyToOne(fetch = LAZY, optional = false)
    private NotificationType type;   // enum, @Enumerated(STRING)
    private String title;            // length=150, nullable=false
    private String content;          // length=500, nullable=false
    private UUID referenceId;        // nullable — id đơn hàng
    private boolean isRead;          // nullable=false, default false
    // createdAt kế thừa
}
```

```java
// enums/NotificationType.java
public enum NotificationType {
    ORDER_STATUS,   // đơn đổi trạng thái  ← dùng ngay
    PROMOTION,      // chừa sẵn
    SYSTEM          // chừa sẵn
}
```

> Dùng `BaseEntity` chứ không `SoftDeletableEntity`: thông báo không có khái niệm ẩn/hiện.
> Muốn cho khách xoá thông báo thì xoá cứng. Kiểm xem dự án có `BaseEntity` chưa, chưa thì
> khai `createdAt` thẳng trong entity.

---

## C.3 API

| # | Method | URL | Quyền | Việc |
|---|---|---|---|---|
| C-1 | `GET` | `/notifications/me` | đã đăng nhập | Danh sách của mình, mới nhất trước, phân trang |
| C-2 | `GET` | `/notifications/me/unread-count` | đã đăng nhập | Số chưa đọc — cho chấm đỏ trên icon |
| C-3 | `PATCH` | `/notifications/me/{id}/read` | đã đăng nhập | Đánh dấu một cái đã đọc |
| C-4 | `PATCH` | `/notifications/me/read-all` | đã đăng nhập | Đánh dấu tất cả đã đọc |
| C-5 | `DELETE` | `/notifications/me/{id}` | đã đăng nhập | Xoá một thông báo *(tuỳ chọn)* |

**Không có endpoint admin.** Admin không cần xem thông báo của khách.

### Chi tiết

**C-1** `GET /notifications/me?page=0&size=20&unreadOnly=false`
- Mặc định sắp `createdAt` giảm dần. **Không cho client đổi sort** — màn thông báo chỉ có một
  thứ tự hợp lý. (Giống cách trang BST công khai đang làm.)
- `unreadOnly=true` để FE làm tab "Chưa đọc".
- Trả `PageResponse<NotificationResponse>`.

**C-2** `GET /notifications/me/unread-count` → `{ "unreadCount": 5 }`
- FE gọi cái này mỗi lần mở app và sau mỗi lần kéo làm mới.
- Dùng `countByAccountIdAndIsReadFalse(UUID)` — derived query, có index lo.

**C-3** `PATCH /notifications/me/{id}/read`
- **Phải kiểm quyền sở hữu**: tìm theo `findByIdAndAccountId(id, me)`, không thấy → `404`.
  Trả `404` chứ **không** `403`, để không lộ rằng thông báo đó tồn tại của người khác.
- Đã đọc rồi mà gọi lại → `200`, không đổi gì (idempotent, FE gọi lại thoải mái).

**C-4** `PATCH /notifications/me/read-all`
- Một câu `UPDATE` hàng loạt, không tải từng bản ghi:
  ```java
  @Modifying
  @Query("UPDATE NotificationEntity n SET n.isRead = true " +
         "WHERE n.account.id = :accountId AND n.isRead = false")
  int markAllRead(@Param("accountId") UUID accountId);
  ```
  ⚠️ Đây là **một trong số ít chỗ `@Query` chính đáng** — derived query không làm được update
  hàng loạt. Phải giải trình đúng như luật base_rule #5.
- Trả về số dòng đã đổi: `{ "updatedCount": 5 }`.

### DTO

```java
// response/NotificationResponse.java  — phẳng
public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String content,
        UUID referenceId,
        boolean isRead,
        Instant createdAt
) {}
```

---

## C.4 🔴 Điểm kỹ thuật quan trọng nhất: nối vào luồng đơn hàng

Chỗ nối là `OrderServiceImpl` — method đổi trạng thái (`PATCH /orders/{id}/status`).

### Làm đúng

```java
// trong OrderServiceImpl, sau khi đã đổi trạng thái thành công
notificationService.notifyOrderStatusChanged(order);
```

Và `notifyOrderStatusChanged` **phải không được làm hỏng việc đổi trạng thái**.

Ba cách, chọn **cách 2**:

| | Cách | Đánh giá |
|---|---|---|
| 1 | Gọi thẳng trong cùng transaction | ❌ Ghi notification lỗi → rollback luôn việc đổi trạng thái đơn. Sai hoàn toàn về nghiệp vụ. |
| 2 | **Gọi trong cùng transaction nhưng bọc try/catch, nuốt lỗi + ghi log** | ✅ **Chọn cái này.** Đơn giản, đơn vẫn đổi được, mất thông báo thì chỉ log lại. |
| 3 | `@TransactionalEventListener(AFTER_COMMIT)` | Đẹp nhất về kiến trúc nhưng thêm event + listener, và luật base_rule #3 nói không tự thêm pattern mới. **Để dành**, không làm bây giờ. |

```java
// Trong NotificationServiceImpl
@Override
@Transactional
public void notifyOrderStatusChanged(OrderEntity order) {
    try {
        // ... dựng entity và save
    } catch (RuntimeException exception) {
        // Thông báo hỏng KHÔNG được kéo đổ việc đổi trạng thái đơn — đó mới là việc chính.
        log.warn("Could not create order notification; orderId={}, status={}",
                 order.getId(), order.getStatus(), exception);
    }
}
```

> ⚠️ Đây là **một trong số ít chỗ log hợp lệ** theo luật dự án: exception bị nuốt hẳn, không log
> thì thông báo mất tích mà không ai biết. Giống `CloudinaryService.deleteQuietly`.

### Chú ý về khách vãng lai

Kiểm xem `OrderEntity.account` có thể `null` không (đặt hàng không đăng nhập). Nếu có thì
**phải bỏ qua, không ném lỗi**:
```java
if (order.getAccount() == null) {
    return;   // khách vãng lai không có chỗ nào để nhận thông báo
}
```

---

## C.5 Checklist test

| # | Ca | Mong đợi |
|---|---|---|
| 1 | Admin đổi đơn sang `CONFIRMED` | chủ đơn có thêm 1 thông báo `ORDER_STATUS` |
| 2 | `referenceId` của thông báo đó | bằng `order.id` |
| 3 | Khách A gọi `/notifications/me` | chỉ thấy của A, **không thấy của B** |
| 4 | Không có token | `401` |
| 5 | `unread-count` khi có 3 chưa đọc | `{"unreadCount": 3}` |
| 6 | `PATCH /me/{id}/read` | `200`, `unread-count` giảm 1 |
| 7 | `PATCH` lại chính nó | `200`, số không đổi |
| 8 | `PATCH /me/{id}/read` với id của người khác | `404` (**không phải 403**) |
| 9 | `read-all` khi có 5 chưa đọc | `{"updatedCount": 5}`, count về 0 |
| 10 | `read-all` khi đã đọc hết | `{"updatedCount": 0}`, không lỗi |
| 11 | `?unreadOnly=true` | chỉ trả cái chưa đọc |
| 12 | Thứ tự mặc định | mới nhất đứng đầu |
| 13 | **Giả lập ghi thông báo lỗi** (mock ném exception) | **đơn vẫn đổi trạng thái thành công** |
| 14 | Đơn của khách vãng lai (`account = null`) đổi trạng thái | không lỗi, không tạo thông báo |

**Ca 13 và 14 là hai ca quan trọng nhất** — chúng kiểm đúng cái dễ làm sai nhất của đợt này.

---

## C.6 File sẽ tạo / sửa

**Tạo**
```
features/notification/
├── controller/NotificationController.java
├── dto/response/NotificationResponse.java
├── entity/NotificationEntity.java
├── enums/NotificationType.java
├── mapper/NotificationMapper.java
├── repository/NotificationRepository.java
├── service/NotificationService.java
└── service/impl/NotificationServiceImpl.java

src/test/.../notification/service/NotificationServiceImplTest.java
```

**Sửa**
```
features/order/service/impl/OrderServiceImpl.java   + 1 lời gọi sau khi đổi trạng thái
                                                    + inject NotificationService
```

⚠️ `OrderServiceImpl` là file **bro đang nắm**, mà Đợt C lại giao bạn kia. Hai người sửa cùng file
là nguồn conflict. **Cách tránh:** bạn kia làm xong toàn bộ module notification và để lại đúng
**một dòng TODO** ở chỗ cần nối; bro ghép dòng đó vào lúc merge. Hoặc đơn giản hơn: bro làm riêng
phần nối, coi như sub-task của Đợt A/E.

---

## C.7 ❓ Cần chốt

**Q1 — Có cho khách xoá thông báo không (C-5)?**
- 👉 **Mình đề xuất CÓ, vì rẻ** — một endpoint, một dòng `deleteByIdAndAccountId`. Màn thông báo
  mà không xoá được thì trông thiếu. Nhưng để cuối cùng, làm sau khi C-1→C-4 đã xanh.

**Q2 — Có sinh thông báo lúc đặt hàng thành công (`PENDING`) không?**
- 👉 **Mình đề xuất KHÔNG.** Khách vừa bấm đặt, đang nhìn màn "đặt hàng thành công" — bắn thông báo
  ngay lúc đó là thừa. Thông báo có ý nghĩa khi khách **không** đang nhìn màn hình.

**Q3 — Thông báo cho admin khi có đơn mới?**
- 👉 **Mình đề xuất KHÔNG làm bây giờ.** Đề bài chấm màn thông báo của app khách. Admin có màn
  danh sách đơn rồi. Ghi vào sổ nợ, dư thời gian thì thêm.
