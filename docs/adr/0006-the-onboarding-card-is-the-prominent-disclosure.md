# The onboarding card is the prominent disclosure

Google Play requires a **prominent disclosure** before an app begins accessing sensitive data: shown
in-app, before access starts, describing what is collected and how it is used, with the user's
affirmative consent — and explicitly *not* satisfied by the privacy policy alone or by the system
permission dialog alone.

`PermissionOnboardingCard` on the alarm list already sat in exactly that position: shown before the camera
permission is ever requested, and therefore before any recording is possible. We decided that card **is**
UpYet's prominent disclosure, and tightened its copy and its button to meet the requirement, rather than
adding a separate blocking dialog in front of it.

The alternative — a modal shown before the card — would put two consecutive explanations of the same
thing in front of a user who has opened an alarm clock and wants to set an alarm. The card is already
non-dismissible until the permissions are granted, which is the property a disclosure needs; it was short
one explicit statement of when recording happens and one unambiguous affirmative action.

## Consequences

**This card is load-bearing for a Play policy, not decoration.** "It's just onboarding, let me redesign
it" is a natural thought and would break the submission. If it is changed, the replacement must still be
in-app, still appear before the camera permission is requested, still say what is recorded, when it is
recorded, and that nothing leaves the device, and still require a distinct affirmative tap that is not the
system dialog.

Its copy is quoted in `docs/STORE.md` under the camera justification, where a reviewer will read it. The
two must not drift.

Because the card is only shown while a permission is missing, a user who granted camera access through
system settings without ever seeing it is possible in principle. That is acceptable: Play's requirement
is about the app's own request flow, and the app's own flow always shows it first.
