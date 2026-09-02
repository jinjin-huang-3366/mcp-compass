"use client";

import { FormEvent, useEffect, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { searchMcps, SearchResponse } from "@/lib/api";
import { detailUrl, pageFromUrl, searchUrl } from "@/lib/search-navigation";
import { CapabilityCoverage } from "@/components/CapabilityCoverage";
import { RankingExplanation } from "@/components/RankingExplanation";
import { SourceRepositoryLink } from "@/components/SourceRepositoryLink";

const EXAMPLE = "Read GitHub issues, comment on them and create pull requests";
const PAGE_SIZE = 10;

export function SearchForm() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const urlRequirement = searchParams.get("q")?.trim() ?? "";
  const urlPage = pageFromUrl(searchParams.get("page"));
  const searchKey = `${urlRequirement}\n${urlPage}`;

  const [requirement, setRequirement] = useState(urlRequirement || EXAMPLE);
  const [result, setResult] = useState<SearchResponse | null>(null);
  const [loading, setLoading] = useState(Boolean(urlRequirement));
  const [error, setError] = useState<{ key: string; message: string } | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const visibleResult = result?.requirement === urlRequirement && result.page === urlPage
    ? result
    : null;
  const visibleError = error?.key === searchKey ? error.message : null;
  const isLoading = Boolean(urlRequirement) && (loading || !visibleResult) && !visibleError;

  useEffect(() => {
    if (!urlRequirement) {
      return;
    }

    const controller = new AbortController();
    searchMcps(urlRequirement, urlPage, PAGE_SIZE, controller.signal)
      .then((response) => {
        setRequirement(urlRequirement);
        setResult(response);
        setError(null);
      })
      .catch((cause: unknown) => {
        if (cause instanceof DOMException && cause.name === "AbortError") {
          return;
        }
        setError({
          key: searchKey,
          message: cause instanceof Error ? cause.message : "Search failed",
        });
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      });

    return () => controller.abort();
  }, [refreshKey, searchKey, urlPage, urlRequirement]);

  function submit(event: FormEvent) {
    event.preventDefault();
    const nextRequirement = requirement.trim();
    if (!nextRequirement) {
      return;
    }

    if (nextRequirement === urlRequirement && urlPage === 1) {
      setLoading(true);
      setError(null);
      setRefreshKey((current) => current + 1);
      return;
    }
    setLoading(true);
    setError(null);
    router.push(searchUrl(pathname, nextRequirement, 1));
  }

  function goToPage(page: number) {
    if (!urlRequirement || page < 1) {
      return;
    }
    setLoading(true);
    setError(null);
    router.push(searchUrl(pathname, urlRequirement, page));
  }

  return (
    <section className="searchCard">
      <form onSubmit={submit}>
        <label htmlFor="requirement">What does your agent need to do?</label>
        <textarea
          id="requirement"
          value={requirement}
          onChange={(event) => setRequirement(event.target.value)}
          rows={5}
          maxLength={2000}
        />
        <button disabled={isLoading || !requirement.trim()}>
          {isLoading ? "Searching..." : "Find MCP"}
        </button>
      </form>

      {urlRequirement && visibleError && (
        <div className="error">{visibleError}. Is the backend running on port 8080?</div>
      )}

      {visibleResult && (
        <div className="results" aria-live="polite">
          <div className="resultSummary">
            <span>{visibleResult.totalMatches} matches</span>
            <span>Page {visibleResult.page} of {Math.max(visibleResult.totalPages, 1)}</span>
            <span>Copy this page&apos;s URL to share the search.</span>
          </div>

          <div className="keywords">
            {visibleResult.keywords.map((keyword) => <span key={keyword}>{keyword}</span>)}
          </div>

          <section className="parsedIntent" aria-labelledby="parsed-intent-heading">
            <h2 id="parsed-intent-heading">Parsed intent</h2>
            <dl>
              <div><dt>Domain</dt><dd>{visibleResult.parsedIntent.domain || "Not identified"}</dd></div>
              <div><dt>Service</dt><dd>{visibleResult.parsedIntent.service || "Not identified"}</dd></div>
            </dl>
            <div className="intentCapabilities">
              <div>
                <h3>Required capabilities</h3>
                <p>{visibleResult.parsedIntent.requiredCapabilities.join(", ") || "None parsed"}</p>
              </div>
              <div>
                <h3>Forbidden capabilities</h3>
                <p>{visibleResult.parsedIntent.forbiddenCapabilities.join(", ") || "None parsed"}</p>
              </div>
              <div>
                <h3>Hard constraints</h3>
                <p>{visibleResult.parsedIntent.constraints.length > 0
                  ? visibleResult.parsedIntent.constraints
                    .map((constraint) => `${constraint.name} ${constraint.operator} ${constraint.value}`)
                    .join(", ")
                  : "None parsed"}</p>
              </div>
            </div>
          </section>

          {!visibleResult.strongMatch && (
            <section className="abstention" aria-labelledby="abstention-heading">
              <h2 id="abstention-heading">No strong match</h2>
              <p>MCP Compass abstained instead of recommending a low-confidence server.</p>
              <ul>{visibleResult.abstentionReasons.map((reason) => <li key={reason}>{reason}</li>)}</ul>
              {visibleResult.totalExcluded > 0 && (
                <p>{visibleResult.totalExcluded} candidate{visibleResult.totalExcluded === 1 ? " was" : "s were"} excluded by hard conditions.</p>
              )}
            </section>
          )}

          {visibleResult.matches.length === 0 ? (
            <div className="empty">
              {!visibleResult.strongMatch
                ? `No result met the ${Math.round(visibleResult.confidenceThreshold * 100)}% strong-match threshold.`
                : visibleResult.totalMatches === 0
                  ? "No local matches yet. Sync Registry data, then search again."
                : "This page has no matches. Go to a previous page."}
            </div>
          ) : (
            visibleResult.matches.map((match, index) => (
              <article className="match" key={match.id}>
                <div className="matchTop">
                  <div>
                    <div className="rank">#{(visibleResult.page - 1) * visibleResult.pageSize + index + 1}</div>
                    <h2>{match.title || match.registryName}</h2>
                    <code>{match.registryName}</code>
                  </div>
                  <div className="score">{Math.round(match.score * 100)}%</div>
                </div>
                <p>{match.description || "No description provided."}</p>
                <div className="meta">
                  Version {match.version || "unknown"} {"\u00b7"} {match.status || "active"} {"\u00b7"}{" "}
                  Quality {Math.round(match.qualityScore * 100)}%
                </div>
                <CapabilityCoverage
                  coverage={match.capabilityCoverage}
                  matchedCapabilities={match.matchedCapabilities}
                  missingCapabilities={match.missingCapabilities}
                />
                <RankingExplanation explanation={match.rankingExplanation} finalScore={match.score} />
                <ul>{match.reasons.slice(0, 5).map((reason) => <li key={reason}>{reason}</li>)}</ul>
                <div className="resultActions">
                  <Link className="detailLink" href={detailUrl(match.id, urlRequirement, urlPage)}>
                    View MCP details
                  </Link>
                  <SourceRepositoryLink repositoryUrl={match.repositoryUrl} />
                </div>
              </article>
            ))
          )}

          {(visibleResult.totalPages > 1 || visibleResult.page > 1) && (
            <nav className="pagination" aria-label="Search result pages">
              <button
                className="secondaryButton"
                type="button"
                onClick={() => goToPage(visibleResult.page - 1)}
                disabled={isLoading || visibleResult.page <= 1}
              >
                Previous
              </button>
              <span>Page {visibleResult.page} of {Math.max(visibleResult.totalPages, 1)}</span>
              <button
                className="secondaryButton"
                type="button"
                onClick={() => goToPage(visibleResult.page + 1)}
                disabled={isLoading || visibleResult.totalPages === 0 || visibleResult.page >= visibleResult.totalPages}
              >
                Next
              </button>
            </nav>
          )}
        </div>
      )}
    </section>
  );
}
