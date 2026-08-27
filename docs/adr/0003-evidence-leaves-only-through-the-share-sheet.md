# Evidence leaves the app only through the share sheet

`AGENTS.md` §12 says evidence is local-only: app-private, non-backed-up storage, no network permission of
any kind. Adding a `FileProvider` and a share action looks, at a glance, like a hole in that. It is not —
the invariant forbids *the app* moving evidence off the device, and a share sheet is the user moving their
own video with a tap they made themselves — but the resemblance is close enough that a future reader will
stop and check, so the reasoning is here.

We decided: evidence may leave through `ACTION_SEND`/`ACTION_SEND_MULTIPLE` and nothing else. No
"export to Downloads", no MediaStore write, no save-to-gallery. The share sheet is per-invocation, names
the receiving app in front of the user, and grants a one-shot read permission on a `content://` URI that
expires with the activity; a MediaStore write is a silent, permanent copy into shared storage that the user
never sees leave. Both put a video beyond retention's reach, but only one of them asks first.

## Consequences

A shared copy is permanently outside the app's control: `RetentionCleaner` will never expire it and deleting
the occurrence will not delete it. That consequence is the entire reason for the one-time explainer shown
before the first share — it is the one thing about sharing a user cannot work out for themselves, and it is
the only thing that dialog exists to say.

The app still declares no `INTERNET` permission and still initiates no egress. Whether a shared copy reaches
the network is the receiving app's business, chosen by the user in the share sheet.
