# External publication approval gate

Use this gate immediately before every remote scheduling or publishing action. It binds a human decision to the
complete payload that will leave MCP Compass; it is not satisfied by approving an idea, a draft, or a checked-in
file.

## Approval package

Present one package per independently publishable item. Do not hide fields in links or attachments. The package must
contain:

- a stable package label and revision;
- the intended action: `schedule` or `publish now`;
- the exact Typefully social set or platform account and channel/community;
- the complete copy exactly as it will appear, including title, body, hashtags, mentions, and disclosure;
- every final URL, attachment, media item, and alt-text value, with `none` stated explicitly where applicable;
- an exact ISO 8601 publication time with UTC offset and a human-readable timezone, or `immediately after approval`
  for a publish-now action;
- known platform or community constraints and the time they were last verified;
- a statement that approval applies only to this revision.

End with an unambiguous prompt such as:

```text
Approve package X-LAUNCH revision 3 for the stated account, exact copy, media, and 2026-09-15T16:00:00+01:00
(Europe/London) schedule? Reply with an explicit approval or request changes. No external action occurs without it.
```

Do not treat silence, an emoji/reaction, approval from an earlier conversation, or a general instruction such as
"launch it" given before the final package as approval. A human must explicitly approve the complete package in the
current conversation.

## Pre-publish check

Immediately before calling Typefully or another platform:

1. Compare the pending action with the approved package field by field.
2. Confirm the account, channel, exact copy, URLs, media and alt text, and schedule still match.
3. Confirm the scheduled time has not passed and the relevant account connection is available.
4. State the exact external action about to occur and invoke only that action.

Stop and request fresh approval if any field changes, the platform transforms the content materially, the schedule
has passed, or the destination cannot be verified. Creating or approving an unscheduled remote draft never grants
permission to schedule or publish it.

After the action, report the platform URL or identifier, destination, actual state, and scheduled/published time.
Never report a draft or queued request as published.
