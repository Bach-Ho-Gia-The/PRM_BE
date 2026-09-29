package com.structure.springboot.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // 2xx - Success
    SUCCESS(200, "Success", HttpStatus.OK),
    CREATED(201, "Created", HttpStatus.CREATED),

    // 4xx - Client Errors
    BAD_REQUEST(400, "Bad Request", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(401, "Unauthorized", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(403, "Forbidden", HttpStatus.FORBIDDEN),
    NOT_FOUND(404, "Not Found", HttpStatus.NOT_FOUND),
    CONFLICT(409, "Conflict", HttpStatus.CONFLICT),
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported Media Type", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    UNPROCESSABLE_ENTITY(422, "Unprocessable Entity", HttpStatus.UNPROCESSABLE_ENTITY),
    TOO_MANY_REQUESTS(429, "Too Many Requests", HttpStatus.TOO_MANY_REQUESTS),

    // 5xx - Server Errors
    INTERNAL_SERVER_ERROR(500, "Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR),
    BAD_GATEWAY(502, "Bad Gateway", HttpStatus.BAD_GATEWAY),
    SERVICE_UNAVAILABLE(503, "Service Unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    GATEWAY_TIMEOUT(504, "Gateway Timeout", HttpStatus.GATEWAY_TIMEOUT);

    private final int code;
    private final String message;
    private final HttpStatus status;
}