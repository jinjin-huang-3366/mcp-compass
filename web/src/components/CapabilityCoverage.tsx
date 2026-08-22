type CapabilityCoverageProps = {
  coverage: number | null;
  matchedCapabilities: string[];
  missingCapabilities: string[];
};

export function CapabilityCoverage({
  coverage,
  matchedCapabilities,
  missingCapabilities,
}: CapabilityCoverageProps) {
  if (coverage === null) {
    return (
      <section className="capabilityCoverage" aria-label="Capability coverage">
        <div className="capabilityCoverageHeader">
          <h3>Capability coverage</h3>
          <span className="coverageValue coverageUnavailable">Not scored</span>
        </div>
        <p className="coverageSummary">
          Coverage is unavailable because this search did not include structured required capabilities.
        </p>
      </section>
    );
  }

  const percentage = Math.round(coverage * 100);

  return (
    <section className="capabilityCoverage" aria-label="Capability coverage">
      <div className="capabilityCoverageHeader">
        <h3>Capability coverage</h3>
        <span className="coverageValue">{percentage}%</span>
      </div>
      <progress aria-label={`${percentage}% capability coverage`} max={100} value={percentage} />
      <p className="coverageSummary">
        {matchedCapabilities.length} of {matchedCapabilities.length + missingCapabilities.length} required
        capabilities are covered.
      </p>

      <div className="capabilityGroups">
        <div className="capabilityGroup capabilityGroupMatched">
          <h4>Covered</h4>
          {matchedCapabilities.length > 0 ? (
            <>
              <p>This server explicitly supports:</p>
              <ul className="capabilityList">
                {matchedCapabilities.map((capability) => <li key={capability}>{capability}</li>)}
              </ul>
            </>
          ) : (
            <p>No required capabilities are covered by this server.</p>
          )}
        </div>

        <div className="capabilityGroup capabilityGroupMissing">
          <h4>Missing</h4>
          {missingCapabilities.length > 0 ? (
            <>
              <p>You would still need:</p>
              <ul className="capabilityList">
                {missingCapabilities.map((capability) => <li key={capability}>{capability}</li>)}
              </ul>
            </>
          ) : (
            <p>All required capabilities are covered.</p>
          )}
        </div>
      </div>
    </section>
  );
}
