# ĐỢT D — Nhắn tin  (mục 9)

> **Đợt nặng nhất** — tốn gấp đôi B hoặc C. Làm sau cùng trong nhóm tính điểm.
> Người làm đề xuất: **làm đôi** — một người backend, một người Flutter, ghép sớm.
> Quyết định đã chốt: **khách ↔ admin, dùng polling, KHÔNG WebSocket** (lý do ở file `00`).

---

## D.0 Phạm vi

Làm:
- Khách nhắn với shop (phía shop là admin trả lời).
- Mỗi khách có **đúng một** hội thoại với shop.
- Admin thấy danh sách hội thoại, vào từng cái trả lời.
- Đánh dấu đã đọc, đếm tin chưa đọc.

Không làm:
- Khách nhắn với khách.
- Nhóm chat.
- Gửi ảnh / file trong chat *(xem Q2)*.
- WebSocket, "đang gõ…", đã xem lúc mấy giờ.

---

## D.1 Vì sao polling là đủ

Màn chat của khách mở ra → cứ **3 giây** gọi một lần:

```
GET /conversations/me/messages?since=2026-10-17T09:30:00Z
```

- Không có tin mới → trả mảng rỗng, tốn gần như không gì (có index lo).
- Có tin mới → trả về, FE nối vào cuối danh sách.
- Người dùng **không phân biệt được** với real-time.
- Chỉ poll khi màn chat đang mở. Đóng màn là dừng.

So với WebSocket: không phải xác thực trên kênh socket, không phải xử lý mất kết nối và nối lại,
không phải bật khối `socket` đang comment trong `application.yaml`. **Tiết kiệm nguyên một đợt**
mà điểm bằng nhau.

> Tham số `since` chính là thứ làm polling rẻ. **Thiết kế nó ngay từ đầu**, đừng để FE tải lại cả
> danh sách mỗi 3 giây.

---

## D.2 Mô hình dữ liệu

```java
@Entity
@Table(
    name = "conversations",
    // Mỗi tài khoản đúng một hội thoại với shop.
    uniqueConstraints = @UniqueConstraint(name="uk_conversations_account", columnNames="account_id"),
    indexes = @Index(name = "idx_conversations_last_message", columnList = "last_message_at")
)
public class ConversationEntity extends BaseEntity {
    private UUID id;
    private AccountEntity account;      // @ManyToOne(LAZY, optional=false) — phía khách
    private Instant lastMessageAt;      // để admin sắp hội thoại mới nhất lên đầu
    private String lastMessagePreview;  // length=200 — khỏi join bảng tin khi liệt kê
    private int unreadByCustomer;       // khách chưa đọc bao nhiêu
    private int unreadByAdmin;          // admin chưa đọc bao nhiêu
}
```

```java
@Entity
@Table(
    name = "chat_messages",
    // Truy vấn xương sống: lấy tin của một hội thoại, mới hơn một mốc.
    indexes = @Index(name = "idx_chat_messages_conversation",
                     columnList = "conversation_id, created_at")
)
public class ChatMessageEntity extends BaseEntity {
    private UUID id;
    private ConversationEntity conversation;  // @ManyToOne(LAZY, optional=false)
    private SenderType senderType;            // CUSTOMER | ADMIN
    private AccountEntity sender;             // ai gõ — admin nào trả lời
    private String content;                   // length=2000, nullable=false
}
```

```java
public enum SenderType { CUSTOMER, ADMIN }
```

### Vì sao có `lastMessagePreview` và `unreadBy*` (dữ liệu lặp)

Đây là **cố ý lặp dữ liệu để tránh truy vấn nặng**, phải giải trình rõ:
- Màn danh sách hội thoại của admin cần hiện *"tin cuối + số chưa đọc"* cho mỗi dòng.
  Không có 3 field này thì mỗi dòng phải chạy thêm 2 truy vấn phụ → **N+1 kinh điển**.
- Đổi lại: mỗi lần gửi tin phải cập nhật cả hội thoại. Chấp nhận được, vì gửi ít hơn đọc rất nhiều.

> ⚠️ Lặp dữ liệu là con dao hai lưỡi: **mọi đường ghi tin nhắn đều phải cập nhật 3 field này**.
> Chỉ được gửi tin qua **một** method duy nhất trong service, không viết đường ghi thứ hai.

---

## D.3 API

### Phía khách

| # | Method | URL | Việc |
|---|---|---|---|
| D-1 | `GET` | `/conversations/me` | Lấy hội thoại của mình, **tự tạo nếu chưa có** |
| D-2 | `GET` | `/conversations/me/messages?since=&page=&size=` | Lấy tin |
| D-3 | `POST` | `/conversations/me/messages` | Gửi tin |
| D-4 | `PATCH` | `/conversations/me/read` | Đánh dấu đã đọc hết |

### Phía admin

