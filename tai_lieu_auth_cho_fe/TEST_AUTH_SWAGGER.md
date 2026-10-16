# KIỂM THỬ AUTH TRÊN SWAGGER — TOÀN BỘ NGHIỆP VỤ

> Tài liệu nghiệm thu khối xác thực. Mỗi phần gồm **Nghiệp vụ** (luật hệ thống đang chạy) rồi tới
> **Ca test** (thao tác → kết quả mong muốn). Mọi message trong cột "Kết quả mong muốn" đều chép
> đúng chuỗi backend thật sự trả về, không phải mô tả.
>
> **Phạm vi:** 10 endpoint `/auth` + 9 endpoint `/accounts` + 3 endpoint `/profiles`.
> **Tổng: 159 ca** — A 28 · B 14 · C 16 · D 15 · E 21 · F 17 · G 8 · H 17 · I 9 · J 14.
>
> Bên Flutter đọc `HUONG_DAN_FE_FLUTTER.md` — cùng bộ nghiệp vụ này nhưng viết theo góc client.

---

# 0. CHUẨN BỊ

## 0.1 — Chạy SQL trước (bắt buộc, một lần)

`ddl-auto: update` **không bao giờ xoá cột và không nới `NOT NULL`**, nên bốn lệnh này phải chạy tay:

```sql
ALTER TABLE accounts DROP COLUMN IF EXISTS username;
ALTER TABLE accounts ALTER COLUMN password DROP NOT NULL;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS provider    VARCHAR(20);
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS provider_id VARCHAR(255);
UPDATE accounts SET provider = 'LOCAL' WHERE provider IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_accounts_provider_id ON accounts(provider_id);
```

## 0.2 — Biến môi trường

| Biến | Bắt buộc | Thiếu thì sao |
|---|---|---|
| `BREVO_API_KEY` | ✅ | `/auth/register` trả **503**, `/auth/forgot-password` vẫn trả 200 (cố ý, xem §E) |
| `BREVO_SENDER_EMAIL` | ✅ | Phải là địa chỉ **đã xác minh trong Brevo**, không thì Brevo từ chối |
| `GOOGLE_CLIENT_ID` | ✅ nếu test Google | `/auth/google` trả **503** |
| `JWT_SIGNER_KEY` | ⬜ | Có mặc định dev. **Rỗng hoặc ngắn < 32 byte ⇒ app không khởi động được** (cố ý) |
| `CONTEXT_PATH` | ⬜ | Mặc định `/api/v1`. **Khai mà bỏ trống ⇒ context-path rỗng**, mọi URL mất tiền tố |
| `FRONTEND_BASE_URL` | ⬜ | Origin được phép gọi CORS. Nhiều origin ngăn bằng **dấu phẩy**. Sai ⇒ FE web bị chặn, mobile không ảnh hưởng |

Tuỳ chọn — để trống là dùng mặc định trong ngoặc:
`OTP_TTL_SECONDS` (90) · `OTP_MAX_ATTEMPTS` (5) · `OTP_VERIFIED_TTL_SECONDS` (300) ·
`OTP_MAX_SENDS_PER_HOUR` (5) · `OTP_REGISTER_TTL_SECONDS` (600) · `OTP_RESEND_COOLDOWN_SECONDS` (20) ·
`PASSWORD_CHANGE_COOLDOWN` (5m) · `LOGIN_MAX_ATTEMPTS` (5) · `LOGIN_MAX_ATTEMPTS_PER_IP` (20) ·
`LOGIN_LOCK_DURATION` (15m) · `AUTH_COOKIE_SECURE` (false) · `AUTH_COOKIE_SAME_SITE` (Lax) ·
`AUTH_COOKIE_PATH` (/) · `CONTEXT_PATH` (/api/v1)

> 🔴 **Khai một biến trong `.env` rồi bỏ trống còn tệ hơn không khai.** Cú pháp `${BIEN:mac-dinh}`
> chỉ lấy mặc định khi biến **không tồn tại**; để trống là biến *có tồn tại* mang chuỗi rỗng.
> Code đã chống lại việc này cho nhóm cookie và Brevo (xem §J), nhưng các biến số khác thì chưa.

## 0.3 — Dọn Redis giữa các nhóm test

```bash
redis-cli KEYS "otp*"        | xargs -r redis-cli DEL
redis-cli KEYS "reg:*"       | xargs -r redis-cli DEL
redis-cli KEYS "login_fail*" | xargs -r redis-cli DEL
redis-cli KEYS "password_change:*" | xargs -r redis-cli DEL
# Hoặc dọn sạch: redis-cli FLUSHDB
```

## 0.4 — Mẹo dùng Swagger

- `CONTEXT_PATH=/api/v1` (đã chốt). Swagger ở **`http://localhost:8088/api/v1/swagger-ui.html`**,
  endpoint thật là `localhost:8088/api/v1/auth/login`. Context-path **nằm ngoài** đường dẫn
  khai trong controller, nên mọi đường dẫn trong tài liệu này viết tương đối (`/auth/login`).
- **Header `X-Client-Type`**: `mobile` ⇒ token nằm trong body. Bỏ trống / bất kỳ giá trị nào khác
  ⇒ coi là web, token đi vào **cookie HttpOnly** và **bị xoá khỏi body**.
  Swagger là web nên mặc định đi nhánh cookie — muốn copy token ra dùng thì đặt `mobile`.
- Nút **Authorize** chỉ cần cho các endpoint ngoài `/auth/**`.
- Soi cookie: DevTools → **Application → Cookies → http://localhost:8088**.

## 0.5 — Khuôn phản hồi chung

Mọi endpoint trả về cùng một vỏ:

```jsonc
{
  "code": 200,
  "message": "Login successful",
  "result": { },                 // dữ liệu, có thể null
  "errors": { "field": "lý do" },// chỉ xuất hiện khi lỗi validate
  "timestamp": "2026-10-16T...",
  "path": "/auth/login"
}
```

`LoginResponse` (dùng chung cho login · verify-registration · refresh · google):

```jsonc
{
  "accessToken": "eyJ...",   // null khi đi nhánh web (đã vào cookie)
  "refreshToken": "eyJ...",  // null khi đi nhánh web
  "tokenType": "Bearer",
  "expiresIn": 900,          // GIÂY
  "userId": "uuid",
  "email": "...",
  "roleName": "USER"
}
```

---

# 1. BẢN ĐỒ ENDPOINT

### `/auth` — 10 endpoint, toàn bộ công khai (whitelist)

| Method | Path | Công dụng |
|---|---|---|
| POST | `/auth/register` | Bước 1 đăng ký — gửi mã, **chưa tạo tài khoản** |
| POST | `/auth/verify-registration` | Bước 2 — nhập mã, **tài khoản ra đời tại đây** |
| POST | `/auth/login` | Đăng nhập email + mật khẩu |
| POST | `/auth/refresh` | Xoay vòng token |
| POST | `/auth/logout` | Thoát phiên hiện tại |
| POST | `/auth/logout-all` | Thoát mọi thiết bị |
| POST | `/auth/google` | Đăng nhập bằng ID token của Google |
| POST | `/auth/forgot-password` | Gửi mã khôi phục |
| POST | `/auth/verify-otp` | Đổi mã lấy vé |
| POST | `/auth/reset-password` | Đặt mật khẩu mới bằng vé |

### `/accounts` — 9 endpoint (6 chỉ ADMIN) · `/profiles` — 3 endpoint (1 chỉ ADMIN)

| Method | Path | Quyền |
|---|---|---|
| GET | `/accounts/me` | đăng nhập |
| PATCH | `/accounts/me` | đăng nhập — **cần `currentPassword`** |
| PATCH | `/accounts/me/password` | đăng nhập |
| POST · GET · GET`/{id}` · PATCH`/{id}` · PATCH`/{id}/status` · DELETE`/{id}` | `/accounts` | **ADMIN** |
| GET | `/profiles/me` · PATCH `/profiles/me` | đăng nhập |
| GET | `/profiles/{accountId}` | **ADMIN** |

