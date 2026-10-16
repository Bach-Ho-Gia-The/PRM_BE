package com.structure.springboot.features.auth.security;

import com.structure.springboot.features.auth.enums.OtpPurpose;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Cơ chế OTP dùng chung cho mọi luồng cần mã xác minh.
 *
 * <p>Tham số {@link OtpPurpose} đi thẳng vào tên khoá ({@code otp:reset:...} và
 * {@code otp:register:...}) nên một người vừa đăng ký vừa quên mật khẩu cũng không thể làm hỏng
 * mã của luồng kia. Viết thành một lớp nhận tham số thay vì nhân bản sang lớp thứ hai: hai bản
 * sao của cùng một logic đếm là kiểu nợ mà sửa một bên quên bên kia.</p>
 *
 * <p><b>Bốn khoá cho mỗi (purpose, subject):</b></p>
 * <ul>
 *   <li>{@code otp:{purpose}:{email}} — mã 6 số.</li>
 *   <li>{@code otp_attempt:{purpose}:{email}} — số lần nhập sai.</li>
 *   <li>{@code otp_cooldown:{purpose}:{email}} — khoảng chờ giữa hai lần bấm "gửi lại".</li>
 *   <li>{@code otp_send:{purpose}:{email}} — trần số lần gửi trong một giờ.</li>
 * </ul>
 *
 * <p><b>Vì sao cooldown phải là khoá RIÊNG, không suy ra từ hạn của mã:</b> hạn của mã đăng ký là
 * 10 phút, nếu lấy nó làm khoảng chờ thì khách không nhận được mail phải ngồi đợi 10 phút mới bấm
 * lại được. Hai con số này phục vụ hai mục đích khác nhau nên phải tách.</p>
 *
 * <p>Dùng {@code StringRedisTemplate} trực tiếp vì cần {@code INCR} nguyên tử — {@code RedisService}
 * chỉ có get/set rời nhau nên hai request song song sẽ đếm mất một lượt.</p>
 */
@Service
@RequiredArgsConstructor
public class OtpRedis {

    private static final String CODE_PREFIX = "otp:";
    private static final String ATTEMPT_PREFIX = "otp_attempt:";
    private static final String COOLDOWN_PREFIX = "otp_cooldown:";
    private static final String SEND_PREFIX = "otp_send:";
    private static final String TICKET_PREFIX = "otp_ticket:";

    private final StringRedisTemplate redis;

    /** Số giây còn phải chờ mới được gửi lại; 0 nghĩa là gửi được ngay. */
    public long cooldownRemaining(OtpPurpose purpose, String subject) {
        Long ttl = redis.getExpire(key(COOLDOWN_PREFIX, purpose, subject));
        return ttl != null && ttl > 0 ? ttl : 0L;
    }

    public void startCooldown(OtpPurpose purpose, String subject, Duration cooldown) {
        redis.opsForValue().set(key(COOLDOWN_PREFIX, purpose, subject), "1", cooldown);
    }

    /** Tăng bộ đếm gửi và trả về số lần đã gửi trong cửa sổ một giờ. */
    public long incrementSendCount(OtpPurpose purpose, String subject) {
        String key = key(SEND_PREFIX, purpose, subject);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            // TTL chỉ đặt ở lần đầu -> cửa sổ cố định, không bị lần gửi sau gia hạn vô tận.
            redis.expire(key, Duration.ofHours(1));
        }
        return count != null ? count : 1L;
    }

    /** Ghi mã mới và xoá bộ đếm nhập sai của mã cũ, nếu không mã mới thừa hưởng án phạt cũ. */
    public void storeCode(OtpPurpose purpose, String subject, String code, Duration ttl) {
        redis.opsForValue().set(key(CODE_PREFIX, purpose, subject), code, ttl);
        redis.delete(key(ATTEMPT_PREFIX, purpose, subject));
    }

    public Optional<String> findCode(OtpPurpose purpose, String subject) {
        return Optional.ofNullable(redis.opsForValue().get(key(CODE_PREFIX, purpose, subject)));
    }

    public long incrementAttempts(OtpPurpose purpose, String subject, Duration ttl) {
        String key = key(ATTEMPT_PREFIX, purpose, subject);
        Long attempts = redis.opsForValue().increment(key);
        if (attempts != null && attempts == 1L) {
            redis.expire(key, ttl);
        }
        return attempts != null ? attempts : 1L;
    }

    /** Xoá mã và bộ đếm sai. KHÔNG xoá cooldown và bộ đếm gửi — hai cái đó là hàng rào chống lạm dụng. */
    public void clearCode(OtpPurpose purpose, String subject) {
        redis.delete(key(CODE_PREFIX, purpose, subject));
        redis.delete(key(ATTEMPT_PREFIX, purpose, subject));
    }

    /**
     * Vé tạm của luồng quên mật khẩu: nhập đúng mã xong thì mã chết ngay, vé sống thêm một lúc
     * cho đủ thời gian gõ mật khẩu mới. Luồng đăng ký không dùng tới vì nó tạo tài khoản luôn.
     */
    public String createTicket(String email, Duration ttl) {
        String ticket = UUID.randomUUID().toString();
        redis.opsForValue().set(TICKET_PREFIX + ticket, email, ttl);
        return ticket;
    }

    /** Đọc rồi xoá — vé dùng được đúng một lần, bấm hai lần không đổi mật khẩu hai lần. */
    public Optional<String> consumeTicket(String ticket) {
        return Optional.ofNullable(redis.opsForValue().getAndDelete(TICKET_PREFIX + ticket));
    }

    private String key(String prefix, OtpPurpose purpose, String subject) {
        return prefix + purpose.name().toLowerCase(Locale.ROOT) + ":" + subject;
    }
}
