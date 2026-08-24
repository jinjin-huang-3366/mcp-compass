"use client";

import { FormEvent, useState } from "react";
import {
  approveMcpToolContract,
  exportTypeScriptMcpProject,
  McpToolContract,
  McpToolReview,
  proposeOpenApiContract,
} from "../lib/api";

export function ContractReview() {
  const [proposal, setProposal] = useState<McpToolContract | null>(null);
  const [reviews, setReviews] = useState<McpToolReview[]>([]);
  const [approved, setApproved] = useState<McpToolContract | null>(null);
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function loadProposal(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const input = event.currentTarget.elements.namedItem("openapi");
    const file = input instanceof HTMLInputElement ? input.files?.[0] : undefined;
    if (!file || file.size === 0) {
      setError("Choose an OpenAPI JSON or YAML file.");
      return;
    }
    setLoading(true);
    setError(null);
    setApproved(null);
    try {
      const contract = await proposeOpenApiContract(file);
      setProposal(contract);
      setReviews(contract.tools.map((tool, toolIndex) => ({
        toolIndex,
        selected: true,
        name: tool.name,
        description: tool.description,
      })));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Unable to propose a contract.");
    } finally {
      setLoading(false);
    }
  }

  async function approve() {
    if (!proposal) return;
    setLoading(true);
    setError(null);
    try {
      setApproved(await approveMcpToolContract(proposal, reviews));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Unable to approve the contract.");
    } finally {
      setLoading(false);
    }
  }

  function updateReview(toolIndex: number, patch: Partial<McpToolReview>) {
    setApproved(null);
    setReviews((current) => current.map((review) =>
      review.toolIndex === toolIndex ? { ...review, ...patch } : review
    ));
  }

  async function downloadProject() {
    if (!approved) return;
    setExporting(true);
    setError(null);
    try {
      const { archive, fileName } = await exportTypeScriptMcpProject(approved);
      const url = URL.createObjectURL(archive);
      try {
        const link = document.createElement("a");
        link.href = url;
        link.download = fileName;
        link.click();
      } finally {
        URL.revokeObjectURL(url);
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Unable to export the project.");
    } finally {
      setExporting(false);
    }
  }

  return (
    <section className="contractReview">
      <form className="contractUpload" onSubmit={loadProposal}>
        <label htmlFor="openapi">OpenAPI document</label>
        <input id="openapi" name="openapi" type="file" accept=".json,.yaml,.yml,application/json,application/yaml,text/yaml" />
        <button disabled={loading}>{loading && !proposal ? "Reading contract..." : "Propose tools"}</button>
      </form>

      {error && <div className="error" role="alert">{error}</div>}

      {proposal && (
        <div className="contractEditor">
          <div className="contractHeading">
            <div>
              <div className="eyebrow">PROPOSED CONTRACT</div>
              <h2>{proposal.source.title}</h2>
              <p>{proposal.tools.length} OpenAPI operations are available. Select and name only the tools this MCP should expose.</p>
            </div>
            <span className="statusBadge">{reviews.filter((review) => review.selected).length} selected</span>
          </div>

          <div className="toolReviews">
            {proposal.tools.map((tool, toolIndex) => {
              const review = reviews[toolIndex];
              return (
                <fieldset className={`toolReview ${review?.selected ? "" : "toolReviewExcluded"}`} key={`${tool.sourceOperation.method}-${tool.sourceOperation.path}`}>
                  <legend>
                    <label>
                      <input
                        type="checkbox"
                        checked={review?.selected ?? false}
                        onChange={(event) => updateReview(toolIndex, { selected: event.target.checked })}
                      />
                      Include {tool.sourceOperation.method} {tool.sourceOperation.path}
                    </label>
                  </legend>
                  <div className="toolMeta">Risk: {tool.risk.replaceAll("_", " ").toLowerCase()}</div>
                  <label htmlFor={`tool-name-${toolIndex}`}>Tool name</label>
                  <input
                    id={`tool-name-${toolIndex}`}
                    type="text"
                    value={review?.name ?? ""}
                    disabled={!review?.selected}
                    onChange={(event) => updateReview(toolIndex, { name: event.target.value })}
                    pattern="[a-z][a-z0-9_]{0,63}"
                    maxLength={64}
                  />
                  <label htmlFor={`tool-description-${toolIndex}`}>Description</label>
                  <textarea
                    id={`tool-description-${toolIndex}`}
                    value={review?.description ?? ""}
                    disabled={!review?.selected}
                    onChange={(event) => updateReview(toolIndex, { description: event.target.value })}
                    rows={2}
                    maxLength={500}
                  />
                </fieldset>
              );
            })}
          </div>
          <button type="button" disabled={loading || reviews.every((review) => !review.selected)} onClick={approve}>
            {loading ? "Approving..." : "Approve selected tools"}
          </button>
        </div>
      )}

      {approved && (
        <div className="approvedContract" aria-live="polite">
          <div className="eyebrow">APPROVED CONTRACT</div>
          <h2>Ready to export</h2>
          <p>{approved.tools.length} reviewed {approved.tools.length === 1 ? "tool" : "tools"}. Download a GitHub-ready TypeScript repository with locked dependencies and CI.</p>
          <button type="button" disabled={exporting} onClick={downloadProject}>
            {exporting ? "Preparing ZIP..." : "Download GitHub-ready ZIP"}
          </button>
          <pre>{JSON.stringify(approved, null, 2)}</pre>
        </div>
      )}
    </section>
  );
}
