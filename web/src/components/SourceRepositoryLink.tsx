import type { ReactNode } from "react";

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
    <a className="sourceLink" href={href} target="_blank" rel="noopener noreferrer">
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
