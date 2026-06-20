---
title: Add 60-second kaios tour transcript
date: 2026-06-19
type: feat
target_repo: morning-verlu/KAI
origin: https://github.com/morning-verlu/KAI/issues/19
status: draft
---

# Add 60-second kaios tour transcript

## Summary

Add a beginner-friendly transcript section to `docs/LOCAL_TOUR.md` showing the key output moments of `kaios tour` so a first-time visitor can understand the Evidence OS loop before installing anything. The section surfaces the product mapping the issue requests (`Agent = Process`, `Tool = Syscall`, `Run = Evidence`, `CI = Gate`) and links to the artifacts the tour produces. Issue #19 ([source](https://github.com/morning-verlu/KAI/issues/19)) is labeled "good first issue" and is docs-only.

## Problem Frame

A first-time visitor lands on the KAI OS docs, sees the `kaios tour` command mentioned in `START_HERE.md` and `docs/LOCAL_TOUR.md`, but has no preview of what the tour actually prints. Without a transcript, the value claim of "Evidence OS" stays abstract until the visitor commits to a Gradle install. The `kaios tour` command itself already prints a deterministic text report that includes the exact "product proof" mapping the issue asks for, so the gap is doc-only: copy the captured text into a beginner-friendly annotated block in the tour doc.

## Requirements

Sourced from issue #19 (verbatim where possible):

- **R1** Add a compact section to `docs/LOCAL_TOUR.md` (the issue says "or `START_HERE.md`" — `LOCAL_TOUR.md` is the natural home; see KTD1).
- **R2** Show key output moments from `kaios tour`, not the full terminal log.
- **R3** Include the product mapping: `Agent = Process`, `Tool = Syscall`, `Run = Evidence`, `CI = Gate`.
- **R4** Link to the generated artifacts a user should inspect after the tour.
- **R5** Pass the verification commands from the issue: `git diff --check` and `build/install/kaios-cli/bin/kaios tour --dir /tmp/kaios-tour-check`.

## Key Technical Decisions

