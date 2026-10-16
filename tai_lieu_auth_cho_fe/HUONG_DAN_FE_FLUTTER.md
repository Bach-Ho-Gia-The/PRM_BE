# HƯỚNG DẪN NỐI API AUTH — FLUTTER MOBILE

> Viết cho anh em Flutter. Mục tiêu: đọc xong là nối được, không phải hỏi backend từng cái.
> Mỗi luồng gồm **sơ đồ bước → request/response thật → bảng lỗi đầy đủ → app phải làm gì**.
>
> Mọi chuỗi `message` trong tài liệu này là chuỗi backend **thật sự trả về** — copy ra so được từng chữ.
> Tài liệu nghiệm thu chi tiết hơn ở `TEST_AUTH_SWAGGER.md`.

---

# 0. BA ĐIỀU PHẢI ĐỌC TRƯỚC KHI GÕ DÒNG CODE ĐẦU TIÊN

## 0.1 — 🔴 Bắt buộc gắn header `X-Client-Type: mobile`

Đây là thứ dễ sai nhất và **sai thì không có lỗi nào báo cho bạn biết**.

Backend phục vụ cả web lẫn mobile bằng cùng một endpoint, phân biệt nhau đúng bằng header này:

| | Web (mặc định, khi **thiếu** header) | Mobile (`X-Client-Type: mobile`) |
|---|---|---|
| Token nằm ở đâu | HttpOnly cookie `access_token` / `refresh_token` | Trong **body** JSON |
| `result.accessToken` | **không có trong JSON** (bị ẩn) | chuỗi JWT |
| `result.refreshToken` | **không có trong JSON** (bị ẩn) | chuỗi JWT |

Quên header ⇒ login vẫn trả **200 "Login successful"**, có `userId`, có `email`, có `roleName` —
nhìn như thành công hoàn hảo. Chỉ có `accessToken` là `null`. Bạn sẽ ngồi debug nhầm hướng rất lâu.

> Vì sao lại ẩn chứ không trả cả hai: cookie đang là `HttpOnly` để JavaScript bên web không đọc
> được. Nếu token vẫn nằm trong body thì `HttpOnly` thành vô nghĩa.

**Gắn một lần ở interceptor, đừng gắn tay từng chỗ:**

```dart
final dio = Dio(BaseOptions(
  baseUrl: 'https://api.example.com/api/v1',   // ⚠ có /api/v1
  headers: {'X-Client-Type': 'mobile'},         // ⚠ gắn TOÀN CỤC
  validateStatus: (s) => s != null && s < 500,  // để tự đọc body lỗi 4xx
));
```

Bốn endpoint đọc header này: `/auth/login`, `/auth/verify-registration`, `/auth/refresh`, `/auth/google`.
Gắn toàn cục thì không bao giờ sót.

## 0.2 — Context path `/api/v1`

Mọi đường dẫn trong tài liệu này viết **tương đối**. Đường thật = `{host}` + `/api/v1` + đường dẫn.
`POST /auth/login` ⇒ `https://api.example.com/api/v1/auth/login`.

Đã chốt `CONTEXT_PATH=/api/v1` (kiểm ngày 16/10/2026). Đổi thì backend sẽ báo trước — nó nằm
ở một biến môi trường nên đừng rải `/api/v1` khắp code, để đúng một chỗ trong `baseUrl`.

## 0.3 — Khuôn phản hồi: mọi response đều giống nhau

Thành công hay thất bại, 200 hay 500, **luôn luôn** là khuôn này:

```jsonc
{
  "code": 200,                       // == HTTP status
  "message": "Login successful",     // câu để hiển thị / đối chiếu
  "result": { },                     // dữ liệu — KHÔNG CÓ khi lỗi
  "errors": { },                     // CHỈ CÓ khi lỗi validate (xem §7.1)
  "timestamp": "2026-10-16T04:21:33.918Z",
  "path": "/api/v1/auth/login"
}
```

`result` và `errors` dùng `NON_NULL`, tức là **field vắng mặt hẳn** khi null chứ không phải `"result": null`.
Dart decode ra `map['result']` sẽ là `null` — xử lý được, nhưng đừng viết test khẳng định key đó tồn tại.

```dart
class ApiResponse<T> {
  final int code;
  final String message;
  final T? result;
  final Map<String, String>? errors;   // field -> câu lỗi
  final String? path;

  factory ApiResponse.from(Map<String, dynamic> j, T Function(dynamic)? parse) =>
      ApiResponse(
        code: j['code'] as int,
        message: j['message'] as String,
        result: j['result'] == null || parse == null ? null : parse(j['result']),
        errors: (j['errors'] as Map?)?.cast<String, String>(),
        path: j['path'] as String?,
      );
}
```

---

# 1. BẢN ĐỒ ENDPOINT — CÁI NÀO APP DÙNG, CÁI NÀO KHÔNG

## 1.1 — `/auth` (10 endpoint, tất cả **công khai**, không cần token)

| Endpoint | App mobile dùng? | Màn hình |
|---|---|---|
| `POST /auth/register` | ✅ | Đăng ký — bước 1 |
| `POST /auth/verify-registration` | ✅ | Đăng ký — bước 2 (nhập OTP) |
| `POST /auth/login` | ✅ | Đăng nhập |
| `POST /auth/google` | ✅ | Nút "Tiếp tục với Google" |
| `POST /auth/refresh` | ✅ | Interceptor, người dùng không thấy |
| `POST /auth/logout` | ✅ | Nút Đăng xuất |
| `POST /auth/forgot-password` | ✅ | Quên mật khẩu — bước 1 |
| `POST /auth/verify-otp` | ✅ | Quên mật khẩu — bước 2 |
| `POST /auth/reset-password` | ✅ | Quên mật khẩu — bước 3 |
| `POST /auth/logout-all` | ⚠️ **tuỳ** | Chỉ cần nếu làm màn "Thiết bị đang đăng nhập" / "Đăng xuất mọi nơi". Không làm màn đó thì **bỏ qua endpoint này** — app vẫn chạy đủ. |

## 1.2 — `/accounts` + `/profiles`

