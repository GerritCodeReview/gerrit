---
name: gerrit-commit-message-review
description: Proofreads and suggests structural improvements for Git commit messages to ensure style guide compliance.
---

# Git Commit Message & Metadata Standards

## Executive Summary

Welcome to the definitive engineering reference for formatting, structuring, and preserving metadata inside Git commit messages for the Gerrit platform. This document encapsulates the core conventions for commit hygiene, which are essential to ensuring our commit log histories remain clean and readable, and that our automated CI/CD and release-note generation pipelines operate without failure.

Adhering to these standards prevents submit blockages, ensures metadata traceability, and provides clear, long-term context to future developers.

## Summary

| Chapter Theme / Title | Scope & Objective |
| :--- | :--- |
| **Commit Title Conventions** | Defines stylistic and length requirements for the first line of the commit message to optimize history navigation. |
| **Commit Body Structure & Formatting** | Outlines instructions to clearly explain the "what" and "why" of the patchset, with precise line-wrapping mandates. |
| **Metadata Footers & Preservations** | Enforces the strict preservation of system-critical footers such as Change-Id, Bug trackers, and Release-Notes flags. |

--------------------------------------------------------------------------------
--------------------------------------------------------------------------------

## Chapter: Commit Title Conventions

**Context:** The title line of a Git commit message is the first line of visual feedback for engineers navigating repository logs. To ensure standard sizing, clarity, and readability, title structures are subject to rigid constraints.

### Summary

| Rule ID | Principle / Constraint | Priority | Primary Symptom / Trap |
| :--- | :--- | :--- | :--- |
| **T1-01** | Concise & Imperative Commit Titles | High | Writing titles exceeding 50 characters, or utilizing past-tense/progressive verbs (e.g. "Fixed...", "Fixing..."). |

--------------------------------------------------------------------------------

### Rules

#### T1-01: Concise & Imperative Commit Titles

> **Rule:** Commit titles must be 50 characters or less, start with an imperative verb (e.g., "Add", "Fix", "Update", "Remove"), and use sentence case without trailing punctuation.
>
> **What:** The commit title line must be a concise, imperative sentence summary strictly 50 characters or less.
>
> **Applies To:** Git commit message first line.
>
> **Why:** The codebase's core validation rules programmatically block and flag commits with subjects exceeding 50 characters. Keeping the title under this strict limit avoids repository presubmit upload blockages and ensures neat display in CLI tools.

**Trap 1: Writing passive, overly long, or descriptive titles using progressive or past tense.**

**Don't:**
```text
Fixing the loading spinner bug in gr-reply-dialog.ts and adding tests (over 50 chars)
```

**Do:**
```text
Fix loading spinner and add test coverage (under 50 chars)
```

--------------------------------------------------------------------------------
--------------------------------------------------------------------------------

## Chapter: Commit Body Structure & Formatting

**Context:** The body of a commit is a vital repository asset storing the architectural intent behind a change. It must provide context, explain engineering decisions, and be wrapped strictly for terminal compatibility.

### Summary

| Rule ID | Principle / Constraint | Priority | Primary Symptom / Trap |
| :--- | :--- | :--- | :--- |
| **T2-01** | Explaining the Context: What and Why | High | Omitting commit bodies entirely, repeating the title verbatim, or describing "how" code changed instead of "why". |
| **T2-02** | Strict Line Wrapping at 72 Characters | High | Writing continuous, single-line paragraphs that span past 72 characters, causing awkward wrapping in console windows. |

--------------------------------------------------------------------------------

### Rules

#### T2-01: Explaining the Context: What and Why

> **Rule:** The commit message body must clearly explain *what* changes were made and *why* they were necessary, focusing on context and architectural intent.
>
> **What:** Explanations in the body must detail the problem and the rationale for the solution, leaving the mechanical "how" to be read from the code diff.
>
> **Applies To:** Commit message lines following the spacer blank line (line 3 and onward).
>
> **Why:** Obvious code listings are redundant; understanding "why" a change was designed a certain way is critical to long-term software lifecycle hygiene.

**Trap 1: Repeating the title word-for-word, leaving the body empty, or describing mechanical changes without context.**

**Don't:**
```text
Fix loading spinner and add test coverage

This change fixes the loading spinner and adds a test coverage to gr-reply-dialog.ts.
```