---

# A. ĐĂNG KÝ 2 BƯỚC (GATE)

## A.1 — Nghiệp vụ

**Nguyên tắc nền: tài khoản chỉ ra đời sau khi xác minh được email.** Suốt bước 1 database
không bị chạm tới (trừ một câu kiểm trùng email), nên đăng ký bỏ dở **không để lại gì phải dọn**.

**Bước 1 — `POST /auth/register`**

1. Chuẩn hoá email về chữ thường.
2. Email đã có tài khoản ⇒ **409 ngay**, không tốn một lá mail nào.
3. Còn trong khoảng chờ 20 giây ⇒ **429**.
4. Quá 5 lần gửi trong một giờ (tính theo **email**) ⇒ **429**.
5. Sinh **`registrationId`** mới (UUID) cho *mỗi lần bấm*.
6. Băm mật khẩu bằng BCrypt rồi cất hồ sơ vào Redis `reg:{registrationId}`, TTL 600s.
   **Redis không bao giờ giữ mật khẩu thô.**
7. Sinh OTP 6 số, cất ở `otp:register:{registrationId}`.
8. Gửi mail. **Gửi xong mới bật khoảng chờ** — Brevo lỗi thì khách không bị phạt oan.
9. Trả **200** `{registrationId, email, expiresIn, resendAfter}`.

> 🔴 **Vì sao khoá theo `registrationId` chứ không theo email.** Khoá theo email thì mỗi địa chỉ
> chỉ có một ô: kẻ xấu đăng ký đè lên hồ sơ người thật, rồi người thật — vốn là chủ hộp thư —
> nhập mã mới nhất và vô tình tạo ra tài khoản **mang mật khẩu của kẻ kia**. Đó là chiếm tài
> khoản hoàn chỉnh. Mỗi lần bấm sinh một mã phiên riêng thì hai hồ sơ không bao giờ đụng nhau.
>
> Riêng **khoảng chờ và trần gửi mail vẫn khoá theo EMAIL** — chúng là hàng rào chống dội mail,
> phải tính trên hộp thư, không thì kẻ tấn công chỉ cần xin `registrationId` mới là né sạch.

**Bước 2 — `POST /auth/verify-registration {registrationId, otp}`**

1. Không tìm thấy hồ sơ ⇒ 400 *"Your registration session has expired. Please sign up again."*
2. **Tăng bộ đếm trước, so mã sau.** Vượt trần ⇒ chặn **trước khi** so (chi tiết §J.2).
3. Sai mã ⇒ 400. Sai đủ 5 lần ⇒ **huỷ cả hồ sơ** ⇒ 429, phải khai lại từ đầu.
4. Đúng mã ⇒ tạo `accounts` + `profiles` trong **một transaction**. Mật khẩu **không băm lại**
   (chuỗi trong Redis đã là BCrypt rồi; băm hai lần là không ai đăng nhập được).
5. Nếu trong lúc chờ có người khác chiếm mất email ⇒ 409 **và dọn hồ sơ ngay**, không bắt khách
   nhận đúng lỗi đó lặp lại tới hết 10 phút.
6. Trả **201** + `LoginResponse` + cookie.

**Ràng buộc đầu vào**

| Trường | Luật |
|---|---|
| `email` | bắt buộc · đúng định dạng · ≤ 100 ký tự |
| `password` | bắt buộc · 8–100 ký tự · **phải có chữ hoa, chữ thường và số** |
| `fullName` | bắt buộc · ≤ 150 ký tự |
| `phone` | bắt buộc · đúng `^0\d{9}$` (10 số, bắt đầu bằng 0) |
| `registrationId` | bắt buộc · ≤ 64 ký tự |
| `otp` | bắt buộc · đúng 6 chữ số |

## A.2 — Ca test

| # | Thao tác | Kết quả mong muốn | ✓ |
|---|---|---|---|
| A-1 | `POST /auth/register`<br>`{"email":"test1@gmail.com","password":"Test@12345","fullName":"Nguyen Van A","phone":"0901234567"}` | **200** `"A verification code has been sent to your email"`, body `{registrationId, email, expiresIn:600, resendAfter:20}`, **không có token**. Mail về hộp thư. **Chép lại `registrationId` = R1** | ☐ |
| A-2 | Ngay sau A-1: `SELECT * FROM accounts WHERE email='test1@gmail.com'` | **0 hàng** — tài khoản chưa tồn tại | ☐ |
| A-3 | `redis-cli HGETALL "reg:R1"` | 4 trường; `passwordHash` bắt đầu bằng `$2a$`. `redis-cli KEYS "reg:*"` **không** thấy khoá nào chứa email | ☐ |
| A-4 | Mở mail, kiểm hình thức | Tiêu đề *"Xác minh email đăng ký"*, có header thương hiệu, **hộp mã dashed chữ to giãn**, dòng hạn dùng 10 phút | ☐ |
| A-5 | Gọi lại A-1 ngay (trong 20 giây) | **429** `"Please wait N more seconds before requesting a new code"` | ☐ |
| A-6 | Chờ hết 20 giây rồi gọi lại A-1 | **200**, `registrationId` **khác R1** (gọi là R2), mail thứ hai mang mã **khác** | ☐ |
| A-7 | `POST /auth/verify-registration` `{"registrationId":"R1","otp":"<mã của mail thứ 2>"}` | **400** `"Incorrect verification code"` — mã của R2 không mở được R1 | ☐ |
| A-8 | `{"registrationId":"R1","otp":"<mã của mail thứ 1>"}` | **201** `"Registration completed"` + `LoginResponse`. **R1 vẫn sống và vẫn dùng được mã của chính nó** | ☐ |
| A-9 | Sau A-8: đếm bản ghi | DB có **1 account + 1 profile**. `profiles.full_name` = "Nguyen Van A", `phone` = "0901234567" | ☐ |
| A-10 | `POST /auth/login` `test1@gmail.com` / `Test@12345` | **200** — chứng minh mật khẩu **không bị băm hai lần** | ☐ |
| A-11 | Dọn sạch, đăng ký lại lấy R3, nhập sai mã **4 lần** | Mỗi lần **400** `"Incorrect verification code"` | ☐ |
| A-12 | Sai lần thứ **5** | **429** `"Too many incorrect attempts. Your registration was cancelled, please sign up again."` | ☐ |
| A-13 | `redis-cli HGETALL "reg:R3"` sau A-12 | **rỗng** — hồ sơ bị huỷ, không chỉ huỷ mã | ☐ |
| A-14 | Gọi verify với R3 lần nữa | **400** `"Your registration session has expired. Please sign up again."` | ☐ |
| A-15 | `{"registrationId":"khong-ton-tai","otp":"123456"}` | **400** cùng câu trên — không lộ thêm thông tin gì | ☐ |
| A-16 | Đăng ký lại email đã có tài khoản | **409** `"Email already in use: ..."`, **không tốn mail** | ☐ |
| A-17 | Đăng ký với `"phone":"123"` | **400**, `errors.phone` = `"Phone must be 10 digits and start with 0"` | ☐ |
| A-18 | Đăng ký với `"password":"1234567"` | **400** `"Password must be between 8 and 100 characters"` | ☐ |
| A-19 | Đăng ký với `"password":"12345678"` | **400** `"Password must contain at least one uppercase letter, one lowercase letter and one digit"` | ☐ |
| A-20 | Đăng ký với `"password":"abcdefgh"` | **400** cùng câu A-19 (thiếu hoa và số) | ☐ |
| A-21 | Đăng ký email VIẾT HOA `TEST2@GMAIL.COM`, verify xong | DB lưu **chữ thường** `test2@gmail.com` | ☐ |
| A-22 | Gửi thừa field lạ, ví dụ `"roleId":"..."` | **200**, field thừa bị bỏ qua, vai trò vẫn là **USER** | ☐ |
| A-23 | Đăng ký rồi để quá `OTP_REGISTER_TTL_SECONDS` mới verify | **400** hết hạn, DB vẫn sạch, không có job dọn nào phải chạy | ☐ |
| A-24 | Bấm `register` **6 lần** cho cùng một email trong một giờ (chờ hết cooldown giữa các lần) | Lần thứ 6: **429** `"Too many verification emails requested for this address. Please try again later."` | ☐ |

