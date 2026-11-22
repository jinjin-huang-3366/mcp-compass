"use client";

import { useState } from "react";
import { recordSelectionEvent, type SelectionEvent } from "@/lib/api";

const CHOICES = [
  ["CHOICE_CHANGED", "Yes"],
  ["CHOICE_UNCHANGED", "No"],
  ["CHOICE_PENDING", "I haven't decided"],
] as const satisfies ReadonlyArray<readonly [SelectionEvent, string]>;

export function SelectionFeedback() {
  const [status, setStatus] = useState<"idle" | "sending" | "sent" | "error">("idle");

  async function send(event: SelectionEvent) {
    setStatus("sending");
    const saved = await recordSelectionEvent(event);
    setStatus(saved ? "sent" : "error");
  }

  return (
    <section className="selectionFeedback" aria-labelledby="selection-feedback-heading">
      <h2 id="selection-feedback-heading">Did these results change which MCP server you chose or ruled out?</h2>
      <p>Optional. Only your answer is counted.</p>
      {status !== "sent" && (
        <div className="resultActions">
          {CHOICES.map(([event, label]) => (
            <button
              className="secondaryButton"
              disabled={status === "sending"}
              key={event}
              onClick={() => void send(event)}
              type="button"
            >
              {label}
            </button>
          ))}
        </div>
      )}
      <p role="status">
        {status === "sent" && "Thanks for sharing your answer."}
        {status === "error" && "Your answer couldn't be recorded. You can try again."}
      </p>
    </section>
  );
}