**Do:**
```text
Fix loading spinner and add test coverage

The loading spinner in gr-reply-dialog was experiencing visual jitter
on rapid page transitions due to a race condition in the reactive
lifecycle hook.

This change moves property assignments out of firstUpdated to avoid
unnecessary second-pass rendering, stabilizing the visual state.
```

--------------------------------------------------------------------------------

#### T2-02: Strict Line Wrapping at 72 Characters

> **Rule:** Wrap the body of all commit messages strictly at 72 characters per line, except for unwrappable URLs, file paths, or commands.
>
> **What:** Lines in the commit body must have explicit carriage returns at or before 72 columns.
>
> **Applies To:** Git commit message bodies.
>
> **Why:** Terminal output screens wrap at standard columns. Explicitly wrapping to 72 characters ensures clean reading in simple text editors, CLI viewers, and patch viewers.

**Trap 1: Appending full paragraphs without manual word wrapping.**

**Don't:**
```text
This change refactors the core caching helper and resolves a race condition that occurs when the same component gets disconnected rapidly from the DOM during teardown, which historically resulted in an uncaught exception.
```

**Do:**
```text
This change refactors the core caching helper and resolves a race
condition that occurs when the same component gets disconnected
rapidly from the DOM during teardown, which historically resulted
in an uncaught exception.
```

--------------------------------------------------------------------------------
--------------------------------------------------------------------------------

## Chapter: Metadata Footers & Preservations

**Context:** Dynamic metadata footers serve as vital integration links connecting Gerrit changes to ticket tracking systems, release notes builders, and security compliance verification tools.

### Summary

| Rule ID | Principle / Constraint | Priority | Primary Symptom / Trap |
| :--- | :--- | :--- | :--- |
| **T3-01** | Mandatory Integration Footer Preservation | Critical | Modifying, corrupting, or dropping structured footers (Change-Id, Bug, google-bug-id) during edits. |
| **T3-02** | Structured Release-Notes Footers | High | Omitting the Release-Notes footer entirely on core edits, or spreading descriptions across multiple paragraph blocks. |

--------------------------------------------------------------------------------

### Rules

#### T3-01: Mandatory Integration Footer Preservation

> **Rule:** Always preserve all structured git metadata footers at the bottom of the commit message, matching the appropriate tracker style based on environment context.
>
> **What:** Do not modify, corrupt, or drop system-critical footers such as Change-Id, Bug, google-bug-id, or BUG during edits.
>
> **Applies To:** Commit message footer block at the bottom.
>
> **Why:** Gerrit tracks revisions strictly using the `Change-Id` footer. Deleting it forces a new change or breaks integration hooks. Similarly, issue trackers rely on matching keys: OS developers require upstream `Bug: Issue <#>` metadata, whereas internal pipelines read `Google-Bug-Id: b/<#>` or `BUG=<#>`.

**Trap 1: Suggesting a new commit message body that neglects to append the existing Change-Id footer.**

**Don't:**
```text
Update system cache configs

Refactored memory size and cache duration parameters.
```

**Do (OS Upstream context):**
```text
Update system cache configs

Refactored memory size and cache duration parameters.

Bug: Issue 12345
Change-Id: Iab12cd34ef56gh78ij90kl12mn34op56qr78st90
```

**Do (Internal corporate sync context):**
```text
Update system cache configs

Refactored memory size and cache duration parameters.

Google-Bug-Id: b/1234567
Change-Id: Iab12cd34ef56gh78ij90kl12mn34op56qr78st90
```

--------------------------------------------------------------------------------

#### T3-02: Structured Release-Notes Footers

> **Rule:** Always append a properly formatted, single-line `Release-Notes:` footer to commits. Use `Release-Notes: skip` for minor bug fixes or formatting updates.
>
> **What:** User-facing commits must declare their release note content using a single-line block.
>
> **Applies To:** Git commit message footers.
>
> **Why:** Downstream changelog generation automation requires this exact format to parse and export accurate deployment releases. Standard submit requirements check for the presence of the tag (e.g., search operator `hasfooter:"Release-Notes"` is verified by test suites).

**Trap 1: Formatting the release notes across a multi-line body paragraph, or omitting the footer entirely.**

**Don't:**
```text
We have added a cache config so that from now on
we can bypass external lookups during local builds.
```

**Do:**
```text
Release-Notes: Add local cache configuration bypass option.
```
