package dev.mcpcompass.requirement;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class HeuristicRequirementAnalyzer implements RequirementAnalyzer {
    private static final Set<String> STOP_WORDS = Set.of(
            "i", "a", "an", "the", "my", "our", "agent", "need", "needs", "to", "and", "or", "but",
            "can", "should", "with", "for", "from", "of", "on", "in", "into", "that", "this", "it", "be",
            "only", "without", "never", "must", "not", "forbidden", "prohibited", "available", "mode"
    );
    private static final Pattern NEGATIVE_CUE = Pattern.compile(
            "(?i)\\b(?:never|must\\s+(?:not|never)|do(?:es)?\\s+not|without|cannot|can't|no(?!\\s+(?:more|larger|less|fewer|named)))\\b"
    );
    private static final Pattern POSTFIX_NEGATIVE_CUE = Pattern.compile("(?i)\\b(?:forbidden|prohibited)\\b");
    private static final Pattern DOCUMENTATION_INTENT = Pattern.compile("(?i)\\b(?:docs?|documentation)\\b");
    private static final Map<Pattern, String> SERVICES = servicePatterns();
    private static final List<ForbiddenRule> FORBIDDEN_RULES = List.of(
            rule("\\b(?:delet\\w*|remov\\w*)\\b[^.;]*\\brepositor(?:y|ies)\\b|\\brepositor(?:y|ies)\\b[^.;]*\\b(?:delet\\w*|remov\\w*)\\b", "repository.delete"),
            rule("\\b(?:delet\\w*|remov\\w*)\\b[^.;]*\\bbranches?\\b|\\bbranches?\\b[^.;]*\\b(?:delet\\w*|remov\\w*)\\b", "branch.delete"),
            rule("\\b(?:delet\\w*|remov\\w*)\\b[^.;]*\\bissues?\\b|\\bissues?\\b[^.;]*\\b(?:delet\\w*|remov\\w*)\\b", "issue.delete"),
            rule("\\b(?:delet\\w*|remov\\w*)\\b[^.;]*\\bmessages?\\b|\\bmessages?\\b[^.;]*\\b(?:delet\\w*|remov\\w*)\\b", "message.delete"),
            rule("\\b(?:delet\\w*|remov\\w*)\\b[^.;]*\\bobjects?\\b|\\bobject\\s+deletion\\b", "object.delete"),
            rule("\\bexec(?:ute)?\\b[^.;]*\\bpods?\\b|\\bpods?\\b[^.;]*\\bexec(?:ute)?\\b", "pod.exec"),
            rule("\\b(?:delet\\w*|remov\\w*)\\b[^.;]*\\bpods?\\b|\\bpods?\\b[^.;]*\\b(?:delet\\w*|remov\\w*)\\b", "pod.delete"),
            rule("\\bshare\\w*\\b[^.;]*\\bfiles?\\b|\\bfiles?\\b[^.;]*\\bshare\\w*\\b", "file.share"),
            rule("\\b(?:delet\\w*|remov\\w*)\\b[^.;]*\\bfiles?\\b|\\bfiles?\\b[^.;]*\\b(?:delet\\w*|remov\\w*)\\b", "file.delete"),
            rule("\\brefund\\w*\\b", "refund.create"),
            rule("\\bvoice\\b|\\bvoice\\s+calls?\\b", "voice.call.create"),
            rule("\\bpublish\\w*\\b", "document.publish"),
            rule("\\bedit\\w*\\b", "document.edit"),
            rule("\\b(?:delet\\w*|remov\\w*)\\b[^.;]*\\brows?\\b|\\brows?\\b[^.;]*\\b(?:delet\\w*|remov\\w*)\\b", "row.delete"),
            rule("\\bschema\\s+writes?\\b", "schema.write")
    );

    @Override
    public RequirementAnalysis analyze(String requirement) {
        StructuredRequirement structuredRequirement = deterministicFallback(requirement);
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        Arrays.stream(positiveText(requirement).toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(token -> token.length() > 2)
                .filter(token -> !STOP_WORDS.contains(token))
                .limit(12)
                .forEach(keywords::add);
        return new RequirementAnalysis(requirement, List.copyOf(keywords), structuredRequirement);
    }

    private static StructuredRequirement deterministicFallback(String requirement) {
        String lowerRequirement = requirement.toLowerCase(Locale.ROOT);
        String service = service(lowerRequirement);
        String negativeText = negativeText(requirement).toLowerCase(Locale.ROOT);
        LinkedHashSet<String> forbiddenCapabilities = new LinkedHashSet<>();
        FORBIDDEN_RULES.stream()
                .filter(rule -> rule.pattern().matcher(negativeText).find())
                .map(rule -> qualify(service, rule.capability()))
                .forEach(forbiddenCapabilities::add);
        if ("postgres".equals(service)) {
            if (Pattern.compile("\\binserts?\\b", Pattern.CASE_INSENSITIVE).matcher(negativeText).find()) {
                forbiddenCapabilities.add("postgres.row.insert");
            }
            if (Pattern.compile("\\bupdates?\\b", Pattern.CASE_INSENSITIVE).matcher(negativeText).find()) {
                forbiddenCapabilities.add("postgres.row.update");
            }
            if (Pattern.compile("\\b(?:delet\\w*|remov\\w*)\\b", Pattern.CASE_INSENSITIVE)
                    .matcher(negativeText).find()) {
                forbiddenCapabilities.add("postgres.row.delete");
            }
        }

        List<RequirementConstraint> constraints = new ArrayList<>();
        if (Pattern.compile("(?i)\\bread[ -]?only\\b").matcher(requirement).find()) {
            constraints.add(equalsConstraint("access-mode", "read-only"));
            if ("postgres".equals(service)) {
                forbiddenCapabilities.add("postgres.row.write");
            }
            if (DOCUMENTATION_INTENT.matcher(requirement).find()) {
                forbiddenCapabilities.add("document.publish");
                forbiddenCapabilities.add("document.edit");
            }
        }
        if (Pattern.compile("(?i)\\b(?:without|no)\\s+(?:authentication|auth)\\b").matcher(requirement).find()) {
            constraints.add(equalsConstraint("authentication", "none"));
        }
        if (Pattern.compile("(?i)\\bsms\\s+only\\b").matcher(requirement).find()) {
            constraints.add(equalsConstraint("communication-channel", "sms"));
        }
        if (Pattern.compile("(?i)\\b(?:over|using|requires?)\\s+tls\\b").matcher(requirement).find()) {
            constraints.add(equalsConstraint("transport-security", "tls"));
        }
        if (Pattern.compile("(?i)\\boauth\\s*2\\b").matcher(requirement).find()) {
            constraints.add(equalsConstraint("authentication", "oauth2"));
        }
        addCapturedConstraint(constraints, requirement, "(?i)\\b([a-z0-9-]+)\\s+namespace\\b", "namespace");
        addCapturedConstraint(constraints, requirement, "(?i)\\b([a-z]{2}-[a-z]+-\\d)\\b", "region");
        addCapturedConstraint(constraints, requirement, "(?i)\\b([a-z0-9-]+)\\s+workspace\\b", "workspace");

        String structuredService = forbiddenCapabilities.isEmpty() && constraints.isEmpty() ? "" : service;
        return new StructuredRequirement(
                StructuredRequirement.CURRENT_SCHEMA_VERSION,
                "",
                structuredService,
                List.of(),
                List.copyOf(forbiddenCapabilities),
                List.copyOf(new LinkedHashSet<>(constraints))
        );
    }

    private static String positiveText(String requirement) {
        List<String> positive = new ArrayList<>();
        for (String clause : requirement.split("[.;]")) {
            if (POSTFIX_NEGATIVE_CUE.matcher(clause).find()) {
                continue;
            }
            for (String part : clause.split("(?i)\\bbut\\b|,")) {
                var cue = NEGATIVE_CUE.matcher(part);
                if (!cue.find()) {
                    positive.add(part);
                } else if (part.substring(cue.start()).toLowerCase(Locale.ROOT).startsWith("without")) {
                    positive.add(part.substring(0, cue.start()));
                }
            }
        }
        return String.join(" ", positive);
    }

    private static String negativeText(String requirement) {
        List<String> negative = new ArrayList<>();
        for (String clause : requirement.split("[.;]")) {
            if (POSTFIX_NEGATIVE_CUE.matcher(clause).find()) {
                negative.add(clause);
                continue;
            }
            for (String part : clause.split("(?i)\\bbut\\b|,")) {
                if (NEGATIVE_CUE.matcher(part).find()) {
                    negative.add(part);
                }
            }
        }
        return String.join(" ", negative);
    }

    private static String service(String requirement) {
        return SERVICES.entrySet().stream()
                .filter(entry -> entry.getKey().matcher(requirement).find())
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("");
    }

    private static String qualify(String service, String capability) {
        if (service.isBlank() || capability.startsWith(service + ".")) {
            return capability;
        }
        return service + "." + capability;
    }

    private static RequirementConstraint equalsConstraint(String name, String value) {
        return new RequirementConstraint(name, RequirementConstraint.Operator.EQUALS, value);
    }

    private static void addCapturedConstraint(
            List<RequirementConstraint> constraints,
            String requirement,
            String expression,
            String name
    ) {
        var matcher = Pattern.compile(expression).matcher(requirement);
        if (matcher.find()) {
            constraints.add(equalsConstraint(name, matcher.group(1).toLowerCase(Locale.ROOT)));
        }
    }

    private static ForbiddenRule rule(String expression, String capability) {
        return new ForbiddenRule(Pattern.compile(expression, Pattern.CASE_INSENSITIVE), capability);
    }

    private static Map<Pattern, String> servicePatterns() {
        Map<Pattern, String> services = new LinkedHashMap<>();
        services.put(Pattern.compile("\\bgithub\\b", Pattern.CASE_INSENSITIVE), "github");
        services.put(Pattern.compile("\\btwilio\\b", Pattern.CASE_INSENSITIVE), "twilio");
        services.put(Pattern.compile("\\b(?:postgresql|postgres)\\b", Pattern.CASE_INSENSITIVE), "postgres");
        services.put(Pattern.compile("\\bslack\\b", Pattern.CASE_INSENSITIVE), "slack");
        services.put(Pattern.compile("\\bjira\\b", Pattern.CASE_INSENSITIVE), "jira");
        services.put(Pattern.compile("\\bkubernetes\\b", Pattern.CASE_INSENSITIVE), "kubernetes");
        services.put(Pattern.compile("\\bnotion\\b", Pattern.CASE_INSENSITIVE), "notion");
        services.put(Pattern.compile("\\bstripe\\b", Pattern.CASE_INSENSITIVE), "stripe");
        services.put(Pattern.compile("\\b(?:amazon\\s+)?s3\\b", Pattern.CASE_INSENSITIVE), "s3");
        services.put(Pattern.compile("\\bgoogle\\s+drive\\b", Pattern.CASE_INSENSITIVE), "google-drive");
        return Map.copyOf(services);
    }

    private record ForbiddenRule(Pattern pattern, String capability) {
    }
}
