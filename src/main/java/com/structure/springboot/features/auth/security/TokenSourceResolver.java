package com.structure.springboot.features.auth.security;

import com.structure.springboot.common.security.AuthCookieProperties;
import com.structure.springboot.features.auth.dto.request.TokenRequest;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Gom <b>mọi</b> chỗ client có thể để token trong một request, theo thứ tự ưu tiên:
 * body (mobile) → cookie refresh → cookie access → header Bearer.
 *
 * <p>Trả về cả danh sách chứ không chọn sẵn một nguồn. Bản cũ thấy body "có chữ" là lấy luôn,
 * nên Swagger điền sẵn chuỗi {@code "string"} đã đủ che mất cookie hợp lệ nằm ngay phía sau —
 * bấm logout xong mà Redis còn nguyên. Giờ service tự thử lần lượt tới khi đọc được.</p>
 *
 * <p>Nhận cả access token vì nó mang claim {@code sid}, đủ để xác định phiên cần thu hồi.</p>
 *
 * <p>Tách khỏi {@code AuthController} để controller quay về đúng vai trò điều phối: ba endpoint
 * refresh/logout/logout-all đều cần đúng phép gom này, để trong controller thì vừa phình vừa
 * không ai ngoài nó dùng lại được.</p>
 */
@Component
@RequiredArgsConstructor
public class TokenSourceResolver {

    private final AuthCookieProperties cookieProperties;

    public List<String> collect(TokenRequest requestDTO, HttpServletRequest request) {
        List<String> candidates = new ArrayList<>(4);

        if (requestDTO != null && StringUtils.hasText(requestDTO.token())) {
            candidates.add(requestDTO.token());
        }
        addIfPresent(candidates, readCookie(request, cookieProperties.refreshTokenName()));
        addIfPresent(candidates, readCookie(request, cookieProperties.accessTokenName()));

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            candidates.add(authHeader.substring(7));
        }
        return candidates;
    }

    private void addIfPresent(List<String> candidates, String token) {
        if (token != null) {
            candidates.add(token);
        }
    }

    private String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
