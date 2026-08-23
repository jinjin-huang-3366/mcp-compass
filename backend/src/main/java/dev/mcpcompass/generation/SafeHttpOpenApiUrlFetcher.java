package dev.mcpcompass.generation;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
class SafeHttpOpenApiUrlFetcher implements OpenApiUrlFetcher {
    private final HttpClient httpClient;
    private final long maxBytes;
    private final Duration readTimeout;

    SafeHttpOpenApiUrlFetcher(OpenApiSourceProperties properties) {
        this.maxBytes = properties.maxBytes() > 0 ? properties.maxBytes() : 2 * 1024 * 1024L;
        this.readTimeout = orDefault(properties.readTimeout(), Duration.ofSeconds(20));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(orDefault(properties.connectTimeout(), Duration.ofSeconds(5)))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public byte[] fetch(URI uri) {
        validateRemoteUri(uri);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(readTimeout)
                .header("Accept", "application/json, application/yaml, text/yaml, */*;q=0.1")
                .header("User-Agent", "mcp-compass")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                closeQuietly(response.body());
                throw new OpenApiSourceException(
                        "OPENAPI_URL_FETCH_FAILED",
                        "The OpenAPI URL returned HTTP " + response.statusCode() + "."
                );
            }
            long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (declaredLength > maxBytes) {
                closeQuietly(response.body());
                throw tooLarge();
            }
            try (InputStream body = response.body()) {
                byte[] content = body.readNBytes(Math.toIntExact(maxBytes + 1));
                if (content.length > maxBytes) {
                    throw tooLarge();
                }
                return content;
            }
        } catch (OpenApiSourceException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OpenApiSourceException("OPENAPI_URL_FETCH_FAILED", "Fetching the OpenAPI URL was interrupted.", exception);
        } catch (IOException | ArithmeticException exception) {
            throw new OpenApiSourceException("OPENAPI_URL_FETCH_FAILED", "Unable to fetch the OpenAPI URL.", exception);
        }
    }

    static void validateRemoteUri(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new OpenApiSourceException("UNSAFE_OPENAPI_URL", "OpenAPI URLs must use HTTPS and include a host.");
        }
        if (uri.getUserInfo() != null || uri.getFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new OpenApiSourceException(
                    "UNSAFE_OPENAPI_URL",
                    "OpenAPI URLs cannot contain credentials or fragments and may only use the default HTTPS port."
            );
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (isNonPublic(address)) {
                    throw new OpenApiSourceException(
                            "UNSAFE_OPENAPI_URL",
                            "OpenAPI URLs must resolve only to public network addresses."
                    );
                }
            }
        } catch (UnknownHostException exception) {
            throw new OpenApiSourceException("OPENAPI_URL_FETCH_FAILED", "The OpenAPI URL host could not be resolved.", exception);
        }
    }

    private static boolean isNonPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return first == 0 || first == 10 || first == 127
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168)
                    || (first == 198 && (second == 18 || second == 19))
                    || first >= 224;
        }
        return address instanceof Inet6Address && (bytes[0] & 0xfe) == 0xfc;
    }

    private OpenApiSourceException tooLarge() {
        return new OpenApiSourceException("OPENAPI_SOURCE_TOO_LARGE", "The OpenAPI source exceeds " + maxBytes + " bytes.");
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // The original response error is more useful than a close failure.
        }
    }

    private static Duration orDefault(Duration value, Duration fallback) {
        return value == null ? fallback : value;
    }
}