| # | Method | URL | Việc |
|---|---|---|---|
| D-5 | `GET` | `/conversations/admin` | Danh sách hội thoại, mới nhất trước |
| D-6 | `GET` | `/conversations/admin/{id}/messages` | Tin của một hội thoại |
| D-7 | `POST` | `/conversations/admin/{id}/messages` | Trả lời |
| D-8 | `PATCH` | `/conversations/admin/{id}/read` | Admin đánh dấu đã đọc |
| D-9 | `GET` | `/conversations/admin/unread-count` | Tổng hội thoại đang có tin chưa đọc |

**9 endpoint.**

### Chi tiết mấy cái dễ sai

**D-1 — "tự tạo nếu chưa có"**
Khách mở màn chat lần đầu thì chưa có hội thoại nào. Hai lựa chọn:
- ❌ Trả `404`, bắt FE gọi thêm `POST /conversations` để tạo → thừa một vòng, và FE phải xử lý
  trạng thái "chưa có hội thoại".
- ✅ **`GET` tự tạo nếu chưa có, luôn trả `200`.** FE chỉ cần gọi một lần là có chỗ để gõ.

⚠️ Hơi lệch chuẩn REST (GET mà ghi DB). **Chấp nhận có ý thức**, phải ghi javadoc giải thích.
Dùng `@Transactional` (không `readOnly`). Nếu hai request vào cùng lúc, ràng buộc `unique` trên
`account_id` sẽ chặn — bắt `DataIntegrityViolationException` rồi đọc lại.

**D-2 — tham số `since`**
```
GET /conversations/me/messages?since=2026-10-17T09:30:00Z
```
- Có `since` → chỉ trả tin `createdAt > since`, sắp **tăng dần**, **không phân trang** (chỉ vài tin).
- Không `since` → trả trang đầu lịch sử, sắp **giảm dần**, có phân trang (để cuộn ngược lên).

> Hai chế độ trong một endpoint. Phải ghi rõ trong tài liệu FE, nếu không FE sẽ hiển thị ngược thứ tự.
> Nếu thấy khó hiểu thì tách thành 2 endpoint riêng — chấp nhận được.

**D-3 — gửi tin**
```json
{ "content": "Áo này còn size M không shop?" }
```
Các bước:
1. Lấy/tạo hội thoại của mình.
2. Lưu tin `senderType = CUSTOMER`.
3. Cập nhật hội thoại: `lastMessageAt`, `lastMessagePreview` (cắt 200 ký tự),
   `unreadByAdmin += 1`, và **`unreadByCustomer = 0`** (mình vừa gõ thì coi như đã đọc hết).
4. Trả về `ChatMessageResponse` của tin vừa gửi.

**D-7 — admin trả lời**: y hệt nhưng đảo chiều, `senderType = ADMIN`, `unreadByCustomer += 1`,
`unreadByAdmin = 0`, và lưu `sender` = admin đang đăng nhập.

> Bước 3 ở D-3 và D-7 **giống nhau tới 90%** → viết **một** hàm private dùng chung,
> nhận `SenderType`. Đừng chép hai bản.

### DTO

```java
// request/ChatMessageRequest.java
public record ChatMessageRequest(
        @NotBlank(message = "Message content is required")
        @Size(max = 2000, message = "Message must not exceed 2000 characters")
        String content
) {}
```

```java
// response/ChatMessageResponse.java
public record ChatMessageResponse(
        UUID id, SenderType senderType, String senderName,
        String content, Instant createdAt
) {}

// response/ConversationResponse.java  — cho khách
public record ConversationResponse(
        UUID id, Instant lastMessageAt, String lastMessagePreview,
        int unreadCount, Instant createdAt
) {}

// response/ConversationSummaryResponse.java  — cho admin, thêm thông tin khách
public record ConversationSummaryResponse(
        UUID id, UUID accountId, String customerName, String customerEmail,
        Instant lastMessageAt, String lastMessagePreview,
        int unreadByAdmin
) {}
```

> `customerName` lấy từ `profiles`. Nhớ fetch join để không N+1 ở màn danh sách admin — đúng cách
> `CollectionProductRepository` đang dùng `@EntityGraph`.

---

## D.4 Edge case

| Tình huống | Kết quả |
|---|---|
| Khách mở chat lần đầu | `200`, hội thoại được tạo ngầm |
| Hai request `GET /conversations/me` cùng lúc | chỉ 1 hội thoại được tạo (unique chặn) |
| Gửi tin rỗng / chỉ khoảng trắng | `400` |
| Gửi tin > 2000 ký tự | `400` |
| Admin mở hội thoại `{id}` không tồn tại | `404` |
| Khách gọi `/conversations/admin` | `403` |
| Khách gọi `/conversations/admin/{id}/messages` với id người khác | `403` (chặn ở `@PreAuthorize`) |
| `since` sai định dạng | `400` |
| `since` ở tương lai | `200`, mảng rỗng |
| Khách gửi tin → `unreadByAdmin` | +1 |
| Admin đọc → `unreadByAdmin` | về 0 |
| Khách tự gửi tin | `unreadByCustomer` về 0 |
| Tài khoản bị khoá gửi tin | `401`/`403` (chuỗi filter lo) |

