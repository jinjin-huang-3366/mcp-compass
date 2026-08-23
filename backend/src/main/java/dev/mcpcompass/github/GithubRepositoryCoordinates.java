package dev.mcpcompass.github;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Pattern;

record GithubRepositoryCoordinates(String owner, String repository) {
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_.-]+");

    static Optional<GithubRepositoryCoordinates> fromUrl(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(repositoryUrl.trim());
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || !"github.com".equalsIgnoreCase(uri.getHost())) {
                return Optional.empty();
            }
            String[] segments = uri.getPath().replaceFirst("^/", "").replaceFirst("/$", "").split("/");
            if (segments.length != 2) {
                return Optional.empty();
            }
            String repository = segments[1].replaceFirst("(?i)\\.git$", "");
            if (!SEGMENT.matcher(segments[0]).matches() || !SEGMENT.matcher(repository).matches()) {
                return Optional.empty();
            }
            return Optional.of(new GithubRepositoryCoordinates(segments[0], repository));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
