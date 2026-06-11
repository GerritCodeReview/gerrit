---
name: gerrit-hygiene-operations
description: Provides rules, patterns, and best practices for code hygiene, formatting, downstream plugin dependencies, and release operations in Gerrit.
---

# Gerrit Hygiene & Operations Engineering Guide

## Executive Summary

Welcome to the authoritative engineering guide for maintaining code hygiene,
managing downstream deployments, and optimizing operations within the codebase.
This living repository serves as the definitive source of tribal knowledge aimed
at preventing technical debt and keeping our development ecosystem scalable,
predictable, and resilient against integration friction.

The domains covered in this payload target the most common sources of technical
debt, pipeline flakiness, and deployment desynchronization. You will find
mandates covering the strict encapsulation of proprietary infrastructure to
protect external contributors from opaque tooling, strategies for eliminating
visual regression test flakiness, and protocols for dead-code elimination during
experiment decommissioning. Furthermore, it outlines performance optimization
standards—such as client-side request parallelization—and mandates strict CI/CD
hygiene via TypeScript formatting rules and automated release note generation.

Engineers are expected to internalize these guidelines when architecting UI
components, authoring API documentation, or coordinating downstream releases.
Adhering to these principles directly reduces deployment noise, ensures a
seamless developer experience for both internal and open-source contributors,
and fortifies the integrity of our continuous integration pipelines.

## Summary

| Chapter Theme / Title               | Scope & Objective                      |
| :---------------------------------- | :------------------------------------- |
| **Downstream Ecosystem Deployment   | Governs the coordination of release    |
: Synchronization**                   : timelines and cross-project dependency :
:                                     : management for external plugins across :
:                                     : isolated, downstream Gerrit            :
:                                     : deployments. Requires strict auditing  :
:                                     : to prevent integration failures during :
:                                     : core platform rollouts.                :
| **TypeScript Code Formatting &      | Defines structural code style and      |
: Syntax Normalization**              : linting mandates for frontend          :
:                                     : TypeScript components. Strict          :
:                                     : enforcement of formatting rules, such  :
:                                     : as line-length constraints, ensures    :
:                                     : optimal diff readability and prevents  :
:                                     : automated CI pipeline failures.        :
| **Proprietary Infrastructure        | Establishes strict boundaries for      |
: Encapsulation**                     : documenting public APIs by forbidding  :
:                                     : the leakage of proprietary backend     :
:                                     : paths or internal corporate URLs.      :
:                                     : Ensures environment encapsulation and  :
:                                     : prevents exposing dead links or opaque :
:                                     : references to open-source              :
:                                     : contributors.                          :
| **Client-Side Request               | Governs the optimization of frontend   |
: Parallelization**                   : loading metrics by transitioning       :
:                                     : monolithic or batched server-side      :
:                                     : queries into parallelized,             :
:                                     : asynchronous client-side fan-out       :
:                                     : requests. Mandates concurrent          :
:                                     : execution to improve performance and   :
:                                     : simplify caching logic.                :
| **Experiment Decommissioning & Dead | Dictates the mandatory cleanup         |
: Code Elimination**                  : required when promoting successful     :
:                                     : experimental features to default       :
:                                     : behavior. Enforces the strict          :
:                                     : elimination of legacy fallback         :
:                                     : methods, feature flag evaluations, and :
:                                     : outdated service dependencies to       :
:                                     : prevent dead code accumulation.        :
| **Commit Metadata & Release Note    | Mandates the injection of structured   |
: Automation**                        : git footers into commit messages.      :
:                                     : Strict adherence ensures that          :
:                                     : automated CI/CD pipelines successfully :
:                                     : parse and generate accurate changelogs :
:                                     : and release notes without manual       :
:                                     : intervention.                          :
| **Visual Regression Test            | Governs strategies for eliminating     |
: Determinism**                       : visual regression test flakiness by    :
:                                     : deliberately orchestrating             :
:                                     : deterministic, transitional UI states. :
:                                     : Establishes the standard of            :
:                                     : intentionally omitting API mocks to    :
:                                     : reliably capture loading states during :
:                                     : screenshot baseline generation.        :

