import Link from "next/link";
import { notFound } from "next/navigation";
import { getMcpDetail } from "@/lib/api";
import type { McpServerDetail } from "@/lib/api";

type McpDetailPageProps = {
  params: Promise<{ id: string }>;
};

export default async function McpDetailPage({ params }: McpDetailPageProps) {
  const { id } = await params;
  let server: McpServerDetail | null;

  try {
    server = await getMcpDetail(id);
  } catch {
    return <DetailError />;
  }

  if (!server) {
    notFound();
  }

  return (
    <main className="shell detailShell">
      <Link className="backLink" href="/">
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

function DetailError() {
  return (
    <main className="shell detailShell">
      <Link className="backLink" href="/">
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
