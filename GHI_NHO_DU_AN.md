# SỔ GHI NHỚ DỰ ÁN — file kế hoạch DUY NHẤT

Việc đã xong thì **xoá khỏi file này** cho nhẹ. Chỉ giữ: quy tắc, quyết định, bẫy kỹ thuật,
và **việc còn nợ**.

> 📘 Chi tiết nghiệp vụ + 154 ca kiểm thử khối auth nằm ở **`TEST_AUTH_SWAGGER.md`**.
> File này không chép lại.

---

## 1. BỐI CẢNH

| | |
|---|---|
| Repo | `github.com/Bach-Ho-Gia-The/PRM_BE.git` · branch **`main`** @ `6e6bc63` · clone tại `/home/user/PRM_BE` · sạch, không file chờ commit |
| Stack | **Spring Boot 4.1.1** · **Spring Security 7** · Java 21 · PostgreSQL · Redis · MapStruct · Lombok · JJWT · Jackson 3 (`tools.jackson.*`) · springdoc 2.8.5 · `spring-security-oauth2-jose` |
| Quy mô | **44 endpoint**, 21 guard ADMIN. `/accounts` 9 · `/auth` 10 · `/products` 6 · `/orders` 6 · `/categories` 5 · `/roles` 5 · `/profiles` 3 |
| FE | `github.com/long301105/PRM391_Project` — web React+Vite và mobile Flutter, **cả hai 100 % mock, 0 dòng gọi API**. BE đi trước |

---

## 2. BASE_RULE — 6 ĐIỀU (`project_rule/base_rule.md`)

1. **Bàn trước – code sau.** Điểm nào mờ phải hỏi.
2. **Chia nhỏ task + Review Gate.** Xong mỗi sub-task phải DỪNG, báo cáo + hướng dẫn test.
3. **Bám golden standard** `features/product`. Không tự thêm thư viện/pattern mới.
4. **DRY + null-safe + edge case.** Cho phép tách hàm private nội bộ.
5. **Repository ưu tiên Derived Query.** `@Query` phải giải trình trước.
6. **Giữ đúng phạm vi.** Hạn chế sửa `common`, sửa thì phải báo và phân tích ảnh hưởng.

---

## 3. QUY TẮC RIÊNG BRO ĐẶT

### Quy trình
- **Mọi deviation phải nêu TRƯỚC khi gõ code**, đặt ở **đầu** báo cáo.
- **Cấm bịa lý do kỹ thuật.** Bốn lần đã bịa, phải nhớ:
  - ❌ *"map qua MapStruct sẽ phải query DB lại"*
  - ❌ *"có `AccountPrincipal` rồi nên không cần mapper"*
  - ❌ *"`INVALID_CREDENTIALS_MESSAGE` bắt buộc là hằng vì 2 nhánh"* — thực tế dùng **1 lần**
  - ❌ *"`RedisService` chờ việc chống brute-force"* — `RedisService` không `INCR` được
- **Trả lời đúng câu hỏi, ngắn gọn.**
- **"Giảng/giải thích"** = dạy chi tiết **từng dòng, thẳng trong chat**, bám số dòng thật.
  Ghi ra `.md` rồi tóm tắt là **chưa đạt**.
- **KHÔNG dùng `ask_user`/question box** — bên LMArena lỗi. Cần hỏi thì viết cuối message.

### Code
- **DTO bám golden standard**: 1 record phẳng, flatten quan hệ, không nested, không hằng,
  không method. Chưa có tiền lệ thì **hỏi trước**.
  *(Ngoại lệ đã chốt: cho nested ở quan hệ 1-nhiều — `gallery`, `variants`, `items`.)*
- **Service kết thúc bằng mapper, CẤM `new` DTO bằng tay.**
- **TRÁNH DÙNG LOG.** Thông tin cần nhớ thì ghi **comment**. Không `@Slf4j` trong service.
- **Hằng `private static final` dùng 1 lần là dư** → viết inline. Chỉ tách khi dùng **≥ 2 chỗ**.
- **Enum nằm ở `features/<feature>/enums/`** (số nhiều — `enum` là từ khoá Java).
  Hai cái cố ý không đụng: `common/exception/ErrorCode` và `infrastructure/persistence/RecordStatus`.

### Workspace — chạy NGAY sau mỗi lần build
```bash
# 🆕 để kho maven trong ~/.cache -> thư mục này KHÔNG bị tính vào snapshot workspace
./mvnw -B -Dmaven.repo.local=$HOME/.cache/m2 clean test
# dọn NGAY trong cùng lượt, nếu không sẽ vỡ budget 128MB
rm -rf ~/.cache/m2 ~/.m2 ~/.tools ~/.local /home/user/PRM_BE/target
chmod 644 /home/user/PRM_BE/mvnw
du -sh /home/user/     # phải ra vài trăm KB
```
Lý do: `.m2` từng phình **1698 file / 101.5 MB**.

---

## 4. QUYẾT ĐỊNH ĐÃ CHỐT

| # | Vấn đề | Chốt |
|---|---|---|
| Q1 | Phân biệt client | Header `X-Client-Type: web\|mobile`, mặc định `web` |
| Q2 | `UserDetailsService` | Đặt trong `features/auth/security/` |
| Q3 | Định danh đăng nhập | **EMAIL**. Cột `username` đã **xoá hẳn** khỏi `accounts` |
| Q4 | `JwtAuthenticationFilter` | `shouldNotFilter()` bỏ qua `/auth/**` |
| Q5 | Claim `scope` | Có tiền tố `ROLE_` |
| Q7 | `@JsonInclude(NON_NULL)` | Chỉ trên `LoginResponse`, **không** toàn cục |
| Q9 | Update một phần | **`PATCH`**, field null = giữ nguyên, không thêm thư viện |
| Q10 | ID liên kết không tồn tại | **404** |
| Q11 | Quan hệ cha–con | **Không cascade từ phía One**, chặn xoá ở service → **409** |
| Q12 | Tài khoản seed | `admin@example.com/Admin@12345` · `user@example.com/User@12345` |
| Q13 | Thu hồi token | Access token mang claim **`sid`** = jti refresh token. Không đẻ denylist riêng |
| Q14 | Refresh token | **Có xoay vòng** + tra lại DB mỗi lần |
| Q15 | Lỗi hạ tầng | `DataAccessResourceFailureException` + `QueryTimeoutException` → **503** tập trung |
| Q16 | Chống brute-force | `StringRedisTemplate.increment()`, hai tầng: email (5) và IP (20) |
| Q17 | Onboarding hồ sơ | **Nudge, KHÔNG gate.** `profileCompleted` chỉ để FE gợi ý |
| Q18 | Đăng ký | **GATE 2 bước** — tài khoản chỉ ra đời sau khi xác minh OTP email |
| Q19 | Google | **Đường B** (`POST /auth/google {idToken}`), thư viện `spring-security-oauth2-jose`. Gộp khi trùng email + `email_verified`, **giữ `provider=LOCAL`** |
| Q20 | Mail | **Brevo REST API qua `RestClient`** (0 thư viện mới). OTP 6 số. **Không làm SMS** |
| Q21 | Xác minh đổi email / mật khẩu | **Gõ lại mật khẩu cũ**, không dùng OTP |
| Q22 | Tồn kho | **Theo size** (`product_variants`), không phải một số tổng |
| Q23 | Đặt hàng | **Bắt buộc đăng nhập**; chỉ xem được đơn của chính mình |
| Q24 | Ảnh | Chỉ lưu **URL**; Google thì dùng thẳng link gốc, không tải về |

