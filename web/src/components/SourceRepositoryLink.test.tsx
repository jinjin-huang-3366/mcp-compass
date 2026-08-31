// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { SourceRepositoryLink } from "./SourceRepositoryLink";

afterEach(cleanup);

describe("SourceRepositoryLink", () => {
  it("opens an HTTP(S) repository without giving the new page opener access", () => {
    render(<SourceRepositoryLink repositoryUrl="https://github.com/example/server" />);

    expect(screen.getByRole("link", { name: /Source repository/ }))
      .toHaveAttribute("href", "https://github.com/example/server");
    expect(screen.getByRole("link", { name: /Source repository/ }))
      .toHaveAttribute("target", "_blank");
    expect(screen.getByRole("link", { name: /Source repository/ }))
      .toHaveAttribute("rel", "noopener noreferrer");
  });

  it("does not render an unsafe publisher URL as a link", () => {
    render(<SourceRepositoryLink repositoryUrl="javascript:alert(1)" fallback="Unavailable" />);

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.getByText("Unavailable")).toBeInTheDocument();
  });
});
