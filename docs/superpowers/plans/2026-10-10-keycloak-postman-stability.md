# Keycloak and Postman Stability Implementation Plan

> **For agentic workers:** Execute inline in this session; the user has authorized these repairs. Follow superpowers:executing-plans and verification-before-completion.

**Goal:** Make the implemented Keycloak APIs usable from the existing FlashTicket Postman collection and make missing prerequisites visible.

**Architecture:** Keep the six existing identity-specific environments and native OAuth PKCE. Validate locally before sending, retain safe write controls, and distinguish unimplemented service templates from runnable Keycloak APIs. Update the running realm through Admin REST without deleting data, then update its import JSON.

**Tech Stack:** Keycloak 26.8, Postman CLI/MCP, Docker, Python diagnostic harness.

**Execution ruling:** Remove the write/email/introspection opt-in flags instead of retaining them: the user has authorized these API operations, and explicit manual Send plus required token/target validation is sufficient. Keep the S2S opt-in because these endpoints do not exist. No database reset or real-user lifecycle tests.

**Spec:** User request in this chat: fix env + Keycloak for stable API use; use real Gmail credentials from deploy/.env or deploy/.env.dev.

## Global Constraints

- Preserve unrelated edits, existing users, secrets, collection IDs and request authorization.
- Do not print passwords or tokens, enable password grant, or claim unimplemented S2S endpoints work.
- Never send a request with missing prerequisites or a mismatched identity profile.
- Use Gmail rather than a local mail substitute.

## Review Focus

- Missing/wrong environment must produce a visible diagnostic and send nothing.
- Missing search input must retain the previous target; an unsuccessful completed lookup must clear it.
- Failed token acquisition must clear stale tokens; a successful OAuth UserInfo must save the matching token.
- Introspection must use a fresh token belonging to the authenticated client and an appropriate audience.
- SMTP authentication failure must be reported without exposing credentials; temporary test users must be removed.

### Task 1: Visible prerequisite validation and environments

- [x] Probe Postman CLI behavior for thrown errors, failed pre-request assertions and skipRequest, counting actual requests received.
- [x] Update collection guards and token-saving scripts; remove unnecessary opt-in flags for introspection/email while retaining explicit write control and unavailable S2S markers.
- [x] Update six local/example environments and cloud environments without replacing masked secrets.
- [x] Verify rejected requests send nothing and report the exact missing condition; verify successful service token acquisition saves accessToken.

### Task 2: Keycloak runtime and durable configuration

- [x] Authenticate master Admin REST using existing Docker credentials; compare Gmail settings from both local dotenv files without printing secrets.
- [x] Test Gmail authentication; apply valid SMTP settings to the live realm and durable import configuration.
- [x] Add only the matching client audience to each machine token needed for own-token introspection; retain issuer validation and DPoP settings.
- [x] Verify fresh token/introspection for each service and error behavior with bad credentials.

### Task 3: End-to-end verification and export

- [x] Read back cloud collection after update; verify all request bodies, auth and scripts match the local export.
- [x] Run Keycloak discovery, OAuth UserInfo, Admin read/write lifecycle and email requests using isolated temporary users; verify cleanup.
- [x] Export refreshed JSON/env files, update README and a sanitized verification report, and report any external or unimplemented blockers honestly.

## Verification ledger

- 31 unique Keycloak requests executed through Postman CLI (32 HTTP requests including a second List Users after service-account token acquisition); 60 assertions passed after container recreation.
- 12 negative guard cases: all expected validation failures were visible; 0 blocked HTTP requests reached the probe server. Existing target IDs preserved for incomplete search/create input.
- Final review found Update User clearing an unspecified email; reproduced with a local HTTP receiver, fixed by omitting email when blank, and regression passed. Stale Create/introspection descriptions were corrected.
- Cloud readback matches all 50 request scripts, bodies, auth, descriptions and folder guards. Six environments synced; 106 scripts parse.
- SMTP auth succeeded; both email endpoints returned 204 in the end-to-end run. Temporary users were deleted and absence verified.
- Existing runtime bootstrap credentials were persisted into ignored dotenv files because the previous dotenv credentials could not authenticate; Compose no longer depends on a missing temporary override. No existing-user passwords were reset.
- Remaining limit: 19 S2S templates have no implemented service endpoints.