### Đã chốt KHÔNG làm
Move `ErrorCode`/`RecordStatus` sang `enums/` · dời `logout-all` sang `/accounts/me/sessions` ·
dời `LoginAttemptProperties` · gate onboarding hồ sơ · `profileCompleted` trong `LoginResponse` ·
parse tên FK constraint · `POST /accounts/{id}/lock|unlock` · tải ảnh Google về server ·
sinh username tự động · băm OTP trong Redis · OTP qua SMS · tách `AuthServiceImpl` ·
thêm `@Operation`/`@Tag` Swagger · dọn code chết của module người khác.

---

## 5. KIẾN THỨC KỸ THUẬT (khỏi tra lại)

### Spring Security 7
- `DaoAuthenticationProvider` **bỏ constructor rỗng** → `new DaoAuthenticationProvider(uds)`.
  `setPasswordEncoder` còn, `setUserDetailsService` bỏ.
- `UserDetails`: 4 cờ trạng thái là **default method trả `true`**.
- Khai bean `AuthenticationProvider` → Spring log WARN. Chấp nhận.
- `authentication.getAuthorities()` **tự chèn `FACTOR_PASSWORD`**. Code lấy từ
  `AccountPrincipal.getAuthorities()` nên claim `scope` sạch — **đừng đổi**.

### ⚠️ BẪY ĐÃ DÍNH — đừng dính lại

