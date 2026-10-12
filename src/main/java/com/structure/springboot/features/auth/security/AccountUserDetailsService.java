package com.structure.springboot.features.auth.security;

import com.structure.springboot.features.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cổng nạp dữ liệu người dùng cho Spring Security.
 *
 * <p><b>Cách nó được đấu nối:</b> chỉ cần tồn tại bean {@code UserDetailsService} + bean
 * {@code PasswordEncoder} (đã có trong {@code SecurityConfig}), Spring Security sẽ tự dựng
 * một {@code DaoAuthenticationProvider} và nạp vào {@code AuthenticationManager} toàn cục.
 * Provider đó lo giúp: kiểm tra 4 cờ trạng thái tài khoản, so khớp BCrypt, chống timing-attack
 * khi username không tồn tại, và gộp mọi lỗi thành {@code BadCredentialsException}.</p>
 *
 * <p>Không dùng {@code @Query}: {@code findByEmailIgnoreCase} là derived query có sẵn
 * {@code @EntityGraph("role")} nên lấy account + role trong 1 câu lệnh, không dính N+1.</p>
 */
@Service
@RequiredArgsConstructor
public class AccountUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    /**
     * Tham số tên là {@code username} vì đó là chữ ký bắt buộc của {@code UserDetailsService},
     * nhưng giá trị truyền vào là <b>email</b> — Spring Security dùng chữ "username" theo nghĩa
     * "chuỗi định danh người dùng", không bắt buộc phải là cột username.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return accountRepository.findByEmailIgnoreCase(username)
                .map(AccountPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Account not found with email: " + username));
    }
}
