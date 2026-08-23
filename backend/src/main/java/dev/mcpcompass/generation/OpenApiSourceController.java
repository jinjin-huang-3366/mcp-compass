package dev.mcpcompass.generation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/generation/sources/openapi")
class OpenApiSourceController {
    private final OpenApiSourceService sourceService;

    OpenApiSourceController(OpenApiSourceService sourceService) {
        this.sourceService = sourceService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    OpenApiSourceResponse upload(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new OpenApiSourceException("EMPTY_OPENAPI_SOURCE", "The OpenAPI file is empty.");
        }
        try {
            return OpenApiSourceResponse.from(sourceService.acceptFile(file.getOriginalFilename(), file.getBytes()));
        } catch (IOException exception) {
            throw new OpenApiSourceException("OPENAPI_FILE_READ_FAILED", "Unable to read the uploaded OpenAPI file.", exception);
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    OpenApiSourceResponse fetch(@Valid @RequestBody OpenApiUrlRequest request) {
        return OpenApiSourceResponse.from(sourceService.acceptUrl(request.url()));
    }

    record OpenApiUrlRequest(@NotNull URI url) {
    }

    record OpenApiSourceResponse(
            String sourceType,
            String sourceLocation,
            String openApiVersion,
            String title,
            String apiVersion,
            int pathCount,
            int operationCount
    ) {
        static OpenApiSourceResponse from(OpenApiSourceDocument document) {
            return new OpenApiSourceResponse(
                    document.sourceKind().name(), document.sourceLocation(), document.openApiVersion(),
                    document.title(), document.apiVersion(), document.pathCount(), document.operationCount()
            );
        }
    }
}
