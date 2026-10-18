package com.structure.springboot.features.account.service;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.common.security.OtpProperties;
import com.structure.springboot.common.security.RefreshTokenRedis;
import com.structure.springboot.features.account.dto.request.EmailChangeConfirmRequest;
import com.structure.springboot.features.account.dto.request.EmailChangeRequest;
import com.structure.springboot.features.account.dto.response.EmailChangePendingResponse;
import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.account.mapper.AccountMapper;
import com.structure.springboot.features.account.mapper.AccountMapperImpl;
import com.structure.springboot.features.account.repository.AccountRepository;
import com.structure.springboot.features.account.security.EmailChangeOtp;
import com.structure.springboot.features.account.security.PasswordChangeGuard;
import com.structure.springboot.features.account.service.impl.AccountServiceImpl;
import com.structure.springboot.features.order.service.OrderService;
import com.structure.springboot.features.profile.service.ProfileService;
import com.structure.springboot.features.role.entity.RoleEntity;
import com.structure.springboot.features.role.repository.RoleRepository;
import com.structure.springboot.infrastructure.external.brevo.BrevoMailClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Luong doi email hai buoc.
 *
 * <p>Nhan test de ASCII thuan vi output surefire ma hoa hong tieng Viet co dau.</p>
 */
@ExtendWith(MockitoExtension.class)
class EmailChangeServiceTest {

    @Mock private AccountRepository accounts;
    @Mock private RoleRepository roles;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenRedis refreshTokenRedis;
    @Mock private ProfileService profileService;
    @Mock private OrderService orderService;
    @Mock private PasswordChangeGuard passwordChangeGuard;
    @Mock private BrevoMailClient mail;
    @Mock private EmailChangeOtp emailChangeOtp;

    private AccountServiceImpl service;
    private AccountEntity account;

    private static final String OLD_EMAIL = "hello@gmail.com";
    private static final String NEW_EMAIL = "hi@gmail.com";

    @BeforeEach
    void setUp() {
        AccountMapper mapper = new AccountMapperImpl();
        OtpProperties otpProperties = new OtpProperties(null, null, null, null, null, null);

        service = new AccountServiceImpl(accounts, roles, mapper, passwordEncoder, refreshTokenRedis,
                profileService, orderService, passwordChangeGuard, mail, emailChangeOtp, otpProperties);

        RoleEntity role = RoleEntity.builder().roleName("USER").build();
        role.setId(UUID.randomUUID());

        account = AccountEntity.builder().email(OLD_EMAIL).password("$2a$hashed").role(role).build();
        account.setId(UUID.randomUUID());
    }

    private void stubAccountFound() {
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
    }

    private EmailChangeRequest requestTo(String newEmail) {
        return new EmailChangeRequest("Correct@123", newEmail);
    }

    // ─────────────── BUOC 1: xin ma ───────────────

    @Test
    void requestSendsTheCodeToTheNewAddressNotTheCurrentOne() {
        stubAccountFound();
        when(passwordEncoder.matches("Correct@123", "$2a$hashed")).thenReturn(true);
        when(accounts.existsByEmailIgnoreCaseAndIdNot(NEW_EMAIL, account.getId())).thenReturn(false);
        when(emailChangeOtp.issueCode(eq(account.getId()), any(Duration.class))).thenReturn("123456");

        EmailChangePendingResponse response = service.requestEmailChange(account.getId(), requestTo(NEW_EMAIL));

        // Diem mau chot cua ca bai: ma phai bay toi dia chi MOI.
        verify(mail).sendEmailChangeOtpEmail(eq(NEW_EMAIL), eq("123456"), eq(600));
        verify(emailChangeOtp).storePending(eq(account.getId()), eq(NEW_EMAIL), any(Duration.class));
        assertEquals(NEW_EMAIL, response.newEmail());
        assertEquals(600, response.expiresIn());
        assertEquals(20, response.resendAfter());
    }

    /** Buoc 1 TUYET DOI khong duoc cham vao database — go nham dia chi thi tai khoan van nguyen. */
    @Test
    void requestNeverTouchesTheAccountRow() {
        stubAccountFound();
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(accounts.existsByEmailIgnoreCaseAndIdNot(anyString(), any())).thenReturn(false);
        when(emailChangeOtp.issueCode(any(), any())).thenReturn("123456");

        service.requestEmailChange(account.getId(), requestTo("sai-chinh-ta@gmial.com"));

        verify(accounts, never()).save(any());
        verify(refreshTokenRedis, never()).revokeEverySession(anyString(), anyString());
        assertEquals(OLD_EMAIL, account.getEmail());
    }

    @Test
    void requestRejectsAWrongCurrentPassword() {
        stubAccountFound();
        when(passwordEncoder.matches("Correct@123", "$2a$hashed")).thenReturn(false);

        assertEquals(ErrorCode.UNAUTHORIZED, assertThrows(ApiException.class,
                () -> service.requestEmailChange(account.getId(), requestTo(NEW_EMAIL))).getErrorCode());
        verify(mail, never()).sendEmailChangeOtpEmail(anyString(), anyString(), anyInt());
    }

