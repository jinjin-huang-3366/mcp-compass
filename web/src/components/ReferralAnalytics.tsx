"use client";

import { Analytics, type AnalyticsProps } from "@vercel/analytics/react";
import { usePathname } from "next/navigation";

const sanitizePageView: NonNullable<AnalyticsProps["beforeSend"]> = (event) => {
  if (event.type !== "pageview") {
    return null;
  }

  const url = new URL(event.url);
  url.search = "";
  url.hash = "";
  url.pathname = analyticsRoute(url.pathname);
  return { ...event, url: url.toString() };
};

export function ReferralAnalytics() {
  const pathname = usePathname();
  const route = pathname ? analyticsRoute(pathname) : null;
  return <Analytics beforeSend={sanitizePageView} route={route} path={route} />;
}

function analyticsRoute(pathname: string) {
  if (pathname === "/" || pathname === "/generate") {
    return pathname;
  }
  return pathname.startsWith("/mcp/") ? "/mcp/[id]" : "/other";
}
