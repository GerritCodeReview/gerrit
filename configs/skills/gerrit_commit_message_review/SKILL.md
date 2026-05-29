---
name: gerrit-commit-message-review
description: Proofreads and suggests structural improvements for Git commit messages to ensure style guide compliance, completeness, and accuracy.
---

# Git Commit Message & Metadata Standards

## Executive Summary

Welcome to the definitive engineering reference for formatting, structuring, and
preserving metadata inside Git commit messages. This document encapsulates the
core conventions for commit hygiene, which are essential to ensuring commit log
histories remain clean, readable, and highly traceable across development
lifecycles.

Adhering to these standards ensures metadata traceability, and provides clear,
long-term context to future developers.

## Summary

| Chapter Theme / Title | Scope & Objective |
| :--- | :--- |
| **Commit Title Conventions** | Defines stylistic and length requirements for the first line of the commit message to optimize history navigation. |
| **Commit Body Structure & Formatting** | Outlines instructions to clearly explain the "what" and "why" of the patchset, with precise line-wrapping mandates. |
| **Metadata Footers & Preservations** | Enforces the strict preservation of system-critical integration footers (such as Change-Id and issue tracking IDs). |

--------------------------------------------------------------------------------

## Chapter: Commit Title Conventions

**Context:** The title line of a Git commit message is the first line of visual feedback for engineers navigating repository logs. To ensure standard sizing, clarity, and readability, title structures are subject to rigid constraints.

### Summary

| Rule ID | Principle / Constraint | Priority | Primary Symptom / Trap |
| :--- | :--- | :--- | :--- |
| **T1-01** | Concise & Imperative Commit Titles | High | Writing titles exceeding 60 characters, or utilizing past-tense/progressive verbs (e.g. "Fixed...", "Fixing..."). |

### Rules

#### T1-01: Concise & Imperative Commit Titles

> **Rule:** Commit titles must be 60 characters or less, start with an imperative verb (e.g., "Add", "Fix", "Update", "Remove"), and use sentence case without trailing punctuation.
>
> **What:** The commit title line must be a concise, imperative sentence summary strictly 60 characters or less.
>
> **Applies To:** Git commit message first line.
>
> **Why:** The codebase's core validation rules programmatically block and flag commits with subjects exceeding 60 characters. Keeping the title under this strict limit avoids repository presubmit upload blockages and ensures neat display in CLI tools.

**Trap 1: Writing passive, overly long, or descriptive titles using progressive or past tense.**

**Don't:**
```text
Fixing the loading spinner bug in gr-reply-dialog.ts and adding tests
```

**Do:**
```text
Fix loading spinner and add test coverage
```

--------------------------------------------------------------------------------

## Chapter: Commit Body Structure & Formatting

**Context:** The body of a commit is a vital repository asset storing the architectural intent behind a change. It must provide context, explain engineering decisions, and be wrapped strictly for terminal compatibility.

### Summary

| Rule ID | Principle / Constraint | Priority | Primary Symptom / Trap |
| :--- | :--- | :--- | :--- |
| **T2-01** | Explaining the Context: What and Why | High | Omitting commit bodies entirely, repeating the title, describing "how" instead of "why", or leaving critical design/bug links without context. |
| **T2-02** | Strict Line Wrapping at 72 Characters | High | Writing continuous, single-line paragraphs that span past 72 characters, causing awkward wrapping in console windows. |

--------------------------------------------------------------------------------

### Rules

#### T2-01: Explaining the Context: What and Why

> **Rule:** The commit message body must clearly explain *what* changes were made and *why* they were necessary, focusing on context and architectural intent. For complex, security-sensitive, or high-risk changes, the explanation should explicitly ground the "why" by referencing the relevant issue, bug tracking ID, or design/RFC document.
>
> **What:** Explanations in the body must detail the problem and the rationale for the solution, leaving the mechanical "how" to be read from the code diff. If the commit relates to a complex problem or implements an approved design specification, the body should draw from and cite these linked resources to clarify the reasoning.
>
> **Applies To:** Commit message lines following the spacer blank line (line 3 and onward).
>
> **Why:** Obvious code listings are redundant. Context is key: for complex or sensitive engineering changes, subsequent maintainers must understand the origin of a requirement or design constraint (e.g., a specific bug, CVE, or design specification) without having to guess, establishing clear auditability.

