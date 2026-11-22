import { afterEach, describe, expect, it, vi } from "vitest";
import { recordSelectionEvent } from "./api";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("recordSelectionEvent", () => {
  it("sends only the fixed event name and omits credentials and referrer", async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetch);

    expect(await recordSelectionEvent("CHOICE_CHANGED")).toBe(true);
    expect(fetch).toHaveBeenCalledExactlyOnceWith(expect.stringMatching(/\/api\/v1\/selection\/events$/), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: '"CHOICE_CHANGED"',
      credentials: "omit",
      referrerPolicy: "no-referrer",
      keepalive: true,
    });
  });

  it("reports delivery failures without throwing", async () => {
    const fetch = vi.fn()
      .mockResolvedValueOnce(new Response(null, { status: 503 }))
      .mockRejectedValueOnce(new Error("Offline"));
    vi.stubGlobal("fetch", fetch);

    expect(await recordSelectionEvent("SOURCE_OPENED")).toBe(false);
    expect(await recordSelectionEvent("SOURCE_OPENED")).toBe(false);
  });
});
