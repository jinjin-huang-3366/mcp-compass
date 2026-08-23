package dev.mcpcompass.generation;

class OpenApiSourceException extends RuntimeException {
    private final String code;

    OpenApiSourceException(String code, String message) {
        super(message);
        this.code = code;
    }

    OpenApiSourceException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    String code() {
        return code;
    }
}
