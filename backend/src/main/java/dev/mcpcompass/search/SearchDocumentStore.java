package dev.mcpcompass.search;

import java.util.Collection;
import java.util.List;

public interface SearchDocumentStore {
    List<SearchDocument> buildForRegistryNames(Collection<String> registryNames);

    List<SearchDocument> buildAll();

    void replace(List<SearchDocument> documents);
}
