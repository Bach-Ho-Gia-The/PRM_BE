# HƯỚNG DẪN DÀNH CHO FRONTEND / MOBILE: TÍCH HỢP GOOGLE SSO & QUẢN LÝ TÀI KHOẢN

> Tài liệu này chuẩn hóa toàn bộ luồng xử lý phía Client (Web / Flutter / React Native) cho tính năng **Đăng nhập Google**, **Cập nhật Hồ sơ (Profile)** và **Thiết lập / Đổi Mật khẩu** dựa theo thiết kế Backend.

---

## 1. NGUYÊN TẮC THIẾT KẾ NỀN TẢNG (BE POLICY)

1. **Cơ chế Nudge (Nhắc nhở), KHÔNG dùng Gate (Chặn cứng):**
   * Người dùng đăng nhập bằng Google lần đầu sẽ **nhận ngay Access Token & Refresh Token**.
   * Backend **không chặn** token hay cấm truy cập API khi người dùng chưa hoàn thiện thông tin (SĐT, ngày sinh...). Frontend chịu trách nhiệm điều hướng UX linh hoạt (bật Popup/Banner nhắc nhở).
2. **Tài khoản Google thuần (`hasPassword = false`):**
   * Không có mật khẩu mặc định. Họ có thể dùng toàn bộ ứng dụng mà không bao giờ cần mật khẩu.
   * **CẤM** gọi API đổi mật khẩu thường `PATCH /accounts/me/password` (Backend sẽ trả lỗi `409 Conflict`).
   * Muốn tạo mật khẩu riêng $\rightarrow$ Phải đi qua luồng **Tạo mật khẩu bằng OTP** (`forgot-password`).

---

## 2. QUY TRÌNH ĐĂNG NHẬP GOOGLE (`POST /auth/google`)

### Bước 1: Client lấy ID Token từ Google SDK
* **Flutter / React Native / Web:** Dùng thư viện Google Sign-In chính thức để người dùng đăng nhập và lấy chuỗi `idToken`.

### Bước 2: Gọi API Backend
* **Endpoint:** `POST /auth/google`
* **Header:** `X-Client-Type: mobile` (nếu là ứng dụng mobile để nhận Token trong body)
* **Body:**
  ```json
  {
    "idToken": "eyJhbGciOiJSUzI1NiIs..."
  }
  ```

### Bước 3: Phân tích Response
* **Success (`200 OK`):**
  ```json
  {
    "code": 200,
    "message": "Login successful",
    "result": {
      "accessToken": "eyJ...",
      "refreshToken": "eyJ...",
      "tokenType": "Bearer",
      "expiresIn": 900,
      "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "email": "user@gmail.com",
      "roleName": "USER"
    }
  }
  ```
* **Lưu Token** vào `SecureStorage` (Mobile) hoặc `Cookie/LocalStorage` (Web).

---

## 3. XỬ LÝ PROFILE & ONBOARDING (NUDGE UX)

Ngay sau khi đăng nhập thành công, FE gọi API thông tin hồ sơ:
* **Endpoint:** `GET /profiles/me`
* **Response:**
  ```json
  {
    "code": 200,
    "result": {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "fullName": "Nguyen Van A",
      "phone": null,
      "avatarUrl": "https://lh3.googleusercontent.com/...",
      "profileCompleted": false
    }
  }
  ```

### Quy tắc hiển thị UI:
* **Nếu `profileCompleted == false`:**
  * Hiển thị **Widget / Card / Toast nhắc nhở** trên trang chủ: *"Hồ sơ của bạn chưa hoàn thiện. Bổ sung ngay để bảo vệ tài khoản!"*
  * Khi bấm vào $\rightarrow$ Điều hướng tới màn hình `EditProfileScreen` (`PATCH /profiles/me`).
  * **Không khóa ứng dụng:** Người dùng vẫn có thể tắt nhắc nhở và dùng các chức năng khác bình thường.

---

## 4. QUẢN LÝ MẬT KHẨU THEO LOẠI TÀI KHOẢN

Trong màn hình **Cài đặt tài khoản (Account Settings)**, FE gọi `GET /accounts/me` để kiểm tra cờ `hasPassword`:

```json
{
  "code": 200,
  "result": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "email": "user@gmail.com",
    "provider": "GOOGLE",
    "hasPassword": false
  }
}
```

---

### TÌNH HUỐNG A: `hasPassword == true` (Tài khoản đã có mật khẩu)

Hiển thị Form **"Đổi Mật Khẩu"** tiêu chuẩn.