| Endpoint | App mobile dùng? | Ghi chú |
|---|---|---|
| `GET /accounts/me` | ✅ | Lấy email, `provider`, `hasPassword` |
| `PATCH /accounts/me` | ✅ | Đổi email |
| `PATCH /accounts/me/password` | ✅ | Đổi mật khẩu |
| `GET /profiles/me` | ✅ | Màn hồ sơ |
| `PATCH /profiles/me` | ✅ | Sửa hồ sơ |
| `GET /profiles/{accountId}` | ❌ | **ADMIN**. App khách không gọi được — 403. |
| `POST /accounts` | ❌ | **ADMIN** tạo hộ tài khoản |
| `GET /accounts` | ❌ | **ADMIN** danh sách |
| `GET /accounts/{id}` | ❌ | **ADMIN** |
| `PATCH /accounts/{id}` | ❌ | **ADMIN** |
| `PATCH /accounts/{id}/status` | ❌ | **ADMIN** khoá / mở |
| `DELETE /accounts/{id}` | ❌ | **ADMIN** |

> **7 endpoint ❌ ở trên là của app quản trị, không phải của app khách hàng.** Đừng viết màn hình
> cho chúng. Nếu sau này có app admin riêng thì mới nối, và nối y hệt — chỉ khác là tài khoản
> đăng nhập phải có `roleName: "ADMIN"`.

## 1.3 — Hai điểm nữa về phạm vi

**CORS không liên quan tới app.** CORS là cơ chế của **trình duyệt**. Flutter gọi HTTP trực tiếp
nên không bao giờ dính lỗi CORS — bên web báo bị chặn thì đó là việc của backend + FE web,
đừng để bị kéo vào.

**Toàn bộ cơ chế cookie cũng không liên quan tới app.** Backend có `access_token` / `refresh_token`
cookie, có `SameSite`, có `Secure`, có logic xoá cookie khi lỗi. Mobile gửi
`X-Client-Type: mobile` là **không nhận cookie nào cả**. Bỏ qua hết, kể cả đừng cài `cookie_jar`.

**Không có API "đổi mật khẩu bằng OTP".** Đổi email và đổi mật khẩu đều xác minh bằng
**mật khẩu hiện tại**, không gửi mã. OTP chỉ dùng ở hai chỗ: đăng ký, và quên mật khẩu.

---

# 2. LUỒNG ĐĂNG KÝ — 2 BƯỚC, CÓ CỔNG OTP

## 2.1 — Hiểu nghiệp vụ trước

🔴 **Bấm "Đăng ký" KHÔNG tạo tài khoản.** Hồ sơ chỉ nằm tạm trong Redis 10 phút. Tài khoản thật
sự ra đời ở bước nhập OTP. Hệ quả cho app:

- Sau `/auth/register` **chưa có token**, chưa đăng nhập được, không được điều hướng vào Home.
- Người dùng tắt app giữa chừng ⇒ coi như chưa từng đăng ký, database sạch.
- Bước 2 mới trả `LoginResponse` + token, giống hệt login.

**Ví dụ dễ hiểu:** giống đặt chỗ nhà hàng. Gọi điện giữ bàn (bước 1) chưa phải là có bàn — tới
nơi báo tên (bước 2) mới thật sự ngồi được. Quá 10 phút không tới thì bàn nhả ra cho người khác.

## 2.2 — Sơ đồ

```
┌─ Màn ĐĂNG KÝ ──────────────────────────────────────────────┐
│ POST /auth/register                                        │
│ { email, password, fullName, phone }                       │
│                                                            │
│ 200 → { registrationId, email, expiresIn, resendAfter }    │
│       ⚠ CHƯA có tài khoản, CHƯA có token                   │
└────────────────────────┬───────────────────────────────────┘
                         │ giữ registrationId trong state
                         ▼
┌─ Màn NHẬP OTP ─────────────────────────────────────────────┐
│ đếm ngược expiresIn (600s) · khoá nút gửi lại resendAfter   │
│                                                            │
│ POST /auth/verify-registration                             │
│ { registrationId, otp }                                    │
│                                                            │
│ 201 → LoginResponse (có token) → TÀI KHOẢN VỪA ĐƯỢC TẠO    │
└────────────────────────┬───────────────────────────────────┘
                         ▼
                   lưu token → vào Home
```

## 2.3 — Bước 1: `POST /auth/register`

```jsonc
// Request
{
  "email": "an@example.com",
  "password": "Abc@12345",      // ≥8 ký tự, phải có hoa + thường + số
  "fullName": "Nguyễn Văn An",
  "phone": "0901234567"          // đúng 10 số, bắt đầu bằng 0
}
```

```jsonc
// 200 OK
{
  "code": 200,
  "message": "A verification code has been sent to your email",
  "result": {
    "registrationId": "b4f1c2e8-9a0d-4c11-8e77-2f3a5d6b7c88",
    "email": "an@example.com",
    "expiresIn": 600,     // giây — mã sống 10 phút
    "resendAfter": 20     // giây — khoá nút "Gửi lại mã" 20 giây
  }
}
```

> `expiresIn` và `resendAfter` trả về để app **đếm ngược theo số server gửi**, đừng hardcode.
> Backend đổi cấu hình là app tự theo, không phải build lại.

**Bảng lỗi đầy đủ:**

| HTTP | `message` | Vì sao | App làm gì |
|---|---|---|---|
| 400 | `"Request validation failed"` | Sai định dạng field. Có `errors` (xem §7.1) | Tô đỏ đúng ô |
| 409 | `"Email already in use: an@example.com"` | Email đã có tài khoản | Hiện "Email này đã được đăng ký" + nút chuyển sang Đăng nhập |
| 429 | `"Please wait 14 more seconds before requesting a new code"` | Bấm lại quá nhanh | **Bóc số 14 ra** để đếm ngược, đừng hiện nguyên câu tiếng Anh |
| 429 | `"Too many verification emails requested for this address. Please try again later."` | Quá 5 mail/giờ cho email đó | Khuyên kiểm tra hộp thư rác, thử lại sau 1 tiếng |
| 503 | `"Service temporarily unavailable, please try again later"` | Redis chết | Hiện lỗi mạng chung, cho nút Thử lại |

<details>
<summary>Bóc số giây từ câu 429 (dùng lại ở nhiều chỗ)</summary>

```dart
/// "Please wait 14 more seconds before requesting a new code" -> 14
int? secondsFrom(String message) =>
    int.tryParse(RegExp(r'(\d+)\s+(more\s+)?seconds?').firstMatch(message)?.group(1) ?? '');
```
Dùng được cho cả 429 của `/auth/register`, `/auth/login` và `/accounts/me/password`.
</details>

## 2.4 — Bước 2: `POST /auth/verify-registration`

```jsonc
// Request
{ "registrationId": "b4f1c2e8-...", "otp": "043921" }
```

