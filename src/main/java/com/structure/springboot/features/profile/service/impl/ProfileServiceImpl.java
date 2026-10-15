package com.structure.springboot.features.profile.service.impl;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.account.repository.AccountRepository;
import com.structure.springboot.features.profile.dto.request.ProfileUpdateRequest;
import com.structure.springboot.features.profile.dto.response.ProfileResponse;
import com.structure.springboot.features.profile.entity.ProfileEntity;
import com.structure.springboot.features.profile.mapper.ProfileMapper;
import com.structure.springboot.features.profile.repository.ProfileRepository;
import com.structure.springboot.features.profile.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final ProfileRepository profileRepository;
    private final AccountRepository accountRepository;
    private final ProfileMapper profileMapper;

    /**
     * {@code @Transactional} bình thường chứ không {@code readOnly}: những tài khoản có từ trước
     * khi bảng {@code profiles} ra đời chưa có hồ sơ, lần đọc đầu tiên phải ghi được.
     */
    @Override
    @Transactional
    public ProfileResponse getMyProfile(UUID accountId) {
        return profileMapper.toResponse(loadOrCreate(accountId));
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileResponse getByAccountId(UUID accountId) {
        return profileRepository.findByAccountId(accountId)
                .map(profileMapper::toResponse)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.NOT_FOUND, "Profile not found for account: " + accountId));
    }

    @Override
    @Transactional
    public ProfileResponse updateMyProfile(UUID accountId, ProfileUpdateRequest request) {
        ProfileEntity entity = loadOrCreate(accountId);
        profileMapper.updateEntity(request, entity);

        return profileMapper.toResponse(profileRepository.save(entity));
    }

    @Override
    @Transactional
    public void createForAccount(AccountEntity account, String fullName, String phone) {
        profileRepository.save(ProfileEntity.builder()
                .account(account)
                .fullName(fullName)
                .phone(phone)
                .build());
    }

    @Override
    @Transactional
    public void deleteByAccountId(UUID accountId) {
        profileRepository.deleteByAccountId(accountId);
    }

    /**
     * Lấy hồ sơ, chưa có thì dựng mới.
     *
     * <p>Hồ sơ được tạo sẵn lúc đăng ký nên nhánh dựng mới hiếm khi chạy — nó là lưới hứng cho
     * các tài khoản cũ (dữ liệu seed, tài khoản tạo trước Đợt 3). Nhờ nó mà frontend không bao
     * giờ phải xử lý trường hợp "người dùng đăng nhập hợp lệ nhưng không có hồ sơ".</p>
     */
    private ProfileEntity loadOrCreate(UUID accountId) {
        return profileRepository.findByAccountId(accountId)
                .orElseGet(() -> {
                    AccountEntity account = accountRepository.findById(accountId)
                            .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Account no longer exists"));

                    return profileRepository.save(ProfileEntity.builder().account(account).build());
                });
    }
}
