// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ContractReview } from "./ContractReview";
import { approveMcpToolContract, proposeOpenApiContract } from "../lib/api";

vi.mock("../lib/api", () => ({
  proposeOpenApiContract: vi.fn(),
  approveMcpToolContract: vi.fn(),
}));

const proposal = {
  contractVersion: "1.0",
  status: "PROPOSED" as const,
  source: { type: "FILE" as const, location: "petstore.yaml", openApiVersion: "3.1.0", title: "Pet Store", apiVersion: "1.0" },
  tools: [
    { name: "list_pets", description: "List pets", inputSchema: {}, outputSchema: {}, sourceOperation: { method: "GET", path: "/pets", operationId: "listPets" }, authenticationRequirements: [], risk: "READ_ONLY" as const },
    { name: "delete_pet", description: "Delete pet", inputSchema: {}, outputSchema: {}, sourceOperation: { method: "DELETE", path: "/pets/{id}", operationId: "deletePet" }, authenticationRequirements: [], risk: "DESTRUCTIVE" as const },
  ],
};

describe("ContractReview", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("submits the developer's endpoint selection and tool edits", async () => {
    vi.mocked(proposeOpenApiContract).mockResolvedValue(proposal);
    vi.mocked(approveMcpToolContract).mockResolvedValue({
      ...proposal,
      status: "APPROVED",
      tools: [{ ...proposal.tools[0], name: "find_pets", description: "Find available pets" }],
    });
    render(<ContractReview />);

    const file = new File(["openapi: 3.1.0"], "petstore.yaml", { type: "application/yaml" });
    fireEvent.change(screen.getByLabelText("OpenAPI document"), { target: { files: [file] } });
    fireEvent.click(screen.getByRole("button", { name: "Propose tools" }));

    await screen.findByText("2 OpenAPI operations are available. Select and name only the tools this MCP should expose.");
    fireEvent.click(screen.getByLabelText("Include DELETE /pets/{id}"));
    fireEvent.change(screen.getByLabelText("Tool name", { selector: "#tool-name-0" }), { target: { value: "find_pets" } });
    fireEvent.change(screen.getByLabelText("Description", { selector: "#tool-description-0" }), { target: { value: "Find available pets" } });
    fireEvent.click(screen.getByRole("button", { name: "Approve selected tools" }));

    await waitFor(() => expect(approveMcpToolContract).toHaveBeenCalledWith(proposal, [
      { toolIndex: 0, selected: true, name: "find_pets", description: "Find available pets" },
      { toolIndex: 1, selected: false, name: "delete_pet", description: "Delete pet" },
    ]));
    expect(await screen.findByText("Ready for generation")).toBeInTheDocument();
    expect(screen.getByText("1 reviewed tool. No source code has been generated.")).toBeInTheDocument();
  });
});
