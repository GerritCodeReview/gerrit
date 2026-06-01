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
| **Commit Body Structure & Formatting** | Outlines instructions to clearly explain the "what" and "why" of the patchset with pragmatic conciseness and precise wrapping. |
| **Metadata Footers & Preservations** | Enforces the strict preservation of system-critical integration footers (such as Change-Id and issue tracking IDs). |
| **Review Feedback & Suggested Commit Message** | Instructs the reviewer to provide a complete, fully-compliant, copy-pasteable revised commit message. |


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

**Context:** The body of a commit is a vital repository asset storing the architectural intent behind a change. It must explain engineering decisions with pragmatic conciseness, provide targeted context, and be wrapped strictly for terminal compatibility.

### Summary

| Rule ID | Principle / Constraint | Priority | Primary Symptom / Trap |
| :--- | :--- | :--- | :--- |
| **T2-01** | Explaining the Context: What and Why | High | Omitting commit bodies entirely, repeating the title, describing "how" instead of "why", or leaving critical design/bug links without context. |
| **T2-02** | Strict Line Wrapping at 72 Characters | High | Writing continuous, single-line paragraphs that span past 72 characters, causing awkward wrapping in console windows. |
| **T2-03** | Pragmatic Tone, Conciseness, and Anti-Filler | High | Writing verbose, flowery prose, introducing generic engineering philosophy/boilerplate, or using redundant Q&A layouts on simple changes. |

--------------------------------------------------------------------------------

### Rules

#### T2-01: Explaining the Context: What and Why

> **Rule:** The commit message body must clearly explain *what* changes were made and *why* they were necessary, focusing on context and architectural intent, while maintaining a concise, non-redundant, and pragmatic tone. The body must jump directly into the technical context or problem and must never repeat, rephrase, or start with a high-level introductory summary of the title. For complex, security-sensitive, or high-risk changes, the explanation should explicitly ground the "why" by referencing the relevant issue, bug tracking ID, or design/RFC document. Do not include empty, low-value, or vacuous bug references (e.g. 'To address b/XXXXX, ...') in the body text unless accompanied by descriptive context explaining what the bug or issue represents. If the specific bug context is not known or cannot be verified, the bug reference must be omitted from the body paragraphs entirely, relying solely on the metadata footer for tracking.
>
> **What:** Explanations in the body must detail the problem and the rationale for the solution, leaving the mechanical "how" to be read from the code diff, and omitting generic value propositions of development practices, non-technical fluff, or introductory meta-sentences. The opening paragraph of the body must begin directly with the context or problem being solved and not restate or paraphrase the commit title. If the commit relates to a complex problem or implements an approved design specification, the body should draw from and cite these linked resources to clarify the reasoning in a direct, straightforward manner. If citing a bug tracker ID in the body paragraphs, ensure it adds concrete value by describing what problem or feedback is tracked there; otherwise, leave the bug reference out of the narrative body and let the footer handle the link.
>
> **Applies To:** Commit message lines following the spacer blank line (line 3 and onward).
>
> **Why:** Obvious code listings are redundant. Context is key: for complex or sensitive engineering changes, subsequent maintainers must understand the origin of a requirement or design constraint (e.g., a specific bug, CVE, or design specification) without having to guess, establishing clear auditability.

**Trap 1: Repeating or rephrasing the title, starting the body with a high-level introductory summary sentence, or omitting concrete context.**

The commit title is already the high-level summary of the change. Starting the body of the commit message with a rephrasing, restatement, or introductory "thesis statement" (e.g. "Add the agent and define its corresponding guidelines") is highly redundant and wastes reader time. Do not write introductory meta-sentences; begin the body paragraphs by jumping directly into the technical context or the problem being solved.

**Don't:**
```text
Fix loading spinner and add test coverage

This change fixes the loading spinner and adds test coverage to gr-reply-dialog.ts.
```
*(Problem: Repeats the title almost verbatim in the body opening.)*

**Do:**
```text
Fix loading spinner and add test coverage

The loading spinner in gr-reply-dialog was experiencing visual jitter
on rapid page transitions due to a race condition in the reactive
lifecycle hook.

This change moves property assignments out of firstUpdated to avoid
unnecessary second-pass rendering, stabilizing the visual state.
```
*(Rationale: Jumps immediately into the concrete problem.)*

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

**Trap 3: Adding vacuous or low-value bug/issue references to the commit body.**

