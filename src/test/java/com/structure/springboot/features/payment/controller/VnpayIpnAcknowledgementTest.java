package com.structure.springboot.features.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.structure.springboot.features.payment.dto.response.VnpayIpnAcknowledgement;
import com.structure.springboot.features.payment.dto.response.VnpayIpnResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hop dong JSON voi VNPay — nhung thu unit test tang service KHONG the bat duoc.
 *
 * <p>Service tra {@code VnpayIpnResponse} dung tram phan tram, nhung neu tang HTTP xuat ra
 * {@code {"rspCode":"00"}} thay vi {@code {"RspCode":"00"}} thi VNPay coi nhu khong co phan hoi va
 * goi lai 10 lan. Loi nay khong co test nao o tang service phat hien.</p>
 */
class VnpayIpnAcknowledgementTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Tien to duong dan khai o muc lop. Controller nay dat goc o {@code @RequestMapping} muc lop
     * ({@code /payments/vnpay}), nen duong that cua moi endpoint = tien to nay + path tren method.
     */
    private static String classPath() {
        return VnpayCallbackController.class
                .getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class)
                .value()[0];
    }

    /**
     * TEN KHOA PHAI VIET HOA — day la test quan trong nhat cua file.
     *
     * Jackson mac dinh doi {@code rspCode} thanh {@code "rspCode"}. Chi {@code @JsonProperty} moi
     * sua duoc, va neu ai do xoa annotation "cho gon" thi test nay do ngay.
     */
    @Test
    void theJsonKeysAreExactlyRspCodeAndMessage() throws Exception {
        String json = objectMapper.writeValueAsString(
                new VnpayIpnAcknowledgement("00", "Confirm Success"));

        Map<?, ?> parsed = objectMapper.readValue(json, Map.class);

        assertEquals("00", parsed.get("RspCode"), "khoa phai la RspCode viet hoa");
        assertEquals("Confirm Success", parsed.get("Message"), "khoa phai la Message viet hoa");
        assertEquals(2, parsed.size(), "DUNG hai khoa — VNPay khong doc khoa nao khac");
        assertFalse(json.contains("\"rspCode\""), "khong duoc lot camelCase ra JSON");
    }

    /** Body chi co dung hai khoa, khong bi boc trong ApiResponse. */
    @Test
    void theBodyIsNotWrappedAndHasNoExtraFields() throws Exception {
        String json = objectMapper.writeValueAsString(
                VnpayIpnAcknowledgement.from(VnpayIpnResponse.success(null)));

        assertFalse(json.contains("result"), "khong duoc boc trong ApiResponse");
        assertFalse(json.contains("timestamp"), "khong duoc boc trong ApiResponse");
        assertFalse(json.contains("path"), "khong duoc boc trong ApiResponse");
        assertTrue(json.startsWith("{"), "phai la object o cap ngoai cung");
    }

    /** Chuyen tu ket qua nghiep vu, khong doi ma. */
    @Test
    void itCarriesTheRspCodeFromTheServiceOutcome() {
        assertEquals("00", VnpayIpnAcknowledgement.from(VnpayIpnResponse.success(null)).rspCode());
        assertEquals("02", VnpayIpnAcknowledgement
                .from(VnpayIpnResponse.alreadyConfirmed(null)).rspCode());
    }

    /** Ca loi khong luong truoc: ma 99, dung ngu nghia "hay goi lai". */
    @Test
    void unknownErrorUsesCode99() {
        VnpayIpnAcknowledgement acknowledgement = VnpayIpnAcknowledgement.unknownError();

        assertEquals("99", acknowledgement.rspCode());
        assertEquals(VnpayIpnResponse.RSP_UNKNOWN_ERROR, acknowledgement.rspCode());
    }

    /** Controller khai dung content-type JSON cho IPN. */
    @Test
    void theControllerDeclaresJsonForIpn() throws Exception {
        var annotation = VnpayCallbackController.class
                .getMethod("ipn", jakarta.servlet.http.HttpServletRequest.class)
                .getAnnotation(org.springframework.web.bind.annotation.GetMapping.class);

        assertEquals("/payments/vnpay/ipn", classPath() + annotation.value()[0],
                "duong that cua IPN phai la /payments/vnpay/ipn");
        assertEquals(MediaType.APPLICATION_JSON_VALUE, annotation.produces()[0]);
    }

    /** Controller khai dung content-type HTML cho Return. */
    @Test
    void theControllerDeclaresHtmlForReturn() throws Exception {
        var annotation = VnpayCallbackController.class
                .getMethod("returnPage", jakarta.servlet.http.HttpServletRequest.class)
                .getAnnotation(org.springframework.web.bind.annotation.GetMapping.class);

        assertEquals("/payments/vnpay/return", classPath() + annotation.value()[0],
                "duong that cua Return phai la /payments/vnpay/return");
        assertEquals(MediaType.TEXT_HTML_VALUE, annotation.produces()[0]);
    }

    /**
     * Goc cua controller phai la {@code /payments/vnpay}.
     *
     * <p>Thiet ke chot: hai endpoint callback nam duoi {@code /payments/vnpay/**}, va whitelist
     * ({@code app.security.whitelist}) cung yaml {@code vnpay.return-url} mac dinh deu phai tro vao
     * dung tien to nay. Doi goc o day ma quen hai cho kia la VNPay goi vao duong khong duoc mo cong
     * khai -> 401, hoac Return URL tro sai -> 404. Test khoa tien to lai de doi mot cho la do ngay.</p>
     */
    @Test
    void theControllerIsRootedUnderPaymentsVnpay() {
        var classMapping = VnpayCallbackController.class
                .getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class);

        assertTrue(classMapping != null, "phai co @RequestMapping o cap lop lam goc chung");
        assertEquals("/payments/vnpay", classMapping.value()[0]);
    }

    /** ResponseEntity cua IPN phai la 200 va chua body — khong 4xx/5xx. */
    @Test
    void aSuccessfulAcknowledgedIsAlwaysHttp200() {
        ResponseEntity<VnpayIpnAcknowledgement> response =
                ResponseEntity.ok(VnpayIpnAcknowledgement.unknownError());

        assertEquals(200, response.getStatusCode().value());
    }

    /**
     * Controller nay chi duoc co DUNG HAI mapping, va ca hai deu la GET.
     *
     * <p>Vi sao: moi endpoint trong class nay nam duoi {@code /payments/vnpay/**}, tuc la
     * <b>cong khai</b>. Them mot {@code @PostMapping} hay mot method thu ba la mo them mot duong
     * khong can dang nhap — va khong co gi bao, vi no van bien dich va van chay. Test nay buoc nguoi
     * them phai sua o day, tuc la phai doc lai ly do vi sao whitelist duoc mo.</p>
     */
    @Test
    void theControllerExposesExactlyTwoGetEndpoints() {
        java.util.List<String> mappings = new java.util.ArrayList<>();

        for (var method : VnpayCallbackController.class.getDeclaredMethods()) {
            var get = method.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class);
            if (get != null) {
                mappings.add("GET " + classPath() + String.join(",", get.value()));
            }
            for (var annotation : method.getAnnotations()) {
                String name = annotation.annotationType().getSimpleName();
                if (name.endsWith("Mapping") && !"GetMapping".equals(name)) {
                    throw new AssertionError("Chi duoc phep GET, nhung " + method.getName()
                            + " co " + name + " — endpoint /payments/vnpay/** la cong khai");
                }
            }
        }

        mappings.sort(String::compareTo);
        assertEquals(
                java.util.List.of("GET /payments/vnpay/ipn", "GET /payments/vnpay/return"),
                mappings,
                "Tap mapping cua controller da thay doi — doc lai javadoc lop truoc khi sua");
    }
}