```jsonc
// 201 Created  ⚠ 201 chứ không phải 200
{
  "code": 201,
  "message": "Registration completed",
  "result": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "eyJhbGciOi...",
    "tokenType": "Bearer",
    "expiresIn": 900,                                   // GIÂY (15 phút)
    "userId": "7f1c2e8b-4a0d-4c11-8e77-2f3a5d6b7c88",
    "email": "an@example.com",
    "roleName": "USER"
  }
}
```

**Bảng lỗi đầy đủ:**

| HTTP | `message` | Vì sao | App làm gì |
|---|---|---|---|
| 400 | `"OTP must be exactly 6 digits"` | OTP không phải 6 chữ số | Chặn ngay ở client, đừng để gọi API |
| 400 | `"Incorrect verification code"` | Gõ sai mã | Giữ nguyên màn, xoá ô nhập, cho thử lại. **Còn tối đa 5 lần** |
| 400 | `"The verification code has expired. Please request a new one."` | Mã hết hạn nhưng hồ sơ còn | Hiện nút "Gửi lại mã" |
| 400 | `"Your registration session has expired. Please sign up again."` | Quá 10 phút, hồ sơ đã bay | 🔴 **Đá về màn Đăng ký**, xoá `registrationId` |
| 429 | `"Too many incorrect attempts. Your registration was cancelled, please sign up again."` | Sai 5 lần | 🔴 **Đá về màn Đăng ký**, xoá `registrationId` |
| 409 | `"Account email already exists: an@example.com"` | Trong 10 phút chờ có người khác chiếm email đó | Đá về Đăng ký, báo email đã bị dùng |

> Phân biệt hai câu 400 ở trên cho kỹ: **"verification code has expired"** = còn cứu được, bấm
> gửi lại. **"registration session has expired"** = hết cứu, phải khai lại từ đầu.

## 2.5 — 🔴 Nút "Gửi lại mã": `registrationId` ĐỔI

Gửi lại mã **không phải** một endpoint riêng — gọi lại đúng `POST /auth/register` với y nguyên
4 field cũ. Và đây là chỗ bẫy:

```
register lần 1 → registrationId = AAA, mã 111111
register lần 2 → registrationId = BBB, mã 222222      ← PHẢI ghi đè AAA

Gửi { registrationId: AAA, otp: "222222" } → 400 "Incorrect verification code"
```

Mã `222222` là đúng, nhưng nó thuộc hồ sơ `BBB`. App giữ `AAA` thì vĩnh viễn sai.

```dart
// Trong state của màn OTP
String registrationId;

Future<void> resend() async {
  final r = await api.register(savedForm);              // y nguyên form cũ
  registrationId = r.result.registrationId;             // 🔴 BẮT BUỘC ghi đè
  startResendCountdown(r.result.resendAfter);
}
```

> Vì sao thiết kế vậy: khoá hồ sơ theo email sẽ bị lợi dụng để chiếm tài khoản — người A khai
> email của người B, rồi chờ người B thật sự đăng ký, mã của B lại mở được hồ sơ của A. Mỗi lần
> bấm sinh một `registrationId` riêng thì mã chỉ mở đúng hồ sơ sinh ra nó.

## 2.6 — Một chuyện app phải biết về quyền riêng tư

`/auth/register` trả **409 kèm nguyên email** khi email đã tồn tại. Nghĩa là ai cũng dò được
"email X đã đăng ký hay chưa" qua endpoint này. Đây là **đánh đổi có chủ ý** — không báo thì UX
đăng ký quá tệ. Bù lại `/auth/login` và `/auth/forgot-password` đã bịt kín (xem §3.4, §5.2).

App chỉ cần nhớ: **đừng** hiển thị thêm thông tin gì ngoài câu "Email này đã được đăng ký".

---

# 3. LUỒNG ĐĂNG NHẬP

## 3.1 — `POST /auth/login`

```jsonc
// Request  ⚠ ĐĂNG NHẬP BẰNG EMAIL, không có username
{ "email": "an@example.com", "password": "Abc@12345" }
```

Response 200 giống hệt `LoginResponse` ở §2.4, `message` là `"Login successful"`.

> 🔴 **Cột `username` đã bị xoá khỏi database.** Nếu bạn đọc tài liệu cũ hoặc code mẫu cũ thấy
> `{ "username": ..., "password": ... }` thì đó là bản đã chết. Danh tính đăng nhập = **email**.
> Tên hiển thị nằm ở `profiles.fullName`, lấy qua `GET /profiles/me`.

## 3.2 — Bảng lỗi đầy đủ

| HTTP | `message` | Vì sao | App làm gì |
|---|---|---|---|
| 400 | `"Request validation failed"` | Thiếu field / email sai định dạng | Tô đỏ ô |
| 401 | `"Invalid email or password"` | Sai mật khẩu **hoặc** email không tồn tại | Hiện đúng câu đó. **Đừng đoán** là "email chưa đăng ký" |
| 403 | `"Account is not allowed to sign in"` | Tài khoản bị khoá hoặc vô hiệu hoá | "Tài khoản đang bị hạn chế, liên hệ hỗ trợ" |
| 429 | `"Too many failed login attempts. Please try again in 843 seconds."` | Chặn dò mật khẩu | Bóc số giây, khoá nút Đăng nhập, đếm ngược |
| 503 | `"Service temporarily unavailable, please try again later"` | Redis / DB chết | Lỗi mạng chung |

## 3.3 — Hiểu cơ chế chặn dò mật khẩu (để không báo sai cho người dùng)

Có **hai bộ đếm chạy song song**:

| Đếm theo | Ngưỡng | Cửa sổ | Chặn kiểu tấn công nào |
|---|---|---|---|
| Email | **5** lần sai | 15 phút | Nhắm một tài khoản, rải mật khẩu từ nhiều IP |
| Địa chỉ IP | **20** lần sai | 15 phút | Lấy một mật khẩu phổ biến quét qua hàng nghìn tài khoản |

Hai điều app cần biết:

1. **Đăng nhập đúng chỉ xoá bộ đếm theo email, KHÔNG xoá bộ đếm theo IP.** Người dùng thật có
   thể đăng nhập đúng xong vẫn dính 429 ở lần kế — vì IP đó (ví dụ wifi quán cà phê, hoặc cả
   công ty đi chung NAT) đã sai 20 lần. Hiện đúng câu server trả, đừng tự diễn giải.
