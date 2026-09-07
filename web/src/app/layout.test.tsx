import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import RootLayout from "./layout";

vi.mock("@vercel/analytics/next", () => ({
  Analytics: () => <span data-testid="referral-analytics" />,
}));

describe("RootLayout", () => {
  it("includes referral analytics on every route", () => {
    const markup = renderToStaticMarkup(
      <RootLayout>
        <main>MCP Compass</main>
      </RootLayout>,
    );

    expect(markup).toContain('data-testid="referral-analytics"');
  });
});
