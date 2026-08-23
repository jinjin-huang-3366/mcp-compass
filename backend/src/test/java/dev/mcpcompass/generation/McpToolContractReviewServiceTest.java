package dev.mcpcompass.generation;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpToolContractReviewServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final McpToolContractReviewService service = new McpToolContractReviewService();

    @Test
    void selectsAndEditsToolsWhilePreservingContractDetails() {
        McpToolContract proposal = proposal();

        McpToolContract approved = service.approve(new McpToolContractReviewRequest(proposal, List.of(
                new McpToolContractReviewRequest.ToolReview(0, true, "find_pets", "Find available pets"),
                new McpToolContractReviewRequest.ToolReview(1, false, "delete_pet", "Delete a pet")
        )));

        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.tools()).hasSize(1);
        assertThat(approved.tools().getFirst().name()).isEqualTo("find_pets");
        assertThat(approved.tools().getFirst().description()).isEqualTo("Find available pets");
        assertThat(approved.tools().getFirst().sourceOperation()).isEqualTo(proposal.tools().getFirst().sourceOperation());
        assertThat(approved.tools().getFirst().risk()).isEqualTo(McpToolContract.Risk.READ_ONLY);
    }

    @Test
    void rejectsAReviewWithNoSelectedTools() {
        assertThatThrownBy(() -> service.approve(new McpToolContractReviewRequest(proposal(), List.of(
                new McpToolContractReviewRequest.ToolReview(0, false, "list_pets", "List pets"),
                new McpToolContractReviewRequest.ToolReview(1, false, "delete_pet", "Delete a pet")
        )))).isInstanceOf(OpenApiSourceException.class)
                .hasMessageContaining("Select at least one tool");
    }

    @Test
    void rejectsDuplicateSelectedNames() {
        assertThatThrownBy(() -> service.approve(new McpToolContractReviewRequest(proposal(), List.of(
                new McpToolContractReviewRequest.ToolReview(0, true, "manage_pet", "List pets"),
                new McpToolContractReviewRequest.ToolReview(1, true, "manage_pet", "Delete a pet")
        )))).isInstanceOf(OpenApiSourceException.class)
                .hasMessageContaining("must be unique");
    }

    private McpToolContract proposal() {
        return new McpToolContract("1.0", "PROPOSED",
                new McpToolContract.Source("FILE", "petstore.yaml", "3.1.0", "Pet Store", "1.0"),
                List.of(
                        tool("list_pets", "GET", McpToolContract.Risk.READ_ONLY),
                        tool("delete_pet", "DELETE", McpToolContract.Risk.DESTRUCTIVE)
                ));
    }

    private McpToolContract.Tool tool(String name, String method, McpToolContract.Risk risk) {
        return new McpToolContract.Tool(name, name.replace('_', ' '),
                objectMapper.createObjectNode().put("type", "object"),
                objectMapper.createObjectNode().put("type", "object"),
                new McpToolContract.Operation(method, "/pets", name), List.of("apiKey"), risk);
    }
}