--------------------------------------------------------------------------------
--------------------------------------------------------------------------------

## Chapter: Downstream Ecosystem Deployment Synchronization

**Context:** This chapter governs the coordination of release timelines and
cross-project dependency management for external plugins across isolated,
downstream Gerrit deployments. Strict auditing of these external ecosystems is
required to prevent integration failures when rolling out core platform updates.

### Summary

| Rule ID   | Principle / Constraint  | Priority | Primary Symptom / Trap      |
| :-------- | :---------------------- | :------- | :-------------------------- |
| **T1-01** | Downstream Plugin       | High     | Proceeding with a core      |
:           : Dependency Verification :          : release timeline without    :
:           : During Core Rollout     :          : verifying downstream custom :
:           :                         :          : plugin compatibility.       :

--------------------------------------------------------------------------------

### Rules

#### T1-01: Downstream Plugin Dependency Verification During Core Rollout

> **Rule:** Always audit and verify the compatibility of cross-project
> dependencies, such as custom plugins, before executing core release updates to
> isolated downstream instances.
>
> **What:** Before rolling out core release updates to isolated downstream
> ecosystem instances, cross-project dependencies (e.g., custom plugins) must be
> explicitly audited and verified for compatibility.
>
> **Applies To:** Release management and deployment synchronization to
> downstream environments (e.g., Chromium, pdfium, v8).
>
> **Why:** Deploying core updates without simultaneously auditing and upgrading
> heavily used plugins (like avatars-external) in downstream installations
> historically risked deployment delays and integration failures. Failing to
> adhere to this typically results in **Deployment Desynchronization**.

**Trap 1: Proceeding with a core release timeline without verifying downstream
custom plugin compatibility.**

**Don't:**

*   Roll out the core release independently of external plugin compatibility
    states.

**Do:**

*   Audit and flag downstream plugin requirements (e.g., avatars-external) for
    updates prior to or alongside the core release.

## Chapter: TypeScript Code Formatting & Syntax Normalization

**Context:** This section defines the structural code style and linting mandates
for frontend TypeScript components. Strict enforcement of formatting rules, such
as line-length constraints, ensures optimal diff readability and prevents
automated CI pipeline failures.

### Summary

| Rule ID   | Principle / Constraint | Priority | Primary Symptom / Trap    |
| :-------- | :--------------------- | :------- | :------------------------ |
| **T2-01** | Strict Line Length     | Medium   | Chaining long promise     |
:           : Formatting             :          : callbacks or variable     :
:           :                        :          : assignments on a single   :
:           :                        :          : line.                     :

--------------------------------------------------------------------------------

### Rules

#### T2-01: Strict Line Length Formatting

> **Rule:** Always format TypeScript files to strictly adhere to linting limits
> by wrapping long chained expressions. Never commit code that triggers structural
> style violations.
>
> **What:** TypeScript frontend files must strictly adhere to linting standards
> by properly wrapping long chained expressions.
>
> **Applies To:** TypeScript UI components (e.g., Lit elements like
> `gr-reply-dialog.ts`).
>
> **Why:** Inconsistent formatting and over-extended lines caused unnecessary diff
> noise and failed automated linting checks in the frontend CI pipeline. Failing
> to adhere to this typically results in **Linting Pipeline Failure**.

**Trap 1: Chaining long promise callbacks or variable assignments on a single
line.**

**Don't:**

```typescript
return this.saveReview(reviewInput, errFn).then(result => {
```

**Do:**

```typescript
return this.saveReview(reviewInput, errFn)
  .then(result => {
```

## Chapter: Proprietary Infrastructure Encapsulation

**Context:** This domain establishes strict boundaries for documenting
public-facing APIs, Enums, and interface definitions by explicitly forbidding
the leakage of proprietary backend paths (e.g., `google3/`) or internal
corporate URLs. Adherence ensures environment encapsulation and prevents
exposing dead links or opaque references to open-source contributors.

### Summary

