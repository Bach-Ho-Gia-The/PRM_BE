package com.structure.springboot.features.auth.service;

import com.structure.springboot.features.auth.dto.request.LoginRequest;
import com.structure.springboot.features.auth.dto.response.LoginResponse;

import java.util.List;

public interface AuthService {

    /*
     * Ba hàm dưới nhận DANH SÁCH nguồn token thay vì một chuỗi: client có thể để token ở body
     * (mobile), cookie (web) hoặc header, nên Controller gom hết theo thứ tự ưu tiên rồi đưa
     * xuống, service chọn nguồn ĐẦU TIÊN đọc được. Nhờ vậy một nguồn rác — ví dụ Swagger điền
     * sẵn chuỗi "string" vào body — không còn che mất cookie hợp lệ nằm ngay phía sau.
     */

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
}
