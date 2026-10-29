package com.structure.springboot.features.order.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bao ve quy uoc: {@code markCancelled} la CUA DUY NHAT ghi trang thai {@code CANCELLED}.
 *
 * <h2>Vi sao phai viet test kieu nay</h2>
 *
 * <p>Quy uoc nay khong the bao bang compiler. Nguoi sau chi can viet
 * {@code order.setStatus(OrderStatus.CANCELLED)} o mot cho khac la co cua thu hai — va cua thu hai
 * se quen {@code restoreStock} hoac quen dong payment hoac quen ghi khoan hoan. Ba thu do deu la loi
 * <b>im lang</b>: khong exception, khong log, chi co du lieu sai dan ra theo thoi gian.</p>
 *
 * <p>Test doc source thay vi chay code vi day la kiem tra <b>cau truc</b>, khong phai hanh vi. Cach
 * duy nhat de biet "chi co mot cho ghi CANCELLED" la dem so cho.</p>
 *
 * <p>Nhuoc diem thua nhan: test nay do khi file bi doi dinh dang (vi du doi sang {@code setStatus(
 * OrderStatus.CANCELLED)} xuong dong). Luc do sua test, khong phai sua code — va do la tin hieu
 * dung: co nguoi vua sua vao khu vuc can doc lai.</p>
 */
class OrderCancellationSingleDoorTest {

    private static final Path IMPL = Path.of(
            "src/main/java/com/structure/springboot/features/order/service/impl/OrderServiceImpl.java");

    /** Dem so lan chuoi xuat hien trong source. */
    private static int count(String source, String needle) {
        int total = 0;
        int index = source.indexOf(needle);
        while (index >= 0) {
            total++;
            index = source.indexOf(needle, index + needle.length());
        }
        return total;
    }

    /** Lấy các dòng chứa chuỗi, để thông điệp lỗi chỉ luôn chỗ sai. */
    private static List<String> linesContaining(String source, String needle) {
        List<String> found = new ArrayList<>();
        String[] lines = source.split("\n");
        for (int index = 0; index < lines.length; index++) {
            if (lines[index].contains(needle)) {
                found.add((index + 1) + ": " + lines[index].trim());
            }
        }
        return found;
    }

    /**
     * Dung MOT cho duy nhat gan trang thai {@code CANCELLED}.
     *
     * <p>Do la dong trong {@code markCancelled}. Them cua thu hai ⇒ test do ⇒ nguoi viet phai doc
     * lai javadoc cua {@code markCancelled} va thay danh sach bon viec phai lam cung nhau.</p>
     */
    @Test
    void onlyMarkCancelledAssignsTheCancelledStatus() throws IOException {
        String source = Files.readString(IMPL, StandardCharsets.UTF_8);

        int assignments = count(source, "setStatus(OrderStatus.CANCELLED)");

        assertEquals(1, assignments,
                "Phai co dung 1 cho gan CANCELLED, tim thay " + assignments
                        + " o:\n" + String.join("\n", linesContaining(source, "setStatus(OrderStatus.CANCELLED)")));
    }

    /**
     * Va cho do phai nam trong {@code markCancelled} — khong phai mot ham khac.
     *
     * <p>Chi dem so la chua du: mot nguoi co the doi CANCELLED tu {@code markCancelled} sang mot ham
     * moi roi tu goi {@code restoreStock} o cho khac. Khi do van dung 1 cho gan, nhung khong con
     * nguyen tu.</p>
     */
    @Test
    void theCancelledAssignmentLivesInsideTheSingleDoor() throws IOException {
        String source = Files.readString(IMPL, StandardCharsets.UTF_8);

        int methodStart = source.indexOf("private OrderResponse closeAndRestore(");
        assertTrue(methodStart > 0,
                "Khong tim thay closeAndRestore — cua duy nhat bi doi ten hoac bi xoa?");

        int assignment = source.indexOf("setStatus(OrderStatus.CANCELLED)");
        assertTrue(assignment > methodStart,
                "Dong gan CANCELLED nam TRUOC closeAndRestore — co cua thu hai");

        int methodEnd = source.indexOf("\n    private ", methodStart + 1);
        assertTrue(methodEnd > assignment,
                "Dong gan CANCELLED nam NGOAI closeAndRestore — co cua thu hai");
    }

