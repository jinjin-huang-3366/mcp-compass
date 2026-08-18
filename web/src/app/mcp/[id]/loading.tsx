export default function McpDetailLoading() {
  return (
    <main className="shell detailShell" aria-busy="true" aria-live="polite">
      <div className="backLink">Back to search</div>
      <section className="detailCard detailLoading">
        <div className="loadingLine loadingEyebrow" />
        <div className="loadingLine loadingTitle" />
        <div className="loadingLine" />
        <span className="srOnly">Loading MCP server details</span>
      </section>
    </main>
  );
}