2. **Sai 5 lần là bị khoá 15 phút, không phải bị khoá tài khoản.** Đừng hiện "Tài khoản của bạn
   đã bị khoá" — người dùng sẽ gọi hỗ trợ trong khi chỉ cần chờ.

## 3.4 — Vì sao sai email và sai mật khẩu trả cùng một câu

Nếu server trả "email không tồn tại" riêng, kẻ xấu chạy một vòng lặp qua 1 triệu email là biết
được email nào có tài khoản trong hệ thống này. Danh sách đó bán được tiền, và là bước đầu của
tấn công nhắm mục tiêu.

Nên app **tuyệt đối không được** tự tách hai trường hợp bằng cách gọi thêm API khác để dò.

---

# 4. ĐĂNG NHẬP GOOGLE

## 4.1 — Luồng

```
┌─ App Flutter ─────────────────────────────────────────┐
│ google_sign_in → GoogleSignInAuthentication.idToken   │
│                  ⚠ idToken, KHÔNG PHẢI accessToken    │
└──────────────────────┬────────────────────────────────┘
                       ▼
          POST /auth/google  { "idToken": "eyJ..." }
                       ▼
┌─ Backend ─────────────────────────────────────────────┐
│ Xác minh 4 thứ với Google: chữ ký RS256 · iss · aud · │
│ exp. Rồi tạo / gộp tài khoản, phát token CỦA HỆ THỐNG │
└──────────────────────┬────────────────────────────────┘
                       ▼
          200 LoginResponse (giống hệt login thường)
```

> 🔴 Hai token dễ nhầm: `google_sign_in` cho bạn **cả** `accessToken` lẫn `idToken`. Backend chỉ
> nhận **`idToken`** (JWT ba đoạn, bắt đầu bằng `eyJ`). Gửi nhầm `accessToken` ⇒ 401
> `"Invalid Google credential"`.

```dart
final account = await GoogleSignIn(
  scopes: ['email', 'profile'],
  serverClientId: GOOGLE_CLIENT_ID,   // 🔴 PHẢI trùng GOOGLE_CLIENT_ID của backend
).signIn();
final idToken = (await account!.authentication).idToken;
```

`serverClientId` phải là **Web client ID** trong Google Cloud Console — cùng một giá trị backend
đặt ở biến `GOOGLE_CLIENT_ID`. Dùng Android client ID là `aud` không khớp ⇒ 401. Xin backend
giá trị này, đừng tự lấy.

## 4.2 — Ba trường hợp gộp tài khoản

| Tình huống | Backend làm gì | Hệ quả app phải biết |
|---|---|---|
| Email **chưa có** trong hệ thống | Tạo tài khoản mới, `provider = GOOGLE`, `password = NULL` | `GET /accounts/me` trả `hasPassword: false`. **Ẩn nút "Đổi mật khẩu"** |
| Email **đã có** (đăng ký thường) | Gắn `provider_id` vào tài khoản cũ, **giữ `provider = LOCAL`** | Người dùng đăng nhập được bằng **cả hai** đường, cùng một tài khoản |
| Đã login Google lần trước | Nhận ra qua `provider_id`, không tạo trùng | Bình thường |

> Vì sao định danh bằng `provider_id` (tức claim `sub` của Google) chứ không phải email: Google
> nói rõ email có thể đổi theo thời gian và không đảm bảo duy nhất. `sub` thì không đổi.

## 4.3 — Bảng lỗi đầy đủ

| HTTP | `message` | Vì sao | App làm gì |
|---|---|---|---|
| 400 | `"idToken is required"` | Gửi chuỗi rỗng | Kiểm trước khi gọi |
| 400 | `"idToken is too long"` | Dài hơn 4096 ký tự | Gần như chắc chắn gửi nhầm loại token |
| 401 | `"Invalid Google credential"` | Chữ ký sai / `aud` sai / hết hạn / gửi nhầm `accessToken` | "Đăng nhập Google thất bại, thử lại". Gặp lúc dev ⇒ kiểm `serverClientId` |
| 403 | `"Your Google account email has not been verified"` | Tài khoản Google Workspace chưa xác minh email | "Tài khoản Google này chưa xác minh email, hãy dùng email + mật khẩu" |
| 403 | `"Account is not allowed to sign in"` | Tài khoản trong hệ thống đã bị khoá | Giống 403 của login thường |
| 503 | `"Google sign-in is not configured. Please set GOOGLE_CLIENT_ID."` | **Backend thiếu cấu hình** | Không phải lỗi của app. Báo backend ngay |

> 401 ở trên cố tình trả **cùng một câu** cho 4 nguyên nhân khác nhau. Lúc dev thấy khó chịu,
> nhưng nói rõ "sai `aud`" là chỉ đường cho kẻ tấn công. Muốn biết lý do thật thì đọc log backend.

---

# 5. QUÊN MẬT KHẨU — 3 BƯỚC

## 5.1 — Sơ đồ

```
Bước 1  POST /auth/forgot-password   { email }
        → 200 LUÔN LUÔN (xem 5.2)        · mã 6 số sống 90 giây
                     ▼
Bước 2  POST /auth/verify-otp        { email, otp }
        → 200 { ticket, expiresIn:300 }  · mã chết, đổi lấy VÉ sống 5 phút
                     ▼
Bước 3  POST /auth/reset-password    { ticket, newPassword }
        → 200 "Password has been reset. Please sign in again."
                     ▼
        🔴 MỌI THIẾT BỊ BỊ ĐĂNG XUẤT → đá về màn Đăng nhập
```

> Vì sao phải có **vé** mà không đổi mật khẩu thẳng ở bước 2: mã OTP chỉ sống 90 giây, không đủ
> cho người dùng nghĩ ra và gõ hai lần mật khẩu mới. Đổi lấy vé 5 phút là tách "chứng minh bạn
> đọc được hộp thư" khỏi "gõ mật khẩu mới".

## 5.2 — 🔴 Bước 1 LUÔN trả 200, kể cả email không tồn tại

```jsonc
// 200 — email có thật, email bịa, email đang bị khoá: GIỐNG HỆT NHAU
{
  "code": 200,
  "message": "If that email is registered, a verification code has been sent",
  "path": "/api/v1/auth/forgot-password"
}
```

Hệ quả cho app: **không thể biết email có tồn tại hay không, và đừng cố biết.** Cứ chuyển thẳng
sang màn nhập OTP. Người gõ nhầm email sẽ tự nhận ra vì không có mã nào tới.

Câu hiển thị nên bám sát server: *"Nếu email này đã đăng ký, chúng tôi đã gửi mã xác minh tới đó."*

