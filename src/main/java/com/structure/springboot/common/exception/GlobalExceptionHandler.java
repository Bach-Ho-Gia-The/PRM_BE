package com.structure.springboot.common.exception;

import com.structure.springboot.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // 1. Bắt lỗi ApiException ném ra từ Service (Giữ nguyên custom message từ Service)
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleApiException(ApiException ex, HttpServletRequest request) {
        ErrorCode ec = ex.getErrorCode();
        return ResponseEntity.status(ec.getStatus())
                .body(buildError(ec.getCode(), ex.getMessage(), request.getRequestURI(), null));
    }

    // 2. Bắt lỗi validate param / query string
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v ->
                errors.put(v.getPropertyPath().toString(), v.getMessage())
        );

        ErrorCode ec = ErrorCode.BAD_REQUEST;
        return ResponseEntity.status(ec.getStatus())
                .body(buildError(ec.getCode(), "Constraint validation failed", request.getRequestURI(), errors));
    }

    // 3. Bắt lỗi validate @Valid @RequestBody DTO
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }

        ErrorCode ec = ErrorCode.BAD_REQUEST;
        ApiResponse<Object> body = buildError(ec.getCode(), "Request validation failed", extractPath(request), errors);
        return new ResponseEntity<>(body, ec.getStatus());
    }

    // 4. Bắt lỗi parse body JSON
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        ErrorCode ec = ErrorCode.BAD_REQUEST;
        ApiResponse<Object> body = buildError(ec.getCode(), "Malformed JSON request body", extractPath(request), null);
        return new ResponseEntity<>(body, ec.getStatus());
    }

    // 5. Bắt lỗi phân quyền Spring Security (403)
    // Tham số ?sort= trỏ tới field không tồn tại trên entity.
    // Spring Data ném PropertyReferenceException; không bắt thì nó rơi xuống handler Exception
    // cuối file và thành 500 — sai hẳn, vì lỗi nằm ở tham số client gửi lên chứ server không hỏng.
    // Để 500 còn có hai cái giá: log ngập stack trace, và ai cũng spam được ?sort=xyz.
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiResponse<Object>> handlePropertyReference(
            PropertyReferenceException ex, HttpServletRequest request) {

        ErrorCode ec = ErrorCode.BAD_REQUEST;
        return ResponseEntity.status(ec.getStatus())
                .body(buildError(ec.getCode(),
                        "Invalid sort or filter field: '" + ex.getPropertyName() + "'",
                        request.getRequestURI(), null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ErrorCode ec = ErrorCode.FORBIDDEN;
        return ResponseEntity.status(ec.getStatus())
                .body(buildError(ec.getCode(), ec.getMessage(), request.getRequestURI(), null));
    }

    // 6. Bắt lỗi ràng buộc dữ liệu DB (Duplicate key, Foreign Key...)
    /**
     * Hai người cùng mua món hàng cuối cùng: {@code @Version} trên {@code ProductVariantEntity}
     * khiến giao dịch ghi sau bị từ chối. Đây KHÔNG phải lỗi hệ thống — client thử lại là xong,
     * nên trả 409 chứ không phải 500.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Object>> handleOptimisticLocking(OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("OptimisticLockingFailure at {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorCode ec = ErrorCode.CONFLICT;
        return ResponseEntity.status(ec.getStatus())
                .body(buildError(ec.getCode(), "This item was just updated by someone else, please try again", request.getRequestURI(), null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("DataIntegrityViolation at {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorCode ec = ErrorCode.CONFLICT;
        return ResponseEntity.status(ec.getStatus())
                .body(buildError(ec.getCode(), "Data integrity violation or duplicated resource", request.getRequestURI(), null));
    }

    // 7. Hạ tầng phụ trợ chết (Redis/DB không kết nối được, query quá hạn) -> 503, KHÔNG phải 500.
    //    503 nói với client "lỗi tạm thời, thử lại sau", còn 500 nghĩa là code sai - rất khác nhau
    //    với cả người dùng lẫn hệ thống giám sát. Chỉ bắt đúng hai nhánh lỗi kết nối/timeout;
    //    DataIntegrityViolationException vẫn do handler số 6 xử lý thành 409.
    @ExceptionHandler({DataAccessResourceFailureException.class, QueryTimeoutException.class})
    public ResponseEntity<ApiResponse<Object>> handleInfrastructureDown(Exception ex, HttpServletRequest request) {
        log.error("Infrastructure unavailable at {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorCode ec = ErrorCode.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(ec.getStatus())
                .body(buildError(ec.getCode(), "Service temporarily unavailable, please try again later",
                        request.getRequestURI(), null));
    }

    // 8. Bắt toàn bộ lỗi hệ thống chưa được định nghĩa trước (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Internal Server Error at {}: ", request.getRequestURI(), ex);
        ErrorCode ec = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(ec.getStatus())
                .body(buildError(ec.getCode(), ec.getMessage(), request.getRequestURI(), null));
    }

    // 9. Bắt các lỗi mặc định còn lại của Spring MVC (404, 405, 415...)
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;

        ErrorCode ec = mapErrorCode(status);
        ApiResponse<Object> resp = buildError(ec.getCode(), ec.getMessage(), extractPath(request), null);
        return new ResponseEntity<>(resp, headers, status);
    }

    private ApiResponse<Object> buildError(int code, String message, String path, Map<String, String> errors) {
        return ApiResponse.<Object>builder()
                .code(code)
                .message(message)
                .result(null)
                .errors(errors)
                .timestamp(Instant.now())
                .path(path)
                .build();
    }

    private String extractPath(WebRequest request) {
        String desc = request.getDescription(false);
        if (desc == null) return null;
        return desc.startsWith("uri=") ? desc.substring(4) : desc;
    }

    private ErrorCode mapErrorCode(HttpStatus status) {
        return switch (status.value()) {
            case 400 -> ErrorCode.BAD_REQUEST;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 409 -> ErrorCode.CONFLICT;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case 422 -> ErrorCode.UNPROCESSABLE_ENTITY;
            case 429 -> ErrorCode.TOO_MANY_REQUESTS;
            case 502 -> ErrorCode.BAD_GATEWAY;
            case 503 -> ErrorCode.SERVICE_UNAVAILABLE;
            case 504 -> ErrorCode.GATEWAY_TIMEOUT;
            default -> status.is4xxClientError() ? ErrorCode.BAD_REQUEST : ErrorCode.INTERNAL_SERVER_ERROR;
        };
    }
}
