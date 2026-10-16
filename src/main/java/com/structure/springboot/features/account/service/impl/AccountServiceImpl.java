package com.structure.springboot.features.account.service.impl;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.common.security.RefreshTokenRedis;
import com.structure.springboot.features.account.dto.request.AccountRequest;
import com.structure.springboot.features.account.dto.request.AccountSelfUpdateRequest;
import com.structure.springboot.features.account.dto.request.AccountStatusUpdateRequest;
import com.structure.springboot.features.account.dto.request.AccountUpdateRequest;
import com.structure.springboot.features.account.dto.request.ChangePasswordRequest;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.account.dto.response.AccountResponse;
import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.account.enums.AccountStatus;
import com.structure.springboot.features.account.security.PasswordChangeGuard;
import com.structure.springboot.infrastructure.external.brevo.BrevoMailClient;
import com.structure.springboot.features.account.enums.AuthProvider;
import com.structure.springboot.features.account.mapper.AccountMapper;
import com.structure.springboot.features.account.repository.AccountRepository;
import com.structure.springboot.features.account.service.AccountService;
import com.structure.springboot.features.order.service.OrderService;
import com.structure.springboot.features.profile.service.ProfileService;
import com.structure.springboot.features.role.entity.RoleEntity;
import com.structure.springboot.features.role.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
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
    private final OrderService orderService;
    private final PasswordChangeGuard passwordChangeGuard;
    private final BrevoMailClient brevoMailClient;

    @Override
    @Transactional
    public AccountResponse createAccount(AccountRequest request) {
        requireUniqueEmail(request.email());

        RoleEntity role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Role not found with id: " + request.roleId()));

        AccountEntity entity = accountMapper.toEntity(request);
        entity.setRole(role);
        entity.setPassword(passwordEncoder.encode(request.password()));

        AccountEntity saved = accountRepository.save(entity);

        // Dựng sẵn hồ sơ rỗng ngay trong cùng transaction: GET /profiles/me nhờ vậy không bao giờ
        // trả 404, frontend khỏi phải phân biệt "chưa có hồ sơ" với "chưa điền gì".
        profileService.createForAccount(saved, null, null, null);

        return accountMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public AccountResponse updateMyAccount(UUID accountId, AccountSelfUpdateRequest request) {
        AccountEntity entity = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Account not found with id: " + accountId));

        requireCurrentPassword(entity, request.currentPassword());

        // Đi thẳng vào phần thân chung, KHÔNG qua updateAccount: ba chốt "tự sửa" của đường
        // admin ở đó sẽ chặn nhầm chính đường này. roleId truyền null nên quyền không thể đổi.
        return applyAccountUpdate(entity, new AccountUpdateRequest(request.email(), null));
    }

    /**
     * Bắt gõ lại mật khẩu hiện tại trước khi cho đổi phần định danh. Dùng chung cho đổi email và
     * đổi mật khẩu để hai đường không bao giờ lệch nhau về câu lỗi lẫn cách xử lý tài khoản
     * Google chưa đặt mật khẩu.
     */
    private void requireCurrentPassword(AccountEntity entity, String currentPassword) {
        // Tài khoản Google chưa từng đặt mật khẩu: không có gì để so. Chặn tường minh thay vì để
        // passwordEncoder.matches(x, null) tự trả false rồi báo "mật khẩu hiện tại sai" — người
        // dùng sẽ ngồi thử lại mãi mà không hiểu vì sao.
        if (entity.getPassword() == null) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "This account has no password yet. Use the forgot-password flow to set one.");
        }

        // matches() tự so khớp theo thuật toán đã băm, KHÔNG được encode rồi đem so chuỗi:
        // BCrypt sinh salt ngẫu nhiên nên encode cùng một mật khẩu hai lần ra hai chuỗi khác nhau.
        if (!passwordEncoder.matches(currentPassword, entity.getPassword())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Current password is incorrect");
        }
    }

    /**
     * Tự đăng ký. Vai trò do backend ấn định là USER — không lấy từ request, nếu không ai cũng
     * tự phong mình làm admin ngay ở bước đăng ký.
     *
     * <p>Một transaction cho cả tài khoản lẫn hồ sơ: hỏng giữa chừng thì không sinh ra tài khoản
     * cụt hồ sơ. Việc phát token nằm ngoài hàm này, bên {@code AuthService} — ghi Redis mà kẹp
     * trong transaction database là giữ connection suốt thời gian đi mạng.</p>
     */
    @Override
    @Transactional
    public AccountResponse createFromVerifiedRegistration(String email, String passwordHash,
                                                           String fullName, String phone) {
        requireUniqueEmail(email);

        RoleEntity role = roleRepository.findByRoleNameIgnoreCase("USER")
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Default role USER is not configured"));

        AccountEntity saved = accountRepository.save(AccountEntity.builder()
                .email(email)
                // KHÔNG encode lại: chuỗi này đã là kết quả BCrypt từ bước gửi mã. Băm hai lần
                // thì mật khẩu người dùng gõ sẽ không bao giờ khớp.
                .password(passwordHash)
                .role(role)
                .build());

        profileService.createForAccount(saved, fullName, phone, null);

        return accountMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public AccountResponse upsertGoogleAccount(String providerId, String email,
                                               String fullName, String avatarUrl) {
        // 1. Đã liên kết từ lần trước. Tra theo providerId chứ KHÔNG theo email: Google cho đổi
        //    email, còn sub thì vĩnh viễn không đổi — tra theo email là có ngày trả nhầm người.
        Optional<AccountEntity> linked = accountRepository.findByProviderId(providerId);
        if (linked.isPresent()) {
            return accountMapper.toResponse(linked.get());
        }

        // 2. Email này đã có tài khoản đăng ký thường -> gộp vào, không đẻ tài khoản thứ hai.
        //    CHỈ điền providerId, giữ nguyên provider = LOCAL vì mật khẩu cũ của họ vẫn dùng được.
        //    An toàn vì GoogleTokenVerifier đã chặn token có email chưa xác minh từ vòng ngoài.
        Optional<AccountEntity> sameEmail = accountRepository.findByEmailIgnoreCase(email);
        if (sameEmail.isPresent()) {
            AccountEntity account = sameEmail.get();
            account.setProviderId(providerId);
            return accountMapper.toResponse(accountRepository.save(account));
        }

        // 3. Người mới hoàn toàn: tạo tài khoản KHÔNG mật khẩu, vai trò do backend ấn định.
        RoleEntity role = roleRepository.findByRoleNameIgnoreCase("USER")
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Default role USER is not configured"));

        AccountEntity saved = accountRepository.save(AccountEntity.builder()
                .email(email)
                .provider(AuthProvider.GOOGLE)
                .providerId(providerId)
                .role(role)
                .build());

        // phone để null: Google không cung cấp số điện thoại. profileCompleted sẽ là false nên
        // frontend tự đẩy màn hình hoàn tất hồ sơ — đúng kiểu nudge bro đã chốt.
        profileService.createForAccount(saved, fullName, null, avatarUrl);

        return accountMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public AccountResponse updateAccount(UUID id, UUID actorId, AccountUpdateRequest request) {
        AccountEntity entity = accountRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Account not found with id: " + id));

        // Ba chốt dưới đây chỉ thuộc về đường ADMIN, nên nằm ở đây chứ không nằm trong phần dùng
        // chung — người dùng tự sửa chính mình đi đường /accounts/me và phải được phép đổi email.
        if (id.equals(actorId)) {
            // Lối vòng cần bịt: /accounts/me bắt gõ lại mật khẩu mới cho đổi email, nhưng admin
            // có thể gọi /accounts/{id} với id của chính mình và đi cửa sau.
            if (isEmailChanging(request.email(), entity.getEmail())) {
                throw new ApiException(ErrorCode.CONFLICT,
                        "Use PATCH /accounts/me to change your own email");
            }

            // Admin tự hạ vai trò mình xuống USER là tự tước quyền của chính mình, và nếu đó là
            // admin duy nhất thì không còn ai nâng lại được ngoài việc sửa tay trong database —
            // cùng một họ với việc tự khoá tài khoản ở updateStatus.
            if (request.roleId() != null && !entity.getRole().getId().equals(request.roleId())) {
                throw new ApiException(ErrorCode.CONFLICT, "You cannot change the role of your own account");
            }
        }

        return applyAccountUpdate(entity, request);
    }

    /**
     * Phần thân chung của hai đường sửa tài khoản: admin sửa người khác và người dùng tự sửa
     * mình. Tách ra vì hai đường chỉ khác nhau ở <b>chốt chặn đầu vào</b> — gộp cả chốt vào một
     * hàm thì chốt của bên này sẽ chặn nhầm bên kia.
     */
    private AccountResponse applyAccountUpdate(AccountEntity entity, AccountUpdateRequest request) {
        // Giữ email CŨ lại: mốc logout được cắm theo email, mà access token đang lưu hành mang
        // email cũ trong claim "sub". Cắm theo email mới thì token cũ lọt lưới.
        String previousEmail = entity.getEmail();

        // PATCH: field null nghĩa là "không đổi" nên mọi kiểm tra đều phải bọc null trước,
        // nếu không sẽ đi hỏi DB bằng tham số null và báo trùng sai.
        if (request.email() != null
                && accountRepository.existsByEmailIgnoreCaseAndIdNot(request.email(), entity.getId())) {
            throw new ApiException(ErrorCode.CONFLICT, "Account email already exists: " + request.email());
        }

        boolean roleChanged = request.roleId() != null && !entity.getRole().getId().equals(request.roleId());
        if (roleChanged) {
            RoleEntity newRole = roleRepository.findById(request.roleId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Role not found with id: " + request.roleId()));
            entity.setRole(newRole);
        }

        accountMapper.updateEntity(request, entity);

        // Đổi email ⇒ mốc logout cũ (cắm theo email) không còn khớp token đang lưu hành, nên
        // phải thu hồi hết phiên theo email CŨ, bằng không token cũ vẫn dùng được.
        // Hạ quyền mà không thu hồi phiên cũng vô nghĩa trong 15 phút kế tiếp: vai trò nằm trong
        // claim "scope" của access token đang lưu hành, filter không tra lại database mỗi request.
        if (roleChanged || isEmailChanging(request.email(), previousEmail)) {
            refreshTokenRedis.revokeEverySession(entity.getId().toString(), previousEmail);
        }

        return accountMapper.toResponse(accountRepository.save(entity));
    }

    /** PATCH nên null = "không đổi"; chỉ coi là đổi khi có giá trị và khác giá trị cũ. */
    private boolean isEmailChanging(String requestedEmail, String currentEmail) {
        return requestedEmail != null && !requestedEmail.equalsIgnoreCase(currentEmail);
    }

    /**
     * Khoá / mở khoá / vô hiệu hoá một tài khoản.
     *
     * <p>Đổi cờ trong database thôi là chưa đủ: access token sống độc lập 15 phút và không tra
     * database mỗi request, nên người vừa bị khoá vẫn gọi API ngon lành tới khi token hết hạn.
     * {@code revokeEverySession} cắm mốc {@code logout_time} để {@code JwtAuthenticationFilter}
     * loại ngay những token phát hành trước đó — người bị khoá rụng ở request kế tiếp.</p>
     *
     * <p>Chỉ thu hồi phiên khi chuyển sang trạng thái chặn. Mở khoá thì không cần đá ai cả, mà
     * lúc đó cũng chẳng còn phiên nào để đá.</p>
     */
    @Override
    @Transactional
    public AccountResponse updateStatus(UUID id, UUID actorId, AccountStatusUpdateRequest request) {
        AccountEntity entity = accountRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Account not found with id: " + id));

        // Admin tự khoá mình là tự nhốt mình ra ngoài, và nếu đó là admin duy nhất thì không còn
        // ai mở lại được nữa ngoài việc sửa tay trong database.
        if (id.equals(actorId)) {
            throw new ApiException(ErrorCode.CONFLICT, "You cannot change the status of your own account");
        }

        entity.setStatus(request.status());
        // Mốc hết khoá chỉ có nghĩa với LOCKED. Giữ lại ở trạng thái khác sẽ thành dữ liệu rác,
        // và nguy hiểm hơn: khoá lại lần sau có thể tự mở vì dính mốc cũ còn sót.
        entity.setLockedUntil(request.status() == AccountStatus.LOCKED ? request.lockedUntil() : null);

        if (request.status() != AccountStatus.ACTIVE) {
            refreshTokenRedis.revokeEverySession(entity.getId().toString(), entity.getEmail());
        }

        return accountMapper.toResponse(accountRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteAccount(UUID id, UUID actorId) {
        AccountEntity entity = accountRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Account not found with id: " + id));

        // Cùng họ với hai chốt trên: admin xoá chính mình là xoá vĩnh viễn, không có đường lùi
        // nào ngoài sửa tay trong database.
        if (id.equals(actorId)) {
            throw new ApiException(ErrorCode.CONFLICT, "You cannot delete your own account");
        }

        // Đơn hàng trỏ về tài khoản bằng khoá ngoại, database sẽ chặn lệnh xoá. Để nó chặn thì
        // client nhận đúng một câu "Data integrity violation" chung chung, không biết vướng ở đâu.
        // Hỏi trước để trả về lý do thật. Hỏi qua OrderService chứ không tiêm OrderRepository:
        // dữ liệu module nào thì module đó giữ.
        if (orderService.hasOrders(id)) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Cannot delete account with existing orders: " + id);
        }

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

        // Kiểm khoảng chờ TRƯỚC khi so BCrypt: không thì chính chốt chống spam lại thành chỗ
        // đốt CPU, mỗi lần bấm vẫn tốn một phép băm rồi mới bị từ chối.
        passwordChangeGuard.assertNotCoolingDown(accountId);

        requireCurrentPassword(entity, request.currentPassword());

        if (passwordEncoder.matches(request.newPassword(), entity.getPassword())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "New password must be different from the current one");
        }

        entity.setPassword(passwordEncoder.encode(request.newPassword()));
        accountRepository.save(entity);

        // Đá sạch mọi thiết bị, kể cả thiết bị vừa đổi: mật khẩu đã lộ thì kẻ kia phải văng ra ngay,
        // người dùng thật chỉ việc đăng nhập lại bằng mật khẩu mới.
        refreshTokenRedis.revokeEverySession(entity.getId().toString(), entity.getEmail());

        passwordChangeGuard.startCooldown(accountId);

        // Đặt ở dòng cuối và chạy bất đồng bộ: nếu không phải chính chủ đổi thì email này là tín
        // hiệu duy nhất để họ kịp giành lại tài khoản. Brevo rớt cũng không được phép làm hỏng
        // thao tác đã thành công, nên BrevoMailClient tự nuốt lỗi ở phía bên kia.
        brevoMailClient.sendPasswordChangedEmail(entity.getEmail());
    }

    @Override
    @Transactional
    public void resetPassword(String email, String rawPassword) {
        AccountEntity entity = accountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Account not found with email: " + email));

        // Vé đổi mật khẩu sống 300 giây, đủ dài để admin kịp khoá tài khoản trong lúc đó. Không
        // chốt lại ở đây thì người vừa bị khoá vẫn đổi được mật khẩu bằng vé xin từ trước.
        // Chỉ kiểm status: lockedUntil là mốc tự mở của LOCKED, mà LOCKED đã bị chặn sẵn rồi.
        if (entity.getStatus() != null && entity.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(ErrorCode.FORBIDDEN, "This account is not allowed to reset its password");
        }

        entity.setPassword(passwordEncoder.encode(rawPassword));
        accountRepository.save(entity);

        // Cùng lý do với changePassword: mật khẩu đã nằm trong diện nghi ngờ thì mọi phiên đang
        // mở phải chết, kể cả phiên của kẻ đã chiếm được tài khoản trước đó.
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

    /**
     * Chặn trùng email khi TẠO MỚI.
     *
     * <p>Dùng chung cho cả đường admin tạo hộ lẫn đường người dùng tự đăng ký — hai đường khác
     * nhau về vai trò nhưng giống hệt nhau về luật định danh. Nhánh cập nhật không dùng được hàm
     * này vì phải loại chính bản ghi đang sửa ra khỏi phép so.</p>
     */
    private void requireUniqueEmail(String email) {
        if (accountRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(ErrorCode.CONFLICT, "Account email already exists: " + email);
        }
    }
}
