package dev.mcpcompass.validation;

import dev.mcpcompass.generation.GeneratedProjectProvider;
import dev.mcpcompass.generation.GeneratedTypeScriptProject;
import dev.mcpcompass.generation.McpToolContract;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ValidationJobQueueTest {
    private final GeneratedProjectProvider projectProvider = mock(GeneratedProjectProvider.class);
    private final ValidationJobRepository repository = mock(ValidationJobRepository.class);
    private final ValidationJobQueue queue = new ValidationJobQueue(
            projectProvider, repository, new ObjectMapper()
    );

    @Test
    void persistsAnImmutableGeneratedProjectSnapshotAsQueued() {
        McpToolContract contract = mock(McpToolContract.class);
        GeneratedTypeScriptProject project = new GeneratedTypeScriptProject(
                "1.0",
                "pet-store-mcp-server",
                "typescript",
                "1.0",
                List.of(new GeneratedTypeScriptProject.File(
                        "package.json",
                        "{\"name\":\"pet-store\"}\n"
                ))
        );
        when(projectProvider.generate(contract)).thenReturn(project);

        ValidationJobResponse response = queue.enqueue(contract);

        ArgumentCaptor<ValidationJobEntity> jobCaptor = ArgumentCaptor.forClass(ValidationJobEntity.class);
        verify(repository).save(jobCaptor.capture());
        ValidationJobEntity persisted = jobCaptor.getValue();
        assertThat(persisted.id()).isEqualTo(response.id());
        assertThat(persisted.status()).isEqualTo(ValidationJobStatus.QUEUED);
        assertThat(persisted.projectName()).isEqualTo("pet-store-mcp-server");
        assertThat(persisted.generatorVersion()).isEqualTo("1.0");
        assertThat(persisted.contractVersion()).isEqualTo("1.0");
        assertThat(persisted.projectManifest()).contains(
                "\"projectName\":\"pet-store-mcp-server\"",
                "\"path\":\"package.json\""
        );
        assertThat(persisted.queuedAt()).isEqualTo(response.queuedAt());
    }
}
