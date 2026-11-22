"use client";

import type { ReactNode } from "react";
import { recordSelectionEvent } from "@/lib/api";

type SourceRepositoryLinkProps = {
  repositoryUrl: string | null;
  fallback?: ReactNode;
};

export function SourceRepositoryLink({
  repositoryUrl,
  fallback = null,
}: SourceRepositoryLinkProps) {
  const href = safeRepositoryUrl(repositoryUrl);
  if (!href) {
    return <>{fallback}</>;
  }

  return (
    <a
      className="sourceLink"
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      onClick={() => void recordSelectionEvent("SOURCE_OPENED")}
      onAuxClick={(event) => {
        if (event.button === 1) void recordSelectionEvent("SOURCE_OPENED");
      }}
    >
      Source repository
      <span className="srOnly"> (opens in a new tab)</span>
    </a>
  );
}

function safeRepositoryUrl(value: string | null) {
  if (!value) {
    return null;
  }

  try {
    const url = new URL(value.trim());
    return url.protocol === "https:" || url.protocol === "http:" ? url.toString() : null;
  } catch {
    return null;
  }
}
