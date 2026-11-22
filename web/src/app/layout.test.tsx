import { renderToStaticMarkup } from "react-dom/server";
import type { AnalyticsProps } from "@vercel/analytics/react";
import { describe, expect, it, vi } from "vitest";
import RootLayout from "./layout";

const analytics = vi.hoisted(() => ({
  render: vi.fn(),
}));

vi.mock("next/navigation", () => ({ usePathname: () => "/mcp/server-1" }));

vi.mock("@vercel/analytics/react", () => ({
  Analytics: (props: AnalyticsProps) => {
    analytics.render(props);
    return <span data-testid="referral-analytics" />;
  },
}));

describe("RootLayout", () => {
  it("includes referral analytics on every route", () => {
    const markup = renderToStaticMarkup(
      <RootLayout>
        <main>MCP Compass</main>
      </RootLayout>,
    );

    expect(markup).toContain('data-testid="referral-analytics"');
    expect(analytics.render).toHaveBeenLastCalledWith(expect.objectContaining({
      path: "/mcp/[id]",
      route: "/mcp/[id]",
    }));
    const [{ beforeSend }] = analytics.render.mock.lastCall! as [AnalyticsProps];
    expect(beforeSend?.({ type: "pageview", url: "https://example.com/?q=private&page=2#token" }))
      .toEqual({ type: "pageview", url: "https://example.com/" });
    expect(beforeSend?.({ type: "pageview", url: "https://example.com/mcp/server-1?q=private" }))
      .toEqual({ type: "pageview", url: "https://example.com/mcp/[id]" });
    expect(beforeSend?.({ type: "event", url: "https://example.com/?q=private" })).toBeNull();
  });
});
