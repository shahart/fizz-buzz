
## Cross-platform changes

For every product or behavior change, check both the Android and web clients. Implement the change in both when the behavior applies to both platforms, and add or update coverage for both. If a change is platform-specific or cannot reasonably be mirrored, document why and preserve consistent shared game rules and user-facing behavior where applicable.

## Tests and verification

- For every behavior change, add or update tests that cover the changed behavior, including important edge cases and failure states. Prefer tests of observable behavior over tests tied to implementation details.
- Keep shared game rules consistent across the Worker, Android, and web clients. When a change affects shared behavior, update tests for each affected layer.
- Run the checks relevant to the files changed before finishing. For cross-platform behavior changes, run both Android and web checks. For Worker changes, run the Worker tests and type check.
- If a relevant check cannot be run, state which check was skipped and why. Report a check as passing only if it completed successfully.
- Do not weaken, delete, or skip existing tests just to make a change pass. When intended behavior changes, update the affected assertions to match it.
- Keep tests deterministic. Prefer local fakes and controlled clocks over real network calls, deployed services, wall-clock timing, or shared external state.
- Add browser-level coverage when a user-visible flow depends on browser behavior that unit tests cannot meaningfully cover.

## Implementation practices

- Follow the existing architecture and patterns in the affected module. Keep shared game rules in the appropriate shared or authoritative layer; avoid duplicating logic across clients.
- Avoid adding dependencies or parallel abstractions unless they solve a clear need that existing code cannot address.
- Keep changes focused on the requested behavior. Avoid unrelated refactors and do not commit generated or build artifacts unless the task requires them.
- Handle network and WebSocket failures so users receive clear feedback and can recover or retry where appropriate.
- Keep interactive controls accessible, including meaningful labels and usable keyboard behavior on the web, and route user-facing text through the existing translation system.

## Documentation and review

- Update the README or relevant documentation when setup steps, commands, configuration, or user-facing behavior change.
- Before finishing, review the diff for accidental changes, exposed secrets, and generated or build artifacts that should not be included.
