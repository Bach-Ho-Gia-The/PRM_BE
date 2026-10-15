package com.structure.springboot.common.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Bọc kết quả phân trang để client biết còn trang nào nữa không.
 *
 * <p>Không trả thẳng {@code Page} của Spring Data: cấu trúc JSON của nó phụ thuộc nội bộ thư viện,
 * nâng version là đổi hình dạng, và nó kèm cả {@code pageable}/{@code sort} lồng nhau rất rườm rà.</p>
 *
 * <p>Đây là lớp vận chuyển dùng chung ở {@code common/response} như {@code ApiResponse},
 * không phải DTO của feature — nên có factory tĩnh để mọi service khỏi lặp lại 7 dòng gán field.</p>
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
