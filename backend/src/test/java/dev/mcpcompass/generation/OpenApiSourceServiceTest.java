package dev.mcpcompass.generation;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenApiSourceServiceTest {
    private static final byte[] DOCUMENT = """
            {"openapi":"3.1.0","info":{"title":"Billing","version":"1"},
             "paths":{"/invoices":{"get":{}}}}
            """.getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsUploadedFileAndRemovesClientPath() {
        OpenApiSourceService service = service(uri -> DOCUMENT, 1024);

        OpenApiSourceDocument result = service.acceptFile("C:\\uploads\\billing.yaml", DOCUMENT);

        assertThat(result.sourceKind()).isEqualTo(OpenApiSourceDocument.SourceKind.FILE);
        assertThat(result.sourceLocation()).isEqualTo("billing.yaml");
        assertThat(result.title()).isEqualTo("Billing");
        assertThat(result.operationCount()).isEqualTo(1);
    }

    @Test
    void acceptsDocumentFetchedFromUrl() {
        URI uri = URI.create("https://developer.example.com/openapi.json");
        OpenApiSourceService service = service(requested -> {
            assertThat(requested).isEqualTo(uri);
            return DOCUMENT;
        }, 1024);

        OpenApiSourceDocument result = service.acceptUrl(uri);

        assertThat(result.sourceKind()).isEqualTo(OpenApiSourceDocument.SourceKind.URL);
        assertThat(result.sourceLocation()).isEqualTo(uri.toString());
        assertThat(result.openApiVersion()).isEqualTo("3.1.0");
    }

    @Test
    void omitsUrlQueryFromResponseMetadata() {
        URI uri = URI.create("https://developer.example.com/openapi.json?token=secret");
        OpenApiSourceDocument result = service(requested -> DOCUMENT, 1024).acceptUrl(uri);

        assertThat(result.sourceLocation()).isEqualTo("https://developer.example.com/openapi.json");
    }

    @Test
    void enforcesConfiguredSizeForFileAndUrlSources() {
        OpenApiSourceService service = service(uri -> DOCUMENT, 10);

        assertThatThrownBy(() -> service.acceptFile("large.json", DOCUMENT))
                .isInstanceOf(OpenApiSourceException.class)
                .hasMessageContaining("10 bytes");
        assertThatThrownBy(() -> service.acceptUrl(URI.create("https://example.com/openapi.json")))
                .isInstanceOf(OpenApiSourceException.class)
                .hasMessageContaining("10 bytes");
    }

    private static OpenApiSourceService service(OpenApiUrlFetcher fetcher, long maxBytes) {
        return new OpenApiSourceService(
                new OpenApiDocumentParser(new ObjectMapper()),
                fetcher,
                new OpenApiSourceProperties(maxBytes, Duration.ofSeconds(1), Duration.ofSeconds(1))
        );
    }
}