---

## D.5 Checklist test

| # | Ca | Mong đợi |
|---|---|---|
| 1 | `GET /conversations/me` lần đầu | `200`, tạo mới |
| 2 | Gọi lại lần 2 | `200`, **cùng id**, không tạo thêm |
| 3 | Khách gửi 1 tin | hội thoại có `lastMessagePreview` đúng, `unreadByAdmin = 1` |
| 4 | Admin `GET /conversations/admin` | thấy hội thoại đó, đúng preview |
| 5 | Admin trả lời | `unreadByCustomer = 1`, `unreadByAdmin = 0` |
| 6 | Khách `GET messages` không `since` | thấy cả 2 tin, mới nhất trước |
| 7 | Khách `GET messages?since=<trước tin admin>` | chỉ thấy tin của admin |
| 8 | Khách `PATCH /me/read` | `unreadByCustomer = 0` |
| 9 | Khách gửi tin nội dung rỗng | `400` |
| 10 | Khách gọi endpoint admin | `403` |
| 11 | Khách A đọc hội thoại của B | `403`/`404` |
| 12 | Admin `unread-count` khi 3 hội thoại có tin mới | `3` |
| 13 | `lastMessagePreview` với tin 500 ký tự | cắt còn 200, không lỗi |
| 14 | Màn danh sách admin với 20 hội thoại | **không N+1** — kiểm bằng đếm số câu SQL |

---

## D.6 File sẽ tạo

```
features/chat/
├── controller/ConversationController.java
├── dto/request/ChatMessageRequest.java
├── dto/response/ChatMessageResponse.java
├── dto/response/ConversationResponse.java
├── dto/response/ConversationSummaryResponse.java
├── entity/ConversationEntity.java
├── entity/ChatMessageEntity.java
├── enums/SenderType.java
├── mapper/ChatMapper.java
├── repository/ConversationRepository.java
├── repository/ChatMessageRepository.java
├── service/ConversationService.java
└── service/impl/ConversationServiceImpl.java

src/test/.../chat/service/ConversationServiceImplTest.java
```

Module độc lập, **không sửa file của module nào khác** → chia cho ai cũng được, không conflict.

### Chia nhỏ để làm dần

| Bước | Nội dung | Gate |
|---|---|---|
| D-a | 2 entity + 2 repository + migration chạy được | build xanh |
| D-b | Phía khách: D-1, D-2, D-3 | ✋ dừng, demo gửi/nhận bằng Swagger |
| D-c | Phía admin: D-5, D-6, D-7 | ✋ dừng, demo 2 chiều |
| D-d | Đã đọc + đếm chưa đọc: D-4, D-8, D-9 | ✋ dừng |
| D-e | Test + tài liệu FE | xanh |

**Làm được tới D-c là đã demo được mục 9.** D-d là phần đánh bóng.

---

## D.7 ❓ Cần chốt

**Q1 — Hội thoại là "mỗi khách một cái với shop", hay "mỗi khách nhiều cuộc theo chủ đề"?**
- 👉 **Mình đề xuất mỗi khách đúng một.** Giống Zalo OA, Messenger của fanpage — khách nhắn shop
  thì chỉ có một luồng. Nhiều cuộc theo chủ đề là mô hình ticket hỗ trợ, phức tạp hơn hẳn và
  không thêm điểm. Ràng buộc `unique` trên `account_id` thể hiện đúng lựa chọn này.

**Q2 — Cho gửi ảnh trong chat không?**
- 👉 **Mình đề xuất KHÔNG ở vòng này.** Hạ tầng thì sẵn rồi (`ManagedImageService` dùng được ngay),
  nhưng kéo theo: hiển thị ảnh trong bong bóng chat, bấm phóng to, xử lý ảnh lỗi. Phần Flutter mới
  là phần tốn. Để sau khi 10 mục đã chạy.

**Q3 — Admin nào cũng trả lời được mọi hội thoại, hay phải gán?**
- 👉 **Mình đề xuất ai cũng trả lời được.** Có mỗi 1–2 admin. Gán phụ trách là nghiệp vụ của hệ
  thống chăm sóc khách hàng thật. Trường `sender` đã lưu ai trả lời nên vẫn truy được.

**Q4 — Chu kỳ polling bao nhiêu giây?**
- 👉 **3 giây khi màn chat đang mở, dừng hẳn khi đóng màn.** Nếu muốn tiết kiệm hơn: 3 giây trong
  30 giây đầu, sau đó giãn ra 10 giây nếu không có tin mới. Cái này **thuần Flutter**, backend
  không cần biết.