| Rule ID   | Principle / Constraint    | Priority | Primary Symptom / Trap    |
| :-------- | :------------------------ | :------- | :------------------------ |
| **T3-01** | Omission of Proprietary   | Medium   | Pasting internal          |
:           : Infrastructure Paths from :          : repository file paths     :
:           : Enum Documentation        :          : into the docblock of an   :
:           :                           :          : interface or Enum.        :
| **T3-02** | Exclusion of Internal     | Medium   | Using proprietary         |
:           : Code Search URIs from     :          : internal code search URLs :
:           : Interface Types           :          : to provide examples of    :
:           :                           :          : acceptable payload        :
:           :                           :          : strings.                  :

--------------------------------------------------------------------------------

### Rules

#### T3-01: Omission of Proprietary Infrastructure Paths from Enum Documentation

> **Rule:** Always strip internal directory paths from JSDoc or TSDoc comments
> when defining public-facing data models.
>
> **What:** JSDoc or TSDoc comments defining data models (e.g., Enums) must not
> leak proprietary, internal backend repository or directory paths.
>
> **Applies To:** Frontend API definitions, specifically Protobuf-mapped Enums.
>
> **Why:** Internal directory paths (e.g., google3/.../proto) were mistakenly
> included in open-source frontend code documentation, breaking environment
> encapsulation. Failing to adhere to this typically results in **Information
> Leakage**.

**Trap 1: Pasting internal repository file paths into the docblock of an
interface or Enum.**

**Don't:**

```typescript
/**
 * Enum to match the Action proto from CRUAS.
 * google3/path/to/internal/service/proto/file.proto.
 */
export enum ActionEnum { ... }
```

**Do:**

```typescript
/**
 * Enum to match the Action proto from CRUAS.
 */
export enum ActionEnum { ... }
```

#### T3-02: Exclusion of Internal Code Search URIs from Interface Types

> **Rule:** Never include proprietary code search URLs when documenting type
> definitions or payload schemas.
>
> **What:** Code comments must not contain URL links to internal code search
> tools or proprietary source depots when documenting interface field types.
>
> **Applies To:** API interface declarations and payload schemas (e.g.,
> `ContextItem`).
>
> **Why:** A developer linked directly to a proprietary source code URI
> (source.corp.google.com) to explain a type ID field, rendering the
> documentation inaccessible and useless for external open-source contributors.
> Failing to adhere to this typically results in **Dead Link / Opacity**.

**Trap 1: Using proprietary internal code search URLs to provide examples of
acceptable payload strings.**

**Don't:**

```typescript
// type_id should match the types here: https://source.corp.google.com/piper///depot/google3/...
type_id: string;
```

**Do:**

```typescript
// type_id should map to standard application contexts (e.g., 'gerrit_change', 'bug_tracker').
type_id: string;
```

## Chapter: Client-Side Request Parallelization

**Context:** This chapter governs the optimization of frontend loading metrics
by transitioning monolithic or batched server-side queries into parallelized,
asynchronous client-side fan-out requests. This approach mandates client-side
concurrent execution to improve dashboard performance and simplify downstream
caching logic.

### Summary

| Rule ID   | Principle / Constraint  | Priority | Primary Symptom / Trap |
| :-------- | :---------------------- | :------- | :--------------------- |
| **T4-01** | Client-Side Fan-out for | High     | Forwarding an array of |
:           : Dashboard Query         :          : queries to a dedicated :
:           : Processing              :          : multi-query endpoint   :
:           :                         :          : method.                :

--------------------------------------------------------------------------------

### Rules

#### T4-01: Client-Side Fan-out for Dashboard Query Processing

> **Rule:** Always map and execute multiple backend query requests concurrently
> using client-side parallelization constructs. Never rely on batched,
> single-request multi-query endpoints to aggregate data.
>
> **What:** Multiple backend query requests must be mapped and executed
> concurrently using Promise.all on the client side, rather than relying on a
> single, batched multi-query backend endpoint.
>
> **Applies To:** API Service layer (`GrRestApiServiceImpl`), specifically
> dashboard data fetching routines.
>
> **Why:** A legacy approach prefetched batched queries in the backend, which
> complicated caching and backend logic. Shifting to client-side parallel
> requests explicitly improved the DashboardDisplayed performance metric.
> Failing to adhere to this typically results in **Performance Degradation**.