## A.3 — Kịch bản chiếm tài khoản (BẮT BUỘC CHẠY)

> Đây là lỗ hổng nặng nhất từng tồn tại trong khối auth. Bốn ca dưới đây là bằng chứng nó đã bị bịt.

| # | Thao tác | Kết quả mong muốn | ✓ |
|---|---|---|---|
| A-25 | Dọn sạch. **Kẻ tấn công**: `register` `{"email":"victim@gmail.com","password":"Attack@123",...}` → nhận **Ra**, mail mang mã **Ma** | **200** | ☐ |
| A-26 | Chờ hết cooldown. **Người thật** (chủ hộp thư): `register` cùng `victim@gmail.com`, `"password":"Real@12345"` → nhận **Rb**, mã **Mb** | **200**, `Rb ≠ Ra` | ☐ |
| A-27 | Người thật nhập **mã mới nhất Mb** với `registrationId = Rb` | **201**. Rồi login: `victim@gmail.com` + **`Real@12345`** ⇒ **200** · `Attack@123` ⇒ **401** ✅ **đã bịt** | ☐ |
| A-28 | Kẻ tấn công thử `{"registrationId":"Ra","otp":"Ma"}` | **409** `"Email already in use"`, **và** `redis-cli HGETALL "reg:Ra"` **rỗng** (dọn ngay, không chờ hết TTL) | ☐ |

---

# B. ĐĂNG NHẬP

## B.1 — Nghiệp vụ

1. **Chặn từ vòng ngoài trước**: email hoặc IP đang bị khoá ⇒ 429 ngay, khỏi tốn một phép so
   BCrypt (~100 ms CPU). Thiếu bước này thì chính cơ chế chống dò trở thành đòn bẩy làm nghẽn server.
2. Sai email **và** sai mật khẩu trả **y hệt một chuỗi** `"Invalid email or password"` ⇒ người
   ngoài không dò được email nào có thật.
3. Mỗi lần thất bại cộng vào **hai** bộ đếm: theo email (trần 5) và theo IP (trần 20).
   Đếm cả khi email không tồn tại — không thì kẻ tấn công vẫn quét được danh sách email vô hạn.
4. Tài khoản `LOCKED` / `DISABLED` ⇒ **403** `"Account is not allowed to sign in"`.
   Cố ý **không** trả chuỗi gốc của Spring (*"User is disabled"*) vì chuỗi đó xác nhận tài khoản có thật.
5. Đăng nhập đúng ⇒ xoá bộ đếm theo email.
6. Phát một phiên: `sessionId` vừa là `jti` của refresh token vừa là claim `sid` của access
   token — sợi dây buộc hai token để logout giết được cả hai.
7. **Ngưỡng theo IP nới hơn hẳn ngưỡng theo email** vì một văn phòng đi chung NAT thì rất nhiều
   người thật cùng ra một IP.

## B.2 — Ca test

| # | Thao tác | Kết quả mong muốn | ✓ |
|---|---|---|---|
| B-1 | `POST /auth/login` `{"email":"user@example.com","password":"User@12345"}`, header `X-Client-Type: mobile` | **200** `"Login successful"`, body có `accessToken`, `refreshToken`, `tokenType:"Bearer"`, `expiresIn:900`, `userId`, `email`, `roleName:"USER"` | ☐ |
| B-2 | Y hệt B-1 nhưng **bỏ** header `X-Client-Type` | **200**, body có `accessToken: null` và `refreshToken: null`; hai header `Set-Cookie` xuất hiện | ☐ |
| B-3 | Sau B-2, soi DevTools → Application → Cookies | Có `access_token` và `refresh_token`, cột **HttpOnly ✓**, **Path = `/`** | ☐ |
| B-4 | Login email **không tồn tại** | **401** `"Invalid email or password"` | ☐ |
| B-5 | Login email có thật + sai mật khẩu | **401** — **chuỗi giống hệt B-4** | ☐ |
| B-6 | Login email VIẾT HOA `USER@EXAMPLE.COM` + mật khẩu đúng | **200** — tra email không phân biệt hoa thường | ☐ |
| B-7 | Sai mật khẩu **5 lần liên tiếp** rồi thử lần 6 | **429** `"Too many failed login attempts..."` kèm gợi ý thời gian chờ | ☐ |
| B-8 | `redis-cli TTL "login_fail:user@example.com"` | ≈ **900** giây | ☐ |
| B-9 | Trong lúc đang bị khoá, login bằng **mật khẩu ĐÚNG** | Vẫn **429** — chặn ở vòng ngoài, không so BCrypt | ☐ |
| B-10 | Xoá `login_fail:` rồi login đúng, sau đó `redis-cli GET "login_fail:user@example.com"` | **nil** — đăng nhập thành công xoá bộ đếm | ☐ |
| B-11 | Admin khoá một tài khoản (`PATCH /accounts/{id}/status` = `LOCKED`), rồi login bằng tài khoản đó | **403** `"Account is not allowed to sign in"` | ☐ |
| B-12 | Đổi status thành `DISABLED` rồi login | **403**, cùng chuỗi B-11 | ☐ |
| B-13 | Login thiếu field `password` | **400**, `errors.password` = `"Password is required"` | ☐ |
| B-14 | Login với `"email":"khong-phai-email"` | **400** `"Email must be valid"` | ☐ |

---

# C. VÒNG ĐỜI TOKEN

## C.1 — Nghiệp vụ

**`POST /auth/refresh` — xoay vòng**

1. Nguồn token xét theo thứ tự: body → cookie → header `Authorization`.
   Access token lẫn trong danh sách bị **bỏ qua**, không làm hỏng cả lượt gọi.
2. **Hỏi lại database thay vì tin vào claim.** Trong 7 ngày sống của refresh token, tài khoản có
   thể đã bị xoá, bị khoá hoặc **đổi vai trò**. Thiếu bước này thì vai trò cũ được cấp lại thêm 15 phút.
3. Tài khoản bị khoá ⇒ **thu hồi sạch mọi phiên** rồi mới từ chối, để các thiết bị còn lại rụng theo ngay.
4. **Thu hồi token vừa dùng TRƯỚC khi phát token mới.** Mỗi chuỗi refresh token chỉ xài được đúng
   một lần — kẻ trộm chép lại token cũ sẽ nhận 401.
5. Mọi lý do thất bại trả **cùng một chuỗi** `"Invalid or expired refresh token"`.
6. Thất bại ở nhánh web thì **cookie bị xoá luôn**, tránh trình duyệt lặp vô hạn với token chết.

**`POST /auth/logout`** — xoá refresh token của phiên hiện tại. Access token cùng phiên **chết ngay
lập tức** vì filter kiểm claim `sid` có còn trong Redis không. Không cần bảng denylist riêng.

**`POST /auth/logout-all`** — xoá mọi refresh token của tài khoản **và** cắm mốc `logout_time`.
Hai bước bổ trợ: bước một giết các phiên Redis đang theo dõi, bước hai chặn nốt token nào không
mang claim `sid`. Đây là **API duy nhất** để xoá sạch phiên, dùng lại ở đổi mật khẩu, đổi email,
đổi vai trò, khoá và xoá tài khoản.

> Logout **không** trả 200 cho xong khi không đọc nổi token. Đây là thao tác an ninh, người dùng
> bấm vì nghi bị lộ; báo thành công trong khi phiên của kẻ trộm vẫn sống là tệ hơn hẳn việc bắt
> họ đăng nhập lại rồi bấm lại.

## C.2 — Ca test

