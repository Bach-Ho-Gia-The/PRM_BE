package com.structure.springboot.features.auth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Kho tạm cho hồ sơ đăng ký chưa xác minh.
 *
 * <p><b>Vì sao không ghi thẳng vào database với cờ {@code email_verified = false}:</b> làm vậy
 * thì phải thêm cột, phải viết job quét xoá bản ghi quá hạn, và tệ nhất là địa chỉ email bị bản
 * ghi chưa xác minh <b>giữ chỗ</b> — người thật đi đăng ký sẽ ăn 409 oan. Để ở Redis thì TTL tự
 * dọn, database chỉ chứa tài khoản đã xác minh, và "quá hạn thì coi như chưa từng đăng ký" trở
 * thành đúng nghĩa đen.</p>
 *
 * <p><b>Khoá là mã phiên đăng ký, KHÔNG phải email.</b> Khoá theo email thì mỗi địa chỉ chỉ có
 * một ô: kẻ xấu đăng ký đè lên hồ sơ của người thật, rồi người thật — vốn là chủ hộp thư — nhập
 * mã mới nhất và vô tình tạo ra tài khoản mang mật khẩu của kẻ kia. Mỗi lần bấm đăng ký sinh một
 * mã phiên riêng nên hai hồ sơ không bao giờ đụng nhau, và chỉ người đọc được hộp thư mới hoàn
 * tất được hồ sơ của chính mình.</p>
 *
 * <p>Lưu dạng Hash thay vì một chuỗi JSON để khỏi kéo bộ tuần tự hoá vào đây; bốn trường đều là
 * chuỗi phẳng nên Hash là vừa đủ.</p>
 */
@Service
@RequiredArgsConstructor
public class PendingRegistrationRedis {

    private static final String PREFIX = "reg:";

    private final StringRedisTemplate redis;

    public void store(String registrationId, PendingRegistration pending, Duration ttl) {
        String key = PREFIX + registrationId;
        redis.<String, String>opsForHash().putAll(key, Map.of(
                "email", pending.email(),
                "passwordHash", pending.passwordHash(),
                "fullName", pending.fullName(),
                "phone", pending.phone()
        ));
        // putAll không nhận TTL nên phải đặt hạn ở lệnh riêng; thiếu dòng này là hồ sơ nằm lại
        // Redis vĩnh viễn.
        redis.expire(key, ttl);
    }

    public Optional<PendingRegistration> find(String registrationId) {
        Map<String, String> entries = redis.<String, String>opsForHash().entries(PREFIX + registrationId);
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PendingRegistration(
                entries.get("email"),
                entries.get("passwordHash"),
                entries.get("fullName"),
                entries.get("phone")
        ));
    }

    public void clear(String registrationId) {
        redis.delete(PREFIX + registrationId);
    }
}