- **KTD1: Place the transcript in `docs/LOCAL_TOUR.md`, not `START_HERE.md`.** The doc's title is "Local Tour" — that is where a tour transcript belongs. `START_HERE.md` is a signpost and already carries a different 4-line OS metaphor block (`Agent = Process`, `Workflow = Scheduler`, `Tool = Syscall`, `Memory = Process state`); adding a tour transcript there would bloat the entry point. If implementation reveals a natural one-line cross-link from `START_HERE.md` to the new section, that is fine — but it is not required.
- **KTD2: Source the transcript text by running the verification command from the issue.** The `kaios tour` command source in `kaios-cli/src/main/kotlin/ai/kaios/cli/Main.kt` (the `renderTourText` function, near line 956) already prints the exact "product proof" mapping the issue requests. Running `build/install/kaios-cli/bin/kaios tour --dir /tmp/kaios-tour-check` (per the issue's verification) yields the canonical text. The transcript section in the doc should match that output verbatim for the parts that are stable across runs; call out run-id-specific lines as examples.
- **KTD3: Annotate, do not dump.** The issue explicitly says "show key output moments, not the full terminal log." Use a short callout block per output moment (1-2 sentences of "what this is" / "why it matters") rather than a raw terminal block with no commentary.

## Scope Boundaries

### In scope

- A new "60-Second Tour Transcript" section in `docs/LOCAL_TOUR.md`.
- Annotated excerpts from `kaios tour` output covering: header + status, the "what happened:" block, the "product proof:" mapping, the "artifacts:" pointer block, and the "next:" follow-up commands.
- A short preamble explaining who this is for and what they should look for.

### Out of scope

- Modifying `START_HERE.md`'s existing OS-metaphor block.
- Modifying the `kaios tour` command's own output.
- Adding screenshots, GIFs, or video embeds.
- Rewriting the rest of `docs/LOCAL_TOUR.md` (the "What it shows" command list, the temp-directory path block, the env-var redirect paragraph all stay as-is).

### Deferred to follow-up work

- A short cross-link from `START_HERE.md` to the new transcript section, if review feedback shows the section is hard to find.
- A side-by-side comparison of the tour's deterministic mock-provider output vs. a real-provider run, if the maintainer later wants to demonstrate provider portability.
- Embedded evidence-viewer iframe for the generated capsule, if `docs/evidence-viewer.html` becomes the preferred "no-install" preview surface.

## Implementation Units

### U1. Add the 60-Second Tour Transcript section to `docs/LOCAL_TOUR.md`

- **Goal:** Insert a beginner-friendly transcript block in the tour doc that shows the key output moments of `kaios tour`, the product mapping the issue requests, and links to the generated artifacts.
- **Requirements:** R1, R2, R3, R4.
- **Dependencies:** None (pure docs change).
- **Files:**
  - Modify: `docs/LOCAL_TOUR.md`
- **Approach:** Add the new section near the top of the doc, right after the opening paragraph and before the "Use the source-tree script when developing KAI OS itself" block (which is for a different audience — KAI OS contributors running the script against an existing project). Use this shape for the new section:
  - A 1-2 sentence intro explaining who this is for (a visitor who has not installed yet) and what they will see (the key output moments, not a full log).
  - A labelled code block of the `kaios tour` text output, split into 3-4 short callouts: (1) the header + status line, (2) the "what happened:" block, (3) the "product proof:" mapping, (4) the "artifacts:" and "next:" pointers. Each callout gets a one-line annotation of what a first-time reader should notice.
  - A small "Inspect these artifacts" pointer list at the end, linking to the artifact paths the tour prints (`review`, `trace`, `capsule`) and noting that they live under the `--dir` location the user specified.
- **Patterns to follow:** The PR #27 walkthrough for `examples/baseline-gate/README.md` (issue #26) used a similar shape — short labelled code block + one-line annotation + pointer list. The new section should feel like a sibling of that work, not a new style.
- **Test scenarios:**
  - **Happy path:** After the change, `docs/LOCAL_TOUR.md` contains a heading "60-Second Tour Transcript" (or equivalent), followed by an intro paragraph, a code block reproducing the `kaios tour` text output, and an artifact-pointer list. Running `git diff --check` on the change produces no warnings (R5).
  - **Mapping accuracy:** The product mapping line in the new section matches the `kaios tour` output's "product proof" block exactly: `Agent = Process`, `Tool = Syscall`, `Run = Evidence`, `CI = Gate` (R3).
  - **Cross-platform path handling:** The new section uses the artifact paths as `kaios tour` prints them (absolute or workspace-relative depending on `--dir`), and does not hardcode `/tmp/...` paths in a way that breaks Windows readers.
  - **No invented content:** Every line of transcript text in the new section is something `kaios tour` actually prints (per the `renderTourText` function in `kaios-cli/src/main/kotlin/ai/kaios/cli/Main.kt`, which is the canonical source). Run-id and absolute-path values in the example output are shown as `<run-id>`, `<workspace>`, etc. — placeholders, not real values.
- **Verification:** `git diff --check` exits 0; a quick visual diff of the new section against a fresh `kaios tour --dir /tmp/kaios-tour-check` run shows the captured text matches the tour's actual output (R5).

### U2. Run the verification commands from issue #19

- **Goal:** Confirm the new section compiles into a clean diff and that `kaios tour` produces the output the section transcribes.
- **Requirements:** R5.
- **Dependencies:** U1.
- **Files:** None modified by this unit — verification only.
- **Approach:** From the KAI repo root, run:
  - `git diff --check` — must exit 0.
  - `./gradlew installDist` — must produce `build/install/kaios-cli/bin/kaios`.
  - `build/install/kaios-cli/bin/kaios tour --dir /tmp/kaios-tour-check` — must exit 0 and produce the same shape as the section captures.
  Compare the captured text in the new section against the live `kaios tour` output; if anything drifted (a label, a section name, a mapping line), update U1 to match before opening the PR.
- **Test scenarios:**
  - `git diff --check` exits 0 on the U1 change.
  - `./gradlew installDist` succeeds (or is already cached from a prior run) and `build/install/kaios-cli/bin/kaios` exists.
  - `kaios tour --dir /tmp/kaios-tour-check` exits 0 and prints a block whose first non-empty line is `KAI OS Evidence OS tour` and whose "product proof:" block contains all four mapping lines (`Agent = Process`, `Tool = Syscall`, `Run = Evidence`, `CI = Gate`).
  - After the run, `/tmp/kaios-tour-check/workspace/artifacts/` contains the three artifact files the section points to (`change-review.md`, `change-review.trace.json`, `change-review.capsule.json`) so the section's "Inspect these artifacts" pointer list is testable end-to-end.
- **Verification:** All three commands above exit 0; the diff between live output and section text is empty (modulo run-id / path placeholders).

## Risks & Dependencies

- **Risk: Tour output drifts between CLI versions.** If `kaios tour` is updated after this PR lands (e.g., a new section is added or a label is renamed), the transcript in `docs/LOCAL_TOUR.md` will go stale silently. Mitigation: keep the change small enough that a docs review can spot drift; if a future tour change is on the roadmap, add a smoke-test assertion that checks the doc still matches the live output.
- **Risk: Absolute paths in the example output are misleading on Windows.** The `kaios tour` text output prints absolute paths under `/tmp/...` (since the tour uses `mktemp -d` under `TMPDIR`, which defaults to `/tmp` on Linux/macOS). On Windows, the same `kaios tour` invocation will print a different path. The transcript section should use placeholders (`<workspace>`, `<run-id>`) and let readers run the command themselves to see the actual paths.
- **Dependency: Java 17+ and Gradle wrapper.** The verification step requires `./gradlew installDist`. If a contributor is on a machine without Java 17, they can still verify the doc by visual review and `git diff --check`, but they cannot confirm the captured text matches the live output. The `CONTRIBUTING.md` "Local Setup" section already calls out the Java 17 requirement.

## Documentation Impact

This change adds one section to one file (`docs/LOCAL_TOUR.md`). No other docs need updating. If a cross-link from `START_HERE.md` is added in a follow-up (per the deferred list), that is a separate one-line edit.

## Sources & Research

- Issue #19: <https://github.com/morning-verlu/KAI/issues/19> (no PR references it at planning time, unlike sibling issue #26 which has open PR #27).
- `kaios tour` command source: `kaios-cli/src/main/kotlin/ai/kaios/cli/Main.kt` — `renderTourText` (near line 956) is the canonical source for the "product proof" mapping the issue asks for; the `TourReport` data class (near line 6737) is the source of truth for the JSON-shape fields if anyone later wants to update the transcript from `--json` output instead of the text renderer.
- Sibling doc pattern: PR #27 (open at planning time) for issue #26 in `examples/baseline-gate/README.md` — same "short labelled section + one-line annotation + pointer list" shape.
- `CONTRIBUTING.md` "Good First Areas" line 63 explicitly lists "`kaios tour` examples that help new users understand KAI OS in under one minute" as a target area — issue #19 is on-strategy.