| # | Thao tác | Kết quả mong muốn | ✓ |
|---|---|---|---|
| C-1 | Login (mobile) lấy `refreshToken` RT1 → `POST /auth/refresh` `{"token":"RT1"}` | **200** `"Token refreshed successfully"` + cặp token **mới** | ☐ |
| C-2 | Dùng lại **RT1** lần nữa | **401** `"Invalid or expired refresh token"` — token chỉ xài một lần | ☐ |
| C-3 | Gửi **accessToken** vào `/auth/refresh` | **401** cùng chuỗi — không nhận nhầm loại token | ☐ |
| C-4 | `/auth/refresh` với body rỗng `{}` ở nhánh web (có cookie) | **200** — tự lấy từ cookie | ☐ |
| C-5 | `/auth/refresh` với token bịa | **401**, và ở nhánh web **cookie bị xoá** (DevTools không còn) | ☐ |
| C-6 | Login, lấy access token AT1 → `POST /auth/logout` → gọi `GET /accounts/me` bằng AT1 | **401** `"Session has been revoked. Please sign in again."` — access token chết theo phiên | ☐ |
| C-7 | Login ở **2 trình duyệt**, logout ở trình duyệt 1 | Trình duyệt 2 **vẫn dùng được** — logout chỉ giết phiên hiện tại | ☐ |
| C-8 | Lặp C-7 nhưng bấm `POST /auth/logout-all` | **Cả hai** trình duyệt đều 401 | ☐ |
| C-9 | `redis-cli GET "logout_time:user@example.com"` sau C-8 | Một chuỗi số epoch millis | ☐ |
| C-10 | `POST /auth/logout` không gửi token nào | **401** `"No readable token found to revoke. Please sign in again."` | ☐ |
| C-11 | `POST /auth/logout` với access token **đã hết hạn** | **200** — logout cố tình chấp nhận token hết hạn, vẫn thu hồi được phiên | ☐ |
| C-12 | Login → đổi vai trò tài khoản đó qua admin → gọi `/auth/refresh` bằng token cũ | **401** — vì đổi vai trò đã thu hồi mọi phiên (xem §H) | ☐ |
| C-13 | Login → admin khoá tài khoản → `/auth/refresh` | **401**, và mọi phiên khác của tài khoản đó cũng chết | ☐ |
| C-14 | Tự ký một JWT bằng `JWT_SIGNER_KEY` của local nhưng **bỏ claim `typ`**, gọi `GET /accounts/me` | **401** `"Invalid token type: expected access token"`. Chốt viết `!"access".equals(typ)` nên `typ` null tự rơi vào nhánh từ chối | ☐ |
| C-15 | Tự ký JWT **bỏ claim `sub`** (có `typ: access`), gọi `GET /accounts/me` | **401** `"Token is missing the subject claim"` — thiếu `sub` thì không tra được mốc thu hồi, phải từ chối thay vì bỏ qua | ☐ |
| C-16 | Tự ký JWT đủ `typ`+`sub` nhưng **`uid` không phải UUID**, gọi `GET /accounts/me` | **401** `"Malformed token"` — `AuthenticatedUser.of` chặn, không để `IllegalArgumentException` thành 500 | ☐ |

---

# D. ĐĂNG NHẬP GOOGLE

## D.1 — Nghiệp vụ

Đi **đường B**: frontend tự lấy ID token từ Google rồi `POST /auth/google {idToken}`. Backend
không tham gia luồng redirect nên **không cần `GOOGLE_CLIENT_SECRET`**.

**Xác minh token — bốn thứ, làm trước khi chạm vào database:**

| # | Kiểm | Vì sao |
|---|---|---|
| 1 | Chữ ký **RS256** bằng khoá công khai tại `https://www.googleapis.com/oauth2/v3/certs` | Chống token giả |
| 2 | `aud` == `GOOGLE_CLIENT_ID` | Token cấp cho app khác không dùng được ở đây |
| 3 | `iss` ∈ {`https://accounts.google.com`, `accounts.google.com`} | Google dùng cả hai dạng |
| 4 | `exp` chưa qua | Chống phát lại |

Thêm một chốt nữa: **`email_verified` phải là true** (đọc được cả kiểu boolean lẫn chuỗi `"true"`).

> Dùng `NimbusJwtDecoder.withJwkSetUri(...)` chứ **không** dùng `fromIssuerLocation` — cái sau tải
> tài liệu discovery ngay lúc dựng bean, mất mạng là **app không khởi động được**.

**Ba nhánh xử lý tài khoản:**

1. `provider_id` đã có ⇒ đăng nhập thẳng.
   Tra theo `sub` chứ **không** theo email: Google cho đổi email, còn `sub` thì vĩnh viễn không đổi.
2. Email trùng tài khoản thường ⇒ **gộp**, chỉ điền `provider_id`, **giữ `provider = LOCAL`**
   (mật khẩu cũ của họ vẫn dùng được). An toàn vì đã chặn `email_verified != true` từ vòng ngoài.
3. Người mới ⇒ tạo tài khoản **không mật khẩu**, `provider = GOOGLE`, `phone = null`
   ⇒ `profileCompleted = false` ⇒ frontend tự đẩy màn hình hoàn tất hồ sơ (nudge, **không gate**).

**Chốt trạng thái chạy TRƯỚC khi gộp**: tài khoản đang khoá thì không được phép ghi `provider_id`
vào bản ghi của họ rồi mới từ chối. Và vì nhánh này **không đi qua `DaoAuthenticationProvider`**,
nó phải tự kiểm `isEnabled` / `isAccountNonLocked` — kiểm hai lần, trước và sau khi gộp.

## D.2 — Ca test

| # | Thao tác | Kết quả mong muốn | ✓ |
|---|---|---|---|
| D-1 | Bỏ trống `GOOGLE_CLIENT_ID`, gọi `/auth/google` | **503** `"Google sign-in is not configured. Please set GOOGLE_CLIENT_ID."` | ☐ |
| D-2 | `{"idToken":"chuoi-bia"}` | **401** `"Invalid Google credential"` | ☐ |
| D-3 | ID token hợp lệ nhưng của **app khác** (`aud` khác) | **401** `"Invalid Google credential"` — chốt `aud` chạy. Chuỗi **giống hệt** D-2/D-4: không nói cho kẻ tấn công biết sai ở đâu | ☐ |
| D-4 | ID token **đã hết hạn** | **401** `"Invalid Google credential"` | ☐ |
| D-5 | ID token dài hơn 4096 ký tự | **400** `"idToken is too long"` — chặn ở DTO, decoder không phải phân tích rác | ☐ |
| D-6 | ID token hợp lệ, email **chưa từng có** trong hệ thống | **200** + `LoginResponse`. DB: `provider='GOOGLE'`, `provider_id` = `sub`, `password` **NULL** | ☐ |
| D-7 | `GET /profiles/me` ngay sau D-6 | `profileCompleted: false`, `avatarUrl` là link ảnh Google, `phone: null` | ☐ |
| D-8 | Gọi lại `/auth/google` cùng tài khoản | **200**, **không** tạo tài khoản thứ hai (nhánh `provider_id`) | ☐ |
| D-9 | Đăng ký thường `merge@gmail.com` → sau đó login Google cùng email | **200**. DB: **vẫn** `provider='LOCAL'`, nhưng `provider_id` đã được điền | ☐ |
| D-10 | Sau D-9, login thường bằng mật khẩu cũ | **200** — gộp không làm mất mật khẩu | ☐ |
| D-11 | Tài khoản Google thuần (D-6) gọi `GET /accounts/me` | `hasPassword: false`, `provider: "GOOGLE"` | ☐ |
| D-12 | Tài khoản Google thuần gọi `PATCH /accounts/me/password` | **409** `"This account has no password yet. Use the forgot-password flow to set one."` | ☐ |
| D-13 | Admin khoá tài khoản đã liên kết Google, rồi login Google | **403** `"Account is not allowed to sign in"`, **và** `SELECT provider_id` cho thấy giá trị **không bị ghi đè** | ☐ |
| D-14 | Login Google bằng tài khoản có email viết hoa từ Google | DB lưu email **chữ thường** | ☐ |
| D-15 | ID token của tài khoản Google có `email_verified: false` (tài khoản Workspace chưa xác minh) | **403** `"Your Google account email has not been verified"`. Khác 401 ở trên: token **thật và hợp lệ**, chỉ là email chưa đáng tin để gộp | ☐ |

