package com.structure.springboot.features.category.dto.response;

import java.util.UUID;

/** Category cửa hàng: không lộ trạng thái hay timestamp quản trị. */
public record CategoryPublicResponse(UUID id, String categoryName, String slug) {
}
