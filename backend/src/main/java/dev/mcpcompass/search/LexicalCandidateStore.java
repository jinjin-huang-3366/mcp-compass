package dev.mcpcompass.search;

import java.util.List;
import java.util.UUID;

public interface LexicalCandidateStore {
    List<LexicalCandidate> findCandidates(List<String> keywords, int limit);

    record LexicalCandidate(UUID serverId, double score) {
    }
}