---

# E. QUÊN MẬT KHẨU (3 BƯỚC)

## E.1 — Nghiệp vụ

```
POST /auth/forgot-password {email}   →  LUÔN 200
POST /auth/verify-otp {email, otp}   →  {ticket, expiresIn: 300}
POST /auth/reset-password {ticket, newPassword}  →  200 + xoá cookie + thu hồi mọi phiên
```

**`forgot-password` luôn trả 200, bất kể email có thật hay không** — đây là cam kết chống dò tài
khoản, và nó kéo theo ba hệ quả thiết kế:

- Tài khoản không tồn tại, hoặc đang `LOCKED`/`DISABLED` ⇒ im lặng kết thúc.
- Đang trong khoảng chờ, hoặc quá trần gửi mail ⇒ **im lặng bỏ qua**, *không* ném 429 như luồng
  đăng ký. Báo "đang trong thời gian chờ" chính là xác nhận email đó có thật.
- **Lỗi gửi mail cũng bị nuốt.** Nếu để nó bay lên thì email có thật trả 502 còn email bịa trả
  200 ⇒ tặng kẻ tấn công đúng cái máy dò mà cam kết kia sinh ra để chặn.
  Đổi lại: cấu hình Brevo sai sẽ **im lặng ở đây** — `/auth/register` vẫn báo 503 rõ ràng nên vẫn
  có chỗ phát hiện.

**`verify-otp`**: nhập đúng ⇒ mã chết ngay, đổi lấy **vé** sống 300 giây — đủ thời gian gõ mật
khẩu mới mà không phải giữ mã OTP sống lâu. Vé dùng được **đúng một lần** (`getAndDelete`).

**`reset-password`**:
- Vé hết hạn / đã dùng ⇒ **401**.
- Tài khoản không còn `ACTIVE` ⇒ **403** — vé sống 300 giây, đủ dài để admin kịp khoá trong khoảng đó.
- Đổi xong ⇒ **thu hồi mọi phiên**, kể cả phiên của kẻ đã chiếm được tài khoản trước đó.
- Tài khoản Google chưa có mật khẩu **vẫn dùng được luồng này** để đặt mật khẩu đầu tiên (cố ý).

> Luồng khôi phục **không** chịu cooldown đổi mật khẩu của §F — chặn thêm ở đây chỉ nhốt người
> thật đang loay hoay lấy lại tài khoản.

## E.2 — Ca test

| # | Thao tác | Kết quả mong muốn | ✓ |
|---|---|---|---|
| E-1 | `POST /auth/forgot-password` `{"email":"user@example.com"}` | **200** `"If that email is registered, a verification code has been sent"`. Mail về | ☐ |
| E-2 | Mail nhận được | Tiêu đề *"Mã xác minh đặt lại mật khẩu"*, hộp mã lớn, dòng hạn dùng **90 giây** | ☐ |
| E-3 | `forgot-password` với email **không tồn tại** | **200**, chuỗi **giống hệt E-1**, không có mail nào gửi đi | ☐ |
| E-4 | Bấm lại ngay trong 20 giây | **200** (không phải 429!) nhưng **không có mail thứ hai** — im lặng bỏ qua | ☐ |
| E-5 | `forgot-password` cho tài khoản đang `LOCKED` | **200**, không có mail | ☐ |
| E-6 | `POST /auth/verify-otp` `{"email":"user@example.com","otp":"<mã đúng>"}` | **200** `"Verification successful"`, body `{ticket, expiresIn:300}` | ☐ |
| E-7 | `verify-otp` với mã sai | **400** `"Incorrect verification code"` | ☐ |
| E-8 | Sai đủ **5 lần** | **429** `"Too many incorrect attempts. Please request a new code."` | ☐ |
| E-9 | Sau E-8, nhập **mã đúng** | **400** `"The verification code has expired. Please request a new one."` — hết lượt là **xoá hẳn mã**, không chỉ chặn tạm | ☐ |
| E-10 | `verify-otp` cho email **không tồn tại** | **400** hết hạn — không lộ email nào có thật | ☐ |
| E-11 | `POST /auth/reset-password` `{"ticket":"<vé>","newPassword":"New@12345"}` | **200** `"Password has been reset. Please sign in again."`, cookie bị xoá | ☐ |
| E-12 | Dùng lại chính vé đó | **401** `"The reset session has expired. Please start again."` | ☐ |
| E-13 | Login bằng mật khẩu **cũ** sau E-11 | **401** | ☐ |
| E-14 | Login bằng mật khẩu **mới** | **200** | ☐ |
| E-15 | Trước E-11, mở một phiên khác; sau E-11 gọi `GET /accounts/me` bằng phiên đó | **401** — mọi phiên bị thu hồi | ☐ |
| E-16 | `reset-password` với `"newPassword":"abcdefgh"` | **400** — luật chữ hoa/thường/số áp cả ở đây | ☐ |
| E-17 | `reset-password` với `ticket` dài 500 ký tự | **400** `"Ticket is not valid"` | ☐ |
| E-18 | Xin vé xong, **admin khoá tài khoản**, rồi mới `reset-password` | **403** `"This account is not allowed to reset its password"` | ☐ |
| E-19 | Tài khoản Google thuần dùng trọn luồng quên mật khẩu | **200**; sau đó `GET /accounts/me` cho `hasPassword: true` và login thường được | ☐ |
| E-20 | Tắt `BREVO_API_KEY`, gọi `forgot-password` với email **có thật** | **200** — **giống hệt** email không có thật, không lộ gì | ☐ |
| E-21 | Vẫn tắt key, gọi `POST /auth/register` | **503** `"Email service is not configured. Please set BREVO_API_KEY."` — vẫn có chỗ phát hiện cấu hình sai | ☐ |

---

# F. TÀI KHOẢN CỦA TÔI

## F.1 — Nghiệp vụ

**`GET /accounts/me`** — trả `{id, email, roleId, roleName, status, provider, hasPassword}`.
Không bao giờ trả mật khẩu, kể cả dạng băm; chỉ có cờ `hasPassword` để frontend biết nên hiện
nút "Đổi mật khẩu" hay "Đặt mật khẩu".

**`PATCH /accounts/me` — đổi email, BẮT BUỘC `currentPassword`**

Email là thứ dùng để khôi phục tài khoản, nên **đổi được email là đổi được chủ sở hữu**. Không
hỏi lại mật khẩu thì một chiếc máy quên khoá màn hình — hay một token bị đánh cắp — đủ để chiếm
tài khoản vĩnh viễn. Đổi thành công ⇒ **thu hồi mọi phiên** (mốc logout cắm theo email, phải cắm
theo email **cũ**, không thì token cũ lọt lưới).

**`PATCH /accounts/me/password` — đổi mật khẩu**

1. **Kiểm khoảng chờ trước khi so BCrypt** — không thì chính chốt chống spam lại thành chỗ đốt CPU.
2. Tài khoản chưa có mật khẩu (Google thuần) ⇒ 409 kèm hướng dẫn, chứ không để
   `matches(x, null)` trả false rồi báo "mật khẩu hiện tại sai" khiến người ta thử lại mãi.
3. Sai mật khẩu hiện tại ⇒ 401. Mật khẩu mới trùng mật khẩu cũ ⇒ 400.
4. Thành công ⇒ **thu hồi mọi phiên kể cả thiết bị vừa đổi** (mật khẩu đã nằm trong diện nghi
   ngờ thì kẻ kia phải văng ra ngay) ⇒ bật **cooldown 5 phút** ⇒ gửi **mail cảnh báo**.
5. Mail chạy **bất đồng bộ và tự nuốt lỗi**: nghiệp vụ đã xong xuôi, không thể vì Brevo rớt mà
   báo thất bại hay quay ngược thao tác đã thành công.

