import type { SetupOption, SetupReadiness as SetupEvidence } from "@/lib/api";

const transportLabels: Record<SetupOption["transport"], string> = {
  STREAMABLE_HTTP: "Streamable HTTP",
  SSE: "HTTP+SSE",
  STDIO: "stdio",
  UNKNOWN: "Unknown transport",
};

const authenticationLabels: Record<SetupOption["authentication"], string> = {
  UNKNOWN: "Authentication unknown",
  DECLARED_REQUIRED: "Registry declares required credentials",
  REQUIRED: "Authentication required, method unknown",
  OAUTH_ADVERTISED: "Authentication required, OAuth metadata advertised",
  INITIALIZED_WITHOUT_AUTH: "Initialized without credentials",
  DECLARATION_MISMATCH: "Registry declares required credentials, but the probe initialized without them",
};

const protocolLabels: Record<SetupOption["protocol"], string> = {
  UNKNOWN: "Protocol compatibility unknown",
  UNVERIFIED: "Protocol compatibility unverified",
  UNSUPPORTED_BY_CHECK: "Transport unsupported by the remote check",
  INITIALIZE_SUCCEEDED: "MCP initialize succeeded",
  MISMATCH: "Probe did not receive a valid MCP initialize response",
};

const inputLabels: Record<SetupOption["missingInputs"][number]["kind"], string> = {
  HEADER: "HTTP header",
  TEMPLATE_VARIABLE: "template variable",
  ENVIRONMENT_VARIABLE: "environment variable",
  RUNTIME_ARGUMENT: "runtime argument",
  PACKAGE_ARGUMENT: "package argument",
  AUTHORIZATION: "credentials or sign-in instructions",
  OAUTH_AUTHORIZATION: "OAuth authorization",
};

export function SetupReadiness({ evidence }: { evidence?: SetupEvidence }) {
  if (!evidence) return null;
  if (!evidence.options.length) {
    return <p className="remoteObservation">Setup details unknown. No install option metadata available.</p>;
  }
  return (
    <ul className="remoteObservation">
      {evidence.options.map((option) => (
        <li key={`${option.source}-${option.position}`}>
          <strong>
            {option.source === "REMOTE" ? "Remote" : "Package"} {option.position} · {transportLabels[option.transport]}
          </strong>
          {" · "}{authenticationLabels[option.authentication]}
          {" · "}{protocolLabels[option.protocol]}
          {option.missingInputs.length > 0 && (
            <div>
              Setup inputs needed · {option.missingInputs.map((input) =>
                input.name ? `${inputLabels[input.kind]} ${input.name}` : inputLabels[input.kind]
              ).join(", ")}
            </div>
          )}
        </li>
      ))}
    </ul>
  );
}