    /**
     * Ca ba loi vao phai di qua cung mot cua — va ca ba deu phai con ton tai.
     *
     * <p>CK-5 them loi vao thu ba (het han). Test nay khoa lai rang viec them loi vao <b>khong</b>
     * duoc bien thanh them mot cua: neu ai do viet mot ham huy rieng cho het han, test do ngay.</p>
     */
    @Test
    void allThreeEntryPointsShareTheSameDoor() throws IOException {
        String source = Files.readString(IMPL, StandardCharsets.UTF_8);

        assertTrue(source.contains("private OrderResponse markCancelled("),
                "thieu loi vao cua khach/shop huy");
        assertTrue(source.contains("private OrderResponse markExpired("),
                "thieu loi vao cua tac vu quet het han");

        // Ca hai loi vao, va CA HAI deu phai goi closeAndRestore.
        assertTrue(count(source, "return closeAndRestore(") == 2,
                "ca markCancelled va markExpired deu phai goi closeAndRestore, tim thay "
                        + count(source, "return closeAndRestore("));

        // Khong loi vao nao duoc tu lam lay bon viec.
        assertTrue(!source.contains("private OrderResponse markExpired(OrderEntity order) {\n        restoreStock"),
                "markExpired tu hoan kho thay vi goi closeAndRestore");
    }

    /**
     * Bon viec cua mot lan huy don phai nam cung mot ham.
     *
     * <p>Neu ai tach {@code restoreStock} ra khoi {@code markCancelled} (vi du chuyen sang
     * {@code afterCommit} cho "nhanh hon"), test nay do. Do la ca song con: khoi bi quan tren don da
     * nha, nen lop chan "cong kho hai lan" thu nhat mat tac dung.</p>
     */
    @Test
    void theCancelFlowKeepsAllFourSideEffectsTogether() throws IOException {
        String source = Files.readString(IMPL, StandardCharsets.UTF_8);

        int methodStart = source.indexOf("private OrderResponse closeAndRestore(");
        int methodEnd = source.indexOf("\n    private ", methodStart + 1);
        String body = source.substring(methodStart, methodEnd);

        assertTrue(body.contains("restoreStock(order)"), "thieu restoreStock trong closeAndRestore");
        assertTrue(body.contains("setStatus(OrderStatus.CANCELLED)"), "thieu doi trang thai");
        assertTrue(body.contains("requestRefundIfPaid(order"), "thieu ghi khoan hoan");

        // Dong payment: hai duong, nhung BAT BUOC phai co dung mot trong hai — va phai phan biet
        // ly do. Kiem ca hai de khong ai xoa mat mot nhanh.
        assertTrue(body.contains("paymentService.releaseForOrderCancel(order)"),
                "thieu duong dong payment CANCELLED");
        assertTrue(body.contains("paymentService.expireForOrder(order)"),
                "thieu duong dong payment EXPIRED");
    }

    /**
     * Khong duong huy nao duoc dung {@code markCancelled} cho ca don het han.
     *
     * <p>Day la he qua truc tiep cua yeu cau "phan biet ly do": {@code markCancelled} phai luon
     * dong payment bang {@code CANCELLED}. Neu tac vu het han goi no thi don het han se mang payment
     * {@code CANCELLED} — mat kha nang phan biet vinh vien.</p>
     */
    @Test
    void theExpiryPathNeverUsesMarkCancelled() throws IOException {
        String source = Files.readString(IMPL, StandardCharsets.UTF_8);

        int methodStart = source.indexOf("private OrderResponse markExpired(");
        int methodEnd = source.indexOf("\n    /**", methodStart + 1);
        assertTrue(methodStart > 0, "thieu markExpired");
        String body = source.substring(methodStart, methodEnd);

        assertTrue(!body.contains("markCancelled("),
                "markExpired goi markCancelled — don het han se mang payment CANCELLED");
        assertTrue(body.contains("closeAndRestore(order, EXPIRY_CANCEL_REASON, true)"),
                "markExpired phai di qua closeAndRestore voi expired = true");
    }
}