> **Cooldown chỉ tính trên lần đổi THÀNH CÔNG.** Gõ sai mật khẩu hiện tại không bị tính — người
> dùng thật lỡ tay một lần không đáng phải ngồi chờ 5 phút.

## F.2 — Ca test

| # | Thao tác | Kết quả mong muốn | ✓ |
|---|---|---|---|
| F-1 | `GET /accounts/me` khi đã đăng nhập | **200**, body đủ 7 field, **không có** `password` dưới bất kỳ dạng nào | ☐ |
| F-2 | `GET /accounts/me` không gửi token | **401** | ☐ |
| F-3 | `PATCH /accounts/me` `{"email":"moi@gmail.com"}` — **thiếu** `currentPassword` | **400** `"Current password is required"` | ☐ |
| F-4 | `PATCH /accounts/me` `{"email":"moi@gmail.com","currentPassword":"sai-bet"}` | **401** `"Current password is incorrect"`, email **không đổi** | ☐ |
| F-5 | `PATCH /accounts/me` với `currentPassword` đúng | **200** `"Account updated successfully"`, email đổi | ☐ |
| F-6 | Sau F-5, gọi lại `GET /accounts/me` bằng **token cũ** | **401** — đổi email thu hồi mọi phiên | ☐ |
| F-7 | Đổi email sang địa chỉ **đã có người dùng** | **409** `"Account email already exists: ..."` | ☐ |
| F-8 | Tài khoản Google thuần gọi `PATCH /accounts/me` | **409** `"This account has no password yet..."` | ☐ |
| F-9 | `PATCH /accounts/me/password` `{"currentPassword":"User@12345","newPassword":"New@12345"}` | **200** `"Password changed successfully. Please sign in again."` | ☐ |
| F-10 | Hộp thư sau F-9 | Mail *"Mật khẩu của bạn vừa được thay đổi"*, có **khối cảnh báo viền hổ phách**, nhắc dùng Quên mật khẩu nếu không phải mình làm | ☐ |
| F-11 | Gọi `GET /accounts/me` bằng token cũ sau F-9 | **401** — kể cả thiết bị vừa đổi cũng bị đá | ☐ |
| F-12 | Đăng nhập lại rồi đổi mật khẩu **lần hai ngay** | **429** `"You changed your password recently. Please wait N more seconds before changing it again."` | ☐ |
| F-13 | `redis-cli TTL "password_change:{accountId}"` | ≈ **300** giây | ☐ |
| F-14 | Trong lúc cooldown, gõ **sai** `currentPassword` | Vẫn **429** — chặn trước khi tốn phép băm | ☐ |
| F-15 | Trong lúc cooldown, dùng luồng **quên mật khẩu** đặt lại | **200** — cooldown không áp cho luồng khôi phục | ☐ |
| F-16 | `newPassword` **trùng** mật khẩu hiện tại | **400** `"New password must be different from the current one"` | ☐ |
| F-17 | `newPassword` = `"abcdefgh"` | **400** — thiếu chữ hoa và số | ☐ |

---

# G. HỒ SƠ

## G.1 — Nghiệp vụ

`accounts` là **danh tính đăng nhập**, `profiles` là **con người**. Cột `username` đã **xoá hẳn** —
email chính là định danh đăng nhập.

- **`GET /profiles/me`** — hồ sơ được dựng sẵn ngay lúc tạo tài khoản, nên endpoint này
  **không bao giờ trả 404**, frontend khỏi phải phân biệt "chưa có hồ sơ" với "chưa điền gì".
  Có một lưới hứng: tài khoản cũ chưa có hồ sơ thì lần đọc đầu tiên tự dựng (vì vậy method này
  cố ý **không** `readOnly`).
- **`PATCH /profiles/me`** — tên thật, số điện thoại, ngày sinh, giới tính, ảnh, địa chỉ nằm ở
  đây, **không** nằm ở `/accounts/me`.
- **`GET /profiles/{accountId}`** — chỉ ADMIN.
- `profileCompleted` là cờ **nudge**, không phải gate: hồ sơ thiếu vẫn dùng app bình thường,
  frontend chỉ gợi ý hoàn tất.
- Truy vấn hồ sơ đã gắn `@EntityGraph("account")` vì `ProfileResponse` có `email` lấy từ bảng
  `accounts` — thiếu nó là mỗi lần đọc thêm một câu SELECT.

## G.2 — Ca test

| # | Thao tác | Kết quả mong muốn | ✓ |
|---|---|---|---|
| G-1 | `GET /profiles/me` ngay sau khi đăng ký | **200**, `fullName` và `phone` đã có sẵn từ lúc đăng ký, `profileCompleted` đúng trạng thái | ☐ |
| G-2 | `GET /profiles/me` bằng tài khoản seed `user@example.com` | **200** — tự dựng hồ sơ rỗng, **không 404** | ☐ |
| G-3 | `PATCH /profiles/me` `{"fullName":"Tran Thi B","birthDate":"2000-05-20","gender":"FEMALE","address":"Q1, HCM"}` | **200**, các field đổi đúng | ☐ |
| G-4 | `PATCH /profiles/me` chỉ gửi `{"address":"..."}` | **200**, các field khác **giữ nguyên** (PATCH, null = không đổi) | ☐ |
| G-5 | `GET /profiles/{accountId}` bằng tài khoản **USER** | **403** | ☐ |
| G-6 | `GET /profiles/{accountId}` bằng **ADMIN** | **200**, trả hồ sơ của người khác kèm `email` | ☐ |
| G-7 | `GET /profiles/{accountId}` với id không tồn tại (ADMIN) | **404** `"Profile not found for account: ..."` | ☐ |
| G-8 | `GET /profiles/me` không gửi token | **401** | ☐ |

---

# H. QUẢN TRỊ TÀI KHOẢN (ADMIN)

## H.1 — Nghiệp vụ

Sáu endpoint chỉ ADMIN: `POST /accounts`, `GET /accounts`, `GET /accounts/{id}`,
`PATCH /accounts/{id}`, `PATCH /accounts/{id}/status`, `DELETE /accounts/{id}`.

**Luật xuyên suốt: trên đường admin, bạn KHÔNG được tự sửa chính mình.** Bốn chốt cùng một họ —
mọi thao tác tự phục vụ phải đi qua `/accounts/me`:

| Tự làm gì với chính mình | Kết quả | Vì sao |
|---|---|---|
| Đổi **email** | 409 `"Use PATCH /accounts/me to change your own email"` | Nếu không thì admin né được yêu cầu `currentPassword` của `/accounts/me` |
| Đổi **vai trò** | 409 `"You cannot change the role of your own account"` | Tự tước quyền mình; nếu là admin duy nhất thì không ai nâng lại được |
| Đổi **trạng thái** | 409 `"You cannot change the status of your own account"` | Tự nhốt mình ra ngoài |
| **Xoá** tài khoản | 409 `"You cannot delete your own account"` | Không có đường lùi nào ngoài sửa tay trong database |

**Đổi vai trò người khác ⇒ bắt buộc thu hồi mọi phiên của họ.** Vai trò nằm trong claim `scope`
của access token đang lưu hành, filter **không tra lại database mỗi request**. Thiếu bước này
thì người vừa bị gỡ quyền admin **vẫn làm admin thêm 15 phút**.

**Đổi trạng thái sang trạng thái chặn ⇒ thu hồi phiên.** Mở khoá thì không, lúc đó cũng chẳng
còn phiên nào để đá. `lockedUntil` chỉ có nghĩa với `LOCKED`; chuyển sang trạng thái khác thì
nó bị xoá — giữ lại là dữ liệu rác, và nguy hiểm hơn: lần khoá sau có thể tự mở vì dính mốc cũ.
`lockedUntil` phải là **thời điểm trong tương lai** (`@Future`); để trống = khoá vô thời hạn.

**Xoá tài khoản**: hỏi `OrderService` xem còn đơn hàng không (hỏi qua service chứ không tiêm
`OrderRepository` — dữ liệu module nào module đó giữ), còn đơn ⇒ 409 với lý do thật thay vì để
database ném ra một câu "Data integrity violation" chung chung. Rồi thu hồi phiên → xoá hồ sơ →
xoá tài khoản.

