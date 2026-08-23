import Link from "next/link";
import { ContractReview } from "@/components/ContractReview";

export default function GeneratePage() {
  return (
    <main className="shell generationShell">
      <Link className="backLink" href="/">Back to search</Link>
      <section className="hero">
        <div className="eyebrow">CONTRACT-FIRST GENERATION</div>
        <h1>Review the tools before generating code.</h1>
        <p>Upload an OpenAPI document, choose the operations the MCP should expose, and edit their developer-facing tool names and descriptions.</p>
      </section>
      <ContractReview />
    </main>
  );
}
