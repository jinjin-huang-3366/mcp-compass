package dev.mcpcompass.search;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record SearchRequest(
        @NotBlank @Size(max = 2000) String requirement,
        @Min(1) @Max(1000) Integer page,
        @Min(1) @Max(25) Integer pageSize
) {
    static final int DEFAULT_PAGE = 1;
    static final int DEFAULT_PAGE_SIZE = 10;

    public int effectivePage() {
        return page == null ? DEFAULT_PAGE : page;
    }

    public int effectivePageSize() {
        return pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
    }
}