1. **User nhập:** Mật khẩu hiện tại (`currentPassword`) & Mật khẩu mới (`newPassword`).
2. **API gọi:** `PATCH /accounts/me/password`
3. **Body:**
   ```json
   {
     "currentPassword": "User@12345",
     "newPassword": "NewPassword@123"
   }
   ```
4. **Xử lý sau khi thành công (`200 OK`):**
   * Xóa sạch Token ở local storage.
   * Hiển thị thông báo: *"Mật khẩu đã thay đổi thành công. Vui lòng đăng nhập lại."*
   * Điều hướng về màn hình **Đăng nhập (`/login`)**.

---

### TÌNH HUỐNG B: `hasPassword == false` (Tài khoản Google thuần)

* **ẨN / VÔ HIỆU HÓA** Form đổi mật khẩu thường.
* Hiển thị nút: **"Tạo mật khẩu cho tài khoản"**.

#### 🚀 Tối ưu UX 2 bước (Không bắt user nhập lại Email):

```
[ Nút: Tạo Mật Khẩu ] 
       │
       ▼
[ Step 1: Popup xác nhận email ]
"Mã OTP sẽ được gửi về: user@gmail.com"
[ Bấm: Gửi Mã OTP ] ────► API: POST /auth/forgot-password
       │
       ▼
[ Step 2: Form Nhập OTP + Mật Khẩu Mới ]
- Nhập 6 số OTP      ────► API: POST /auth/verify-otp
- Nhập Mật Khẩu Mới  ────► API: POST /auth/reset-password
       │
       ▼
[ Hoàn tất: Cập nhật hasPassword = true ]
```

#### Chi tiết các cuộc gọi API ở Tình huống B:

1. **Yêu cầu gửi OTP:**
   * **Endpoint:** `POST /auth/forgot-password`
   * **Body:** `{"email": "user@gmail.com"}` *(FE tự lấy email có sẵn)*
2. **Xác minh OTP thu lấy Ticket:**
   * **Endpoint:** `POST /auth/verify-otp`
   * **Body:** `{"email": "user@gmail.com", "otp": "123456"}`
   * **Response:**
     ```json
     {
       "code": 200,
       "result": {
         "ticket": "8f14e45f-...",
         "expiresIn": 300
       }
     }
     ```
3. **Đặt mật khẩu mới bằng Ticket:**
   * **Endpoint:** `POST /auth/reset-password`
   * **Body:**
     ```json
     {
       "ticket": "8f14e45f-...",
       "newPassword": "MyNewPassword@123"
     }
     ```
4. **Sau khi thành công (`200 OK`):**
   * Cập nhật lại state local: `hasPassword = true`.
   * Đăng xuất và bắt đăng nhập lại (hoặc cho phép tiếp tục dùng phiên tùy UX).

---

## 5. BẢNG MÃ LỖI CẦN BẮT TRÊN FRONTEND (ERROR HANDLING MATRIX)

| Endpoint | Status Code | Phản hồi từ BE (`message`) | Hướng xử lý UI trên FE |
|---|---|---|---|
| `PATCH /accounts/me/password` | `409 Conflict` | `"This account has no password yet..."` | Báo lỗi hoặc tự chuyển user sang Modal "Tạo mật khẩu qua OTP". |
| `PATCH /accounts/me/password` | `429 Too Many Requests` | `"You changed your password recently..."` | Hiện đếm ngược / nhắc user chờ 5 phút trước khi đổi lại. |
| `POST /auth/verify-otp` | `400 Bad Request` | `"Incorrect verification code"` | Báo mã OTP không chính xác. |
| `POST /auth/verify-otp` | `429 Too Many Requests` | `"Too many incorrect attempts..."` | Yêu cầu user bấm "Gửi lại mã OTP" do đã gõ sai quá 5 lần. |
| `POST /auth/reset-password` | `400 Bad Request` | `"Password must contain at least one uppercase..."` | Validate mật khẩu tại Client trước: 8–100 ký tự, có chữ HOA, chữ thường và số. |
| `POST /auth/google` | `403 Forbidden` | `"Your Google account email has not been verified"` | Báo lỗi tài khoản Google chưa xác thực email. |

---

## 6. MA TRẬN VALIDATION MẬT KHẨU PHÍA CLIENT

Để tránh gọi API vô ích, FE **bắt buộc** validate trường `password` trước khi gửi lên:
* **Độ dài:** Từ 8 đến 100 ký tự.
* **Regex kiểm tra:** `^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,100}$`
* **Yêu cầu:** Ít nhất **1 chữ cái viết hoa**, **1 chữ cái viết thường** và **1 chữ số**.