**Trap 1: Forwarding an array of queries to a dedicated multi-query endpoint
method.**

**Don't:**

```typescript
return this.getChangesForMultipleQueries(changesPerPage, queries, offset, options);
```

**Do:**

```typescript
const requestPromises = queries.map(query =>
  this.getChanges(changesPerPage, query, offset, options)
);
return Promise.all(requestPromises).then(results => {
  if (results.includes(undefined)) return undefined;
  return results as ChangeInfo[][];
});
```

--------------------------------------------------------------------------------

### Cross-Domain Dependencies

*   **Downstream:** T5 | Experiment Decommissioning & Dead Code Elimination -
    *Transitioning to client-side fan-outs routinely exposes obsolete backend
    batching mechanisms and experimental feature flags that must be subsequently
    purged.*

## Chapter: Experiment Decommissioning & Dead Code Elimination

**Context:** This domain governs the mandatory cleanup required when promoting
successful experimental features to default behavior. It enforces the strict
elimination of legacy fallback methods, feature flag evaluations, and outdated
service dependencies to prevent dead code accumulation.

### Summary

| Rule ID   | Principle / Constraint    | Priority | Primary Symptom / Trap    |
| :-------- | :------------------------ | :------- | :------------------------ |
| **T5-01** | Aggressive Pruning of     | Medium   | Leaving the legacy        |
:           : Decommissioned Experiment :          : fallback method in the    :
:           : Fallbacks                 :          : class after removing the  :
:           :                           :          : experiment flag toggle    :
:           :                           :          : and its invocation block. :

--------------------------------------------------------------------------------

### Rules

#### T5-01: Aggressive Pruning of Decommissioned Experiment Fallbacks

> **Rule:** Must completely delete all fallback methods, feature flags, and
> obsolete service dependencies immediately upon graduating an experimental
> feature to default behavior.
>
> **What:** When an experimental feature is promoted to default behavior, all
> corresponding flag evaluations (`KnownExperimentId`), service dependencies
> (`FlagsService`), and obsolete fallback routing methods must be entirely
> deleted.
>
> **Applies To:** Service layer components and API implementations during
> feature flag graduation.
>
> **Why:** Failing to aggressively remove obsolete fallback methods (like
> `getChangesForMultipleQueries`) after adopting client-side parallel requests
> natively led to dead code and confusion over correct API utilization. Failing
> to adhere to this typically results in **Technical Debt Accumulation**.

**Trap 1: Leaving the legacy fallback method in the class after removing the
experiment flag toggle and its invocation block.**

**Don't:**

*   Remove the `if (experimentEnabled)` evaluation, but leave
    `getChangesForMultipleQueries(queries)` declared in the class definition.

**Do:**

*   Delete the deprecated fallback method `getChangesForMultipleQueries`
    entirely to ensure no other components attempt to invoke it.

--------------------------------------------------------------------------------

### Cross-Domain Dependencies

*   **Upstream:** T4 | Client-Side Request Parallelization - *Transitioning to
    parallelized client-side requests triggers the deprecation and subsequent
    cleanup of legacy monolithic server-side queries.*

## Chapter: Commit Metadata & Release Note Automation

**Context:** This section mandates the injection of structured git footers into
commit messages. Strict adherence ensures that automated CI/CD pipelines
successfully parse and generate accurate changelogs and release notes without
manual intervention.

### Summary

| Rule ID   | Principle / Constraint  | Priority | Primary Symptom / Trap      |
| :-------- | :---------------------- | :------- | :-------------------------- |
| **T6-01** | Mandatory Release-Notes | High     | Omitting the footer         |
:           : Footer Injection        :          : entirely or formatting the  :
:           :                         :          : release note description as :
:           :                         :          : a multi-line paragraph.     :

