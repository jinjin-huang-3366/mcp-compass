package dev.mcpcompass.generation;

import dev.mcpcompass.validation.ValidationJobController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        OpenApiSourceController.class,
        OpenApiToolContractController.class,
        McpToolContractReviewController.class,
        TypeScriptMcpProjectController.class,
        ValidationJobController.class
})
class OpenApiSourceErrorHandler {
    @ExceptionHandler(OpenApiSourceException.class)
    ResponseEntity<OpenApiSourceError> sourceError(OpenApiSourceException exception) {
        HttpStatus status = "OPENAPI_URL_FETCH_FAILED".equals(exception.code())
                ? HttpStatus.BAD_GATEWAY
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new OpenApiSourceError(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<OpenApiSourceError> validationError() {
        return ResponseEntity.badRequest().body(new OpenApiSourceError("INVALID_OPENAPI_SOURCE", "A URL is required."));
    }

    record OpenApiSourceError(String code, String message) {
    }
}
