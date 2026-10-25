import type { RemoteConnectionState } from "@/lib/api";

type Observation = {
  remoteConnectionState: RemoteConnectionState;
  remoteConnectionCheckedAt: string | null;
  remoteConnectionReason: string | null;
};

const labels: Record<RemoteConnectionState, string> = {
  REACHABLE: "MCP connection reached",
  AUTH_REQUIRED: "Authentication required",
  PROTOCOL_MISMATCH: "MCP protocol mismatch",
  UNREACHABLE: "Connection unreachable",
  NOT_CHECKED: "Connection not checked",
};

export function RemoteConnectionObservation({ observation }: { observation: Observation }) {
  const checked = observation.remoteConnectionCheckedAt
    ? new Date(observation.remoteConnectionCheckedAt)
    : null;
  const valid = checked && !Number.isNaN(checked.getTime());
  return (
    <p className="remoteObservation">
      Remote: {labels[observation.remoteConnectionState]}
      {valid && <> · Recorded {checked.toLocaleString("en", { timeZone: "UTC" })} UTC</>}
      {observation.remoteConnectionReason && <> · {observation.remoteConnectionReason}</>}
    </p>
  );
}
