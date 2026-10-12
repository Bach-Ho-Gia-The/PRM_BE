package com.structure.springboot.features.profile.service;

import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.profile.dto.request.ProfileUpdateRequest;
import com.structure.springboot.features.profile.dto.response.ProfileResponse;

import java.util.UUID;

public interface ProfileService {

    /** Hồ sơ của chính mình. Chưa có thì dựng một bản rỗng rồi trả về, không bao giờ 404. */
    ProfileResponse getMyProfile(UUID accountId);

    /** Cập nhật hồ sơ của chính mình theo kiểu PATCH. */
    ProfileResponse updateMyProfile(UUID accountId, ProfileUpdateRequest request);

    /**
     * Tạo hồ sơ rỗng đi kèm tài khoản vừa đăng ký.
     *
     * <p>Nhận thẳng {@link AccountEntity} chứ không nhận id: hàm này được gọi ngay trong
     * transaction tạo tài khoản, lúc đó entity đang nằm sẵn trong persistence context — nhận id
     * rồi đi tra lại database là thừa một truy vấn.</p>
     */
    void createForAccount(AccountEntity account);

    /** Xoá hồ sơ khi tài khoản bị xoá. */
    void deleteByAccountId(UUID accountId);
}
