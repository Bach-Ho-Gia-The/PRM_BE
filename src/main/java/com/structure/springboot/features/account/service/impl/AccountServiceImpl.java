package com.structure.springboot.features.account.service.impl;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.common.security.RefreshTokenRedis;
import com.structure.springboot.features.account.dto.request.AccountRequest;
import com.structure.springboot.features.account.dto.request.AccountSelfUpdateRequest;
import com.structure.springboot.features.account.dto.request.AccountUpdateRequest;
import com.structure.springboot.features.account.dto.request.ChangePasswordRequest;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.account.dto.response.AccountResponse;
import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.account.mapper.AccountMapper;
import com.structure.springboot.features.account.repository.AccountRepository;
import com.structure.springboot.features.account.service.AccountService;
import com.structure.springboot.features.profile.service.ProfileService;
import com.structure.springboot.features.role.entity.RoleEntity;
import com.structure.springboot.features.role.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRedis refreshTokenRedis;
    private final ProfileService profileService;

    @Override
    @Transactional
    public AccountResponse createAccount(AccountRequest request) {
        if (accountRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new ApiException(ErrorCode.CONFLICT, "Account username already exists: " + request.username());
        }
        if (accountRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ApiException(ErrorCode.CONFLICT, "Account email already exists: " + request.email());
        }

        RoleEntity role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Role not found with id: " + request.roleId()));

        AccountEntity entity = accountMapper.toEntity(request);
        entity.setRole(role);
        entity.setPassword(passwordEncoder.encode(request.password()));

        AccountEntity saved = accountRepository.save(entity);

        // Dựng sẵn hồ sơ rỗng ngay trong cùng transaction: GET /profiles/me nhờ vậy không bao giờ
        // trả 404, frontend khỏi phải phân biệt "chưa có hồ sơ" với "chưa điền gì".
        profileService.createForAccount(saved);

        return accountMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public AccountResponse updateMyAccount(UUID accountId, AccountSelfUpdateRequest request) {
        // Dồn về đúng một chỗ xử lý: truyền roleId = null nên nhánh đổi quyền trong updateAccount
        // không bao giờ chạy, mà phần kiểm trùng username/email và thu hồi phiên khi đổi email
        // vẫn được dùng lại nguyên vẹn.
        return updateAccount(accountId, new AccountUpdateRequest(request.username(), request.email(), null));
    }

    @Override
    @Transactional
    public AccountResponse updateAccount(UUID id, AccountUpdateRequest request) {
        AccountEntity entity = accountRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Account not found with id: " + id));

        // Giữ email CŨ lại: mốc logout được cắm theo email, mà access token đang lưu hành mang
        // email cũ trong claim "sub". Cắm theo email mới thì token cũ lọt lưới.
        String previousEmail = entity.getEmail();

        // PATCH: field null nghĩa là "không đổi" nên mọi kiểm tra đều phải bọc null trước,
        // nếu không sẽ đi hỏi DB bằng tham số null và báo trùng sai.
        if (request.username() != null && accountRepository.existsByUsernameIgnoreCaseAndIdNot(request.username(), id)) {
            throw new ApiException(ErrorCode.CONFLICT, "Account username already exists: " + request.username());
        }
        if (request.email() != null && accountRepository.existsByEmailIgnoreCaseAndIdNot(request.email(), id)) {
            throw new ApiException(ErrorCode.CONFLICT, "Account email already exists: " + request.email());
        }

        if (request.roleId() != null && !entity.getRole().getId().equals(request.roleId())) {
            RoleEntity newRole = roleRepository.findById(request.roleId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Role not found with id: " + request.roleId()));
            entity.setRole(newRole);
        }

        accountMapper.updateEntity(request, entity);

        // Đổi email ⇒ mốc logout cũ (cắm theo email) không còn khớp token đang lưu hành,
        // nên phải thu hồi hết phiên theo email CŨ, bằng không token cũ vẫn dùng được.
        if (request.email() != null && !request.email().equalsIgnoreCase(previousEmail)) {
            refreshTokenRedis.revokeEverySession(entity.getId().toString(), previousEmail);
        }

        return accountMapper.toResponse(accountRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteAccount(UUID id) {
        AccountEntity entity = accountRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Account not found with id: " + id));

        // Xoá tài khoản mà để token còn sống thì người đó vẫn gọi API được cho tới khi token hết hạn.
        refreshTokenRedis.revokeEverySession(entity.getId().toString(), entity.getEmail());

        // Hồ sơ trỏ về tài khoản bằng khoá ngoại NOT NULL; không dọn trước thì database chặn
        // lệnh xoá. Cố ý KHÔNG khai báo quan hệ ngược có cascade trên AccountEntity: Hibernate
        // không proxy được @OneToOne phía mappedBy, mỗi lần đọc tài khoản sẽ kéo thêm một
        // truy vấn hồ sơ — đắt cho GET /accounts dạng danh sách.
        profileService.deleteByAccountId(entity.getId());

        accountRepository.delete(entity);
    }

    @Override
    @Transactional
    public void changePassword(UUID accountId, ChangePasswordRequest request) {
        AccountEntity entity = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Account not found with id: " + accountId));

        // matches() tự so khớp theo thuật toán đã băm, KHÔNG được encode rồi đem so chuỗi:
        // BCrypt sinh salt ngẫu nhiên nên encode cùng một mật khẩu hai lần ra hai chuỗi khác nhau.
        if (!passwordEncoder.matches(request.currentPassword(), entity.getPassword())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Current password is incorrect");
        }

        if (passwordEncoder.matches(request.newPassword(), entity.getPassword())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "New password must be different from the current one");
        }

        entity.setPassword(passwordEncoder.encode(request.newPassword()));
        accountRepository.save(entity);

        // Đá sạch mọi thiết bị, kể cả thiết bị vừa đổi: mật khẩu đã lộ thì kẻ kia phải văng ra ngay,
        // người dùng thật chỉ việc đăng nhập lại bằng mật khẩu mới.
        refreshTokenRedis.revokeEverySession(entity.getId().toString(), entity.getEmail());
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(UUID id) {
        return accountRepository.findById(id)
                .map(accountMapper::toResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Account not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> getAllAccounts(Pageable pageable) {
        // map() của Page giữ nguyên thông tin phân trang, chỉ đổi kiểu phần tử —
        // không được .getContent().stream() vì làm vậy là mất totalElements/totalPages.
        return PageResponse.from(accountRepository.findAll(pageable).map(accountMapper::toResponse));
    }
}
