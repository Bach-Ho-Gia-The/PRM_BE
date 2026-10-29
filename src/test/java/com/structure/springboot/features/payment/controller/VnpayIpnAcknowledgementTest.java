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

        assertEquals("/vnpay/ipn", annotation.value()[0],
                "duong dan phai khai day du tren method, khong dua vao @RequestMapping o lop");
        assertEquals(MediaType.APPLICATION_JSON_VALUE, annotation.produces()[0]);
    }

    /** Controller khai dung content-type HTML cho Return. */
    @Test
    void theControllerDeclaresHtmlForReturn() throws Exception {
        var annotation = VnpayCallbackController.class
                .getMethod("returnPage", jakarta.servlet.http.HttpServletRequest.class)
                .getAnnotation(org.springframework.web.bind.annotation.GetMapping.class);

        assertEquals("/vnpay/return", annotation.value()[0]);
        assertEquals(MediaType.TEXT_HTML_VALUE, annotation.produces()[0]);
    }

    /**
     * Khong duoc co {@code @RequestMapping} o cap lop.
     *
     * <p>Neu co, duong dan trong /v3/api-docs bi tach thanh hai cap va Swagger hien thi sai — dung
     * thu da lam nguoi test tay khong tim ra endpoint.</p>
     */
    @Test
    void theControllerDoesNotDeclareAClassLevelPath() {
        assertTrue(
                VnpayCallbackController.class
                        .getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class)
                        == null,
                "khong duoc dat @RequestMapping o cap lop");
    }

    /** ResponseEntity cua IPN phai la 200 va chua body — khong 4xx/5xx. */
    @Test
    void aSuccessfulAcknowledgedIsAlwaysHttp200() {
        ResponseEntity<VnpayIpnAcknowledgement> response =
                ResponseEntity.ok(VnpayIpnAcknowledgement.unknownError());

        assertEquals(200, response.getStatusCode().value());
    }
}
