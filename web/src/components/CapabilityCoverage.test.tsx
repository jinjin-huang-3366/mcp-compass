// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { CapabilityCoverage } from "./CapabilityCoverage";

afterEach(cleanup);

describe("CapabilityCoverage", () => {
  it("renders partial coverage with covered and missing capability explanations", () => {
    render(
      <CapabilityCoverage
        coverage={0.5}
        matchedCapabilities={["github.issue.read"]}
        missingCapabilities={["github.pull-request.create"]}
      />,
    );

    expect(screen.getByText("50%")).toBeInTheDocument();
    expect(screen.getByRole("progressbar", { name: "50% capability coverage" })).toHaveValue(50);
    expect(screen.getByText("1 of 2 required capabilities are covered.")).toBeInTheDocument();

    const covered = screen.getByRole("heading", { name: "Covered" }).parentElement;
    const missing = screen.getByRole("heading", { name: "Missing" }).parentElement;
    expect(covered).not.toBeNull();
    expect(missing).not.toBeNull();
    expect(within(covered!).getByText("github.issue.read")).toBeInTheDocument();
    expect(within(missing!).getByText("github.pull-request.create")).toBeInTheDocument();
    expect(within(missing!).getByText("You would still need:")).toBeInTheDocument();
  });

  it("explains when all required capabilities are covered", () => {
    render(
      <CapabilityCoverage
        coverage={1}
        matchedCapabilities={["github.issue.read"]}
        missingCapabilities={[]}
      />,
    );

    expect(screen.getByText("100%")).toBeInTheDocument();
    expect(screen.getByText("All required capabilities are covered.")).toBeInTheDocument();
  });

  it("explains when structured coverage was not scored", () => {
    render(
      <CapabilityCoverage coverage={null} matchedCapabilities={[]} missingCapabilities={[]} />,
    );

    expect(screen.getByText("Not scored")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Coverage is unavailable because this search did not include structured required capabilities.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
  });
});
