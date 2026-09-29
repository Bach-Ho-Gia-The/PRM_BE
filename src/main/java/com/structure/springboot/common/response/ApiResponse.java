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

    // Helper method: Trả về thành công 200 có dữ liệu
    public static <T> ApiResponse<T> success(T result) {
        return ApiResponse.<T>builder()
                .code(200)
                .message("Success")
                .result(result)
                .build();
    }

    // Helper method: Trả về thành công khi tạo mới 201
    public static <T> ApiResponse<T> created(T result) {
        return ApiResponse.<T>builder()
                .code(201)
                .message("Resource created successfully")
                .result(result)
                .build();
    }

    // Helper method: Trả về thành công chỉ có message (cho các action DELETE, UPDATE không cần body)
    public static <T> ApiResponse<T> ok(String message) {
        return ApiResponse.<T>builder()
                .code(200)
                .message(message)
                .build();
    }
}