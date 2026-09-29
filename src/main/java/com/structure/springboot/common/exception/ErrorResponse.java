package com.structure.springboot.common.exception;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        Instant timestamp,
        int status,
        int code,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
}
