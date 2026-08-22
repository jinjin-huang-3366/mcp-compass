import { Suspense } from "react";
import { SearchForm } from "@/components/SearchForm";

export default function Home() {
  return (
    <main className="shell">
      <section className="hero">
        <div className="eyebrow">MCP COMPASS</div>
        <h1>Find the right MCP for your agent.</h1>
        <p>
          Describe the capability you need. We search the MCP ecosystem, rank the strongest matches,
          and explain why they fit.
        </p>
      </section>
      <Suspense fallback={<div className="searchCard">Loading search...</div>}>
        <SearchForm />
      </Suspense>
      <footer>
        V0.1: Registry discovery and ranking. MCP generation comes after search quality is proven.
      </footer>
    </main>
  );
}
