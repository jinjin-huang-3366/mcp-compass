package dev.mcpcompass.generation;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/generation/contracts/review")
class McpToolContractReviewController {
    private final McpToolContractReviewService reviewService;

    McpToolContractReviewController(McpToolContractReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    McpToolContract approve(@RequestBody McpToolContractReviewRequest request) {
        return reviewService.approve(request);
    }
}
