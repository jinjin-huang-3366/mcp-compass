package dev.mcpcompass.capability;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic normalization shared by Registry ingestion and capability coverage scoring.
 */
public final class CapabilityNameNormalizer {
    private static final int MAX_NAME_LENGTH = 255;
    private static final Pattern CAPABILITY_SEPARATOR = Pattern.compile("[^a-z0-9-]+");
    private static final Pattern MATCH_SEPARATOR = Pattern.compile("[^a-z0-9]+");
    private static final Pattern REPEATED_DOTS = Pattern.compile("\\.{2,}");
    private static final Set<String> ACTIONS = Set.of(
            "archive", "cancel", "create", "delete", "diarize", "download", "execute", "get",
            "insert", "invite", "list", "merge", "navigate", "post", "push", "query", "read",
            "rerun", "resolve", "run", "search", "select", "send", "set", "share", "screenshot",
            "transcribe", "update", "upload", "write"
    );

    private CapabilityNameNormalizer() {
    }

    /** Preserves the public dotted/hyphenated capability convention used by persisted metadata. */
    public static String canonicalName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String canonical = CAPABILITY_SEPARATOR.matcher(value.strip().toLowerCase(Locale.ROOT))
                .replaceAll(".");
        canonical = REPEATED_DOTS.matcher(canonical).replaceAll(".");
        canonical = trimDots(canonical);
        return canonical.isBlank() || canonical.length() > MAX_NAME_LENGTH ? null : canonical;
    }

    /**
     * Produces a comparison key that tolerates common MCP tool-name variants. For example,
     * {@code github.create_pull_requests} and {@code github.pull-request.create} share a key.
     */
    public static String matchingKey(String value) {
        String canonical = canonicalName(value);
        if (canonical == null) {
            return null;
        }
        List<String> tokens = new ArrayList<>(List.of(MATCH_SEPARATOR.split(canonical)));
        int actionIndex = -1;
        for (int index = 1; index < tokens.size() - 1; index++) {
            if (ACTIONS.contains(tokens.get(index))) {
                actionIndex = index;
                break;
            }
        }
        for (int index = 1; index < tokens.size(); index++) {
            tokens.set(index, singularize(tokens.get(index)));
        }
        if (actionIndex >= 0) {
            String action = tokens.remove(actionIndex);
            tokens.add(action);
        }
        return String.join(".", tokens);
    }

    private static String singularize(String token) {
        if (token.endsWith("ies") && token.length() > 3) {
            return token.substring(0, token.length() - 3) + "y";
        }
        if (token.endsWith("sses") || token.endsWith("ss")
                || token.endsWith("us") || token.endsWith("is")) {
            return token;
        }
        if (token.endsWith("s") && token.length() > 3) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }

    private static String trimDots(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '.') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '.') {
            end--;
        }
        return value.substring(start, end);
    }
}