> Cùng một lý do với §3.4. Và không chỉ nội dung giống nhau — backend còn **nuốt cả lỗi gửi mail**
> ở luồng này. Vì nếu email thật trả 502 còn email bịa trả 200, thì chính cái chênh lệch đó lại
> trở thành máy dò, dù câu chữ giống hệt.
>
> Mặt trái app phải chấp nhận: **Brevo chết thì người dùng không nhận được mã mà app không hay
> biết gì.** Không có cách nào phát hiện từ phía client. Cho nút "Gửi lại mã" và màn hỗ trợ.

| HTTP | `message` | Khi nào |
|---|---|---|
| 200 | `"If that email is registered, a verification code has been sent"` | **Mọi trường hợp bình thường** |
| 400 | `"Request validation failed"` | Email sai định dạng (`errors.email`) |
| 503 | `"Service temporarily unavailable, please try again later"` | Redis chết |

Lưu ý: luồng này **không trả 429**, kể cả khi bấm liên tục. Bị hãm thì backend im lặng không gửi
mail — vì báo "đang trong thời gian chờ" cũng là xác nhận email có thật. App tự khoá nút gửi lại
**20 giây** bằng timer phía client.

## 5.3 — Bước 2: `POST /auth/verify-otp`

```jsonc
// Request
{ "email": "an@example.com", "otp": "043921" }

// 200
{
  "code": 200,
  "message": "Verification successful",
  "result": { "ticket": "9c2f...-...-...", "expiresIn": 300 }
}
```

| HTTP | `message` | App làm gì |
|---|---|---|
| 400 | `"OTP must be exactly 6 digits"` | Chặn ở client |
| 400 | `"Incorrect verification code"` | Xoá ô nhập, cho thử lại (tối đa 5 lần) |
| 400 | `"The verification code has expired. Please request a new one."` | Mã quá 90 giây → nút Gửi lại |
| 429 | `"Too many incorrect attempts. Please request a new code."` | Sai 5 lần → mã bị huỷ, **quay lại bước 1** |

> Khác với đăng ký: ở đây sai 5 lần chỉ **huỷ mã**, không huỷ gì khác — bấm gửi lại là xong,
> không phải khai lại thông tin.

## 5.4 — Bước 3: `POST /auth/reset-password`

```jsonc
// Request
{ "ticket": "9c2f...", "newPassword": "Moi@12345" }

// 200
{ "code": 200, "message": "Password has been reset. Please sign in again." }
```

| HTTP | `message` | Vì sao | App làm gì |
|---|---|---|---|
| 400 | `"Password must contain at least one uppercase letter, one lowercase letter and one digit"` | Mật khẩu yếu | Tô đỏ, nêu rõ 3 yêu cầu **trước khi** gửi |
| 400 | `"Password must be between 8 and 100 characters"` | Quá ngắn/dài | Như trên |
| 401 | `"The reset session has expired. Please start again."` | Vé hết hạn, hoặc **vé đã dùng rồi** | Đá về bước 1 |
| 403 | `"This account is not allowed to reset its password"` | Admin khoá tài khoản trong lúc đang đổi | "Tài khoản đang bị hạn chế" |

🔴 **Sau 200: xoá sạch token đã lưu và đá về màn Đăng nhập.** Đặt lại mật khẩu thu hồi **mọi phiên
trên mọi thiết bị** — kể cả thiết bị đang thao tác. Không xoá thì request kế tiếp nhận 401 và
người dùng không hiểu chuyện gì.

**Vé chỉ dùng được một lần.** Gọi lại cùng `ticket` → 401. Đừng retry tự động ở bước này.

## 5.5 — Tài khoản Google dùng luồng này để ĐẶT mật khẩu lần đầu

Người đăng nhập bằng Google có `password = NULL`. Họ muốn thêm mật khẩu để đăng nhập cả hai
đường thì đi đúng luồng quên mật khẩu này — backend cho phép, và nó là **con đường duy nhất**.

Backend đã nói thẳng điều đó trong câu lỗi khi họ bấm nhầm vào Đổi mật khẩu:
`409 "This account has no password yet. Use the forgot-password flow to set one."`

**App nên làm tốt hơn là đợi lỗi:** đọc `hasPassword` từ `GET /accounts/me`, nếu `false` thì
đổi nhãn nút thành **"Đặt mật khẩu"** và dẫn thẳng sang luồng quên mật khẩu.

---

# 6. VÒNG ĐỜI TOKEN — PHẦN QUAN TRỌNG NHẤT

## 6.1 — Hai token, hai vai trò

| | Access token | Refresh token |
|---|---|---|
| Sống | **15 phút** | **7 ngày** |
| Dùng để | Gắn `Authorization: Bearer` vào mọi request | Đổi lấy cặp token mới |
| Gửi đi đâu | Mọi endpoint cần đăng nhập | **Chỉ** `/auth/refresh` và `/auth/logout` |

`expiresIn: 900` trong `LoginResponse` là **giây** của access token (chuẩn OAuth2 RFC 6749).
Đừng nhầm thành mili giây — nhầm ra 900ms thì app refresh liên tục.

## 6.2 — 🔴 Refresh token XOAY VÒNG: dùng một lần là chết

Đây là cơ chế dễ làm hỏng app nhất nếu không hiểu.

```
RT1 ──/auth/refresh──► { AT2, RT2 }      RT1 CHẾT NGAY LẬP TỨC
RT1 ──/auth/refresh──► 401 "Invalid or expired refresh token"
```

Backend thu hồi RT cũ **trước khi** phát RT mới. Hai hệ quả:

1. **Phải lưu RT mới đè lên RT cũ sau mỗi lần refresh.** Quên ghi đè ⇒ lần refresh sau 401 ⇒
   người dùng bị đá ra sau đúng 15 phút dùng app. Lỗi này rất hay gặp và rất khó đoán.
2. **Không được để hai request refresh chạy song song.** 5 API cùng nhận 401 cùng lúc ⇒ 5 lời
   gọi refresh ⇒ cái đầu thành công, 4 cái sau dùng RT đã chết ⇒ 401 ⇒ đá người dùng ra oan.
   **Bắt buộc gom chung một lần refresh** (xem code §6.4).

> Vì sao thiết kế khó chịu vậy: refresh token sống 7 ngày. Nếu dùng lại được nhiều lần, ai chép
> được nó một lần là có 7 ngày truy cập. Xoay vòng khiến token bị trộm chỉ dùng được tới lần
> refresh kế tiếp của người dùng thật.

