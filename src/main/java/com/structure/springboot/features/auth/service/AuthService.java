package com.structure.springboot.features.auth.service;

import com.structure.springboot.features.auth.dto.request.LoginRequest;
import com.structure.springboot.features.account.dto.request.AccountRegistrationRequest;
import com.structure.springboot.features.auth.dto.request.ForgotPasswordRequest;
import com.structure.springboot.features.auth.dto.request.GoogleLoginRequest;
import com.structure.springboot.features.auth.dto.request.ResetPasswordRequest;
import com.structure.springboot.features.auth.dto.request.VerifyOtpRequest;
import com.structure.springboot.features.auth.dto.request.VerifyRegistrationRequest;
import com.structure.springboot.features.auth.dto.response.LoginResponse;
import com.structure.springboot.features.auth.dto.response.OtpTicketResponse;
import com.structure.springboot.features.auth.dto.response.RegistrationPendingResponse;

import java.util.List;

public interface AuthService {

    /*
     * Ba hàm dưới nhận DANH SÁCH nguồn token thay vì một chuỗi: client có thể để token ở body
     * (mobile), cookie (web) hoặc header, nên Controller gom hết theo thứ tự ưu tiên rồi đưa
     * xuống, service chọn nguồn ĐẦU TIÊN đọc được. Nhờ vậy một nguồn rác — ví dụ Swagger điền
     * sẵn chuỗi "string" vào body — không còn che mất cookie hợp lệ nằm ngay phía sau.
     */

    /**
     * Bước 1 của đăng ký: <b>KHÔNG tạo tài khoản</b>, chỉ cất hồ sơ vào Redis và gửi mã xác minh.
     *
     * <p>Đổi so với thiết kế cũ (đăng ký xong trả token thẳng): tài khoản chỉ ra đời sau khi
     * {@link #verifyRegistration} xác nhận người đăng ký thật sự đọc được hộp thư đó.</p>
     */
    RegistrationPendingResponse register(AccountRegistrationRequest request);

    /**
     * Bước 2 của đăng ký: nhập đúng mã thì <b>lúc này tài khoản mới được tạo</b>, rồi cấp token
     * như một lần đăng nhập bình thường.
     *
     * <p>Sai quá số lần cho phép, hoặc để quá hạn, thì hồ sơ tạm bị xoá sạch — coi như chưa từng
     * có ai đăng ký bằng địa chỉ đó, database không hề bị động tới.</p>
     */
    LoginResponse verifyRegistration(VerifyRegistrationRequest request);

    /**
     * Xác thực tài khoản và phát hành cặp access/refresh token.
     *
     * <p>Service thuần nghiệp vụ, KHÔNG biết tới HttpServletRequest/Response — việc quyết định
     * nhét token vào cookie (web) hay trả trong body (mobile) thuộc về Controller. Vì vậy
     * {@code clientIp} được Controller bóc sẵn rồi truyền vào, chỉ dùng cho bộ đếm chống dò mật
     * khẩu; truyền null thì bỏ qua tầng chặn theo IP.</p>
     */
    LoginResponse login(LoginRequest request, String clientIp);

    /**
     * Đổi refresh token còn hạn lấy một cặp token mới, không bắt người dùng đăng nhập lại.
     *
     * <p><b>Có xoay vòng (rotation)</b>: refresh token cũ bị thu hồi ngay khi cấp token mới,
     * nên một chuỗi refresh token chỉ dùng được đúng một lần.</p>
     *
     * <p>Hàm này <b>ném 401</b> khi không nguồn nào là refresh token còn hạn — client cần biết
     * để đẩy người dùng về màn hình đăng nhập.</p>
     */
    LoginResponse refresh(List<String> tokenCandidates);

    /**
     * Đăng xuất <b>đúng thiết bị hiện tại</b>: xoá refresh token của phiên này khỏi Redis,
     * các thiết bị khác vẫn đăng nhập bình thường.
     *
     * <p>Token <b>đã hết hạn vẫn thu hồi được</b>: access token chết không đồng nghĩa phiên đã
     * chết, refresh token của phiên đó còn sống 7 ngày. Nhưng nếu KHÔNG nguồn nào đọc nổi thì
     * ném <b>401</b> — báo 200 trong khi chưa thu hồi được gì là nói dối người dùng.</p>
     */
    void logout(List<String> tokenCandidates);

    /**
     * Đăng xuất <b>mọi thiết bị</b>: xoá sạch refresh token của tài khoản và cắm mốc thời gian
     * chặn những access token đã phát hành trước đó.
     *
     * <p>Cùng quy ước lỗi với {@code logout}: hết hạn vẫn thu hồi, không đọc nổi thì 401.</p>
     */
    void logoutAll(List<String> tokenCandidates);

    /**
     * Bước 1 quên mật khẩu: phát mã OTP và gửi qua email.
     *
     * <p><b>Không bao giờ báo lỗi "email không tồn tại"</b> — kể cả khi tài khoản không có hoặc
     * đang bị khoá, hàm vẫn kết thúc êm. Báo thật là tặng kẻ tấn công công cụ dò danh sách email
     * đã đăng ký, đúng lý do {@code /auth/login} gộp mọi lỗi thành một câu duy nhất.</p>
     */
    void forgotPassword(ForgotPasswordRequest request);

    /**
     * Bước 2: đổi mã OTP lấy một vé tạm.
     *
     * <p>Tách làm hai bước thay vì nhận cả OTP lẫn mật khẩu mới trong một lần gọi để giao diện
     * xác minh xong là chuyển màn ngay, và để OTP chết trước khi người dùng kịp gõ mật khẩu mới —
     * mã 6 số không nên sống lâu bằng thời gian ngồi nghĩ mật khẩu.</p>
     */
    OtpTicketResponse verifyOtp(VerifyOtpRequest request);

    /** Bước 3: dùng vé tạm đặt mật khẩu mới, rồi đá sạch mọi phiên đang mở. */
    void resetPassword(ResetPasswordRequest request);

    /**
     * Đăng nhập bằng ID token của Google, trả về đúng {@code LoginResponse} của
     * {@code /auth/login} nên frontend tái dùng y nguyên code xử lý sau đăng nhập.
     *
     * <p>Lần đầu của một người mới sẽ tự tạo tài khoản — không có bước "đăng ký bằng Google"
     * riêng, vì bắt bấm thêm một nút nữa trong khi đã biết đủ thông tin là thừa.</p>
     */
    LoginResponse loginWithGoogle(GoogleLoginRequest request);
}
