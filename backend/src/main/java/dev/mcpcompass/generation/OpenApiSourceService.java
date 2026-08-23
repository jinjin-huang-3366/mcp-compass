package dev.mcpcompass.generation;

import org.springframework.stereotype.Service;

import java.net.URI;

@Service
class OpenApiSourceService {
    private final OpenApiDocumentParser parser;
    private final OpenApiUrlFetcher urlFetcher;
    private final long maxBytes;

    OpenApiSourceService(OpenApiDocumentParser parser, OpenApiUrlFetcher urlFetcher, OpenApiSourceProperties properties) {
        this.parser = parser;
        this.urlFetcher = urlFetcher;
        this.maxBytes = properties.maxBytes() > 0 ? properties.maxBytes() : 2 * 1024 * 1024L;
    }

    OpenApiSourceDocument acceptFile(String fileName, byte[] content) {
        requireWithinLimit(content);
        return document(OpenApiSourceDocument.SourceKind.FILE, safeFileName(fileName), parser.parse(content));
    }

    OpenApiSourceDocument acceptUrl(URI uri) {
        byte[] content = urlFetcher.fetch(uri);
        requireWithinLimit(content);
        return document(OpenApiSourceDocument.SourceKind.URL, publicUrl(uri), parser.parse(content));
    }

    private OpenApiSourceDocument document(
            OpenApiSourceDocument.SourceKind kind,
            String location,
            OpenApiDocumentParser.ParsedOpenApiDocument parsed
    ) {
        return new OpenApiSourceDocument(
                kind, location, parsed.openApiVersion(), parsed.title(), parsed.apiVersion(),
                parsed.pathCount(), parsed.operationCount(), parsed.document()
        );
    }

    private void requireWithinLimit(byte[] content) {
        if (content == null || content.length > maxBytes) {
            throw new OpenApiSourceException(
                    "OPENAPI_SOURCE_TOO_LARGE",
                    "The OpenAPI source exceeds " + maxBytes + " bytes."
            );
        }
    }

    private static String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "uploaded-openapi";
        }
        String normalized = fileName.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private static String publicUrl(URI uri) {
        try {
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null).toASCIIString();
        } catch (Exception exception) {
            throw new OpenApiSourceException("INVALID_OPENAPI_SOURCE", "The OpenAPI URL is invalid.", exception);
        }
    }
}