**Cấu hình**
- 🔴 **`.env` đọc theo định dạng PROPERTIES ⇒ `\` là ký tự THOÁT.** `APP_TIMEZONE=Asia\Ho_Chi_Minh`
  thành `AsiaHo_Chi_Minh` → **app không khởi động**. Luôn dùng `/` hoặc `\\`.
  Mẹo đọc lỗi: Spring in `Origin: application.yaml - 55:16` **nhưng đó là nơi khai placeholder**,
  không phải nơi lấy giá trị — đừng vội loại trừ `.env`.
- 🔴 **YAML trùng key = app không khởi động.** `allowDuplicateKeys=false`, ném ngay lúc đọc file.
  Sửa yaml xong phải tự dò key trùng.
- 🔴 **`${BIEN:mac-dinh}` chỉ cứu khi biến KHÔNG TỒN TẠI.** Khai trong `.env` rồi bỏ trống là
  chuỗi rỗng, phép so `!= null` thả lọt. Properties kiểu String phải dùng `StringUtils.hasText`.
- 🔴 **Boot 4.0 đổi `mongo` → `mongodb`** trong tên property. Khoá cũ bị đánh dấu `level: error`
  ⇒ **Spring bỏ qua hoàn toàn, không cảnh báo**.
- 🔴 **`BUILD SUCCESS` KHÔNG chứng minh app khởi động được.** Sandbox không có Docker nên context
  test luôn skip.

**Spring Data / JPA**
- 🔴 **Spring Data 4 dời package `PropertyReferenceException`**: `...data.mapping` → **`...data.core`**.
- 🔴 **`?sort=` trỏ field không tồn tại** ném `PropertyReferenceException` → đã có handler → 400.
  **`?sort=category.name` là HỢP LỆ** và âm thầm sinh JOIN ⇒ endpoint công khai phải whitelist sort.
- 🔴 **Bản `findAll(Pageable)` KHÔNG kế thừa `@EntityGraph` của bản `findAll()`** — hai method khác
  nhau. Thiếu override là mỗi trang 20 hàng bắn thêm 20 câu SELECT.
- 🔴 **`ddl-auto: update` không bao giờ DROP cột và không nới `NOT NULL`.** Mọi thay đổi kiểu đó
  phải chạy SQL tay.
- 🔴 **`NULL <> 'DELETED'` ra NULL, không ra true** ⇒ mọi điều kiện soft-delete phải kèm `IS NULL`.
- 📌 **`spring.jpa.open-in-view: false`** đã bật. Mọi lời gọi mapper **bắt buộc** nằm trong method
  `@Transactional`; không controller nào được gọi mapper hay trả thẳng Entity.
- 🔴 `MultipleBagFetchException` khi fetch 2 `List` cùng lúc. Đừng cộng dồn lazy trong MapStruct expression.

**Auth**
- 🔴 **`/auth/**` bị `shouldNotFilter()` bỏ qua** ⇒ nhánh dọn cookie trong filter không chạy cho
  `/auth/*`. Phải tự xoá cookie trong `AuthController` khi lỗi.
- 🔴 **Whitelist theo đường dẫn KHÔNG phân biệt HTTP method.** Để `/products/**` trong whitelist
  là mở cả GET lẫn DELETE — từng hở 6 endpoint ghi. Mọi endpoint ghi phải có `@PreAuthorize` riêng.
- 🔴 **Logout không được nuốt `ExpiredJwtException`.** Access token hết hạn ≠ phiên đã chết.
  Dùng `ex.getClaims()` — JJWT xác minh chữ ký XONG mới ném Expired nên claims đáng tin.
- 🔴 **Đừng ưu tiên tuyệt đối một nguồn token.** Swagger điền sẵn `"string"` vào body che mất
  cookie hợp lệ. Giờ gom **danh sách** nguồn qua `TokenSourceResolver`.
- 🔴 **Mọi nhánh tự cấp token phải tự gọi `isEnabled`/`isAccountNonLocked`** — chỉ luồng login
  thường mới được `DaoAuthenticationProvider` kiểm hộ.
- 🔴 **Cookie `Path` rỗng ≠ `/`.** Debug lỗi 401 thì đọc bảng Cookie của DevTools **trước tiên**.
- 🔴 **Hạn của mã và khoảng chờ gửi lại là HAI con số độc lập**, phải tách khoá Redis riêng.
  Lấy "mã còn sống" làm khoảng chờ biến TTL 10 phút thành thời gian chờ 10 phút.
- 🔴 **Hồ sơ đăng ký chờ phải khoá theo `registrationId`, không theo email** — nếu không thì
  kẻ xấu đè hồ sơ và chiếm được tài khoản. Nhưng **cooldown/trần gửi mail vẫn khoá theo email**.
- 🔴 **Dò OTP: ĐẾM TRƯỚC — SO SAU.** Đảo lại là 5 lượt đoán biến thành hàng trăm, và build vẫn xanh.
- 🔴 **Đổi vai trò phải thu hồi phiên** — `scope` nằm trong access token, filter không tra DB.
- 🔴 **Đừng nhét lời gọi mạng vào trong `@Transactional`.** Mail báo tin phải `@Async` + nuốt lỗi.

**Công cụ**
- 🔴 **Chèn method mới vào giữa khối `@Mapping` và method của nó ⇒ annotation gán nhầm.** Dính 2 lần.
  Thêm method vào interface MapStruct thì **chèn ngay sau dấu `{` mở interface**.
- 🔴 **javac KHÔNG bắt `{@link #method}` mồ côi** và cũng không bắt javadoc nhắc code đã xoá.
- 🔴 **Đừng dựng chuỗi tìm-thay từ output `grep -v` đã lọc** — luôn `assert` số khớp trước khi ghi.
- 🔴 **Regex chèn annotation có thể đẻ bản mồ côi.** Xoá method repository phải dọn import theo.
- 🔴 MapStruct **không** chạy `@AfterMapping` với record. Nó khớp nhiều tham số vô hướng **theo tên**.

### Redis
`RedisService` dùng `RedisTemplate<String,Object>` + JSON ⇒ **không `INCR` được** và `get`/`set`
**không nguyên tử**. Việc cần đếm/khoá phải dùng `StringRedisTemplate` trực tiếp.

---

## 6. ✅ ĐÃ ĐÓNG — KHỐI AUTH

Đóng sau **8 vòng audit, 48 phát hiện đã vá, 0 lỗi 🔴 còn trong code**.

**Ba tài liệu, ba mục đích khác nhau — đừng gộp:**

| File | Cho ai | Nội dung |
|---|---|---|
| `TEST_AUTH_SWAGGER.md` | người nghiệm thu | **159 ca test** trên Swagger, nghiệp vụ + kết quả mong muốn |
| `HUONG_DAN_FE_FLUTTER.md` | anh em Flutter | luồng nối API, interceptor mẫu, **toàn bộ bảng lỗi**, checklist |
| `GHI_NHO_DU_AN.md` (file này) | người tiếp tục code | quyết định, bẫy, việc còn nợ |

**Audit 8 (vòng cuối) — 9 phát hiện, TOÀN BỘ là tài liệu trong code, không đụng logic:**

| # | Chỗ | Vấn đề |
|---|---|---|
| A8-1 | `AuthController.register` | javadoc ghi *"trả 201 kèm `LoginResponse`"* — sai, bản trước khi làm GATE. Thật ra 200 + `RegistrationPendingResponse`, không token |
| A8-2 | `AuthController` | javadoc "hai kiểu client" **mồ côi**, treo trên `register()` thay vì `login()` |
| A8-3 | `AuthController.logout` | javadoc ghi *"luôn trả 200"* — **mâu thuẫn với code**: `firstRevocableClaims` ném **401**. FE đọc nhầm là xử lý sai |
| A8-4 | `AuthController` | javadoc của `hideTokens()` mồ côi, treo trên `sessionResponse()` |
| A8-5 | `LoginResponse` | javadoc còn liệt kê field `username` — cột đã xoá hẳn |
| A8-6 | `AccountController` | javadoc `/accounts/me` còn ghi *"tự sửa username/email"* |
| A8-7 | `RefreshTokenRedis` | javadoc của `isAccessTokenRevoked()` mồ côi, treo trên `revokeEverySession()` |
| A8-8 | `AccountService` / `AuthService` | 2 javadoc mồ côi: của hàm `register` **đã bị xoá**, và của `login()` treo nhầm trên `register()` |
| A8-9 | `ProfileResponse` | javadoc còn nhắc `username` |

🆕 **Bẫy rút ra:** javadoc mồ côi (`*/` rồi `/**` ngay dòng sau) **javac không bắt**, build vẫn
SUCCESS, nhưng người đọc tin vào chú thích sai còn nguy hơn không có chú thích. Script soát:
tìm dòng `*/` mà dòng kế tiếp bắt đầu bằng `/**`. Đã quét sạch toàn dự án — **0 ca còn lại**.

Doc test cũng được bổ sung 5 ca còn thiếu sau khi đối chiếu máy móc **52 message thật trong code**
với nội dung file: D-15 (`email_verified:false` → 403), C-14/C-15/C-16 (token tự ký thiếu
`typ`/`sub`/`uid` hỏng), J-9b (xoá role USER khỏi DB).

10 endpoint `/auth`: register · verify-registration · login · refresh · logout · logout-all ·
google · forgot-password · verify-otp · reset-password.

**Trạng thái:** `./mvnw -B clean test` → `BUILD SUCCESS`. **Bro chưa chạy test thật**
(sandbox không có Postgres/Redis).

🆕 **Việc cần làm với bên FE:** gửi `HUONG_DAN_FE_FLUTTER.md`. Ba điểm họ chắc chắn sẽ sai nếu
không đọc: ① thiếu header `X-Client-Type: mobile` ⇒ token `null` mà API vẫn trả 200 ·
② không ghi đè `refreshToken` mới sau mỗi lần refresh (token xoay vòng) ·
③ không ghi đè `registrationId` sau khi bấm "Gửi lại mã".

---

## 7. 🔴 CÒN NỢ

> 🆕 **17/10/2026 — ĐỔI NGUỒN CHUẨN.** Workspace cũ (`Base_Structure/`) đã xoá sạch.
> Nguồn chuẩn giờ là **`/home/user/PRM_BE`** = clone nhánh **`main`** (`6e6bc63`).
> `main` đã gộp cả Collection 2B/2C.1.2, Product 2C.3, Category 2C4, Cloudinary của người khác.
> Đối chiếu: **10/10 endpoint `/auth`**, **8/8 bản vá audit**, CORS + `context-path` đều đúng.
> `./mvnw clean test` ⇒ **BUILD SUCCESS, 94 test, 0 fail** (11 skip là Testcontainers).
> Soát phân quyền lại trên `main`: **66 endpoint · 39 guard ADMIN · 0 lỗ hổng ghi**.
> Whitelist đã siết tốt hơn bản cũ — admin dời sang `/<module>/admin/**` nằm NGOÀI whitelist.

### 7.1 — Bắt buộc trước khi deploy
| # | Việc |
|---|---|
| N2 | `DATA_INIT_ENABLED=false` |
| N3 | `DDL_AUTO=validate` · `AUTH_COOKIE_SECURE=true` · `DB_SSL_MODE=require` · `SWAGGER_ENABLED=false` |
| N4 | Đổi `JWT_SIGNER_KEY` — 🔴 khoá local **đã lộ** (bro dán trong chat 16/10), sinh lại bằng `openssl rand -base64 64` |

| N5 | 🔴 **Quyết phương án CSRF** — xem `TEST_AUTH_SWAGGER.md` §K.4. Bro đã chốt **hoãn**, nhưng **phải nhắc lại trước khi deploy** |
| ~~N6~~ | ~~Chạy 6 lệnh SQL migration~~ — ✅ **bro báo đã chạy xong 17/10** |
| ~~N9~~ | ~~`.env-example` lệch biến~~ — ✅ **XONG 17/10**: bổ sung 15 biến thật sự thiếu (`CLOUDINARY_*` 11, `DB_POOL_SIZE`, `REDIS_TIMEOUT`, `UPLOAD_MAX_*`). Nay 60 biến, lệch 0. ⚠️ Lần đầu em đếm 28 là SAI — grep tính cả dòng comment (`SUPABASE_*`/`PAYOS_*`/`ANYTHING_LLM_*`/`SOCKET_*` đang bị comment trong yaml) |

#### ✅ N7 + N8 — ĐÃ XỬ LÝ (16/10/2026)

**N8 — `CONTEXT_PATH` đã chốt `/api/v1`.** Trước đó `.env` khai biến rồi bỏ trống ⇒ context-path
thành chuỗi rỗng, **không** rơi về default. Đã soát: `JwtAuthenticationFilter.shouldNotFilter`
cắt context-path đúng, `OpenApiConfig` không ghi cứng server URL, Spring Security khớp whitelist
sau khi đã bỏ context-path ⇒ **không phải sửa dòng Java nào**.

**N7 — lỗi CORS đã vá, chỉ sửa `application.yaml`, KHÔNG đụng `common/`.**

Nguyên nhân: `SecurityConfig` đọc bằng `@Value`, yaml lại khai bằng **list YAML**. `@Value` không
nhìn thấy list YAML — list bị tách thành `allowed-origins[0]`, khoá `allowed-origins` không tồn
tại ⇒ placeholder âm thầm rơi về default ⇒ **`FRONTEND_BASE_URL` bị bỏ qua hoàn toàn**.
Lỗi có sẵn trong khuôn mẫu gốc (`git diff HEAD` không có dòng nào về cors).

Vá bằng cách đổi list thành **chuỗi một dòng ngăn bằng dấu phẩy**:
```yaml
app:
  cors:
    allowed-origins: ${FRONTEND_BASE_URL:http://localhost:5173,http://localhost:3000}
```

Đã kiểm bằng test tạm (3 ca, chạy xong xoá file): `.env` hiện tại → 2 origin tách đúng ·
giả lập deploy `https://shop.diauml.id.vn` → **được áp dụng** (ca này trước đây hỏng) ·
không khai biến → dùng default.

🆕 **BẪY phải nhớ: `@Value` KHÔNG đọc được list YAML.** Chỉ đọc chuỗi ngăn bằng dấu phẩy.
Mà vì luôn có default nên nó **không ném lỗi** — app chạy ngon với giá trị sai. Chỗ nào cần
list thì hoặc viết chuỗi phẩy, hoặc dùng `@ConfigurationProperties`.

---

### 7.2 — Tính năng còn trong hàng chờ
| Đợt | Việc |
|---|---|
| **4D-phần 2** | Admin CRUD `product_variants` + CRUD `product_images`. **Bro bảo ĐỂ ĐÓ** |
| **5** | Review (web đã có form) · Wishlist · Voucher (mobile) · Notification · Chat |
| sau | Huỷ đơn phía khách `PATCH /orders/me/{orderNumber}/cancel` (**phải hoàn kho**) |
| sau | Thanh toán thật cho `BANK_TRANSFER` (hiện chỉ ghi nhận) |
| sau | Mail xác nhận đơn hàng + mail chào mừng — **chờ bên product–order xong mới làm** |
| ~~sau~~ | ~~Upload ảnh thật~~ — ✅ **người khác đã làm**: `infrastructure/external/cloudinary/` + `PUT /profiles/me/avatar` + `/products/admin/{id}/images`. Chốt **Cloudinary** |

### 7.4 — 🆕 AUDIT MODULE NGƯỜI KHÁC (17/10/2026) — Collection · Product · Category · Cloudinary

Phạm vi: ~3.800 dòng. Chất lượng nền **tốt** (magic-byte, redact credential, @EntityGraph chống N+1,
optimistic lock có `expectedVersion`, soft-delete nhất quán, @Valid đủ). ✅ **ĐÃ VÁ 17/10** — xem cột cuối.
Build sau khi vá: `Tests run: 94, Failures: 0, Skipped: 11` · `BUILD SUCCESS`. 19 file đổi, +174/−30.

| # | Mức | Phát hiện |
|---|---|---|
| A1 | 🔴 | **Gọi mạng Cloudinary nằm TRONG transaction, sau khi đã giữ khoá bi quan.** `ProfileServiceImpl.uploadAvatar:76`, `ProductServiceImpl.addImage:127`/`replaceImage:266`/`uploadThumbnail:148`, `CollectionServiceImpl.uploadCover:132`. Chuỗi: `@Transactional` → `findWithLockById` (PESSIMISTIC_WRITE) → `upload()` mạng 30s → `saveAndFlush` → `deleteOrFail()` mạng 30s. Cộng quota check (`CloudinaryQuotaService.getState()` **synchronized** + mạng 30s) ⇒ 1 request giam 1 connection tới ~90s. `DB_POOL_SIZE:5`, không đặt transaction timeout ⇒ **5 người upload cùng lúc lúc Cloudinary chậm = chết TOÀN BỘ API**, không riêng upload | ✅ **VÁ**: upload ảnh dời lên TRƯỚC khi chạm DB (5 chỗ) + `hikari.auto-commit=false` & `provider_disables_autocommit=true` (bắt buộc đi cặp) + pool 5→20 + Cloudinary timeout 30s→10s |
| A2 | 🔴 | **`IMAGE_ORDER` (ProductServiceImpl:47) NPE khi `sortOrder` null** — dùng ở dòng 108 `.min`, 195 `.sorted`, 200 `.compare`. Mâu thuẫn nội bộ: `nextSortOrder:294` lại có `.filter(Objects::nonNull)`. Cột khai `nullable=false` nhưng **`ddl-auto: update` không bao giờ siết NOT NULL lên bảng đã có dữ liệu** ⇒ hàng cũ có thể null ⇒ sửa gallery sản phẩm cũ là 500. Phải chọn 1 giả định: `nullsLast` hoặc backfill + bỏ filter | ✅ **VÁ**: `Comparator.nullsLast` cho cả `sortOrder` lẫn `id`. ⚠️ Kiểm lại thấy tỷ lệ thật ~0% trên DB mới (entity+cột cùng commit, seed có gán sortOrder) — vá để gỡ mâu thuẫn, không phải vì đang chảy máu |
| A3 | 🟠 | **`validateFormat:214` bắt Content-Type khớp tuyệt đối** ⇒ Flutter gửi `application/octet-stream` (mặc định của `MultipartFile.fromPath` khi không truyền `contentType`) sẽ ăn **415** dù ảnh hợp lệ và magic-byte đã đúng | ✅ **VÁ**: bỏ qua khi content-type rỗng hoặc `application/octet-stream`; vẫn chặn khi client khai RÕ một loại ảnh sai |
| A4 | 🟠 | **Không chặn `size` trang** — thiếu `spring.data.web.pageable.max-page-size` ⇒ mặc định Spring 2000. `GET /products?size=2000` kéo 2000 bản ghi. Áp cho MỌI module kể cả `/accounts`. Nên đặt 100 | ✅ **VÁ**: `max-page-size: 100` + `default-page-size: 20` |
| A5 | 🟠 | **Luật thumbnail không nhất quán**: `createProduct:59` cho tạo product ACTIVE **không có** thumbnail, nhưng `updateProduct:110` lại ném 409 "An ACTIVE product must have a thumbnail" | ✅ **VÁ**: `createProduct` ép HIDDEN khi `!hasText(thumbnail)`, theo đúng tiền lệ `createCollection`. Test cũ `...KeepsOldActiveDefault...` đã đổi tên + mở rộng thành 3 ca |
| A6 | 🟡 | `ManagedImageService.deleteOrFail:128` bắt `RuntimeException` rồi ép hết thành BAD_GATEWAY ⇒ che mất `ApiException(BAD_REQUEST,"Invalid managed image public ID")` là lỗi dữ liệu thật | ✅ **VÁ**: thêm `catch (ApiException) { throw exception; }` trước nhánh `RuntimeException` |
| A7 | 🟡 | **Sửa BST đang ACTIVE phải `PATCH /hide` trước** (`requireHidden:309`). Hợp lý nhưng FE chưa được báo, không có trong tài liệu | ✅ **TÀI LIỆU**: `tai_lieu_shop_cho_fe/HUONG_DAN_FE_SHOP.md` §5.2 có sơ đồ trạng thái |
| A8 | 🟡 | Quota chặn ở 75% ⇒ bỏ không 25% hạn mức; đọc usage lỗi thì **chặn sạch upload** (fail-closed, có chủ ý) | ⏸ **GIỮ NGUYÊN 75%** theo chốt của bro. Đã ghi cách chỉnh vào tài liệu §4.4 |
| A9 | 🟡 | `@Slf4j` quay lại ở 3 class Cloudinary — trái quy ước "không log trong service" đặt hồi làm auth. Ở đây em thấy HỢP LÝ (lỗi dịch vụ ngoài cần dấu vết) nhưng nên thống nhất 1 luật chung | ✅ **ĐÃ SOÁT KỸ 17/10 — log GIỮ NGUYÊN, có lý do**: `GlobalExceptionHandler.handleApiException` KHÔNG log, nên cả 3 chỗ log đều là dấu vết duy nhất (2 chỗ nuốt hẳn exception, 1 chỗ giữ `publicId` mà message client không có). Khác log auth cũ — mấy cái đó chỉ in lại thứ đã nằm trong response. **Nhưng dọn 3 thứ rác cạnh đó**: ① logger khai 2 kiểu (`@Slf4j` vs `LoggerFactory` thủ công) ⇒ thống nhất `@Slf4j`, bỏ 2 import + 1 field ② `if (exception instanceof ApiException)` trong `catch (RuntimeException)` lặp 2 lần ⇒ đổi sang `catch (ApiException)` riêng, gộp `IOException | RuntimeException` ③ `ProfileServiceImpl` inject CẢ `ManagedImageService` lẫn `CloudinaryService` (façade thủng) ⇒ thêm `deleteAfterCommit` vào `ManagedImageService`, feature chỉ còn phụ thuộc 1 cửa |
| A10 | 🟡 | **0 test cho auth/account** — 94 test đều thuộc collection/product/category/profile/cloudinary/order | ⏸ **CHƯA LÀM** — việc lớn, tách đợt riêng |

---

**Vòng audit 3 (17/10) — 4 phát hiện đã vá:**
① `removeImage` có **hai guard trùng message**, guard sau còn bắn nhầm ca sản phẩm cũ ACTIVE/thumbnail null
(xoá ảnh giữa cũng báo *"last image"*) ⇒ gộp còn **một guard đặt TRƯỚC mọi thay đổi**, message nói đúng sự thật.
② `thumbnail` của Product **không hề bị ép http/https** trong khi `coverImageUrl` của Collection thì có
⇒ thêm `ManagedImageService.requireExternalImageUrl()` dùng chung, Collection bỏ bản `URI` chép tay.
③ Định nghĩa *"sản phẩm đang bán"* (`status == null || ACTIVE`) **chép ở 3 service** (Product/Collection/Order)
⇒ gom về `ProductEntity.isVisible()`.
④ Dọn 4 import mồ côi sinh ra sau khi gộp.
**Đã tự bác bỏ ở vòng 3, đừng báo lại:** N+1 ở `collection_products` (đã có `@EntityGraph(product)`) ·
thiếu `@Transactional` (không thiếu chỗ nào) · `CategoryRequest` thiếu guard `status=DELETED` (record không có field status).
**❌ CHỐT KHÔNG LÀM (bro quyết 17/10):** *"không ảnh hưởng nghiệp vụ hay làm dự án chạy nặng thì không cần,
sợ sửa lỗi của người ta"*. Ba mục dưới đây đóng lại, **đừng đề xuất lại ở các vòng audit sau**:
- (a) `CollectionServiceImpl:280,358` tự `new` DTO thay vì qua mapper — lệch luật nội bộ, **0 ảnh hưởng hành vi, 0 ảnh hưởng hiệu năng**.
- (b) Regex slug `^[a-z0-9]+(-[a-z0-9]+)*$` lặp 7 nơi (6 DTO + `ProductServiceImpl:374`) — thuần DRY, phải đụng `common/util/SlugUtils`.
- (c) Category không khoá bi quan khi ghi. ⚠️ Khác (a)(b): đây **là rủi ro thật, chỉ là chấp nhận có ý thức**.
  Đua *xoá category rỗng ↔ tạo product vào nó* ⇒ product nằm trong category DELETED. Hậu quả nhẹ (product vẫn
  hiện bình thường, `GET /products` không phụ thuộc trạng thái category) và cách bịt lại tốn hơn: phải khoá dòng
  category trên **mọi** lượt ghi product. Nếu sau này có màn admin sửa category hàng loạt thì cân nhắc lại.

---

**Dọn dẹp kèm theo (vòng audit 2):** xoá 3 method repository chết (`CategoryRepository.findByCategoryNameIgnoreCase`,
`ProductRepository.existsByCategoryId`, `CollectionProductRepository.existsByCollectionIdAndProductId`) ·
đưa Specification viết lạc trong `CategoryServiceImpl.deleteCategory` về `ProductSpecifications.inCategory()` ·
thêm 5 index còn thiếu (`products.category_id`, `products.status`, `product_images(product_id,sort_order)`,
`collections.status`, `categories.status`) — Postgres KHÔNG tự đánh index cho khoá ngoại.
⚠️ Việc này **đảo quyết định cũ "không dọn CategoryRepository"**, vì nay Category nằm trong phạm vi bro giao.
Báo nhầm đã tự loại: `CategoryServiceImpl.isPublic` (dùng qua method reference) · nghi ngờ Category null-status
không nhất quán (có javadoc, là cố ý).

---

### 7.8 — 🔬 AUDIT NGHIỆP VỤ + CẤU TRÚC + TÀI LIỆU (17/10) — vòng 2

**Build: `Tests run: 172, Failures: 0, Skipped: 11` · 95 endpoint · độ phủ tài liệu 95/95.**

**🐞 N1 — trần `@Max(999)` LÁCH ĐƯỢC qua việc gộp dòng.** Validation chạy trên TỪNG phần tử client
gửi; `mergeDuplicates()` cộng lại sau đó. Gửi cùng `(productId,size)` ba dòng 999 ⇒ đơn 2.997 món.
⇒ Đã kiểm LẠI trần sau khi gộp. Test `OrderBusinessRuleTest`.

**🐞 N2 — giỏ không giới hạn số dòng.** `items` chỉ có `@NotEmpty`. 10.000 dòng ⇒ một câu
`IN (...)` khổng lồ. ⇒ Thêm `@Size(max = 50)`.

**🐞 N3 — admin KHÔNG tra được đơn theo mã.** Chỉ có `GET /orders/{id}` (UUID) và `?status=`.
Khách gọi điện đọc `LBVN-2610-A7K2` thì admin phải lật từng trang. ⇒ Thêm
`GET /orders/number/{orderNumber}` + `findWithItemsByOrderNumber` (có `@EntityGraph`).

**🐞 N4 — `StoreServiceImpl` chép lại định nghĩa "đang bán"** (`status == null || ACTIVE`) thay vì
theo tiền lệ `ProductEntity.isVisible()`. ⇒ Thêm `StoreEntity.isVisible()`.

**🔴 N5 — TÀI LIỆU: 13 endpoint KHÔNG có tài liệu nào.** `/orders` **7 cái** (rubric mục 5+6!),
`/roles` 5 cái, `PUT /profiles/me/avatar`. ⇒ Đã viết `tai_lieu_don_hang_cho_fe/` (2 file: hướng
dẫn + **86 ca test**) và thêm Phụ lục P.1/P.2 vào `HUONG_DAN_FE_FLUTTER.md`.
**Bài học: soát ĐỘ PHỦ tài liệu bằng script, đừng tin cảm giác "đã viết rồi".**

**🐞 N6 — số liệu lệch trong tài liệu:** "92 endpoint" (thật 95); tài liệu shop không nhắc 4
endpoint variant. ⇒ Đã sửa + thêm con trỏ chéo giữa 4 bộ tài liệu.

**✅ 12 mặt audit KHÔNG ra lỗi:** TODO/FIXME/`System.out` · mapper không `componentModel=spring` ·
controller không trả `ApiResponse` · cấu trúc gói 11 module (auth thiếu repo/entity và notification
thiếu dto/request là ĐÚNG, không phải lệch) · `@Enumerated(STRING)` · `@Size` vs `length` cột ·
giá ≤ 0 · xoá Category còn sản phẩm · `GlobalExceptionHandler` (7 handler, có
`OptimisticLocking` → 409 và `DataIntegrityViolation`) · `orderNumber` `unique=true` · snapshot
dòng hàng · độ dài dòng (306/330 dòng dài là comment, base_rule cấm format lại file cũ).

**⚠️ 5 KHOẢNG TRỐNG NGHIỆP VỤ SO VỚI SHOP QUẦN ÁO THẬT — cố ý không làm, KHÔNG nằm trong rubric:**
1. 🔴 **Đơn `PENDING` bị bỏ quên GIAM TỒN KHO vĩnh viễn.** Không có `@Scheduled` nào dọn.
   Shop thật tự huỷ sau 24–48h rồi trả hàng về kho. **Cố ý không làm**: một tác vụ nền tự huỷ đơn
   chạy đúng lúc demo là rủi ro lớn hơn lợi ích. Admin huỷ tay. **Nếu làm tiếp thì đây là việc #1.**
2. Mã giảm giá — `discount` luôn 0, cột đã có sẵn.
3. Đánh giá sản phẩm / wishlist / gợi ý sản phẩm liên quan.
4. Sổ địa chỉ — FE lấy tạm từ `GET /profiles/me`.
5. Đổi trả hàng, mã vận đơn.

**⚠️ 2 rủi ro nhỏ chấp nhận:** không chặn tần suất gửi tin nhắn chat · thông báo không có cơ chế
dọn bớt khi tích nhiều.

**🗑️ Đã XOÁ `ke_hoach_do_an/`** (6 file, 1.450 dòng) — Đợt A–E đều xong, nội dung đã chuyển hết
sang code + 4 bộ tài liệu. Theo luật "plan nào xong thì xoá đi cho nhẹ".

**📚 BỐN BỘ TÀI LIỆU HIỆN CÓ:** `tai_lieu_auth_cho_fe/` (auth + phụ lục avatar/roles) ·
`tai_lieu_shop_cho_fe/` (product/category/collection) · `tai_lieu_don_hang_cho_fe/` (🆕 đơn hàng) ·
`tai_lieu_phan_moi_cho_fe/` (variant/cửa hàng/thông báo/chat/huỷ đơn).

---

### 7.7 — 🔬 AUDIT SÂU SAU KHI XÂY XONG (17/10) — 3 LỖI THẬT ĐÃ VÁ

**Build cuối: `Tests run: 166, Failures: 0, Skipped: 11` · 94 endpoint.**

**🐞 V1 — `notifyOrderStatusChanged` có try/catch KHÔNG BAO GIỜ CHẠY (lỗi do chính em gây ra).**
`@Transactional` mặc định = REQUIRED ⇒ nhập chung giao dịch. `save()` với khoá UUID **chưa đẩy
câu lệnh xuống DB ngay**, nên vi phạm ràng buộc chỉ nổ lúc **commit giao dịch ngoài** — sau khi
khối `catch` đã thoát. Bắt rồi đi tiếp trong cùng giao dịch còn tệ hơn: Hibernate đã đánh dấu
rollback-only ⇒ commit ném `UnexpectedRollbackException`.
⇒ **Đã bỏ try/catch + bỏ `@Slf4j`, cho lỗi nổi lên, ghi chung giao dịch.** Nuốt lỗi chỉ hợp lý
với hệ thống NGOÀI (Cloudinary, Brevo), không hợp lý với một dòng INSERT xuống chính DB đang mở.
**Test cũ pass vì mock `save()` ném ngay — kiểm sai kịch bản thật.** Đã viết lại.

**🐞 V2 — mail xác nhận đơn gửi TRƯỚC khi commit.** `product_variants` có `@Version` ⇒ hai khách
cùng mua món cuối làm một giao dịch ném `OptimisticLockException` **đúng lúc commit** ⇒ đơn bị huỷ
bỏ mà khách đã nhận mail xác nhận.
⇒ **Đã chuyển sang `TransactionSynchronizationManager...afterCommit`.** Đăng ký đặt ở
`OrderServiceImpl` chứ KHÔNG ở `BrevoMailClient`: `@Async` đi qua proxy, gọi từ trong chính bean
đó thì proxy bị bỏ qua và mail chạy đồng bộ, chặn response.

**🐞 V3 — hội thoại RỖNG nhảy lên đầu danh sách admin.** `GET /conversations/me` tạo sẵn hội thoại
lúc khách mở màn chat ⇒ có bản ghi `last_message_at = null`. **PostgreSQL coi NULL là lớn nhất**
nên `ORDER BY ... DESC` đẩy NULL LÊN ĐẦU.
⇒ Đã đổi sang `findByLastMessageAtNotNullOrderByLastMessageAtDesc`. **Cố ý KHÔNG dùng
`@Query ... NULLS LAST`** vì HQL đó chưa chạy thật bao giờ trong sandbox (không có DB) — derived
query vừa an toàn vừa đúng nghiệp vụ hơn.

**🐞 V4 — tài liệu lệch code** sau khi tách endpoint chat. Đã đồng bộ lại 22 chỗ.
**Bài học: đổi API xong phải soát lại docs NGAY, đừng để sang lượt sau.**

**✅ 10 mặt audit KHÔNG RA LỖI:** `readOnly` trên hàm ghi · thiếu `@Transactional` · endpoint ghi
không guard (11 cái `/me` đều có `@AuthenticationPrincipal`, repository đều ràng theo `accountId`)
· whitelist nuốt route admin · `.env-example` lệch (60/60) · method repository chết · import mồ côi
· DTO lệch khuôn · `@Size` vượt `length` cột · enum thiếu `@Enumerated(STRING)`.

**⚠️ 2 RỦI RO CHẤP NHẬN CÓ Ý THỨC (không vá):**
1. `ConversationEntity` **không có `@Version`** ⇒ hai admin trả lời cùng lúc có thể mất 1 đơn vị
   ở bộ đếm chưa đọc. Chỉ sai con số hiển thị, không mất tin nhắn. Mở lại nếu làm nhiều admin.
2. `CollectionServiceImpl:277,350` tự `new` DTO — đã chốt KHÔNG LÀM từ lượt 38.

**🚫 PayOS / VNPay — CHỐT KHÔNG LÀM (user duyệt 17/10: "tạm bỏ cũng được nhưng nên note lại").**
Lý do: webhook cần URL công khai (ngrok/deploy), cần xác thực chữ ký, cần idempotent chống gọi lại.
Đề bài không yêu cầu, rubric không có mục thanh toán online ⇒ 0 điểm thêm, rủi ro vỡ lúc demo cao.
Hiện có `COD` + `BANK_TRANSFER` (đối soát tay) là đủ. **Muốn mở lại thì đây là việc đầu tiên.**

**✉️ Mail xác nhận đơn — ĐÃ LÀM (user duyệt: dùng chung Brevo, khỏi đăng ký tài khoản mới).**
⚠️ Chia nhau 300 mail/ngày với OTP đăng ký. Hết quota: mail đơn im lặng bỏ qua, `/auth/register`
trả 503. **Dặn cả nhóm đừng spam đăng ký trước buổi demo.**

**🔀 Endpoint chat đã TÁCH LÀM HAI** (quyết định của em, user bảo "theo ý em"):
`GET /messages` trả `PageResponse` mới→cũ · `GET /messages/new?since=` trả mảng cũ→mới.
Lý do: một URL trả hai kiểu dữ liệu ⇒ phải khai `ApiResponse<?>`, Swagger sinh tài liệu sai và FE
không sinh được model.

---

### 7.6 — ✅ ĐÃ XÂY XONG A→E TRONG MỘT LƯỢT (17/10)

**(Số liệu lượt đó; sau audit §7.7 là 166 test / 94 endpoint.) CHƯA COMMIT.**

| Đợt | Nội dung | Test |
|---|---|---|
| **A** ✅ | CRUD variant: `POST/PATCH/DELETE/GET /products/admin/{id}/variants`. `expectedVersion` như Collection. Xoá bị chặn khi size còn trong đơn PENDING/CONFIRMED/SHIPPING, và khi là size cuối của sản phẩm ACTIVE | 14 |
| **B** ✅ | Module `store` 7 endpoint + seed 4 chi nhánh HCM toạ độ thật | 10 |
| **C** ✅ | Module `notification` 5 endpoint, nối vào `OrderServiceImpl.updateStatus`, **nuốt lỗi có chủ đích** | 14 |
| **D** ✅ | Module `chat` 9 endpoint, polling qua `?since=`, không WebSocket | 18 |
| **E** ✅ | `POST /orders/me/{orderNumber}/cancel` + cột `orders.cancel_reason` | 10 |

**3 câu đã tự quyết (user uỷ quyền):** giỏ hàng để ở Flutter · thông báo chỉ in-app, KHÔNG FCM ·
chat polling 3s, KHÔNG WebSocket. Lý do đầy đủ ở `ke_hoach_do_an/00_TONG_QUAN_VA_CHIA_VIEC.md` §3.

**🐞 3 LỖI TỰ BẮT ĐƯỢC TRONG LÚC LÀM — nhớ để không tái phạm:**
1. 🔴 **`/stores/*` trong whitelist khớp luôn `/stores/admin`** ⇒ đổi route công khai thành
   `/stores/id/{id}` + whitelist `/stores,/stores/id/**`, đúng cách `/categories/id/{id}` đang làm.
   Đã có ca test chống hồi quy (§A3 của `TEST_PHAN_MOI_SWAGGER.md`).
2. 🔴 **MapStruct âm thầm trả `isRead = false` cho MỌI thông báo**: Lombok sinh getter `isRead()`
   cho field `isRead` ⇒ theo JavaBeans property tên là `read`, không khớp component `isRead` của
   record ⇒ MapStruct không tìm thấy nguồn và bỏ qua. Phải `@Mapping(target="isRead", source="read")`.
   **Bài học: field boolean đặt tên bắt đầu bằng `is` thì luôn phải map tường minh.**
3. `@Builder.Default` cần `import lombok.Builder` dù class dùng `@SuperBuilder`.

**4 bảng + 1 cột mới do `ddl-auto: update` tự tạo:** `stores`, `notifications`, `conversations`,
`chat_messages`, `orders.cancel_reason`. 5 index mới. **Không thêm biến môi trường nào.**

**📄 Tài liệu:** `tai_lieu_phan_moi_cho_fe/{HUONG_DAN_FE_PHAN_MOI.md, TEST_PHAN_MOI_SWAGGER.md}` (110 ca).

**⚠️ SANDBOX:** từ lượt này `apt-get` phải chạy qua **`sudo -n apt-get install -y openjdk-21-jdk-headless`**,
không có `sudo` thì lỗi dpkg lock. Đừng chạy apt trong background.

---

### 7.5 — 🆕 KẾ HOẠCH PHẦN CÒN LẠI (17/10)

**📁 Chi tiết nằm ở `PRM_BE/ke_hoach_do_an/` — 6 file. Đây chỉ là bản tóm tắt, đừng sửa 2 nơi.**

| Mục rubric | BE | Đợt |
|---|---|---|
| 1 DB/API · 2 Login · 3 Danh sách · 6 Thanh toán | ✅ xong | — |
| 4 Chi tiết SP | ⚠️ **thiếu CRUD variant** | **A** 🔴 |
| 5 Giỏ hàng | ✅ chốt để ở Flutter, BE không làm | — |
| 7 Thông báo | ❌ trắng | **C** |
| 8 Bản đồ cửa hàng | ❌ trắng | **B** |
| 9 Nhắn tin | ❌ trắng | **D** |
| 10 State management | — thuần Flutter | — |

**🔴 CHẶN DEMO:** `product_variants` có entity + `@Version` + đã lộ ra `ProductDetailResponse`, và
`OrderServiceImpl` đã trừ/hoàn kho — **nhưng không có API nào tạo variant**. Sản phẩm mới tạo có 0 size
⇒ không mua được ⇒ hỏng cả mục 4, 5, 6. Làm Đợt A trước mọi thứ.

**Thứ tự:** A → B → C → D → E. **Chia việc:** bro = A, E, phần nối notification vào `OrderServiceImpl`
+ màn 2/5/6/10; bạn kia = B, C + màn 3/4/7/8; D làm đôi.

**3 quyết định đã khuyến nghị (chờ bro chốt):** giỏ hàng để ở Flutter · thông báo chỉ in-app, KHÔNG FCM ·
chat dùng polling 3s có tham số `since`, KHÔNG WebSocket.

**⚠️ Nhận định quan trọng:** 8/10 mục là MÀN HÌNH ⇒ nút thắt đồ án nay nằm ở **Flutter**, không phải BE.
Chưa từng nhìn thấy repo Flutter ⇒ phần đó trong kế hoạch mới chỉ là ước lượng thô.

---

### 7.3 — Nợ kỹ thuật
| # | Việc |
|---|---|
| ① | **`user_rt:{uid}` tích rác — HOÃN, bro chốt.** Redis SET không TTL theo phần tử nên jti chết nằm lại. Sửa: đổi SET → **ZSET**, score = mốc hết hạn, `removeRangeByScore` trong `store()`. 🔴 **Hoãn vì key đổi KIỂU DỮ LIỆU** — `ZADD` lên key SET cũ ném `WRONGTYPE` ⇒ **sập đăng nhập**. Chỉ làm khi có dịp `FLUSHDB`. Giảm nhẹ: SET bị xoá sạch mỗi lần logout-all/đổi mật khẩu/đổi email/xoá tài khoản, và tự hết hạn sau 7 ngày |
| ② | **Tách cookie `Path=/auth` cho refresh token** — hoãn, không huỷ |
| ③ | **Gỡ `spring-boot-starter-data-mongodb`** nếu chốt Chat không dùng Mongo. Hiện 0 dòng code nào dùng, giữ lại chỉ vì Đợt 5 có Chat |
| ~~④~~ | ~~Commit + push ~100 file~~ — ✅ **XONG 17/10**. Toàn bộ auth đã nằm trên `main` (`6e6bc63`), 8/8 bản vá audit khớp hệt bản local. **Nhánh `feat/auth` là ảnh chụp cũ, BỎ QUA.** |
| ⑤ | 🆕 Xoá `tai_lieu_auth_cho_fe/hi.md` — file **rỗng 0 byte**, lọt vào commit `2a7c946 auth v3` |
| ⑤ | `RefreshTokenRedis` dùng SET, cân nhắc gộp với ① |
| ~~⑥~~ | ~~**OTP đổi email**~~ — ✅ **XONG 18/10, commit `2da739f`.** `PATCH /accounts/me` đã XOÁ, thay bằng `POST /accounts/me/email/request` → `/confirm`. Khoá OTP cắm theo **accountId** (cắm theo email mới thì đổi địa chỉ mỗi lần bấm là lách sạch cooldown + trần gửi). Bước 2 **kiểm trùng email lần hai** vì giữa hai bước có 10 phút. `EmailChangeServiceTest` 11 ca. |
| ~~⑦~~ | ~~**Điều tra `lockedUntil`**~~ — ✅ **XONG 18/10.** Khoá tạm **có** tự hết hạn (`AccountPrincipal.isAccountNonLocked` đọc mốc, không cần job). Để trống = khoá **vô thời hạn**. **Nhưng phát hiện lỗi:** cột `status` không bao giờ được ghi lại về `ACTIVE`, mà `forgotPassword`/`resetPassword` chỉ so `status == ACTIVE` ⇒ người hết hạn khoá **đăng nhập được nhưng không lấy lại mật khẩu được**. Đã gom luật vào `AccountEntity.isLoginAllowed()` + `AccountLockTest` 7 ca phủ bảng chân trị. |
| ⑧ | 🆕 **Màn quản trị phải tự so `lockedUntil` với hiện tại.** `GET /accounts/{id}` của tài khoản đã hết hạn khoá **vẫn trả `status: LOCKED`** — đúng với dữ liệu trong DB, nhưng nhìn vào thì tưởng còn bị khoá. ✅ **BRO CHỐT 18/10: ĐỂ YÊN** — `lockedUntil` đã nằm sẵn trong response, FE so một dòng là xong. KHÔNG thêm `effectiveStatus` (đụng DTO dùng chung ở 6 endpoint). Đã ghi cảnh báo vào `TEST_AUTH_SWAGGER.md`. |
| ⑨ | 🆕 **Cloudinary `upload()` / `deleteOrFail()` nằm TRONG `@Transactional`** — 8 chỗ ở `ProductServiceImpl`, `CollectionServiceImpl`, `StoreServiceImpl`, `ProfileServiceImpl`. Giữ connection pool suốt lúc tải ảnh lên mạng; vài người tải cùng lúc là cạn pool, kéo sập cả endpoint không liên quan. **Code của team, CHƯA ĐỤNG** — sửa là phải tách upload ra ngoài ranh giới giao dịch ở cả 4 service. Lưu ý `requireExternalImageUrl`/`requireUnmanagedUrl` CHỈ parse chuỗi, không chạm mạng ⇒ không tính. |
| ⑩ | `sendPasswordChangedEmail` (`AccountServiceImpl:434`) gửi TRƯỚC commit — **đã chốt KHÔNG LÀM**, ghi lại để khỏi báo trùng. |

---

## 8. GIỚI HẠN SANDBOX

- **Không có Postgres/Redis** → không boot được app. Mọi test end-to-end bro chạy máy mình.
- **JDK mặc định của sandbox là 11 (SAI)** — gây `handshake_failure` đánh lừa.
  Mỗi lượt build phải cài lại:
  ```bash
  apt-get install -y openjdk-21-jdk-headless
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
  ```
  Gói apt **không nằm trong snapshot workspace**, chỉ `/home/user` được giữ.
- `chmod +x mvnw` làm git báo ` M mvnw` → chạy xong `chmod 644` lại.