## 6.3 — `POST /auth/refresh`

```jsonc
// Request — mobile PHẢI để refresh token trong body
{ "token": "eyJhbGciOi..." }
```

Response 200 = `LoginResponse` đầy đủ, `message` là `"Token refreshed successfully"`.

| HTTP | `message` | Nguyên nhân gộp chung |
|---|---|---|
| 401 | `"Invalid or expired refresh token"` | Hết hạn · đã dùng rồi · gửi nhầm **access token** · tài khoản bị khoá · đổi mật khẩu ở thiết bị khác · bị đổi vai trò · tài khoản đã xoá |
| 503 | `"Service temporarily unavailable, please try again later"` | Redis chết |

🔴 **401 ở đây = hết đường cứu.** Xoá token, đá về Đăng nhập. **Đừng retry.**
Phân biệt rõ với 503: 503 là lỗi tạm, **giữ nguyên token**, cho nút Thử lại.

## 6.4 — Interceptor mẫu (gom refresh, chống đua)

```dart
class AuthInterceptor extends Interceptor {
  final Dio _dio;            // dio chính
  final Dio _plain;          // dio RIÊNG cho /auth/refresh, KHÔNG gắn interceptor này
  final TokenStore _store;

  Future<void>? _refreshing;  // 🔴 chìa khoá chống đua

  @override
  void onRequest(options, handler) async {
    final at = await _store.accessToken;
    if (at != null) options.headers['Authorization'] = 'Bearer $at';
    handler.next(options);
  }

  @override
  void onError(DioException e, handler) async {
    final path = e.requestOptions.path;

    // Không can thiệp vào chính luồng auth, tránh đệ quy vô tận
    final isAuthFlow = path.startsWith('/auth/');
    if (e.response?.statusCode != 401 || isAuthFlow) return handler.next(e);

    try {
      // Mọi request 401 cùng lúc đều chờ CHUNG một Future refresh
      _refreshing ??= _doRefresh();
      await _refreshing;

      final retry = e.requestOptions
        ..headers['Authorization'] = 'Bearer ${await _store.accessToken}';
      handler.resolve(await _dio.fetch(retry));
    } catch (_) {
      await _store.clear();
      navigateToLogin();          // refresh hỏng = hết đường, không retry
      handler.next(e);
    } finally {
      _refreshing = null;
    }
  }

  Future<void> _doRefresh() async {
    final rt = await _store.refreshToken;
    if (rt == null) throw Exception('no refresh token');

    final r = await _plain.post('/auth/refresh',
        data: {'token': rt},
        options: Options(headers: {'X-Client-Type': 'mobile'}));

    final d = r.data['result'];
    // 🔴 LƯU CẢ HAI — refresh token đã xoay vòng
    await _store.save(d['accessToken'], d['refreshToken']);
  }
}
```

Ba chỗ không được bỏ:

- `_plain` là **Dio riêng** không gắn interceptor này. Dùng chung ⇒ refresh lỗi 401 ⇒ gọi lại
  refresh ⇒ đệ quy vô tận.
- `_refreshing ??=` là toàn bộ cơ chế chống đua. Bỏ đi là dính đúng bẫy §6.2 mục 2.
- `_store.save(cả hai)` — quên `refreshToken` là dính bẫy §6.2 mục 1.

## 6.5 — Refresh chủ động (nên làm)

Đợi 401 rồi mới refresh là cách tối thiểu. Mượt hơn: trước khi gửi request, nếu access token còn
dưới 60 giây thì refresh trước. Lưu thời điểm hết hạn lúc nhận token:

```dart
final expiresAt = DateTime.now().add(Duration(seconds: result['expiresIn'] as int));
```

Vẫn phải giữ nhánh xử lý 401 ở §6.4 — token có thể chết sớm hơn hạn (xem §6.6).

## 6.6 — 🔴 Access token có thể chết TRƯỚC 15 phút

Backend thu hồi phiên ngay lập tức trong 6 trường hợp, không đợi token hết hạn:

| Việc xảy ra | Thu hồi | Người dùng thấy gì |
|---|---|---|
| Đổi mật khẩu (ở bất kỳ thiết bị nào) | **Mọi** thiết bị | Bị đăng xuất đột ngột |
| Đặt lại mật khẩu qua quên mật khẩu | **Mọi** thiết bị | Như trên |
| Đổi email | **Mọi** thiết bị | Như trên |
| Admin đổi vai trò tài khoản | **Mọi** thiết bị | Như trên |
| Admin khoá / vô hiệu hoá | **Mọi** thiết bị | Như trên |
| Bấm `/auth/logout` | Chỉ thiết bị đó | Bình thường |

Khi đó mọi API trả:

```jsonc
{ "code": 401, "message": "Session has been revoked. Please sign in again." }
```

App gặp chuỗi này thì **xoá token, về Đăng nhập, đừng refresh** — refresh cũng 401.

> Cơ chế: access token mang claim `sid` trỏ tới refresh token sinh ra nó. Refresh token bị xoá
> khỏi Redis ⇒ access token chết theo ngay lập tức. Nhờ vậy không cần chờ hết 15 phút.

## 6.7 — Đăng xuất

```jsonc
// POST /auth/logout        { "token": "<refreshToken>" }
// POST /auth/logout-all    { "token": "<refreshToken>" }
```

| Endpoint | Phạm vi | `message` khi 200 |
|---|---|---|
| `/auth/logout` | Chỉ thiết bị hiện tại | `"Logout successful"` |
| `/auth/logout-all` | Toàn bộ thiết bị | `"Logged out from all devices"` |

**Token hết hạn vẫn đăng xuất được** — backend cố tình chấp nhận access/refresh token quá hạn ở
đây, vì chữ ký đã xác minh xong trước khi kiểm hạn, và access token hết hạn không có nghĩa phiên
đã chết.

| HTTP | `message` | Khi nào |
|---|---|---|
| 200 | `"Logout successful"` | Bình thường, **kể cả token đã hết hạn** |
| 401 | `"No readable token found to revoke. Please sign in again."` | Không gửi token nào, hoặc chuỗi hỏng/sai chữ ký |

🔴 **Nhận 401 ở logout thì app VẪN PHẢI xoá sạch token local và về màn Đăng nhập.** 401 nghĩa là
server không tìm thấy gì để thu hồi — không có gì để mất.

