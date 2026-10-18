package com.structure.springboot.features.account.entity;

import com.structure.springboot.features.account.enums.AccountStatus;
import com.structure.springboot.features.auth.security.AccountPrincipal;
import com.structure.springboot.features.role.entity.RoleEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bang chan tri cua khoa tam: status + lockedUntil quyet dinh dang nhap duoc hay khong.
 *
 * <p>Hai lop phai tra loi GIONG NHAU: {@code AccountPrincipal} (duong dang nhap, Spring doc) va
 * {@code AccountEntity.isLoginAllowed()} (duong quen mat khau, nghiep vu tu doc). Lech nhau la
 * sinh ra canh "dang nhap duoc nhung khong lay lai mat khau duoc".</p>
 *
 * <p>Nhan test de ASCII thuan vi output surefire ma hoa hong tieng Viet co dau.</p>
 */
class AccountLockTest {

    private static final Instant TOMORROW = Instant.now().plus(1, ChronoUnit.DAYS);
    private static final Instant YESTERDAY = Instant.now().minus(1, ChronoUnit.DAYS);

    private AccountEntity account(AccountStatus status, Instant lockedUntil) {
        RoleEntity role = RoleEntity.builder().roleName("USER").build();
        role.setId(UUID.randomUUID());

        AccountEntity entity = AccountEntity.builder()
                .email("a@b.com").password("$2a$x").role(role)
                .status(status).lockedUntil(lockedUntil).build();
        entity.setId(UUID.randomUUID());
        return entity;
    }

    /** Spring coi la dang nhap duoc khi CA HAI co cung bat. */
    private boolean springWouldAllowLogin(AccountEntity entity) {
        AccountPrincipal principal = AccountPrincipal.from(entity);
        return principal.isEnabled() && principal.isAccountNonLocked();
    }

    private void bothLayersAgree(AccountEntity entity, boolean expected) {
        assertTrue(entity.isLoginAllowed() == expected,
                "isLoginAllowed() sai cho status=" + entity.getStatus()
                        + " lockedUntil=" + entity.getLockedUntil());
        assertTrue(springWouldAllowLogin(entity) == expected,
                "AccountPrincipal sai cho status=" + entity.getStatus()
                        + " lockedUntil=" + entity.getLockedUntil());
    }

    // ─────────── bang chan tri ───────────

    @Test
    void activeAccountCanSignIn() {
        bothLayersAgree(account(AccountStatus.ACTIVE, null), true);
    }

    /** Du lieu cu chua co cot status: coi nhu ACTIVE, neu khong ca he thong khoa khi trien khai. */
    @Test
    void legacyNullStatusIsTreatedAsActive() {
        bothLayersAgree(account(null, null), true);
    }

    @Test
    void disabledAccountIsBlockedForever() {
        bothLayersAgree(account(AccountStatus.DISABLED, null), false);
    }

    /** lockedUntil con o tuong lai: dang trong thoi gian khoa. */
    @Test
    void lockedUntilInTheFutureBlocksSignIn() {
        bothLayersAgree(account(AccountStatus.LOCKED, TOMORROW), false);
    }

    /** 🎯 lockedUntil da qua: TU MO, khong can job don dep nao. */
    @Test
    void lockExpiresByItselfOnceTheMomentHasPassed() {
        bothLayersAgree(account(AccountStatus.LOCKED, YESTERDAY), true);
    }

    /** 🎯 Khong dien lockedUntil = khoa VO THOI HAN, cho admin mo tay. */
    @Test
    void lockedWithoutAnEndDateNeverExpiresOnItsOwn() {
        bothLayersAgree(account(AccountStatus.LOCKED, null), false);
    }

    /**
     * Ca chong hoi quy cho dung con bug vua va: cot status KHONG BAO GIO duoc ghi lai ve ACTIVE
     * khi het han khoa. Chi so status la chan nham nguoi da duoc tu mo khoa — ho dang nhap duoc
     * nhung quen mat khau thi im lang khong nhan duoc ma nao.
     */
    @Test
    void anExpiredLockStillReadsAsLockedInTheColumnSoNeverCompareStatusAlone() {
        AccountEntity expired = account(AccountStatus.LOCKED, YESTERDAY);

        assertTrue(expired.getStatus() == AccountStatus.LOCKED, "cot status van nam o LOCKED");
        assertFalse(expired.getStatus() == AccountStatus.ACTIVE, "khong ai ghi lai ve ACTIVE");
        assertTrue(expired.isLoginAllowed(), "nhung nghiep vu phai coi la dung duoc");
    }
}
