package com.structure.springboot.features.account.service;

import com.structure.springboot.features.account.dto.request.AccountRequest;
import com.structure.springboot.features.account.dto.request.AccountStatusUpdateRequest;
import com.structure.springboot.features.account.dto.request.AccountUpdateRequest;
import com.structure.springboot.features.account.dto.request.ChangePasswordRequest;
import com.structure.springboot.features.account.dto.request.EmailChangeConfirmRequest;
import com.structure.springboot.features.account.dto.request.EmailChangeRequest;
import com.structure.springboot.features.account.dto.response.EmailChangePendingResponse;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.account.dto.response.AccountResponse;

import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface AccountService {
    AccountResponse createAccount(AccountRequest request);

    /**
     * Tạo tài khoản cho một hồ sơ đăng ký <b>đã xác minh email xong</b>.
     *
     * <p>Thay cho hàm {@code register} cũ. Khác biệt sống còn: {@code passwordHash} truyền vào
     * đây <b>đã qua BCrypt</b> và KHÔNG được mã hoá lần nữa — nó được băm từ lúc hồ sơ bắt đầu
     * nằm chờ trong Redis, vì cất mật khẩu thô ở đó là không chấp nhận được.</p>
     *
     * <p>Vẫn kiểm trùng email lần nữa dù bước gửi mã đã kiểm rồi: hồ sơ nằm chờ tới 10 phút,
     * trong quãng đó admin hoàn toàn có thể đã tạo một tài khoản cùng email.</p>
     */
    AccountResponse createFromVerifiedRegistration(String email, String passwordHash,
                                                    String fullName, String phone);
    AccountResponse updateAccount(UUID id, UUID actorId, AccountUpdateRequest request);

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
    /**
     * Bước 1 đổi email: kiểm mật khẩu hiện tại rồi gửi mã xác minh tới ĐỊA CHỈ MỚI.
     *
     * <p>Chưa ghi gì vào database. Người dùng gõ nhầm địa chỉ thì chỉ đơn giản là không nhận
     * được mã, tài khoản vẫn nguyên và phiên đăng nhập vẫn còn.</p>
     */
    EmailChangePendingResponse requestEmailChange(UUID accountId, EmailChangeRequest request);

    /**
     * Bước 2 đổi email: nhập đúng mã thì email mới có hiệu lực.
     *
     * <p>Đơn hàng, thông báo, hội thoại, hồ sơ đều móc vào {@code account_id} nên đi theo tài
     * khoản mà không phải dời gì. Mọi phiên bị thu hồi, người dùng đăng nhập lại bằng email mới.</p>
     */
    AccountResponse confirmEmailChange(UUID accountId, EmailChangeConfirmRequest request);
    void deleteAccount(UUID id, UUID actorId);

    /**
     * Người dùng tự đổi mật khẩu, bắt buộc nhập đúng mật khẩu hiện tại.
     * Đổi xong thu hồi toàn bộ phiên — kể cả phiên đang thao tác — nên client phải đăng nhập lại.
     */
    void changePassword(UUID accountId, ChangePasswordRequest request);

    /**
     * Đặt lại mật khẩu sau khi đã xác minh OTP — KHÔNG hỏi mật khẩu cũ.
     *
     * <p>Tách khỏi {@link #changePassword} chứ không thêm cờ vào hàm đó: một hàm đổi mật khẩu mà
     * có đường tắt bỏ qua bước kiểm mật khẩu hiện tại là chỗ rất dễ bị gọi nhầm. Danh tính ở đây
     * đã được chứng minh bằng vé tạm của luồng OTP, nên hàm này nhận email chứ không nhận id.</p>
     */
    void resetPassword(String email, String rawPassword);

    /**
     * Tìm hoặc tạo tài khoản cho một người vừa đăng nhập Google.
     *
     * <p><b>Nhận tham số rời chứ không nhận một object của module auth:</b> chiều phụ thuộc hợp
     * lệ của dự án là auth → account. Để account import kiểu dữ liệu của auth là tạo vòng lặp
     * giữa hai module.</p>
     *
     * <p>Ba nhánh, xét theo đúng thứ tự: đã liên kết Google trước đó → trùng email với tài khoản
     * sẵn có (gộp) → hoàn toàn mới. Mọi dữ liệu truyền vào đây đều phải đã được bóc ra từ ID
     * token ĐÃ XÁC MINH, không bao giờ lấy thẳng từ body request.</p>
     */
    AccountResponse upsertGoogleAccount(String providerId, String email, String fullName, String avatarUrl);
    AccountResponse getAccountById(UUID id);
    PageResponse<AccountResponse> getAllAccounts(Pageable pageable);
}
