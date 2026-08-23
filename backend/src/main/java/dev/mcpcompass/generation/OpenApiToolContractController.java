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
@RequestMapping("/api/v1/generation/contracts/openapi")
class OpenApiToolContractController {
    private final OpenApiSourceService sourceService;
    private final OpenApiToolContractDesigner contractDesigner;

    OpenApiToolContractController(OpenApiSourceService sourceService, OpenApiToolContractDesigner contractDesigner) {
        this.sourceService = sourceService;
        this.contractDesigner = contractDesigner;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    McpToolContract upload(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new OpenApiSourceException("EMPTY_OPENAPI_SOURCE", "The OpenAPI file is empty.");
        }
        try {
            return contractDesigner.design(sourceService.acceptFile(file.getOriginalFilename(), file.getBytes()));
        } catch (IOException exception) {
            throw new OpenApiSourceException(
                    "OPENAPI_FILE_READ_FAILED", "Unable to read the uploaded OpenAPI file.", exception
            );
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    McpToolContract fetch(@Valid @RequestBody OpenApiContractUrlRequest request) {
        return contractDesigner.design(sourceService.acceptUrl(request.url()));
    }

    record OpenApiContractUrlRequest(@NotNull URI url) {
    }
}