```dart
Future<void> logout() async {
  try {
    await _dio.post('/auth/logout', data: {'token': await _store.refreshToken});
  } catch (_) {
    // kệ. 401/timeout/mất mạng đều không đổi việc phải làm bên dưới
  } finally {
    await _store.clear();     // 🔴 LUÔN LUÔN chạy
    navigateToLogin();
  }
}
```

## 6.8 — Lưu token ở đâu

`flutter_secure_storage` — Keychain trên iOS, EncryptedSharedPreferences trên Android.
**Không** `SharedPreferences` trần, **không** file, **không** biến toàn cục.

Refresh token sống 7 ngày và đổi được access token mới liên tục. Để nó ở chỗ app khác đọc được
thì toàn bộ phần bảo mật phía trên thành vô nghĩa.

---

# 7. XỬ LÝ LỖI CHUNG

## 7.1 — Lỗi validate (400) có thêm field `errors`

Chỉ **400 do `@Valid` DTO** mới có `errors`. Các 400 khác (OTP sai, vé hết hạn...) chỉ có `message`.

```jsonc
{
  "code": 400,
  "message": "Request validation failed",
  "errors": {
    "email": "Email must be valid",
    "password": "Password must contain at least one uppercase letter, one lowercase letter and one digit",
    "phone": "Phone must be 10 digits and start with 0"
  },
  "path": "/api/v1/auth/register"
}
```

Key của `errors` = **đúng tên field trong request JSON** ⇒ map thẳng vào `TextFormField`:

```dart
void applyServerErrors(Map<String, String>? errors) {
  if (errors == null) return;
  emailError    = errors['email'];
  passwordError = errors['password'];
  phoneError    = errors['phone'];
}
```

Luật validate hiện tại — **nên kiểm ở client trước để đỡ một vòng mạng**:

| Field | Luật | Câu lỗi server |
|---|---|---|
| `email` | đúng định dạng, ≤ 100 ký tự | `"Email must be valid"` |
| `password` / `newPassword` | 8–100 ký tự, **có hoa + có thường + có số** | `"Password must contain at least one uppercase letter, one lowercase letter and one digit"` |
| `fullName` | không rỗng, ≤ 150 | `"Full name is required"` |
| `phone` | **đúng 10 số, bắt đầu bằng `0`** | `"Phone must be 10 digits and start with 0"` |
| `otp` | **đúng 6 chữ số** | `"OTP must be exactly 6 digits"` |

Mật khẩu **không** bắt buộc ký tự đặc biệt. `Abc12345` hợp lệ.

## 7.2 — Bảng mã trạng thái: app phản ứng thế nào

| Mã | Nghĩa trong hệ thống này | Có nên retry? | App làm gì |
|---|---|---|---|
| **400** | Dữ liệu client gửi sai | ❌ | Sửa input rồi gửi lại |
| **401** | Chưa đăng nhập / token chết / sai mật khẩu | ❌ (trừ nhánh refresh ở §6.4) | Đọc `message` để phân biệt (xem 7.3) |
| **403** | Đã đăng nhập nhưng **không được phép** | ❌ | "Tài khoản bị hạn chế" hoặc "Không có quyền" |
| **404** | Không tìm thấy | ❌ | Màn trống |
| **409** | Xung đột trạng thái (email trùng, chưa có mật khẩu) | ❌ | Đọc `message`, hướng dẫn cụ thể |
| **429** | Bị hãm tốc độ | ⏳ sau khi hết đếm ngược | Bóc số giây, khoá nút |
| **503** | **Hạ tầng tạm chết** (Redis/DB/Brevo) | ✅ nên retry | "Hệ thống bận, thử lại" + nút Thử lại. **Giữ nguyên token** |
| **500** | Bug backend | ❌ | "Có lỗi xảy ra" + báo backend |

> 🔴 Phân biệt **503 và 401** cho kỹ. 503 là Redis chết — token của người dùng **vẫn tốt nguyên**.
> Đá họ ra màn đăng nhập lúc đó là sai, vì đăng nhập lại cũng 503.

## 7.3 — Bốn câu 401 và cách phản ứng khác nhau

| `message` | Nghĩa | App làm gì |
|---|---|---|
| `"Invalid email or password"` | Sai thông tin đăng nhập | Ở lại màn Đăng nhập, báo lỗi |
| `"Session has been revoked. Please sign in again."` | Phiên bị thu hồi (§6.6) | Xoá token → Đăng nhập. **Không refresh** |
| `"Invalid or expired refresh token"` | Refresh thất bại | Xoá token → Đăng nhập. **Không retry** |
| `"Unauthorized"` | Gọi API cần đăng nhập mà **không gửi token** | Thường là bug interceptor — kiểm `Authorization` |

Hai câu hiếm, chỉ gặp khi token bị chỉnh sửa bằng tay hoặc backend đổi khoá ký:
`"Invalid token type: expected access token"`, `"Token is missing the subject claim"`.
Xử lý giống "Session has been revoked".

---

# 8. MÀN "TÀI KHOẢN CỦA TÔI"

## 8.1 — `GET /accounts/me` và `GET /profiles/me` là hai thứ khác nhau

| | `GET /accounts/me` | `GET /profiles/me` |
|---|---|---|
| Trả về | **danh tính đăng nhập** | **con người** |
| Field | `id, email, roleId, roleName, status, provider, hasPassword, lockedUntil, createdAt, updatedAt` | `id, accountId, email, fullName, phone, birthDate, gender, avatarUrl, address, profileCompleted, createdAt, updatedAt` |
| Màn hình | Bảo mật / Cài đặt tài khoản | Hồ sơ, hiển thị tên + avatar |

Muốn hiện **tên người dùng** ở góc màn hình ⇒ `GET /profiles/me` → `fullName`.
`GET /accounts/me` **không có** tên.

> `GET /profiles/me` tự tạo hồ sơ rỗng nếu chưa có, nên không bao giờ trả 404.
> `profileCompleted` = đã điền đủ các trường chính hay chưa — dùng để hiện gợi ý hoàn thiện hồ sơ.
> Đây chỉ là **nhắc nhở**, backend **không chặn** người dùng chưa hoàn thiện hồ sơ.

Hai field quyết định giao diện màn Bảo mật:

```dart
// GET /accounts/me
if (!account.hasPassword) {
  // Tài khoản Google thuần: CHƯA có mật khẩu
  show('Đặt mật khẩu');        // → dẫn sang luồng quên mật khẩu (§5.5)
} else {
  show('Đổi mật khẩu');        // → PATCH /accounts/me/password
}
// account.provider: "LOCAL" | "GOOGLE" — hiện huy hiệu "Đã liên kết Google"
```