Do not introduce generic, filler sentences or phrases (like "To address b/12345...", "In reference to b/12345...", or "As requested in b/12345...") into the narrative body of a commit message unless you are actually adding descriptive, valuable context from that bug or design document. If the bug context is not known, or if you cannot visit/verify the bug contents, **omit** the reference from the body text entirely. The metadata footer at the bottom (e.g., `Bug: b/12345` or `Google-Bug-ID: b/12345`) is the correct place to handle automated tracking; adding a vacuous mention in the body adds zero explanatory value and only increases noise.

**Don't (Vacuous reference in body):**
```text
Suggest full commit message and avoid pedantic reviews

To address b/505405738, this change updates the review guidelines to
require that commit message reviews provide a complete, copy-pasteable
revised message when formatting issues are found. It also instructs the
reviewer to avoid pedantic feedback on minor casing or phrasing if the
original message is already clear and compliant.

Google-Bug-ID: b/505405738
Release-Notes: skip
Change-Id: I75ef56099ea36b8838a65746abb3a4771fcefd23
```
*(Problem: The body paragraph starts with "To address b/505405738, ...", but this phrase does not explain what the bug represents or what context it provides. It is a low-value, mechanical filler phrase.)*

**Do (When bug context is unknown - omit the reference from the body):**
```text
Suggest full commit message and avoid pedantic reviews

Automated commit message reviews can sometimes generate fragmented,
pedantic feedback on minor phrasing nits, creating friction rather than
saving developer time.

To improve usability, SKILL.md now requires reviewers to generate a
complete, copy-pasteable revised commit message when formatting issues
are found, and to suppress feedback on minor nits if the original
commit is fundamentally sound.

Google-Bug-ID: b/505405738
Release-Notes: skip
Change-Id: I75ef56099ea36b8838a65746abb3a4771fcefd23
```
*(Rationale: Since the bug context is not explicitly visited or verified, the vacuous reference is completely removed from the body text, and the body jumps directly into the problem context first rather than starting with a generic "This change..." statement.)*

**Do (When bug context is visited/known - integrate descriptive context):**
```text
Suggest full commit message and avoid pedantic reviews

In b/505405738, developers reported being slowed down by fragmented,
pedantic automated review feedback and requested ready-to-apply
suggestions to reduce manual editing friction.

To address this, this change updates the guidelines to require a
complete, copy-pasteable revised message when formatting issues are
found, and instructs reviewers to tolerate minor casing or phrasing
variations when the original message is already compliant.

Google-Bug-ID: b/505405738
Release-Notes: skip
Change-Id: I75ef56099ea36b8838a65746abb3a4771fcefd23
```
*(Rationale: The bug reference in the body now adds actual value by explaining exactly what issue/feedback was reported in b/505405738, providing important context to future maintainers, while explaining the problem/need before the solution.)*
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

#### T2-03: Pragmatic Tone, Conciseness, and Anti-Filler

> **Rule:** Commit messages must be concise, direct, and free of conversational filler, obvious generalities, marketing/PR speak, or excessive boilerplate structure. Every sentence must serve to communicate technical context.
>
> **What:** Avoid long-winded introductions (e.g. "To ensure standard high-quality..."), platitudes (e.g. "Commit messages are vital repositories of engineering intent..."), and rigid Q&A-style templates (like "What is changing / Why this is necessary" headers) unless the change is highly complex and structurally demands them. Keep explanations simple, direct, and focused on the technical problem and its solution.
>
> **Applies To:** Git commit message bodies.
>
> **Why:** Verbose, flowery, or highly templated commit messages clutter repository logs and increase cognitive load for engineers searching history. A staff engineer's time is valuable; the commit message must deliver maximum signal-to-noise ratio.

**Trap 1: Including generic software engineering justifications or explaining why code review, testing, or good practices are important in general.**
Do not write essays on general design philosophy in the commit message. Stick strictly to the specific change's technical facts.

**Don't:**
```text
Register gerrit-commit-message-review agent and skill

To enforce rigorous commit log hygiene across this repository, this
change registers the new 'gerrit-commit-message-review' AI reviewer
agent and establishes its associated quality guidelines.

Commit messages are permanent repositories of engineering intent, but
manual review is prone to human oversight. Under the feature request
in Issue 505405738, this system automates audit checks to provide
instant, high-fidelity feedback. By deploying a specialized agent that
evaluates formatting style, structural completeness, and footer
integrity, developers receive automated proofreading findings directly
in the Gerrit Checks UI (with ready-to-apply autofixes where
applicable).
```
*(Problem: Highly verbose, generic text explaining general AI reviewer value propositions and repository hygiene, filled with fluff sentences like "Commit messages are permanent repositories...")*

