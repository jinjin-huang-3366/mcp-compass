package dev.mcpcompass.remote;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class RemoteAuthenticationChallenge {
    private static final String OAUTH_HINT = " (Bearer protected resource metadata advertised)";
    private static final Pattern BEARER = Pattern.compile("(?i)^Bearer[ \\t]+");
    private static final Pattern PARAMETER = Pattern.compile(
            "\\G\\s*([a-zA-Z0-9_-]+)\\s*=\\s*(?:\"([^\"\\\\]*)\"|[!#$%&'*+.^_`|~0-9a-zA-Z-]+)\\s*(?:,|$)");
    private static final int MAX_HEADER_LENGTH = 4096;

    private RemoteAuthenticationChallenge() {}

    static String reason(int status, Map<String, List<String>> headers) {
        boolean advertised = headers.entrySet().stream()
                .filter(header -> "WWW-Authenticate".equalsIgnoreCase(header.getKey()))
                .flatMap(header -> header.getValue().stream())
                .anyMatch(RemoteAuthenticationChallenge::advertisesResourceMetadata);
        return gatedReason(status) + (advertised ? OAUTH_HINT : "");
    }

    // EVI-04 stores a fixed observation reason. Persist a classification only, never the challenge or URL.
    static boolean oauthAdvertised(String reason) {
        return (gatedReason(401) + OAUTH_HINT).equals(reason) || (gatedReason(403) + OAUTH_HINT).equals(reason);
    }

    private static String gatedReason(int status) {
        return "HTTP " + status + " requires authorization";
    }

    private static boolean advertisesResourceMetadata(String header) {
        if (header.length() > MAX_HEADER_LENGTH) {
            return false;
        }
        var challenge = BEARER.matcher(header);
        if (!challenge.find()) {
            return false;
        }
        var parameters = PARAMETER.matcher(header).region(challenge.end(), header.length());
        while (parameters.find()) {
            if ("resource_metadata".equalsIgnoreCase(parameters.group(1)) && parameters.group(2) != null) {
                return isHttpsUrl(parameters.group(2));
            }
        }
        return false;
    }

    private static boolean isHttpsUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getFragment() == null;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
