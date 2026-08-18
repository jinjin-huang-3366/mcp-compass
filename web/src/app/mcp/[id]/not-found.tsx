import Link from "next/link";

export default function McpDetailNotFound() {
  return (
    <main className="shell detailShell">
      <Link className="backLink" href="/">
        Back to search
      </Link>
      <section className="detailState">
        <div className="eyebrow">MCP SERVER</div>
        <h1>Server not found</h1>
        <p>This server is not available in the local Registry index.</p>
      </section>
    </main>
  );
}
