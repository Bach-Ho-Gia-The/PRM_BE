package com.structure.springboot.features.account.service;

import com.structure.springboot.features.account.dto.request.AccountRegistrationRequest;
import com.structure.springboot.features.account.dto.request.AccountRequest;
import com.structure.springboot.features.account.dto.request.AccountSelfUpdateRequest;
import com.structure.springboot.features.account.dto.request.AccountStatusUpdateRequest;
import com.structure.springboot.features.account.dto.request.AccountUpdateRequest;
import com.structure.springboot.features.account.dto.request.ChangePasswordRequest;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.account.dto.response.AccountResponse;

import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface AccountService {
    AccountResponse createAccount(AccountRequest request);

    /**
     * Người dùng tự đăng ký: tạo tài khoản vai trò USER kèm hồ sơ, trong cùng một transaction.
     *
     * <p>Tách khỏi {@link #createAccount} vì hai đường có luật khác nhau — đường này KHÔNG cho
     * chọn vai trò.</p>
     */
    AccountResponse register(AccountRegistrationRequest request);
    AccountResponse updateAccount(UUID id, AccountUpdateRequest request);

    /**
     * Admin bật/tắt một tài khoản.
     *
     * @param actorId id của chính admin đang thao tác, để chặn tự khoá mình
     */
    AccountResponse updateStatus(UUID id, UUID actorId, AccountStatusUpdateRequest request);

    /**
     * Người dùng tự sửa email của chính mình. Tách khỏi {@code updateAccount} vì bản
     * admin cho phép đổi cả {@code roleId}.
     */
    AccountResponse updateMyAccount(UUID accountId, AccountSelfUpdateRequest request);
    void deleteAccount(UUID id);

    /**
     * Người dùng tự đổi mật khẩu, bắt buộc nhập đúng mật khẩu hiện tại.
     * Đổi xong thu hồi toàn bộ phiên — kể cả phiên đang thao tác — nên client phải đăng nhập lại.
     */
    void changePassword(UUID accountId, ChangePasswordRequest request);
    AccountResponse getAccountById(UUID id);
    PageResponse<AccountResponse> getAllAccounts(Pageable pageable);
}
