package dev.mcpcompass.security;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Offline EVI-10 experiment only; deliberately not a production detector or safety classifier. */
final class SuspiciousInstructionPrototype {
    static final int MAX_DESCRIPTION_CHARS = 8192;
    private static final List<Rule> RULES = List.of(
            new Rule("instruction-override", Pattern.compile(
                    "\\b(?:ignore|disregard)\\s+(?:(?:all|any)\\s+)?(?:previous|prior|system)\\s+instructions\\b")),
            new Rule("claimed-authority", Pattern.compile("\\b(?:system|developer)\\s+(?:message|override)\\s*:")),
            new Rule("secret-transfer", Pattern.compile(
                    "\\b(?:send|upload|post)\\s+(?:your\\s+|the\\s+)?(?:api[ _-]?key|token|credentials|secrets)\\b")),
            new Rule("concealment", Pattern.compile("\\bdo not (?:tell|inform) the user\\b"))
    );

    Observation inspect(Source source, String description) {
        Objects.requireNonNull(source);
        if (description == null) {
            return new Observation(source, null, Status.NOT_INSPECTED, List.of());
        }
        // Reject oversized input, rather than certifying an uninspected suffix. No regex runs on it.
        if (description.length() > MAX_DESCRIPTION_CHARS) {
            return new Observation(source, null, Status.NOT_INSPECTED, List.of());
        }
        String hash = sha256(description);
        String normalized = description.toLowerCase(java.util.Locale.ROOT);
        List<String> matches = RULES.stream().filter(rule -> rule.pattern().matcher(normalized).find())
                .map(Rule::id).toList();
        return new Observation(source, hash,
                matches.isEmpty() ? Status.NO_PATTERN_MATCHED : Status.REVIEW_SUGGESTED, matches);
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    enum Status { REVIEW_SUGGESTED, NO_PATTERN_MATCHED, NOT_INSPECTED }

    record Source(URI url, String revision, String toolName, Instant observedAt) {
        Source {
            Objects.requireNonNull(url);
            Objects.requireNonNull(revision);
            Objects.requireNonNull(toolName);
            Objects.requireNonNull(observedAt);
        }
    }

    record Observation(Source source, String inspectedDescriptionSha256, Status status, List<String> ruleIds) {
        Observation {
            ruleIds = List.copyOf(ruleIds);
        }
    }

    private record Rule(String id, Pattern pattern) { }
}
