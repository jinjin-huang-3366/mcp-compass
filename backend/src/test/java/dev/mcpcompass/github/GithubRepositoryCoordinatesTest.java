package dev.mcpcompass.github;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GithubRepositoryCoordinatesTest {
    @Test
    void acceptsCanonicalRepositoryUrlsAndRemovesGitSuffix() {
        assertThat(GithubRepositoryCoordinates.fromUrl("https://github.com/modelcontextprotocol/servers.git"))
                .contains(new GithubRepositoryCoordinates("modelcontextprotocol", "servers"));
    }

    @Test
    void rejectsNonGithubAndNonRepositoryUrls() {
        assertThat(GithubRepositoryCoordinates.fromUrl("https://gitlab.com/example/server")).isEmpty();
        assertThat(GithubRepositoryCoordinates.fromUrl("https://github.com/example/server/issues")).isEmpty();
        assertThat(GithubRepositoryCoordinates.fromUrl("not a url")).isEmpty();
    }
}
