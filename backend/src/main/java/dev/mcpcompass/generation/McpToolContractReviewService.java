package dev.mcpcompass.generation;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
class McpToolContractReviewService {
    private static final Pattern TOOL_NAME = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    McpToolContract approve(McpToolContractReviewRequest request) {
        if (request == null || request.contract() == null || request.tools() == null) {
            throw invalid("A proposed contract and its tool reviews are required.");
        }

        McpToolContract proposal = request.contract();
        if (!"1.0".equals(proposal.contractVersion()) || !"PROPOSED".equals(proposal.status())) {
            throw invalid("Only a version 1.0 PROPOSED contract can be reviewed.");
        }
        if (proposal.source() == null || proposal.tools() == null || proposal.tools().isEmpty()) {
            throw invalid("The proposed contract must contain a source and at least one tool.");
        }
        if (request.tools().size() != proposal.tools().size()) {
            throw invalid("Provide exactly one review for every proposed tool.");
        }

        Set<Integer> reviewedIndexes = new HashSet<>();
        Set<String> approvedNames = new HashSet<>();
        List<McpToolContract.Tool> approvedTools = new ArrayList<>();
        for (McpToolContractReviewRequest.ToolReview review : request.tools()) {
            if (review == null || review.toolIndex() < 0 || review.toolIndex() >= proposal.tools().size()
                    || !reviewedIndexes.add(review.toolIndex())) {
                throw invalid("Every proposed tool must have one unique review.");
            }
            if (!review.selected()) {
                continue;
            }

            String name = review.name() == null ? "" : review.name().trim().toLowerCase(Locale.ROOT);
            String description = review.description() == null ? "" : review.description().trim();
            if (!TOOL_NAME.matcher(name).matches()) {
                throw invalid("Selected tool names must start with a letter and contain only lowercase letters, numbers, and underscores (maximum 64 characters).");
            }
            if (!approvedNames.add(name)) {
                throw invalid("Selected tool names must be unique.");
            }
            if (description.isEmpty() || description.length() > MAX_DESCRIPTION_LENGTH) {
                throw invalid("Selected tool descriptions must contain 1 to 500 characters.");
            }

            McpToolContract.Tool proposedTool = proposal.tools().get(review.toolIndex());
            approvedTools.add(new McpToolContract.Tool(
                    name,
                    description,
                    proposedTool.inputSchema(),
                    proposedTool.outputSchema(),
                    proposedTool.sourceOperation(),
                    proposedTool.authenticationRequirements(),
                    proposedTool.risk()
            ));
        }
        if (approvedTools.isEmpty()) {
            throw invalid("Select at least one tool before approving the contract.");
        }

        return new McpToolContract(
                proposal.contractVersion(),
                "APPROVED",
                proposal.source(),
                approvedTools
        );
    }

    private static OpenApiSourceException invalid(String message) {
        return new OpenApiSourceException("INVALID_CONTRACT_REVIEW", message);
    }
}
