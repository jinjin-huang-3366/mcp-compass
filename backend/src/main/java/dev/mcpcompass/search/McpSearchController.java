package dev.mcpcompass.search;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mcp")
public class McpSearchController {
    private final McpSearchService searchService;

    public McpSearchController(McpSearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/search")
    public SearchResponse search(@Valid @RequestBody SearchRequest request) {
        return searchService.search(
                request.requirement(),
                request.effectivePage(),
                request.effectivePageSize()
        );
    }
}