--------------------------------------------------------------------------------

### Rules

#### T6-01: Mandatory Release-Notes Footer Injection

> **Rule:** Always append a properly formatted, single-line `Release-Notes:`
> footer to all commits. While a descriptive note is valuable for significant
> updates, using `Release-Notes: skip` is completely acceptable and common for
> minor UI changes, styling tweaks, small fixes, or any change where a detailed
> public changelog entry is not necessary.
>
> **What:** Commit messages must include a properly formatted, single-line
> 'Release-Notes:' footer to satisfy CI/CD submit requirements and trigger
> automated changelog generation. If a change is minor or a public release note
> is not needed (even for user-facing adjustments like adding spacing to prevent
> accidental clicks), using the value 'skip' is fully valid and acceptable.
>
> **Applies To:** Git commit messages for all changes, including frontend UI,
> backend, and configuration updates.
>
> **Why:** Without a dedicated footer, automated release note parsers or submission
> checks can fail. However, not every commit needs a public release note; hence,
> `Release-Notes: skip` is supported to keep git history clean and satisfy automation
> without generating unnecessary public changelog entries.

**Trap 1: Omitting the footer entirely or formatting the release note
description as a multi-line paragraph.**

**Don't:**

*   Submit a commit without an explicit `Release-Notes:` footer, or spread the
    release note description across multiple lines in the commit body.

**Do:**

*   For major features or configuration changes: Append a single-line footer with a
    descriptive summary, e.g.:
    `Release-Notes: Add config to control if review footers should be included into the commit message on submit`
*   For minor adjustments (such as fixing accidental clicks by adding spacing),
    trivial fixes, or refactorings: A detailed release note description can still be
    provided if desired, but appending `Release-Notes: skip` is completely acceptable.

**Exceptions:** None. The `Release-Notes:` footer must be present on all commits,
but the value `skip` is always acceptable.

## Chapter: Visual Regression Test Determinism

**Context:** This chapter governs the strategies for eliminating visual
regression test flakiness by deliberately orchestrating deterministic,
transitional UI states. Specifically, it establishes the standard of
intentionally omitting API mocks to reliably capture loading states during
screenshot baseline generation.

### Summary

| Rule ID   | Principle / Constraint  | Priority | Primary Symptom / Trap      |
| :-------- | :---------------------- | :------- | :-------------------------- |
| **T7-01** | Intentional Omission of | High     | Mocking all APIs by default |
:           : Mocks for Deterministic :          : in a screenshot test,       :
:           : UI Baselines            :          : causing a race condition    :
:           :                         :          : where the mock resolves     :
:           :                         :          : faster than the screenshot  :
:           :                         :          : is taken.                   :

--------------------------------------------------------------------------------

### Rules

#### T7-01: Intentional Omission of Mocks for Deterministic UI Baselines

> **Rule:** Always omit API mocks when attempting to reliably capture
> transitional UI states (such as loading spinners) in visual regression tests.
> Never provide mock responses if the objective is to baseline a pre-resolved,
> asynchronous view.
>
> **What:** To capture stable, deterministic transitional UI states (like
> loading spinners) in visual regression tests, the underlying API endpoints
> must be intentionally left unmocked.
>
> **Applies To:** Screenshot baseline testing (e.g., `_screenshot_test.ts`) for
> asynchronous UI components.
>
> **Why:** Capturing fully resolved UI states was difficult to synchronize,
> causing flaky screenshot tests. Relying on an unmocked API ensures the
> component hangs reliably in the 'loading' state, producing a stable visual
> baseline for the loader. Failing to adhere to this typically results in **Test
> Flakiness**.

**Trap 1: Mocking all APIs by default in a screenshot test, causing a race
condition where the mock resolves faster than the screenshot is taken.**

**Don't:**

*   Providing mock responses for the API in order to capture the transitional
    loading state, introducing timing-based flakiness.

**Do:**

*   Leave the data-fetching API unmocked so the component indefinitely hangs in
    its transitional `loading` state, yielding a 100% deterministic screenshot
    of the loader.
