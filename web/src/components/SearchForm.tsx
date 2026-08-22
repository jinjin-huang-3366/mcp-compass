"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { searchMcps, SearchResponse } from "@/lib/api";
import { CapabilityCoverage } from "@/components/CapabilityCoverage";

const EXAMPLE = "Read GitHub issues, comment on them and create pull requests";

export function SearchForm() {
  const [requirement, setRequirement] = useState(EXAMPLE);
  const [result, setResult] = useState<SearchResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    try {
      setResult(await searchMcps(requirement));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Search failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="searchCard">
      <form onSubmit={submit}>
        <label htmlFor="requirement">What does your agent need to do?</label>
        <textarea
          id="requirement"
          value={requirement}
          onChange={(e) => setRequirement(e.target.value)}
          rows={5}
          maxLength={2000}
        />
        <button disabled={loading || !requirement.trim()}>{loading ? "Searching…" : "Find MCP"}</button>
      </form>

      {error && <div className="error">{error}. Is the backend running on port 8080?</div>}

      {result && (
        <div className="results">
          <div className="keywords">
            {result.keywords.map((keyword) => <span key={keyword}>{keyword}</span>)}
          </div>

          {result.matches.length === 0 ? (
            <div className="empty">No local matches yet. Sync Registry data, then search again.</div>
          ) : (
            result.matches.map((match, index) => (
              <article className="match" key={match.id}>
                <div className="matchTop">
                  <div>
                    <div className="rank">#{index + 1}</div>
                    <h2>{match.title || match.registryName}</h2>
                    <code>{match.registryName}</code>
                  </div>
                  <div className="score">{Math.round(match.score * 100)}%</div>
                </div>
                <p>{match.description || "No description provided."}</p>
                <div className="meta">Version {match.version || "unknown"} · {match.status || "active"}</div>
                <CapabilityCoverage
                  coverage={match.capabilityCoverage}
                  matchedCapabilities={match.matchedCapabilities}
                  missingCapabilities={match.missingCapabilities}
                />
                <ul>{match.reasons.slice(0, 5).map((reason) => <li key={reason}>{reason}</li>)}</ul>
                <Link className="detailLink" href={`/mcp/${match.id}`}>
                  View MCP details
                </Link>
              </article>
            ))
          )}
        </div>
      )}
    </section>
  );
}
