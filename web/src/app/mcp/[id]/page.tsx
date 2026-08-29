import Link from "next/link";
import { notFound } from "next/navigation";
import { getMcpDetail } from "@/lib/api";
import type { McpServerDetail } from "@/lib/api";
import { searchReturnUrl } from "@/lib/search-navigation";

type McpDetailPageProps = {
  params: Promise<{ id: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
};

export default async function McpDetailPage({ params, searchParams }: McpDetailPageProps) {
  const [{ id }, detailSearchParams] = await Promise.all([params, searchParams]);
  const backHref = searchReturnUrl(detailSearchParams);
  let server: McpServerDetail | null;

  try {
    server = await getMcpDetail(id);
  } catch {
    return <DetailError backHref={backHref} />;
  }

  if (!server) {
    notFound();
  }

  return (
    <main className="shell detailShell">
      <Link className="backLink" href={backHref}>
        Back to search
      </Link>

      <article className="detailCard">
        <header className="detailHeader">
          <div>
            <div className="eyebrow">MCP SERVER</div>
            <h1 className="detailTitle">{server.title || server.registryName}</h1>
            <code className="registryName">{server.registryName}</code>
          </div>
          <span className="statusBadge">{server.status || "active"}</span>
        </header>

        <section className="detailSection" aria-labelledby="description-heading">
          <h2 id="description-heading">About this server</h2>
          <p>{server.description || "No description was provided by the Registry publisher."}</p>
        </section>

        <section className="detailSection" aria-labelledby="metadata-heading">
          <h2 id="metadata-heading">Registry metadata</h2>
          <dl className="metadataGrid">
            <div>
              <dt>Version</dt>
              <dd>{server.version || "Unknown"}</dd>
            </div>
            <div>
              <dt>Status</dt>
              <dd>{server.status || "Active"}</dd>
            </div>
            <div>
              <dt>First indexed</dt>
              <dd>{formatTimestamp(server.firstSeenAt)}</dd>
            </div>
            <div>
              <dt>Last indexed</dt>
              <dd>{formatTimestamp(server.lastSeenAt)}</dd>
            </div>
          </dl>
        </section>
      </article>
    </main>
  );
}

function DetailError({ backHref }: { backHref: string }) {
  return (
    <main className="shell detailShell">
      <Link className="backLink" href={backHref}>
        Back to search
      </Link>
      <section className="detailState error" role="alert">
        <h1>Server details are unavailable</h1>
        <p>Check that the MCP Compass backend is running, then reload this page.</p>
      </section>
    </main>
  );
}

function formatTimestamp(value: string) {
  const timestamp = new Date(value);
  if (Number.isNaN(timestamp.getTime())) {
    return "Unknown";
  }
  return new Intl.DateTimeFormat("en", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "UTC",
  }).format(timestamp);
}
