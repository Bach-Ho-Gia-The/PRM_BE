package com.structure.springboot.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    @Builder.Default
    private int code = 200;

    @Builder.Default
    private String message = "Success";

    private T result;
    private Map<String, String> errors;

    @Builder.Default
    private Instant timestamp = Instant.now();

    private String path;

    // Không khai báo helper tĩnh (success/created/ok): chúng không nhận được "path" nên không thể
    // thay thế builder, để lại chỉ khiến người sau phân vân giữa hai cách làm cùng một việc.
}