**Do:**
```text
Register gerrit-commit-message-review agent and skill

To automate commit log hygiene audits across this repository, this
change registers the new agent to trigger automatically on COMMIT_MSG
changes. The accompanying skill definition outlines rules for
subject-line format, strict 72-character line wrapping, context and
intent explanation, and strict preservation of integration footers.

Bug: Issue 505405738
```
*(Rationale: High signal-to-noise ratio. Completely avoids vacuous issue references in the body and jumps directly into the technical mechanism, leaving the issue linkage to the metadata footer.)*

**Trap 2: Forcing complex multi-section layouts (like bullet points or Q&A headers) for straightforward, medium-sized, or simple changes.**
Use simple, direct paragraphs instead of lists and structural headings whenever possible. Only use numbered lists when listing a sequence of highly distinct architectural changes.

**Don't:**
```text
Fix loading spinner and add test coverage

What is changing:
1. The loading spinner in gr-reply-dialog.ts is fixed.
2. Property assignments are moved out of firstUpdated to avoid dual rendering.
3. Tests are added.

Why this is necessary:
The loading spinner was experiencing visual jitter on rapid page transitions
due to a race condition in the reactive lifecycle hook, which is bad for UX.
```
*(Problem: Trivial bug fix is forced into a multi-headed structure with redundant wording.)*

**Do:**
```text
Fix loading spinner and add test coverage

The loading spinner in gr-reply-dialog was experiencing visual jitter
on rapid page transitions due to a race condition in the reactive
lifecycle hook.

This change moves property assignments out of firstUpdated to avoid
unnecessary second-pass rendering, stabilizing the visual state.
```
*(Rationale: Short, elegant, two paragraphs of narrative flow. Fully explains the problem and high-level solution without rigid, repetitive structural overhead.)*

**Trap 3: Over-explaining straightforward changes, using sequential narrations ("First", "Second"), or explaining obvious developer-experience benefits.**
Avoid writing multiple paragraphs or sequential lists to explain simple, singular enhancements. Never dedicate sentences to explaining *why* a change is beneficial in general (e.g. explaining that "this saves developer effort" or "improves developer experience"). Ground the explanation purely in the technical delta and keep simple changes within a single, dense paragraph of 2 to 3 sentences max.

**Don't (Too Verbose):**
```text
Suggest full commit message and avoid pedantic reviews

To address b/505405738 and improve the developer experience when
addressing commit message feedback, this change updates the review
guidelines in SKILL.md with a new chapter outlining response standards.

First, if any formatting or hygiene issues are found, the reviewer must
provide a complete, fully-compliant, and copy-pasteable revised commit
message. This saves developer effort and streamlines the edit workflow.

Second, to prevent automated review noise, the reviewer must adopt a
pragmatic approach and tolerate minor casing or subjective phrasing
differences if the message is already clear and compliant.
```
*(Problem: Highly verbose, uses sequential enumeration ("First", "Second"), and explains obvious general benefits like "This saves developer effort and streamlines the edit workflow".)*

**Do (Ultra-Concise & High Density):**
```text
Suggest full commit message and avoid pedantic reviews

Automated commit message reviews can generate fragmented, pedantic
feedback on minor phrasing nits, creating friction rather than saving
developer time. This change updates SKILL.md to require reviewers to
provide a complete, copy-pasteable revised commit message when
violations are found, and to tolerate minor variations if the original
message is fundamentally compliant.

Google-Bug-ID: b/505405738
Release-Notes: skip
```
*(Rationale: Highly concise and high signal-to-noise ratio. Explains the problem/why context first, and avoids any vacuous issue reference prefixes or "This change..." introductory meta-sentences, while utilizing the metadata footer for bug linkage.)*


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
Change-Id: Iab12cd34ef560078009000120034005600780090
```

**Do (GitHub/GitLab tracker format):**
```text
Update system cache configs

Refactored memory size and cache duration parameters.