    @Test
    void requestRejectsAnAddressSomeoneElseAlreadyUses() {
        stubAccountFound();
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(accounts.existsByEmailIgnoreCaseAndIdNot(NEW_EMAIL, account.getId())).thenReturn(true);

        assertEquals(ErrorCode.CONFLICT, assertThrows(ApiException.class,
                () -> service.requestEmailChange(account.getId(), requestTo(NEW_EMAIL))).getErrorCode());
        verify(mail, never()).sendEmailChangeOtpEmail(anyString(), anyString(), anyInt());
    }

    @Test
    void requestRejectsTheAddressAlreadyOnTheAccount() {
        stubAccountFound();
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertEquals(ErrorCode.CONFLICT, assertThrows(ApiException.class,
                () -> service.requestEmailChange(account.getId(), requestTo("HELLO@gmail.com"))).getErrorCode());
    }

    @Test
    void requestNormalisesTheNewAddressBeforeUsingIt() {
        stubAccountFound();
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(accounts.existsByEmailIgnoreCaseAndIdNot(NEW_EMAIL, account.getId())).thenReturn(false);
        when(emailChangeOtp.issueCode(any(), any())).thenReturn("123456");

        service.requestEmailChange(account.getId(), requestTo("  HI@Gmail.COM  "));

        verify(mail).sendEmailChangeOtpEmail(eq(NEW_EMAIL), anyString(), anyInt());
    }

    /** Khoang cho chan TRUOC khi so BCrypt, de chot chong spam khong tro thanh cho dot CPU. */
    @Test
    void requestChecksTheCooldownBeforeHashingThePassword() {
        stubAccountFound();
        doThrowTooMany();

        assertThrows(ApiException.class,
                () -> service.requestEmailChange(account.getId(), requestTo(NEW_EMAIL)));

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    private void doThrowTooMany() {
        org.mockito.Mockito.doThrow(new ApiException(ErrorCode.TOO_MANY_REQUESTS, "wait"))
                .when(emailChangeOtp).assertCanSend(account.getId());
    }

    // ─────────────── BUOC 2: xac nhan ───────────────

    @Test
    void confirmSwapsTheEmailAndKicksEveryOldSession() {
        stubAccountFound();
        when(emailChangeOtp.findPending(account.getId())).thenReturn(Optional.of(NEW_EMAIL));
        when(accounts.existsByEmailIgnoreCaseAndIdNot(NEW_EMAIL, account.getId())).thenReturn(false);
        when(accounts.save(account)).thenReturn(account);

        var result = service.confirmEmailChange(account.getId(), new EmailChangeConfirmRequest("123456"));

        assertEquals(NEW_EMAIL, result.email());
        assertEquals(NEW_EMAIL, account.getEmail());
        // Thu hoi theo email CU: token dang luu hanh mang email cu trong claim "sub".
        verify(refreshTokenRedis).revokeEverySession(account.getId().toString(), OLD_EMAIL);
        verify(emailChangeOtp).clearPending(account.getId());
        // Hop thu cu phai duoc bao, vi tu gio no khong nhan duoc gi tu he thong nua.
        verify(mail).sendEmailChangedEmail(OLD_EMAIL, NEW_EMAIL);
    }

    @Test
    void confirmWithoutAPendingRequestIsRejected() {
        stubAccountFound();
        when(emailChangeOtp.findPending(account.getId())).thenReturn(Optional.empty());

        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(ApiException.class, () ->
                service.confirmEmailChange(account.getId(), new EmailChangeConfirmRequest("123456")))
                .getErrorCode());
        verify(accounts, never()).save(any());
    }

    @Test
    void confirmWithAWrongCodeLeavesTheEmailAlone() {
        stubAccountFound();
        when(emailChangeOtp.findPending(account.getId())).thenReturn(Optional.of(NEW_EMAIL));
        org.mockito.Mockito.doThrow(new ApiException(ErrorCode.BAD_REQUEST, "Incorrect verification code"))
                .when(emailChangeOtp).requireValidCode(eq(account.getId()), eq("000000"), any(Duration.class));

        assertThrows(ApiException.class, () ->
                service.confirmEmailChange(account.getId(), new EmailChangeConfirmRequest("000000")));

        assertEquals(OLD_EMAIL, account.getEmail());
        verify(accounts, never()).save(any());
        verify(refreshTokenRedis, never()).revokeEverySession(anyString(), anyString());
    }

    /**
     * Ca chong hoi quy quan trong nhat cua buoc 2: giua luc xin ma va luc xac nhan co ca chuc
     * phut, du de nguoi khac dang ky dung dia chi do. Tin vao lan kiem o buoc 1 la dam thang
     * vao rang buoc unique cua cot email.
     */
    @Test
    void confirmChecksAvailabilityASecondTimeBecauseTenMinutesHavePassed() {
        stubAccountFound();
        when(emailChangeOtp.findPending(account.getId())).thenReturn(Optional.of(NEW_EMAIL));
        when(accounts.existsByEmailIgnoreCaseAndIdNot(NEW_EMAIL, account.getId())).thenReturn(true);

        assertEquals(ErrorCode.CONFLICT, assertThrows(ApiException.class, () ->
                service.confirmEmailChange(account.getId(), new EmailChangeConfirmRequest("123456")))
                .getErrorCode());

        assertEquals(OLD_EMAIL, account.getEmail());
        verify(accounts, never()).save(any());
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
