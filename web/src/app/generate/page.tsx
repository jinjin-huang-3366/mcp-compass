import Link from "next/link";
import { ContractReview } from "@/components/ContractReview";

export default function GeneratePage() {
  return (
    <main className="shell generationShell">
      <Link className="backLink" href="/">Back to search</Link>
      <section className="hero">
        <div className="eyebrow">CONTRACT-FIRST GENERATION</div>
        <h1>Review the tools before generating code.</h1>
        <p>Upload an OpenAPI document, choose and review the operations to expose as tools, then download a GitHub-ready Node.js/TypeScript MCP server.</p>
      </section>
      <ContractReview />
    </main>
  );
}