**Trap 1: Repeating the title word-for-word, leaving the body empty, or describing mechanical changes without context.**

**Don't:**
```text
Fix loading spinner and add test coverage

This change fixes the loading spinner and adds test coverage to gr-reply-dialog.ts.
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

**Trap 2: Omitting context from linked bugs or design documents in complex, critical, or security-sensitive changes.**

While simple or minor bug fixes do not need to explicitly reference their issue IDs inside the body, major, high-risk, or architectural modifications that reference external specs, RFCs, or bug tracker tickets should integrate that context into the "why" explanation. Failing to do so makes the commit message look disconnected from its metadata, making review and auditability difficult.

**Don't:**
```text
Enhance project deletion permission validation

Only users with administrative privileges are allowed to delete a
project, but the server was previously checking for owner status only.
This change corrects the check to require system administrator scope.

Bug: gerrit:40012901
```
*(Problem: A security-sensitive permission model change is being made under a bug, but the body only explains the mechanical change. It completely misses the security context—like the permission bypass mentioned in the bug report—making the reasoning for this risk-heavy change unclear without looking up the bug.)*

**Do:**
```text
Enhance project deletion permission validation

To resolve the permission bypass reported in gerrit:40012901, where
project owners could bypass global security policies to delete resource
containers, we must restrict deletion calls to administrators.

As defined in the project deletion security spec (https://example.com/gerrit-delete-spec),
only system-level administrators should have the capability to
destroy project repositories in production environments.

Bug: gerrit:40012901
```
*(Rationale: Since this is a high-risk change (a security bypass), the body explicitly connects to the bug report gerrit:40012901 and links the authoritative security spec (e.g., https://example.com/gerrit-delete-spec). This establishes bulletproof reasoning and traceability for a critical modification.)*

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

## Chapter: Metadata Footers & Preservations

**Context:** Dynamic metadata footers serve as vital integration links connecting code changes to issue tracking systems, code review platforms, and automated release auditing pipelines.

### Summary

| Rule ID | Principle / Constraint | Priority | Primary Symptom / Trap |
| :--- | :--- | :--- | :--- |
| **T3-01** | Mandatory Integration Footer Preservation | Critical | Modifying, corrupting, or dropping structured footers (Change-Id, Bug, or issue tracking keys) during edits. |

--------------------------------------------------------------------------------

### Rules

#### T3-01: Mandatory Integration Footer Preservation

> **Rule:** Always preserve all structured git metadata footers at the bottom of the commit message, matching the appropriate tracker style based on environment context.
>
> **What:** Do not modify, corrupt, or drop system-critical footers such as Change-Id, Bug, or tracker reference keys during edits.
>
> **Applies To:** Commit message footer block at the bottom.
>
> **Why:** Code review platforms (such as Gerrit) track revisions strictly using the `Change-Id` footer. Deleting it detaches revision history or breaks integration webhooks. Similarly, issue tracking systems rely on matching keys (e.g., `Bug: <ID>`, `Closes #<ID>`) to link code commits with project tickets.

**Trap 1: Amending or rewriting the commit message and dropping the original metadata footers.**

**Don't:**
```text
Update system cache configs

Refactored memory size and cache duration parameters.
```

**Do (Standard issue tracker format):**
```text
Update system cache configs

Refactored memory size and cache duration parameters.

Bug: Issue 12345
Release-Notes: skip
Change-Id: Iab12cd34ef56gh78ij90kl12mn34op56qr78st90
```

**Do (GitHub/GitLab tracker format):**
```text
Update system cache configs

Refactored memory size and cache duration parameters.

Closes #1234567
Release-Notes: skip
Change-Id: Iab12cd34ef56gh78ij90kl12mn34op56qr78st90
```
