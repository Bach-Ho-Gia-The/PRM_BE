package com.structure.springboot.common.util;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import org.springframework.data.domain.Sort;

import java.util.Set;
import java.util.TreeSet;

/**
 * Chặn tham số {@code ?sort=} chỉ còn những field đã khai báo trước.
 *
 * <p>Mặc định Spring Data cho sắp xếp theo <b>bất kỳ</b> field nào của entity, kèm ba hệ quả
 * không mong muốn:</p>
 * <ul>
 *   <li>{@code ?sort=category.name} là hợp lệ và <b>âm thầm sinh thêm JOIN</b> sang bảng khác,
 *       nằm ngoài mọi tính toán hiệu năng của mình;</li>
 *   <li>sắp xếp theo cột không có index đồng nghĩa quét toàn bảng rồi filesort, chỉ để lấy 20 dòng;</li>
 *   <li>{@code sort} trở thành hợp đồng ngầm: đổi tên field trong entity là FE gãy lặng lẽ
 *       ở production, không ai báo.</li>
 * </ul>
 *
 * <p>Chỉ áp cho endpoint công khai. Khu vực quản trị dùng 400 của
 * {@code PropertyReferenceException} là đủ, khỏi cứng nhắc.</p>
 */
public final class SortWhitelist {

    private SortWhitelist() {
    }

    /**
     * @param sort         {@code pageable.getSort()} lấy từ request
     * @param allowedFields danh sách field cho phép, truyền thẳng tại nơi gọi để đọc phát là thấy
     * @throws ApiException 400 kèm danh sách field hợp lệ, để FE sửa được ngay mà không phải mò docs
     */
    public static void check(Sort sort, Set<String> allowedFields) {
        for (Sort.Order order : sort) {
            if (!allowedFields.contains(order.getProperty())) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Sort field '" + order.getProperty()
                        + "' is not allowed. Allowed fields: " + String.join(", ", new TreeSet<>(allowedFields)));
            }
        }
    }
}
