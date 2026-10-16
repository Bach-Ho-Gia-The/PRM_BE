package com.structure.springboot.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Sinh slug thân thiện URL từ tên tiếng Việt: {@code "Chân Váy Chữ A"} → {@code "chan-vay-chu-a"}.
 *
 * <p>Lớp tiện ích thuần tuý, không trạng thái nên để {@code static} — dùng chung cho cả sản phẩm
 * lẫn danh mục, tránh chép đôi thuật toán bỏ dấu ở hai service.</p>
 */
public final class SlugUtils {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_SEPARATORS = Pattern.compile("^-+|-+$");

    private SlugUtils() {
    }

    /**
     * @return slug đã chuẩn hoá, hoặc {@code null} nếu đầu vào rỗng/không còn ký tự nào hợp lệ
     *         (ví dụ tên chỉ gồm emoji) — để tầng gọi tự quyết định báo lỗi hay sinh giá trị thay thế.
     */
    public static String toSlug(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        // Tách ký tự gốc khỏi dấu thanh ("ế" -> "e" + dấu sắc + dấu mũ) rồi xoá phần dấu đi.
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        normalized = COMBINING_MARKS.matcher(normalized).replaceAll("");

        // "đ"/"Đ" KHÔNG phải "d" + dấu nên NFD không tách được, phải thay tay.
        normalized = normalized.replace('\u0111', 'd').replace('\u0110', 'D');

        String slug = NON_ALPHANUMERIC.matcher(normalized.toLowerCase(Locale.ROOT)).replaceAll("-");
        slug = EDGE_SEPARATORS.matcher(slug).replaceAll("");

        return slug.isEmpty() ? null : slug;
    }
}