## 8.2 — `PATCH /accounts/me` — đổi email

```jsonc
// Request  🔴 currentPassword LÀ BẮT BUỘC, luôn luôn
{ "email": "moi@example.com", "currentPassword": "Abc@12345" }
```

Trả 200 + `AccountResponse` mới, `message` = `"Account updated successfully"`.

| HTTP | `message` | App làm gì |
|---|---|---|
| 400 | `"Current password is required"` | Thiếu field — bug client |
| 401 | `"Current password is incorrect"` | "Mật khẩu không đúng" |
| 409 | `"Account email already exists: moi@example.com"` | "Email này đã có người dùng" |
| 409 | `"This account has no password yet. Use the forgot-password flow to set one."` | Tài khoản Google thuần ⇒ dẫn sang §5.5 |

🔴 **Đổi email thành công = thu hồi mọi phiên.** Xoá token, về Đăng nhập, báo rõ
*"Email đã đổi, vui lòng đăng nhập lại bằng email mới."* Người dùng không được báo trước sẽ
tưởng app lỗi.

## 8.3 — `PATCH /accounts/me/password` — đổi mật khẩu

```jsonc
// Request
{ "currentPassword": "Abc@12345", "newPassword": "Moi@12345" }

// 200
{ "code": 200, "message": "Password changed successfully" }
```

| HTTP | `message` | App làm gì |
|---|---|---|
| 400 | `"New password must be different from the current one"` | "Mật khẩu mới phải khác mật khẩu cũ" |
| 400 | `"Password must contain at least one uppercase letter, one lowercase letter and one digit"` | Kiểm ở client trước |
| 401 | `"Current password is incorrect"` | "Mật khẩu hiện tại không đúng" |
| 409 | `"This account has no password yet. Use the forgot-password flow to set one."` | Tài khoản Google ⇒ §5.5 |
| 429 | `"You changed your password recently. Please wait 214 more seconds before changing it again."` | **Cooldown 5 phút**. Bóc số giây, khoá nút |

🔴 **Đổi mật khẩu thành công = thu hồi mọi phiên, kể cả thiết bị vừa đổi.** Xoá token, về Đăng
nhập, báo *"Đổi mật khẩu thành công, vui lòng đăng nhập lại."*

> **Cooldown 5 phút chỉ tính từ lần đổi THÀNH CÔNG**, và **chỉ áp cho luồng này** — luồng quên
> mật khẩu (§5) không bị cooldown, vì người quên mật khẩu đáng lẽ phải đổi được ngay.
>
> Người dùng cũng sẽ nhận một **email thông báo** sau mỗi lần đổi mật khẩu thành công. Mail này
> gửi bất đồng bộ và tự nuốt lỗi — Brevo chết cũng không làm hỏng việc đổi mật khẩu đã xong.

---

# 9. CHECKLIST TRƯỚC KHI BÁO "XONG PHẦN AUTH"

**Cấu hình**
- [ ] `X-Client-Type: mobile` gắn **toàn cục** ở Dio, không phải gắn tay từng chỗ
- [ ] `baseUrl` có `/api/v1`
- [ ] `GOOGLE_CLIENT_ID` dùng **Web client ID**, trùng giá trị backend
- [ ] Token lưu bằng `flutter_secure_storage`

**Đăng ký**
- [ ] Sau `/auth/register` **không** điều hướng vào Home (chưa có tài khoản)
- [ ] Màn OTP đếm ngược theo `expiresIn`, khoá nút gửi lại theo `resendAfter`
- [ ] Bấm "Gửi lại mã" **ghi đè `registrationId`** 🔴
- [ ] Phân biệt "code has expired" (gửi lại được) với "registration session has expired" (khai lại)

**Token**
- [ ] Refresh lưu **cả** `accessToken` **và** `refreshToken` mới 🔴
- [ ] Có cơ chế gom refresh, 5 request 401 cùng lúc chỉ gọi refresh **1 lần** 🔴
- [ ] Dio gọi `/auth/refresh` là instance **riêng**, không gắn interceptor
- [ ] 401 từ `/auth/refresh` ⇒ đá về Đăng nhập, **không retry**
- [ ] 503 ⇒ **giữ token**, cho nút Thử lại (không đá ra)

**Thu hồi phiên**
- [ ] Bắt được `"Session has been revoked. Please sign in again."` ⇒ xoá token, về Đăng nhập
- [ ] Sau đổi mật khẩu / đổi email / reset mật khẩu ⇒ **tự xoá token** và báo người dùng đăng nhập lại

**Đăng xuất**
- [ ] 401 ở `/auth/logout` **vẫn** xoá token local và về Đăng nhập 🔴
- [ ] Mất mạng khi logout cũng vẫn xoá token local

**Giao diện theo trạng thái**
- [ ] `hasPassword == false` ⇒ nút "Đặt mật khẩu" dẫn sang luồng quên mật khẩu
- [ ] Tên người dùng lấy từ `GET /profiles/me` → `fullName`, không phải `/accounts/me`
- [ ] 429 bóc số giây ra đếm ngược, không hiện nguyên câu tiếng Anh

**Không làm**
- [ ] Không cài `cookie_jar` / không đụng tới cookie
- [ ] Không gọi 7 endpoint ADMIN ở §1.2
- [ ] Không tự dò "email đã tồn tại chưa" ngoài phản hồi 409 của `/auth/register`

---

# 10. HỎI BACKEND KHI GẶP MẤY CÁI NÀY

Các trường hợp **không phải lỗi app**, đừng cố xử lý ở client:

| Triệu chứng | Nguyên nhân phía backend |
|---|---|
| 503 `"Google sign-in is not configured. Please set GOOGLE_CLIENT_ID."` | Backend chưa đặt biến môi trường |
| Mọi API 503 `"Service temporarily unavailable..."` | Redis hoặc DB chết |
| Không nhận được mail OTP mà API trả 200 | Brevo chết / chưa verify sender — luồng quên mật khẩu **cố tình** nuốt lỗi này |
| 500 `"Internal Server Error"` | Bug backend. Gửi kèm `path` + `timestamp` trong response để tra log |
| Login Google luôn 401 dù token đúng | `serverClientId` bên app khác `GOOGLE_CLIENT_ID` bên backend |

---

*Backend tham chiếu: `TEST_AUTH_SWAGGER.md` (159 ca nghiệm thu) · `GHI_NHO_DU_AN.md` (quyết định + việc còn nợ).*