**`GET /accounts`** phân trang, đã gắn `@EntityGraph("role")` cho **cả bản `List` lẫn bản
`Pageable`** (hai method khác nhau, bản phân trang không kế thừa) — không có N+1.

## H.2 — Ca test

> Đăng nhập `admin@example.com` / `Admin@12345`. Lấy id của chính mình qua `GET /accounts/me`.

| # | Thao tác | Kết quả mong muốn | ✓ |
|---|---|---|---|
| H-1 | `POST /accounts` `{"email":"staff@gmail.com","password":"Staff@123","roleId":"<id USER>"}` | **201** `"Account created successfully"`, đồng thời **tự dựng hồ sơ rỗng** | ☐ |
| H-2 | `GET /accounts?page=0&size=20` | **200**, có `totalElements`/`totalPages`; bật `show-sql` thấy **1 câu SELECT** cho cả trang, không phải 1 + N | ☐ |
| H-3 | `GET /accounts/{id}` | **200** | ☐ |
| H-4 | `PATCH /accounts/{id-chính-mình}` `{"email":"khac@gmail.com"}` | **409** `"Use PATCH /accounts/me to change your own email"` | ☐ |
| H-5 | Ngay sau H-4: `PATCH /accounts/me` với `currentPassword` đúng, cùng email đó | **200** — đường chính vẫn thông, chốt H-4 không chặn nhầm | ☐ |
| H-6 | `PATCH /accounts/{id-chính-mình}` `{"roleId":"<id USER>"}` | **409** `"You cannot change the role of your own account"` | ☐ |
| H-7 | `PATCH /accounts/{id-chính-mình}/status` `{"status":"LOCKED"}` | **409** `"You cannot change the status of your own account"` | ☐ |
| H-8 | `DELETE /accounts/{id-chính-mình}` | **409** `"You cannot delete your own account"` | ☐ |
| H-9 | `PATCH /accounts/{id-người-khác}` đổi `roleId` | **200**; người đó gọi API bằng access token cũ ⇒ **401** `"Session has been revoked"` ✅ bịt lỗ 15 phút | ☐ |
| H-10 | `PATCH /accounts/{id-người-khác}` đổi email | **200**, phiên của họ cũng bị thu hồi | ☐ |
| H-11 | `PATCH /accounts/{id}/status` `{"status":"LOCKED","lockedUntil":"2020-01-01T00:00:00Z"}` | **400** `"Locked until must be a future moment"` | ☐ |
| H-12 | `PATCH /accounts/{id}/status` `{"status":"LOCKED"}` (không có `lockedUntil`) | **200** — khoá vô thời hạn; người đó login ⇒ **403** | ☐ |
| H-13 | Đổi tài khoản đó về `ACTIVE` rồi `SELECT locked_until` | **NULL** — mốc cũ bị dọn, không để lần khoá sau tự mở | ☐ |
| H-14 | `DELETE /accounts/{id}` của tài khoản **đã có đơn hàng** | **409** `"Cannot delete account with existing orders: ..."` | ☐ |
| H-15 | `DELETE /accounts/{id}` của tài khoản chưa có đơn | **200**; hồ sơ cũng bị xoá, phiên bị thu hồi | ☐ |
| H-16 | `POST /accounts` với `roleId` không tồn tại | **404** `"Role not found with id: ..."` | ☐ |
| H-17 | `POST /accounts` với `password` = `"12345678"` | **400** — luật chữ hoa/thường/số áp cả đường admin | ☐ |

---

# I. PHÂN QUYỀN

## I.1 — Nghiệp vụ

Chuỗi phân quyền chạy qua 5 chặng:
`@EnableMethodSecurity` → claim `scope` trong access token → `SimpleGrantedAuthority` →
tiền tố `ROLE_` → quyết định 403.

- **Whitelist nghiệp vụ** (công khai, không cần token): `/auth/**`, `/categories`,
  `/categories/**`, `/products`, `/products/**`.
- ⚠️ **Whitelist không phân biệt HTTP method.** `/products/**` công khai nghĩa là `POST /products`
  cũng lọt qua tầng filter — nó được chặn ở tầng sau bằng `@PreAuthorize` trên từng method.
  Đó là lý do mọi endpoint ghi của product/category **bắt buộc** phải có annotation riêng.
- Filter **bỏ qua hoàn toàn `/auth/**`**: cookie `access_token` có path `/` nên trình duyệt gửi
  kèm mọi request; token hết hạn mà filter vẫn chặn thì người dùng **không thể đăng nhập lại**.
- Tổng **21 guard ADMIN**: `/accounts` 6 · `/roles` 5 · `/products` 3 · `/categories` 3 ·
  `/orders` 3 · `/profiles` 1.

## I.2 — Ca test

| # | Thao tác | Kết quả mong muốn | ✓ |
|---|---|---|---|
| I-1 | USER gọi `GET /accounts` | **403** | ☐ |
| I-2 | USER gọi `DELETE /accounts/{id}` | **403** | ☐ |
| I-3 | USER gọi `POST /products` | **403** — whitelist không cứu được endpoint ghi | ☐ |
| I-4 | USER gọi `PATCH /orders/{id}/status` | **403** | ☐ |
| I-5 | Không token, gọi `GET /products` | **200** — whitelist công khai | ☐ |
| I-6 | Không token, gọi `GET /accounts/me` | **401** (chưa đăng nhập) chứ không phải 403 | ☐ |
| I-7 | Access token đã hết hạn, gọi `POST /auth/login` | **200** — filter bỏ qua `/auth/**`, vẫn đăng nhập lại được | ☐ |
| I-8 | Sửa một ký tự trong access token rồi gọi `/accounts/me` | **401**, và ở nhánh web cookie bị xoá | ☐ |
| I-9 | Gửi **refresh token** vào header `Authorization` rồi gọi `/accounts/me` | **401** `"Invalid token type: expected access token"` | ☐ |

---

# J. ĐỘ BỀN & CHỐNG LẠM DỤNG

## J.1 — Nghiệp vụ

Nhóm này không test nghiệp vụ mà test **những thứ chỉ hỏng khi bị ép**: chạy song song, cấu hình
sai, dịch vụ ngoài chết.

## J.2 — Đua song song khi dò mã OTP

Bản đầu tiên so mã **trước** rồi mới tăng bộ đếm. Bắn 200 request cùng lúc thì cả 200 đều kịp
đọc mã và so **trước khi** bộ đếm chạm trần ⇒ 5 lượt đoán trên giấy biến thành 200 lượt đoán
thật. Với 5 mã/giờ, xác suất chiếm tài khoản trong một tuần nhảy từ **0,4 %** lên **≈ 15 %**.

Bản hiện tại đổi sang **ĐẾM TRƯỚC — SO SAU**. `INCR` của Redis là nguyên tử nên mỗi request nhận
một số thứ tự riêng; chặn ngay tại số thứ tự thì request thứ sáu trở đi **không bao giờ chạm tới
phép so**, dù tới cùng một mili giây.

> ⚠️ **Đừng đảo lại thứ tự này.** Đảo là mở lại lỗ hổng, và build vẫn xanh.

| # | Thao tác | Kết quả mong muốn | ✓ |
|---|---|---|---|
| J-1 | Xin OTP quên mật khẩu, rồi bắn 50 request song song:<br>`seq 1 50 \| xargs -P50 -I{} curl -s -X POST localhost:8088/api/v1/auth/verify-otp -H 'Content-Type: application/json' -d '{"email":"user@example.com","otp":"00000{}"}' -o /dev/null -w '%{http_code}\n' \| sort \| uniq -c` | Tối đa **5 lần ra 400**, phần còn lại **429** | ☐ |
| J-2 | `redis-cli GET "otp_attempt:reset:user@example.com"` sau J-1 | Bằng **tổng số request** — `INCR` không mất nhịp nào | ☐ |
| J-3 | Nhập sai OTP **tuần tự** đủ 5 lần | Lần thứ **5** ra 429 — hành vi cũ giữ nguyên, không bắt bấm thêm lần thứ 6 | ☐ |
| J-4 | Bắn 2 request `verify-registration` song song cùng `registrationId` và **cùng mã đúng** | Một cái **201**, một cái **409** — ràng buộc unique của database chặn, không đẻ hai tài khoản | ☐ |