Closes #1234567
Release-Notes: skip
Change-Id: Iab12cd34ef560078009000120034005600780090
```

--------------------------------------------------------------------------------

## Chapter: Review Feedback & Suggested Commit Message

**Context:** To provide maximum value and minimize friction, the reviewer must not only point out formatting/hygiene violations but also provide a complete, corrected, and fully-compliant commit message that the developer can copy and paste directly into Gerrit's commit message editor.

### Summary

| Rule ID | Principle / Constraint | Priority | Primary Symptom / Trap |
| :--- | :--- | :--- | :--- |
| **T4-01** | Provide a Complete Suggested Commit Message | High | Providing only high-level feedback or listing line-by-line suggestions without providing a single, copy-pasteable revised commit message. |
| **T4-02** | Pragmatic Tolerance & Anti-Noise | High | Suggesting rewrites for minor stylistic differences or trivial casing when the original commit message is already highly informative and readable. |

--------------------------------------------------------------------------------

### Rules

#### T4-01: Provide a Complete Suggested Commit Message

> **Rule:** Whenever any formatting, structure, or hygiene issues are identified in the commit message, the review feedback must include a dedicated section containing the complete, fully-compliant, and improved version of the commit message wrapped inside a markdown code block (e.g., using `text` or `git` syntax highlighting). If the original commit message is already satisfactory, fully compliant, and informative, no revised version or suggestions should be provided.
>
> **What:** The review feedback must provide the complete revised commit message in a single code block as a drop-in replacement ONLY when violations or potential improvements are found. This suggested message must meticulously apply all the guidelines defined in this skill (e.g., title length under 60 chars, imperative verbs, strict 72-character line wrapping in the body, pragmatic and concise explanation). It **must** preserve all existing metadata footers (like `Change-Id`, `Bug`, `Closes`, and `Release-Notes`) exactly as they appeared in the original message. If the commit message is already fully compliant, the reviewer should state that no improvements are necessary and omit the suggestion block.
>
> **Applies To:** Review feedback reports and summary comments on COMMIT_MSG.
>
> **Why:** Developers want to resolve formatting issues as quickly as possible. Providing a complete, copy-pasteable improved message eliminates the need for the developer to manually re-wrap lines or rewrite sentences, significantly improving the developer experience. However, forcing a rewrite when the message is already of high quality causes unnecessary noise and friction.

**Trap 1: Providing feedback comments on individual lines but omitting a single, unified revised commit message.**

**Don't:**
```text
Line 1: The title has 65 characters, which is over the 60-character limit. Please shorten it.
Line 3: This line is 85 characters long. Please wrap it at 72 characters.
```

**Do:**
````text
### Commit Message Review

I found a few formatting issues with your commit message:
1. The title is too long (65 characters).
2. The body paragraphs are not wrapped at 72 characters.

Here is an improved, fully-compliant version of your commit message that you can copy and paste directly into the Gerrit edit dialog:

```text
Fix loading spinner and add test coverage

The loading spinner in gr-reply-dialog was experiencing visual jitter
on rapid page transitions due to a race condition in the reactive
lifecycle hook.

This change moves property assignments out of firstUpdated to avoid
unnecessary second-pass rendering, stabilizing the visual state.

Bug: Issue 12345
Release-Notes: skip
Change-Id: Iab12cd34ef560078009000120034005600780090
```
````
*(Rationale: Provides a ready-to-use solution that saves developer effort.)*

--------------------------------------------------------------------------------

#### T4-02: Pragmatic Tolerance & Anti-Noise

> **Rule:** The reviewer must adopt a pragmatic, non-pedantic approach to evaluating commit messages. Do NOT suggest rewrites for minor stylistic differences, subjective phrasing preferences, or trivial casing choices if the original message is already clear, informative, correctly wrapped, and compliant with core limits.
>
> **What:** Apply a high threshold of value before flagging a commit message or suggesting an alternative. Trivial stylistic points (such as starting a component prefix colon with a lowercase verb, e.g., `GrepServlet: add ...` vs `GrepServlet: Add ...`, or slightly differing sentence structures that express the same context) are considered acceptable. The reviewer must **never** post comments or generate a suggestion block for these minor variations. Suggest revisions **only** when there are clear, objective violations (e.g., title > 60 chars, body lines > 72 chars, missing essential context, or corrupted/missing metadata).
>
> **Applies To:** All review feedback reports and comments.
>
> **Why:** Superfluous or nitpicky reviews (often referred to as "pedantic noise") irritate authors, waste review cycles, and erode trust in automated tooling. AI reviews must focus strictly on high-value correctness, safety, and critical readability standards.

**Trap 1: Flagging a well-written, informative, and correctly-wrapped commit message over minor sentence formatting or prefix casing.**

**Don't (Pedantic Noise):**
```text
### Commit Message Review

The title uses a lowercase verb after the prefix ("add" instead of "Add"). Also, we can improve the body phrasing to be slightly more descriptive.

Suggested Commit Message:
GrepServlet: Add JSON content search endpoint

To support content search in Gitiles (Issue 376381593)...
```
*(Problem: The original commit message was already outstanding. Suggesting a rewrite for trivial casing and wording differences adds no structural value.)*

**Do:**
State that the commit message is fully compliant and satisfactory, and do not post any individual comments or suggested rewrite.


