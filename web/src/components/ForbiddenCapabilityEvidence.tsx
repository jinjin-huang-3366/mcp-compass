import type { ForbiddenCapabilityEvidence as Evidence } from "@/lib/api";

const signalLabels = {
  ARBITRARY_SQL: "Caller-supplied SQL",
  ARBITRARY_COMMAND: "Caller-supplied command",
  CALLER_CONTROLLED_APPROVAL: "Caller-controlled approval setting",
};

const uncertaintyLabels = {
  INFERRED: "Risk inferred from schema",
  NO_SIGNAL: "No recognized risk signal",
  INCOMPLETE: "Incomplete inspection",
  UNAVAILABLE: "Schema unavailable",
};

export function ForbiddenCapabilityEvidence({ evidence }: { evidence?: Evidence[] }) {
  if (!evidence?.length) {
    return null;
  }

  const report = evidence[0].staticToolSchema;
  return (
    <section aria-label="Forbidden capability evidence">
      <h3>Forbidden capability evidence</h3>
      <p>Registry declarations and normalized tool metadata are self-described. This search has not observed tools/list or verified server-enforced permissions.</p>
      <ul>{evidence.map((entry) => (
        <li key={entry.capability}>
          <code>{entry.capability}</code>: {entry.status} / {entry.permissionEnforcement}
          {entry.registryDeclaration === "SELF_DESCRIBED_READ_ONLY" ? "; Registry claims read-only" : ""}
          {entry.registryDeclaration === "SELF_DESCRIBED_WRITE_PATH" ? "; Registry advertises a write path" : ""}
          {entry.normalizedToolMetadata === "SELF_DESCRIBED_WRITE_PATH" ? "; normalized tool metadata advertises a write path" : ""}
        </li>
      ))}</ul>
      {report ? (
        <details>
          <summary>Static schema inspection{report.partial ? " (partial)" : ""}</summary>
          <p>Registry revision {report.revision || "unknown"} · Inspector version {report.inspectorVersion}</p>
          <ul>{report.schemas.map((schema, index) => (
            <li key={index}>
              <code>{schema.tool}</code> · {schema.source} · {uncertaintyLabels[schema.uncertainty]}
              {schema.signals.length > 0 && <> · {schema.signals.map((signal) => signalLabels[signal]).join(", ")}</>}
              {schema.sha256 && <> · SHA-256 <code>{schema.sha256}</code></>}
            </li>
          ))}</ul>
        </details>
      ) : <p>Static schema inspection unavailable.</p>}
    </section>
  );
}