## J.3 — Biến môi trường để trống

Loại lỗi đắt nhất vì **không ném ra lỗi nào** — chỉ "tự dưng không chạy". Code đã chống bằng
`hasText` thay vì `!= null` cho nhóm cookie và Brevo.

| # | Thao tác | Kết quả mong muốn | ✓ |
|---|---|---|---|
| J-5 | `AUTH_COOKIE_PATH=` (có dòng, bỏ trống), khởi động, login web | **200** và DevTools thấy `Path = /`. Trước bản vá: `Path` rỗng ⇒ trình duyệt không gửi lại ⇒ **mọi request sau đó 401** | ☐ |
| J-6 | `AUTH_ACCESS_COOKIE_NAME=` rồi login | **200**, cookie tên `access_token`. Trước bản vá: **500 mỗi lần đăng nhập** | ☐ |
| J-7 | `AUTH_COOKIE_SAME_SITE=` rồi login | Header có `SameSite=Lax`, không phải `SameSite=` què | ☐ |
| J-8 | `BREVO_SENDER_EMAIL=` rồi `register` | Rơi về `no-reply@example.com` thay vì để Brevo trả 400 khó hiểu | ☐ |
| J-9 | `JWT_SIGNER_KEY=abc` (quá ngắn) rồi khởi động | **App không khởi động**, log báo khoá yếu — cố ý, lỗi phải to tiếng | ☐ |
| J-9b | `DELETE FROM roles WHERE role_name='USER'` rồi chạy `/auth/verify-registration` với mã đúng | **404** `"Default role USER is not configured"`. Mã OTP đúng vẫn **không tạo được tài khoản** — và hồ sơ chờ bị xoá, người dùng phải đăng ký lại. Bật `DATA_INIT_ENABLED=true` một lần để seed lại | ☐ |

## J.4 — Dịch vụ ngoài chết

| # | Thao tác | Kết quả mong muốn | ✓ |
|---|---|---|---|
| J-10 | Tắt Redis rồi gọi `POST /auth/login` | **503** `"Service temporarily unavailable, please try again later"` — không phải 500 | ☐ |
| J-11 | Tắt Redis rồi gọi `GET /products` | **200** — endpoint công khai không phụ thuộc Redis | ☐ |
| J-12 | `BREVO_API_KEY` sai, gọi `register` | **503** kèm tên biến còn thiếu | ☐ |
| J-13 | Chặn mạng ra Brevo, đổi mật khẩu thành công | **200** — mail báo tin chạy bất đồng bộ và tự nuốt lỗi, không làm hỏng việc đã xong | ☐ |

---

# K. PHỤ LỤC

## K.1 — Toàn bộ khoá Redis

| Khoá | TTL | Dùng để |
|---|---|---|
| `rt:{jti}` | 7 ngày | Refresh token đang sống |
| `user_rt:{userId}` | 7 ngày | Set các `jti` của một người — phục vụ logout-all |
| `logout_time:{email}` | 7 ngày | Mốc chặn mọi token phát hành trước đó |
| `login_fail:{email}` | 15 phút | Đếm đăng nhập sai theo email, trần 5 |
| `login_fail_ip:{ip}` | 15 phút | Đếm theo IP, trần 20 |
| `otp:{purpose}:{subject}` | 90s (reset) / 600s (register) | Mã 6 số |
| `otp_attempt:{purpose}:{subject}` | theo mã | Số lần nhập sai, trần 5 |
| `otp_cooldown:{purpose}:{email}` | 20s | Khoảng chờ giữa hai lần bấm gửi lại |
| `otp_send:{purpose}:{email}` | 1 giờ | Trần số lần gửi mail, trần 5 |
| `otp_ticket:{uuid}` | 300s | Vé đổi mật khẩu, dùng một lần |
| `reg:{registrationId}` | 600s | Hồ sơ đăng ký đang chờ (Hash 4 trường) |
| `password_change:{accountId}` | 5 phút | Khoảng chờ giữa hai lần đổi mật khẩu |

> `{subject}` = **email** ở luồng reset, nhưng = **registrationId** ở luồng register.
> Riêng `otp_cooldown` và `otp_send` **luôn** khoá theo email.
> `{purpose}` ∈ `{reset, register}`.

## K.2 — Mã lỗi hay gặp

| HTTP | Ý nghĩa trong khối auth |
|---|---|
| 400 | Sai định dạng đầu vào · mã OTP sai · phiên đăng ký/mã đã hết hạn |
| 401 | Chưa đăng nhập · sai email/mật khẩu · token hỏng/hết hạn/đã thu hồi · vé hết hạn · sai `currentPassword` |
| 403 | Đã đăng nhập nhưng không đủ quyền · tài khoản `LOCKED`/`DISABLED` |
| 404 | Không tìm thấy tài khoản / vai trò / hồ sơ |
| 409 | Trùng email · tài khoản chưa có mật khẩu · tự sửa chính mình trên đường admin · xoá tài khoản còn đơn |
| 429 | Khoá đăng nhập · khoảng chờ OTP · quá trần gửi mail · hết lượt nhập mã · cooldown đổi mật khẩu |
| 502 | Không gửi được mail (trừ luồng quên mật khẩu — xem §E) |
| 503 | Thiếu `BREVO_API_KEY` / `GOOGLE_CLIENT_ID` · Redis hoặc database không truy cập được |

## K.3 — Danh sách thay đổi phá vỡ dành cho Frontend

| Mã | Thay đổi |
|---|---|
| j | Đăng nhập dùng **`email`**, không còn `username` |
| s | Trường `username` **biến mất** khỏi mọi response |
| t | `POST /auth/register` nhận 4 field: `email`, `password`, `fullName`, `phone` |
| u | `AccountResponse` có thêm `provider` và `hasPassword` |
| v | Endpoint mới `POST /auth/google {idToken}` |
| w | `POST /auth/register` **không còn trả token**, trả **200** `{registrationId, email, expiresIn, resendAfter}` — FE phải chèn màn hình nhập mã |
| x | `POST /auth/verify-registration` nhận `{registrationId, otp}` — **bỏ hẳn `email`**. Bấm "gửi lại mã" trả về `registrationId` **mới**, FE phải ghi đè giá trị cũ |
| y | `PATCH /accounts/me` **bắt buộc** thêm `currentPassword` |

## K.4 — Checklist trước khi deploy

- [ ] `DATA_INIT_ENABLED=false`
- [ ] `DDL_AUTO=validate`
- [ ] Đổi `JWT_SIGNER_KEY` (≥ 32 byte, không dùng mặc định dev)
- [ ] `AUTH_COOKIE_SECURE=true`
- [ ] `FRONTEND_BASE_URL` đổi khỏi `localhost` (nhiều origin thì ngăn bằng dấu phẩy,
      **không** viết thành list YAML — xem bẫy ở `GHI_NHO_DU_AN.md` N7)
- [ ] **Quyết phương án CSRF.** Hiện `csrf` đang tắt và token đọc được từ cookie. Local
      `SameSite=Lax` nên an toàn, nhưng nếu FE khác domain phải đổi `SameSite=None` ⇒ **mọi
      endpoint ghi đều bị CSRF**. Ba đường: ① deploy FE + BE **cùng domain** qua reverse proxy
      (0 dòng code, giữ được `SameSite=Lax`) · ② bật `CookieCsrfTokenRepository`, FE gửi kèm
      header `X-XSRF-TOKEN` · ③ web bỏ cookie, dùng header `Authorization` như mobile.
- [ ] Chạy đủ 6 lệnh SQL ở §0.1 trên database thật
