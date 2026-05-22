---
name: gerrit-system-logic
description: Provides rules, patterns, and best practices for Gerrit backend system logic, Java APIs, performance, and correctness.
---

# System Logic & Correctness Engineering Guide

## Executive Summary

This engineering guide serves as the definitive reference for maintaining system
logic and operational correctness within Gerrit's repository management and
code review infrastructure. It exists to capture critical
historical tribal knowledge, prevent the regression of known failure modes—such
as JGit thread-safety violations or NoteDb schema lock-ins—and establish strict,
non-negotiable architectural boundaries for newly contributed code. By
standardizing these patterns, the guide provides incoming engineers with the
necessary context to safely navigate and modify highly concurrent,
state-dependent subsystems.

Historically, undocumented assumptions surrounding asynchronous task resolution,
object cache sizes, and concurrent worker pools led to silent data omissions,
degraded latency, and complex race conditions. To mitigate this, the guide
enforces strict constraints around execution environments, including explicit
per-thread JGit object isolation, decoupled persistence models, and two-step
schema rollouts. It additionally hardens the system's security posture by
formalizing impersonation tracking, standardizing Contributor License Agreement
(CLA) checks, and preventing identity-cycling vulnerabilities through rigorous
account validation protocols.

The overarching technical domains covered within this repository span concurrent
thread safety, resilient NoteDb serialization and Protobuf schema evolution,
optimized REST API payload handling, deterministic Guice-based test sandboxing,
and Bazel build infrastructure alignment. Adhering to these documented policies
guarantees that the platform remains scalable, secure, and resilient against
regressions across both internal infrastructure workflows and distributed
cluster upgrades.

## Summary

| Chapter Theme / Title                | Scope & Objective                     |
| :----------------------------------- | :------------------------------------ |
| **JGit Concurrency & Thread Safety** | To prevent severe race conditions and |
:                                      : data corruption during parallel       :
:                                      : processing, Gerrit requires strict    :
:                                      : per-thread isolation of JGit          :
:                                      : resources like `RevWalk`,             :
:                                      : `Repository`, and `ObjectReader`.     :
:                                      : Never share these non-thread-safe     :
:                                      : instances across `ExecutorService`    :
:                                      : boundaries or parallel streams.       :
| **NoteDb Serialization & Schema      | Governs the serialization and schema  |
: Evolution**                          : evolution strategies for persisting   :
:                                      : Gerrit change metadata to Git notes   :
:                                      : (NoteDb). Strictly enforces two-step  :
:                                      : schema rollouts, decoupled transfer   :
:                                      : objects, and permissive JSON parsing  :
:                                      : to guarantee data integrity across    :
:                                      : distributed cluster upgrades.         :
| **Query Engine Performance &         | Governs the configuration,            |
: Operator Enforcement**               : evaluation, and strict syntactic      :
:                                      : enforcement of query predicates       :
:                                      : within the repository engine. Focuses :
:                                      : on decoupling strict user-facing      :
:                                      : evaluation constraints from           :
:                                      : background observability and          :
:                                      : implementing phased, non-blocking     :
:                                      : rollouts to prevent configuration     :
:                                      : deadlocks.                            :
| **Access Control & Impersonation     | Governs the evaluation and auditing   |
: Security**                           : of impersonated access operations     :
:                                      : (e.g., `SUBMIT_AS` and `RUN_AS`).     :
:                                      : Defines strict mechanisms for         :
:                                      : isolating the initiating "real user"  :
:                                      : from the "impersonated user" during   :
:                                      : dynamic permission checks, and        :
:                                      : mandates automated, centralized       :
:                                      : logging in NoteDb and Protobuf to     :
:                                      : guarantee uncorrupted audit trails.   :
| **Account Lifecycle & Vulnerability  | Defines constraints for handling      |
: Mitigation**                         : deleted or recreated user accounts to :
:                                      : prevent identity-cycling exploits,    :
:                                      : specifically isolating validation     :
:                                      : checks and filtering orphaned         :
:                                      : approvals during merge operations     :
:                                      : without degrading system performance. :
| **REST API Resource Routing &        | REST API endpoints must strictly      |
: Payload Optimization**               : reflect hierarchical entity           :
:                                      : relationships while optimizing JSON   :
:                                      : payloads to minimize bandwidth and    :
:                                      : processing latency. Modifications to  :
:                                      : serialization pipelines must          :
:                                      : explicitly gate expensive permission  :
:                                      : checks and gracefully handle          :
:                                      : redundant or experimental fields      :
:                                      : without causing UI ambiguity.         :
| **Build Infrastructure & Dependency  | Governs the configuration,            |
: Alignment**                          : versioning, and migration of the      :
:                                      : build system infrastructure, focusing :
:                                      : heavily on Bazel modules (`bzlmod`)   :
:                                      : and dependency graphs. Establishes    :
:                                      : constraints to ensure strict version  :
:                                      : alignment, prevent compliance         :
:                                      : pipeline stalls, and maintain         :
:                                      : reliable developer bootstrapping      :
:                                      : environments.                         :
| **Asynchronous Notification          | Asynchronous notification pipelines   |
: Consistency**                        : must capture exact entity state at    :
:                                      : the moment of initialization rather   :
:                                      : than fetching it dynamically during   :
:                                      : execution. This guarantees data       :
:                                      : consistency and prevents delayed      :
:                                      : background tasks from inadvertently   :
:                                      : processing future, out-of-band        :
:                                      : updates from persistent storage.      :
| **Test Suite Configuration &         | Dictates the implementation of        |
: Isolation**                          : parameterized integration tests,      :
:                                      : strict Guice dependency injection,    :
:                                      : and granular flaky test isolation.    :
:                                      : Ensures deterministic test coverage   :
:                                      : across multiple backend               :
:                                      : configurations without leaking        :
:                                      : generic APIs or brittle global        :
:                                      : states.                               :
| **Diff Engine Computation Limits**   | Explicitly tracking timeouts and      |
:                                      : fallback states for expensive file    :
:                                      : diff computations guarantees that     :
:                                      : frontend clients are alerted to       :
:                                      : incomplete operations. This prevents  :
:                                      : silent data omission and ensures      :
:                                      : accurate representation of modified   :
:                                      : files in the user interface.          :
| **Concurrent Formatting & Caching**  | Parallelizing expensive API           |
:                                      : formatting requires strict adherence  :
:                                      : to explicit thread-local context      :
:                                      : propagation, cache isolation for      :
:                                      : mutated elements, and collection      :
:                                      : pre-allocation. Failing to manage     :
:                                      : concurrency strictly leads to context :
:                                      : leakage across parallel streams,      :
:                                      : memory overhead, and poisoned UI      :
:                                      : states.                               :
| **Protobuf Schema Management &       | Governs the mapping between Java      |
: Conversion**                         : entities and Protobuf messages,       :
:                                      : mandating automated reflection-based  :
:                                      : validation and standardized converter :
:                                      : abstractions to prevent cache         :
:                                      : inconsistencies and silent field      :
:                                      : loss.                                 :
| **Compliance & CLA Enforcement**     | Strictly enforces Contributor License |
:                                      : Agreement (CLA) checks across all     :
:                                      : administrative REST API endpoints     :
:                                      : that modify project configurations    :
:                                      : and access rules. This guarantees     :
:                                      : compliance consistency across both    :
:                                      : direct code pushes and API-driven     :
:                                      : administrative actions.               :

--------------------------------------------------------------------------------
--------------------------------------------------------------------------------

## Chapter: JGit Concurrency & Thread Safety

**Context:** To prevent severe race conditions and data corruption during
parallel processing, Gerrit requires strict per-thread isolation of JGit
resources like `RevWalk`, `Repository`, and `ObjectReader`. Never share these
non-thread-safe instances across `ExecutorService` boundaries or parallel
streams.

### Summary

| Rule ID   | Principle / Constraint    | Priority | Primary Symptom / Trap    |
| :-------- | :------------------------ | :------- | :------------------------ |
| **T1-01** | Strict Thread Isolation   | Critical | Passing a shared          |
:           : of JGit RevWalk Instances :          : `RevWalk` instance from   :
:           :                           :          : the main thread into      :
:           :                           :          : worker tasks submitted to :
:           :                           :          : an executor.              :
| **T1-02** | Thread-Safe JGit          | Critical | Passing a single shared   |
:           : Repository Instantiation  :          : JGit Repository instance  :
:           : for Concurrent            :          : to multiple asynchronous  :
:           : Computation               :          : worker threads.           :

--------------------------------------------------------------------------------

### Rules

#### T1-01: Strict Thread Isolation of JGit RevWalk Instances

> **Rule:** Always instantiate JGit objects like `RevWalk`, `Repository`, and
> `ObjectReader` per-thread when parallelizing tasks. Never share a single
> instance across worker threads in an `ExecutorService`.
>
> **What:** JGit objects like `RevWalk`, `Repository`, and `ObjectReader` are
> not thread-safe and must be strictly instantiated per-thread when
> parallelizing tasks via an `ExecutorService`.
>
> **Applies To:** JGit operations, specifically diff computation and object
> parsing within multi-threaded ExecutorServices.
>
> **Why:** Sharing a single RevWalk instance across parallel diff evaluation
> tasks led to severe race conditions, resulting in corrupted diff results,
> application crashes, or thread deadlocks. Failing to adhere to this typically
> results in **Race Conditions / Data Corruption**.

**Trap 1: Passing a shared `RevWalk` instance from the main thread into worker
tasks submitted to an executor.**

**Don't:**

```java
// BAD: Sharing a single RevWalk across executor threads
RevWalk sharedRw = new RevWalk(repo);
for (Key key : keys) {
  executor.submit(() -> evaluator.execute(key, sharedRw));
}
```

**Do:**

```java
// GOOD: Opening a new Repository and RevWalk inside the submitted task
for (Key key : keys) {
  executor.submit(() -> {
    try (Repository threadRepo = repoManager.openRepository(project);
         ObjectReader threadReader = threadRepo.newObjectReader();
         RevWalk threadRw = new RevWalk(threadReader)) {
      return evaluator.execute(key, threadRw);
    }
  });
}
```

--------------------------------------------------------------------------------

#### T1-02: Thread-Safe JGit Repository Instantiation for Concurrent Computation

> **Rule:** Must open individual `Repository` instances for each worker thread
> when parallelizing background computations.
>
> **What:** JGit's `openRepository` returns a `Repository` object that is not
> thread-safe. When parallelizing computations (such as file diffing), each
> thread or worker must instantiate its own isolated repository handle rather
> than sharing a single instance.
>
> **Applies To:** Background workers, parallel stream processors, and
> `DiffExecutor` implementations interacting with JGit `Repository` instances.
>
> **Why:** Historically, reusing a single `Repository` handle across multiple
> threads during parallel cache generation caused unpredictable speed-ups, race
> conditions, and corrupted object reads. Failing to adhere to this typically
> results in **Race Condition / State Corruption**.

**Trap 1: Passing a single shared JGit Repository instance to multiple
asynchronous worker threads.**

**Don't:**

```java
Repository sharedRepo = repoManager.openRepository(project);
for (FileDiffCacheKey key : keys) {
  executor.submit(() -> computeDiff(sharedRepo, key));
}
```

**Do:**

```java
for (FileDiffCacheKey key : keys) {
  executor.submit(() -> {
    try (Repository workerRepo = repoManager.openRepository(project)) {
      return computeDiff(workerRepo, key);
    }
  });
}
```

--------------------------------------------------------------------------------

### Cross-Domain Dependencies

*   **Downstream:** T10 | Diff Engine Computation Limits - *Parallelizing file
    diff computations requires strict per-thread JGit instantiation to safely
    populate the FileDiffCache without race conditions.*
*   **Downstream:** T11 | Concurrent Formatting & Caching - *Parallel streams
    executing asynchronous tasks must internally allocate thread-local
    Repository handles to prevent state leakage.*

## Chapter: NoteDb Serialization & Schema Evolution

**Context:** This chapter governs the serialization and schema evolution
strategies for persisting Gerrit change metadata to Git notes (NoteDb). It
strictly enforces two-step schema rollouts, decoupled transfer objects, and
permissive JSON parsing to guarantee data integrity across distributed cluster
upgrades.

### Summary

| Rule ID   | Principle /        | Priority | Primary Symptom / Trap           |
:           : Constraint         :          :                                  :
| :-------- | :----------------- | :------- | :------------------------------- |
| **T2-01** | Two-Step Rollouts  | Critical | Updating reading and writing     |
:           : for Cached         :          : logic simultaneously without     :
:           : Serialization      :          : accounting for mixed-version     :
:           : Schemas            :          : cluster environments.            :
| **T2-02** | Forward-Compatible | High     | Deploying a strict JSON parser   |
:           : NoteDb JSON        :          : that throws exceptions upon      :
:           : Parsing            :          : encountering undocumented        :
:           :                    :          : fields.                          :
| **T2-03** | Decoupling         | High     | Serializing an internal database |
:           : Persistent Storage :          : representation straight to a Git :
:           : Format from Legacy :          : storage blob.                    :
:           : DB Entities        :          :                                  :
| **T2-04** | Push Certificate   | High     | Using negative boolean logic to  |
:           : Isolation by       :          : conditionally process metadata   :
:           : Comment Status     :          : that only applies to specific    :
:           :                    :          : data domains.                    :
| **T2-05** | Industry-Standard  | Medium   | Using non-standard slang to      |
:           : Terminology for    :          : differentiate older formatting   :
:           : Deprecated Logic   :          : methods from modern JSON ones.   :

--------------------------------------------------------------------------------

### Rules

#### T2-01: Two-Step Rollouts for Cached Serialization Schemas

> **Rule:** Always execute a two-step rollout and explicitly increment the cache
> version when mutating persistent cache schemas.
>
> **What:** Schema mutations in persistent caches must follow a two-step
> rollout: Step 1 introduces reader support for the new schema while continuing
> to write the old schema. Step 2 enables the new writer logic. A cache version
> increment is required.
>
> **Applies To:** NoteDb serialization, `ChangeNotesState`, `ChangeNotesCache`.
>
> **Why:** If a schema change is deployed without a two-step rollout, older
> instances of the application during a rolling restart will encounter cache
> records written by updated instances. Unable to parse the new schema, the
> older binaries will crash or drop data. Failing to adhere to this typically
> results in **Deserialization Crash**.

**Trap 1: Updating reading and writing logic simultaneously without accounting
for mixed-version cluster environments.**

**Don't:**

*   Deploying a single patchset that modifies both the serialization and
    deserialization formats of a persistent cache object without bumping the
    cache version.

**Do:**

*   Deploying the schema change in two phases: first patchset updates
    deserializers to handle both V1 and V2, and explicitly bumps the cache
    version. A subsequent patchset updates the serializers to emit V2.

--------------------------------------------------------------------------------

#### T2-02: Forward-Compatible NoteDb JSON Parsing

> **Rule:** Must configure JSON parsers to be inherently permissive and safely
> ignore unknown fields when deserializing NoteDb revision notes.
>
> **What:** JSON parsers reading NoteDb revision notes must be inherently
> permissive, safely ignoring unknown fields to ensure system resilience against
> future metadata schema expansions.
>
> **Applies To:** NoteDb deserialization algorithms parsing Git blob byte arrays
> into JSON objects.
>
> **Why:** When introducing a new JSON-based payload format for persistent
> Gerrit metadata, strict parsing risked immediately breaking older service
> instances or external consumers whenever a new, valid metadata field was
> appended to the payload. Failing to adhere to this typically results in
> **Parsing Exception**.

**Trap 1: Deploying a strict JSON parser that throws exceptions upon
encountering undocumented fields.**

**Don't:**

```java
// BAD: Strict parsing crashes on new schema properties
Gson strictGson = new GsonBuilder().create();
RevisionNoteData data = strictGson.fromJson(reader, RevisionNoteData.class);
```

**Do:**

```java
// GOOD: Permissive parsing explicitly ignoring unknown fields + regression tests verifying this behavior
Gson permissiveGson = new GsonBuilder().create();
RevisionNoteData data = permissiveGson.fromJson(reader, RevisionNoteData.class);
```

--------------------------------------------------------------------------------

#### T2-03: Decoupling Persistent Storage Format from Legacy DB Entities

> **Rule:** Never directly serialize legacy relational database objects into Git
> persistent storage.
>
> **What:** Data models persisted to Git (NoteDb) must utilize dedicated,
> format-specific DTOs rather than directly serializing legacy relational
> database objects.
>
> **Applies To:** NoteDb JSON Serialization pipelines and storage layer
> architecture.
>
> **Why:** Initially, legacy ReviewDb models were mapped directly into JSON
> payloads. This inherited awkward relational quirks (e.g., missing critical
> keys that were traditionally injected at runtime) and locked the new storage
> schema to the constraints of an outgoing SQL-based design. Failing to adhere
> to this typically results in **Schema Lock-In**.

**Trap 1: Serializing an internal database representation straight to a Git
storage blob.**

**Don't:**

```java
// BAD: Serializing legacy database class
PatchLineComment comment = db.getComment();
gson.toJson(comment, outputStream);
```

**Do:**

```java
// GOOD: Mapping to a decoupled storage DTO
RevisionNoteData.Comment noteComment = new RevisionNoteData.Comment(comment, serverId);
gson.toJson(noteComment, outputStream);
```

--------------------------------------------------------------------------------

#### T2-04: Push Certificate Isolation by Comment Status

> **Rule:** Must strictly map and extract push certificates exclusively when
> evaluating PUBLISHED comment records.
>
> **What:** Push certificates must be strictly mapped and extracted only when
> evaluating PUBLISHED comment records, as they inherently do not exist in the
> isolated user refs storing DRAFT data.
>
> **Applies To:** NoteDb revision note parsing logic handling cryptographic push
> metadata extraction.
>
> **Why:** Push certificates are persisted exclusively in the main change meta
> ref. Draft comments are inherently personal and physically stored in isolated
> refs within `All-Users`. Parsing logic initially failed to explicitly connect
> the certificate to the published context, risking logically malformed data
> associations. Failing to adhere to this typically results in **Metadata
> Corruption**.

**Trap 1: Using negative boolean logic to conditionally process metadata that
only applies to specific data domains.**

**Don't:**

```java
if (!draftsOnly) {
  pushCert = parsePushCert(changeId, raw, p);
}
```

**Do:**

```java
if (status == PatchLineComment.Status.PUBLISHED) {
  // Certificates exist strictly in the published change context
  pushCert = parsePushCert(changeId, raw, p);
} else {
  pushCert = null;
}
```

--------------------------------------------------------------------------------

#### T2-05: Industry-Standard Terminology for Deprecated Logic

> **Rule:** Always use standard terminology like 'legacy' to designate older,
> decoupled schema formats.
>
> **What:** Code paths, variables, and methods handling older, decoupled schema
> formats must use standard terminology like 'legacy' rather than informal
> slang.
>
> **Applies To:** NoteDb schema evolution, serialization backwards-compatibility
> layers.
>
> **Why:** Engineers occasionally used informal suffixes (like 'Homebrew') to
> designate older serialization logic, which obscured the architectural intent
> for developers unfamiliar with the slang. Failing to adhere to this typically
> results in **Maintainability Degradation**.

**Trap 1: Using non-standard slang to differentiate older formatting methods
from modern JSON ones.**

**Don't:**

```java
private void buildNoteHomebrew(ChangeNoteUtil noteUtil, OutputStream out) {
  noteUtil.buildNote(buildCommentMap(), out);
}
```

**Do:**

```java
private void buildNoteLegacy(ChangeNoteUtil noteUtil, OutputStream out) {
  noteUtil.buildNote(buildCommentMap(), out);
}
```

## Chapter: Query Engine Performance & Operator Enforcement

**Context:** This chapter governs the configuration, evaluation, and strict
syntactic enforcement of query predicates within the repository engine. It
focuses on decoupling strict user-facing evaluation constraints from background
observability and implementing phased, non-blocking rollouts to prevent
configuration deadlocks.

### Summary

| Rule ID   | Principle / Constraint            | Priority | Primary Symptom / |
:           :                                   :          : Trap              :
| :-------- | :-------------------------------- | :------- | :---------------- |
| **T3-01** | Non-Contributor Label Query       | Medium   | Blanket-excluding |
:           : Isolation                         :          : the 'owner' of    :
:           :                                   :          : the change from   :
:           :                                   :          : acting as a       :
:           :                                   :          : reviewer, even if :
:           :                                   :          : someone else      :
:           :                                   :          : authored and      :
:           :                                   :          : uploaded the      :
:           :                                   :          : specific patchset :
:           :                                   :          : being reviewed.   :
| **T3-02** | Phased Rollout of Strict Query    | Critical | Using a single    |
:           : Operator Enforcement              :          : feature toggle to :
:           :                                   :          : immediately       :
:           :                                   :          : reject bad syntax :
:           :                                   :          : across the entire :
:           :                                   :          : system.           :
| **T3-03** | Memoization of Static             | Medium   | Querying the      |
:           : Configuration in Query Evaluators :          : global            :
:           :                                   :          : configuration     :
:           :                                   :          : object repeatedly :
:           :                                   :          : within the core   :
:           :                                   :          : evaluation loop.  :
| **T3-04** | Metrics Collection Decoupling     | High     | Passing global    |
:           : from Strict Query Constraints     :          : strict-mode       :
:           :                                   :          : configuration     :
:           :                                   :          : booleans into     :
:           :                                   :          : metrics-gathering :
:           :                                   :          : query builders,   :
:           :                                   :          : thereby enforcing :
:           :                                   :          : user-facing rules :
:           :                                   :          : on background     :
:           :                                   :          : telemetry.        :

--------------------------------------------------------------------------------

### Rules

#### T3-01: Non-Contributor Label Query Isolation

> **Rule:** Always explicitly exclude the uploader, committer, and author of the
> current patchset when evaluating `non_contributor` queries, allowing the
> change owner to act as an independent reviewer if they did not author the
> specific patchset.
>
> **What:** When evaluating label queries for `non_contributor`, the query
> engine must explicitly exclude votes from the uploader, committer, and author
> of the current patchset, treating the change owner as a valid independent
> reviewer only if they didn't author/upload the specific patchset.
>
> **Applies To:** Query Engine (`EqualsLabelPredicate.java`), Access Control
> Policies.
>
> **Why:** To enforce the 'four-eyes' principle, the system needed a way to
> distinguish between a change's code contributors and independent reviewers.
> The logic explicitly handles cases where the owner and uploader are different
> people, allowing the owner to act as an independent reviewer. Failing to
> adhere to this typically results in **Policy Bypass / Self-Approval**.

**Trap 1: Blanket-excluding the 'owner' of the change from acting as a reviewer,
even if someone else authored and uploaded the specific patchset being
reviewed.**

**Don't:**

```java
// BAD: Excluding owner regardless of patchset authorship
if (account.equals(NON_CONTRIBUTOR_ACCOUNT_ID)) {
  if (approver.equals(cd.getOwner())) return false;
}
```

**Do:**

```java
// GOOD: Excluding only the uploader, committer, and author of the current patchset
if (account.equals(NON_CONTRIBUTOR_ACCOUNT_ID)) {
  if (uploader.equals(approver) || committer.equals(approver) || author.equals(approver)) {
    return false;
  }
}
```

#### T3-02: Phased Rollout of Strict Query Operator Enforcement

> **Rule:** Never use a single feature toggle to instantly enforce new query
> syntax constraints; you must decouple read and write configurations to allow
> manual migrations.
>
> **What:** Breaking syntactic requirements for repository queries must be
> rolled out via decoupled read/write configuration flags to avoid circular
> dependency deadlocks.
>
> **Applies To:** Submit Requirements evaluation and configuration validation
> layers.
>
> **Why:** Instantly enforcing strict query syntax (removing default searches)
> would break legacy submit configurations. Because a broken configuration
> blocks all repository submissions, teams would be permanently locked out from
> submitting the code necessary to fix the broken configuration. Failing to
> adhere to this typically results in **Repository Deadlock**.

**Trap 1: Using a single feature toggle to immediately reject bad syntax across
the entire system.**

**Don't:**

*   Instantly rejecting all evaluation queries that lack explicit operators. If
    a project has a broken legacy config, it instantly fails, blocking all
    submissions including configuration fixes.

**Do:**

*   Split enforcement into two configs: `requireOperatorForUpdate` (blocks
    saving new bad configs) and `requireOperatorForEvaluation` (throws errors
    dynamically). Enable the update block first, migrate legacy rules manually,
    then enable evaluation blocking.

#### T3-03: Memoization of Static Configuration in Query Evaluators

> **Rule:** Must cache query parsing and evaluation configurations during
> component initialization instead of polling the configuration object per
> execution.
>
> **What:** Configuration values that govern query parsing and evaluation must
> be read once during component initialization rather than fetched dynamically
> per execution.
>
> **Applies To:** Query Builders, Evaluators (e.g.,
> SubmitRequirementsEvaluatorImpl), and high-frequency validation execution
> paths.
>
> **Why:** Historically, reading configuration values directly from the Config
> object upon every expression validation or evaluation incurred unnecessary CPU
> overhead, degrading the performance of high-volume operations like submit
> requirement checks. Failing to adhere to this typically results in
> **Performance Degradation**.

**Trap 1: Querying the global configuration object repeatedly within the core
evaluation loop.**

**Don't:**

```java
public SubmitRequirementExpressionResult evaluateExpression(
    SubmitRequirementExpression expression, ChangeData changeData) {
  // BAD: Reading config on every evaluation
  boolean reqOp = config.getBoolean("submit-requirement", null, "requireOperatorForEvaluation", false);
  Predicate<ChangeData> predicate = queryBuilderFactory.create(reqOp).parse(expression.expressionString());
  // ...
}
```

**Do:**

```java
// GOOD: Cache the configuration value at initialization time
public SubmitRequirementsEvaluatorImpl(...) {
  this.requireOperatorForEvaluation = config.getBoolean("submit-requirement", null, "requireOperatorForEvaluation", false);
}

public SubmitRequirementExpressionResult evaluateExpression(
    SubmitRequirementExpression expression, ChangeData changeData) {
  Predicate<ChangeData> predicate = queryBuilderFactory.create(this.requireOperatorForEvaluation).parse(expression.expressionString());
  // ...
}
```

**Exceptions:** Dynamic configurations explicitly designed and documented to be
hot-reloaded without server restarts.

#### T3-04: Metrics Collection Decoupling from Strict Query Constraints

> **Rule:** Always hard-code permissive configuration for metrics-gathering
> systems to prevent observability failures caused by malformed user metadata.
>
> **What:** Metrics gathering systems must bypass strict, user-facing query
> validation configurations to guarantee that observability pipelines do not
> fail due to malformed metadata or legacy data.
>
> **Applies To:** Metrics exporters (e.g., MergeMetrics), Background Jobs, and
> Observability Hooks.
>
> **Why:** Metrics collection for change merging relied on global server
> configuration for query parsing. If strict operator evaluation was enabled
> globally, legacy or malformed submit requirements would cause the metrics
> collection to throw an exception, halting the entire telemetry pipeline.
> Failing to adhere to this typically results in **Metrics Data Loss**.

**Trap 1: Passing global strict-mode configuration booleans into
metrics-gathering query builders, thereby enforcing user-facing rules on
background telemetry.**

**Don't:**

```java
// BAD: Tying metrics collection to strict user configurations
boolean strictEvaluation = config.getBoolean("submit-requirement", null, "requireOperatorForEvaluation", false);
Predicate<ChangeData> predicate = submitRequirementChangequeryBuilderFactory
    .create(strictEvaluation)
    .parse(submitRequirement.submittabilityExpression());
```

**Do:**

```java
// GOOD: Hard-code permissive configuration to guarantee metrics stability
Predicate<ChangeData> predicate = submitRequirementChangequeryBuilderFactory
    .create(false) // Hardcoded leniency
    .parse(submitRequirement.submittabilityExpression());
```

--------------------------------------------------------------------------------

### Cross-Domain Dependencies

*   **Upstream:** T4 | Access Control & Impersonation Security - *Access control
    models dictate the contributor versus independent reviewer roles enforced
    during query evaluation.*
*   **Downstream:** T5 | Account Lifecycle & Vulnerability Mitigation - *The
    query engine's submit requirement evaluations act as the enforcement barrier
    against orphaned approvals from deleted accounts.*

## Chapter: Access Control & Impersonation Security

**Context:** This chapter governs the evaluation and auditing of impersonated
access operations (e.g., `SUBMIT_AS` and `RUN_AS`). It defines strict mechanisms
for isolating the initiating "real user" from the "impersonated user" during
dynamic permission checks, and mandates automated, centralized logging in NoteDb
and Protobuf to guarantee uncorrupted audit trails.

### Summary

| Rule ID   | Principle / Constraint    | Priority | Primary Symptom / Trap    |
| :-------- | :------------------------ | :------- | :------------------------ |
| **T4-01** | Impersonation Log         | Medium   | Allowing an impersonated  |
:           : Suppression for Reviewer  :          : session to generate a     :
:           : Updates                   :          : change log message for a  :
:           :                           :          : reviewer addition.        :
| **T4-02** | Strict Context Evaluation | Critical | Checking for `SUBMIT_AS`  |
:           : for Impersonated Access   :          : within a pre-calculated   :
:           : Control                   :          : collection of permissions :
:           :                           :          : belonging to the user     :
:           :                           :          : context being             :
:           :                           :          : impersonated.             :
| **T4-03** | Centralized Impersonation | High     | Appending the             |
:           : Auditing in NoteDb        :          : impersonation string      :
:           : Commits                   :          : manually in specific API  :
:           :                           :          : endpoints.                :
| **T4-04** | System Metadata           | High     | Manually mutating the     |
:           : Preservation During       :          : change message to inject  :
:           : Impersonation Updates     :          : audit metadata during a   :
:           :                           :          : batch update operation.   :
| **T4-05** | Explicit Distinction of   | Medium   | Adding tracking fields to |
:           : Effective vs Real         :          : Protobuf messages without :
:           : Identity in Protobuf      :          : clarifying the            :
:           : Schemas                   :          : impersonation             :
:           :                           :          : relationship.             :

--------------------------------------------------------------------------------

### Rules

#### T4-01: Impersonation Log Suppression for Reviewer Updates

> **Rule:** Always suppress system-generated impersonation change messages
> during operations that strictly update reviewers to avoid redundant logs.
>
> **What:** System-generated impersonation change messages must be suppressed
> for operations that explicitly update reviewers, as reviewer updates
> inherently persist their own metadata.
>
> **Applies To:** Gerrit server API, specifically `AddReviewersOp.java` and
> operations modifying `ReviewerUpdateInfo`.
>
> **Why:** Operations like adding a reviewer do not generate standard change
> messages by default, relying instead on structured reviewer-update lists.
> Allowing impersonation logic to inject a change message for these operations
> created redundant, noisy log entries. Failing to adhere to this typically
> results in **Audit Log Noise**.

**Trap 1: Allowing an impersonated session to generate a change log message for
a reviewer addition.**

**Don't:**

```java
change = ctx.getChange();
if (!accountIds.isEmpty()) {
  // Proceeds with update, implicitly allowing impersonation messages
}
```

**Do:**

```java
change = ctx.getChange();
// Reviewer updates do not create change messages. In case of impersonation, we do not want to add an extra message to the log.
ctx.getUpdate(change.currentPatchSetId()).setSuppressImpersonationMessage(true);
if (!accountIds.isEmpty()) {
  // Proceeds with update
}
```

#### T4-02: Strict Context Evaluation for Impersonated Access Control

> **Rule:** Never authorize impersonated actions against the bulk permission set
> of the impersonated user; always evaluate against the real user initiating the
> impersonation.
>
> **What:** When authorizing impersonated actions (e.g., `SUBMIT_AS`),
> permissions must be verified against the `REAL_USER` (the identity initiating
> the impersonation) rather than checking the bulk permission set of the
> impersonated user.
>
> **Applies To:** Access control layers, `PermissionBackend`, and operational
> checkpoints like `MergeOp.java`.
>
> **Why:** Evaluating `SUBMIT_AS` against the general permissions pool of the
> impersonated user context was conceptually flawed and risked allowing
> unauthorized users to execute privileged actions if the real user lacked
> explicit impersonation rights. Failing to adhere to this typically results in
> **Security Bypass**.

**Trap 1: Checking for `SUBMIT_AS` within a pre-calculated collection of
permissions belonging to the user context being impersonated.**

**Don't:**

```java
// BAD: Checking against the overall 'can' permission set which may not represent the real user's explicit rights.
if (!can.contains(ChangePermission.SUBMIT_AS)) {
  // reject
}
```

**Do:**

```java
// GOOD: Permissions are checked against the user who initiated the impersonation (REAL_USER).
if (!permissionBackend
    .user(caller, ImpersonationPermissionMode.REAL_USER)
    .change(cd)
    .test(ChangePermission.SUBMIT_AS)) {
  // reject
}
```

#### T4-03: Centralized Impersonation Auditing in NoteDb Commits

> **Rule:** Must centrally inject impersonation clauses directly into NoteDb
> commit builders to guarantee consistent audit trails across all paths.
>
> **What:** Impersonation clauses (e.g., 'Performed by X on behalf of Y') must
> be automatically centralized within the NoteDb `AbstractChangeUpdate` commit
> builder, guaranteeing their presence in the system's versioned storage
> regardless of the operation or whether a user-provided message exists.
>
> **Applies To:** NoteDb mutation layers, `AbstractChangeUpdate.java`, and all
> REST endpoints modifying change states.
>
> **Why:** Historically, impersonation messages were manually appended by
> individual REST API handlers (like `PostReview`). This decentralized approach
> led to inconsistent audit trails where certain backend or automated operations
> failed to properly record the real user identity in the underlying Git commit.
> Failing to adhere to this typically results in **Incomplete Audit Trail**.

**Trap 1: Appending the impersonation string manually in specific API
endpoints.**

**Don't:**

```java
// BAD: Decentralized in PostReview.java
String impersonationClause = String.format("(Posted by %s on behalf of %s)", caller, reviewer);
if (Strings.isNullOrEmpty(in.message)) {
  in.message = impersonationClause;
}
```

**Do:**

```java
// GOOD: Centralized in AbstractChangeUpdate.java for all NoteDb writes
private void addOptionalImpersonationMessage(CommitBuilder cb) {
  if (realAccountId == null || realAccountId.equals(accountId)) return;
  String impersonationClause = String.format("(Performed by %s on behalf of %s)", realLoggableName, loggableName);
  // Safely inject before the footer
}
```

**Trap 2: Skipping the impersonation clause if the change update has no
user-visible commit message body.**

**Don't:**

```java
int firstFooterLine = indexOfFirstFooterLine(commitMsgLines);
// BAD: Bailing out if the message has no body
if (firstFooterLine == 2) return;
```

**Do:**

```java
// GOOD: Append the clause regardless of the message body length using standard string manipulation.
Stream.concat(Arrays.stream(commitMsgLines).limit(firstFooterLine), Stream.of(impersonationClause, ""))...
```

**Exceptions:** Operations where the `realAccountId` strictly equals the
`accountId` (no impersonation taking place).

#### T4-04: System Metadata Preservation During Impersonation Updates

> **Rule:** Never manually mutate standard system change messages to document
> batch update reviewer modifications on behalf of other users.
>
> **What:** Manual override of change messages must be avoided when making
> system-level reviewer updates on behalf of other users, as it corrupts or
> suppresses structured audit logs.
>
> **Applies To:** REST API mutation endpoints, specifically batch updates
> involving user impersonation (`RUN_AS` / `onBehalfOf`).
>
> **Why:** Adding a manual "on behalf of" message to a reviewer modification
> suppressed the default system message (e.g., "Bob added to reviewers"), wiping
> out the primary context. Furthermore, tests verifying impersonation broke
> because reviewer updates bypass standard visible change messages entirely.
> Failing to adhere to this typically results in **Audit Trail Corruption**.

**Trap 1: Manually mutating the change message to inject audit metadata during a
batch update operation.**

**Don't:**

```java
update.setChangeMessage(String.format("Reviewer added by %s on behalf of %s", realUser, impersonatedUser));
```

**Do:**

*   Allow the backend to populate the structured `ReviewerUpdateInfo`
    automatically. Do not append manual strings to the batch update.

**Trap 2: Writing tests that assert against visible change messages to verify
background auditing.**

**Don't:**

```java
ChangeMessage m = Iterables.getLast(cmUtil.byChange(r.getChange().notes()));
assertThat(m.getMessage()).contains(expectedReviewerName);
```

**Do:**

```java
ChangeMessageInfo lastMessage = Iterables.getLast(gApi.changes().id(r.getChangeId()).get().messages);
assertThat(lastMessage.realAuthor._accountId).isEqualTo(realUser.id().get());
assertThat(lastMessage.author._accountId).isEqualTo(impersonatedUser.id().get());
```

#### T4-05: Explicit Distinction of Effective vs Real Identity in Protobuf Schemas

> **Rule:** Must explicitly document Protobuf schema fields storing
> impersonation audit trails with their exact `RUN_AS` semantics.
>
> **What:** Fields storing impersonation audit trails in Protobuf schemas must
> contain explicit inline documentation demonstrating exact `RUN_AS` semantics.
>
> **Applies To:** Protobuf schemas, particularly caching and status update
> models like `ReviewerStatusUpdateProto`.
>
> **Why:** Ambiguity in the protobuf schema left developers unsure which field
> represented the impersonated account vs the actual administrative account
> performing the action. Failing to adhere to this typically results in
> **Misinterpreted Audit Logs**.

**Trap 1: Adding tracking fields to Protobuf messages without clarifying the
impersonation relationship.**

**Don't:**

```proto
int32 updated_by = 2;
int32 real_updated_by = 8;
```

**Do:**

```proto
// Account ID of the effective user.
int32 updated_by = 2;
// Account ID of the real user. Set when impersonating using the RUN_AS permission.
// Example: if User X is impersonating user Y, real_updated_by is X.
int32 real_updated_by = 8;
```

--------------------------------------------------------------------------------

### Cross-Domain Dependencies

*   **Downstream:** T2 | NoteDb Serialization & Schema Evolution - *Centralized
    impersonation auditing forces structured formatting and strict insertion
    behaviors on underlying Git commits generated via NoteDb.*
*   **Downstream:** T12 | Protobuf Schema Management & Conversion - *Protobuf
    messages tracking access control state require rigorous entity documentation
    to preserve the distinction between real and effective users during cache
    serialization.*

## Chapter: Account Lifecycle & Vulnerability Mitigation

**Context:** This theme defines constraints for handling deleted or recreated
user accounts to prevent identity-cycling exploits, specifically isolating
validation checks and filtering orphaned approvals during merge operations
without degrading system performance.

### Summary

| Rule ID   | Principle /       | Priority | Primary Symptom / Trap            |
:           : Constraint        :          :                                   :
| :-------- | :---------------- | :------- | :-------------------------------- |
| **T5-01** | Invalidation of   | Critical | Evaluating submittability by      |
:           : Approvals from    :          : aggregating all present votes     :
:           : Deleted Accounts  :          : without verifying if the account  :
:           :                   :          : IDs associated with those votes   :
:           :                   :          : still exist.                      :
| **T5-02** | Deferral of       | High     | Triggering cache lookups for      |
:           : Expensive Account :          : every account associated with a   :
:           : Validation to     :          : change during standard page loads :
:           : Submission Time   :          : or SR recalculations.             :
| **T5-03** | Graceful          | High     | Throwing a terminal exception as  |
:           : Degradation for   :          : soon as an orphaned or invalid    :
:           : Invalid Approval  :          : property is discovered during an  :
:           : Entities          :          : aggregation check.                :
| **T5-04** | Positive-Intent   | Medium   | Creating a flag that requires an  |
:           : Feature Toggles   :          : administrator to explicitly turn  :
:           : for               :          : *on* a necessary security patch.  :
:           : Secure-by-Default :          :                                   :
:           : States            :          :                                   :

--------------------------------------------------------------------------------

### Rules

#### T5-01: Invalidation of Approvals from Deleted Accounts

> **Rule:** Always dynamically filter and ignore `PatchSetApproval` votes
> originating from deleted accounts during submission checks.
>
> **What:** Code review approvals (`PatchSetApproval` votes) from deleted
> accounts must be dynamically filtered and ignored during submission checks to
> prevent identity-cycling exploits.
>
> **Applies To:** Submission validation (`MergeOp.checkSubmitRequirements`),
> Submit Requirement evaluation, and IAM lifecycle processing.
>
> **Why:** An exploit vector was identified where a user could upload a patch,
> delete their Gerrit account, and recreate an account linked to the same
> external ID. The new account inherited external group permissions (e.g.,
> Code-Review+2) and could self-approve the change made by the now-deleted
> identity, bypassing the 'no self-approval' rule. Failing to adhere to this
> typically results in **Security Bypass / Self-Approval Exploit**.

**Trap 1: Evaluating submittability by aggregating all present votes without
verifying if the account IDs associated with those votes still exist.**

**Don't:**

```java
for (PatchSetApproval psa : cd.currentApprovals()) {
  if (psa.isApproved()) return true;
}
```

**Do:**

```java
for (PatchSetApproval psa : filterOutApprovalsOfDeletedAccounts(cd.currentApprovals())) {
  if (psa.isApproved()) return true;
}
```

#### T5-02: Deferral of Expensive Account Validation to Submission Time

> **Rule:** Never execute costly cache lookups for account existence during
> continuous operations; must defer these validations to the final submission
> phase.
>
> **What:** Costly verification checks, such as querying an `AccountCache` for
> account existence, must not be integrated into continuous operations like
> general Submit Requirement (SR) evaluation. They must be deferred to the final
> `MergeOp` submission phase.
>
> **Applies To:** Submit Requirements engine and `MergeOp`.
>
> **Why:** An initial attempt to fix a vulnerability by filtering deleted
> account votes inside the continuous Submit Requirements engine caused severe
> global latency regressions because `AccountCache` was queried excessively. The
> fix had to be moved strictly to the submit button execution path. Failing to
> adhere to this typically results in **Severe Latency / System Degradation**.

**Trap 1: Triggering cache lookups for every account associated with a change
during standard page loads or SR recalculations.**

**Don't:**

*   Embedding `accountCache.get(accountId).isPresent()` inside the highly
    trafficked Submit Requirement evaluation engine.

**Do:**

*   Executing the account validation loop only within
    `MergeOp.checkSubmitRequirements()` directly prior to the final merge
    action.

#### T5-03: Graceful Degradation for Invalid Approval Entities

> **Rule:** Always silently filter out invalid or orphaned entities on a
> resource rather than hard-failing the operation.
>
> **What:** The presence of an invalid or orphaned entity (e.g., an approval
> from a deleted user) on a resource must not hard-fail operations on that
> resource. The invalid entity should be filtered out, and the operation allowed
> to proceed if the remaining valid entities satisfy the requirements.
>
> **Applies To:** Access validation, Submit requirement evaluation, and Merge
> logic.
>
> **Why:** A proposed solution suggested throwing a `ResourceConflictException`
> if *any* deleted account vote was detected on a change. This was rejected
> because it would block a change from being merged even if it possessed enough
> valid votes from active users to pass independently. Failing to adhere to this
> typically results in **Unnecessary Operation Blocking**.

**Trap 1: Throwing a terminal exception as soon as an orphaned or invalid
property is discovered during an aggregation check.**

**Don't:**

```java
if (isDeleted(account)) {
  throw new ResourceConflictException("Approval made by deleted account");
}
```

**Do:**

```java
// Silently filter invalid data and evaluate based on remaining valid data
Iterable<PatchSetApproval> validVotes = Iterables.filter(votes, v -> !isDeleted(v.accountId()));
```

#### T5-04: Positive-Intent Feature Toggles for Secure-by-Default States

> **Rule:** Must name and implement feature toggles for security fixes such that
> the insecure legacy behavior requires an explicit opt-in.
>
> **What:** When introducing a critical security or architectural fix guarded by
> a feature toggle, the toggle's name and logic must represent the opt-in of the
> *legacy/insecure* behavior, ensuring the new secure behavior is the
> unconfigured system default.
>
> **Applies To:** Experiment Flag definitions (`ExperimentFeaturesConstants`)
> and Feature Toggle conditionals.
>
> **Why:** A security patch introduced a flag named
> `IGNORE_VOTES_OF_DELETED_ACCOUNTS`. Code reviewers forced an inversion of the
> flag to `CONSIDER_VOTES_OF_DELETED_ACCOUNTS` so that administrators did not
> have to explicitly enable the fix, preventing unpatched defaults. Failing to
> adhere to this typically results in **Accidental Misconfiguration / Security
> Hole**.

**Trap 1: Creating a flag that requires an administrator to explicitly turn *on*
a necessary security patch.**

**Don't:**

```java
if (experimentFeatures.isFeatureEnabled("IGNORE_VOTES_OF_DELETED_ACCOUNTS")) {
  filterDeletedAccounts();
}
```

**Do:**

```java
if (!experimentFeatures.isFeatureEnabled("CONSIDER_VOTES_OF_DELETED_ACCOUNTS")) {
  filterDeletedAccounts();
}
```

## Chapter: REST API Resource Routing & Payload Optimization

**Context:** REST API endpoints must strictly reflect hierarchical entity
relationships while optimizing JSON payloads to minimize bandwidth and
processing latency. Modifications to serialization pipelines must explicitly
gate expensive permission checks and gracefully handle redundant or experimental
fields without causing UI ambiguity.

### Summary

| Rule ID   | Principle / Constraint    | Priority | Primary Symptom / Trap    |
| :-------- | :------------------------ | :------- | :------------------------ |
| **T6-01** | Explicit Boolean          | Medium   | Using a `toBoolean`       |
:           : Rendering for             :          : helper that converts      :
:           : Experimental UI Features  :          : `false` to `null` to      :
:           :                           :          : minimize payload size,    :
:           :                           :          : unintentionally leaving   :
:           :                           :          : the UI client ambiguous   :
:           :                           :          : about whether the feature :
:           :                           :          : is disabled or simply     :
:           :                           :          : unconfigured.             :
| **T6-02** | Hierarchical REST API     | Medium   | Exposing a sub-resource   |
:           : Resource Routing          :          : directly under a          :
:           :                           :          : high-level container      :
:           :                           :          : because it seems more     :
:           :                           :          : direct.                   :
| **T6-03** | Explicit Query Parameters | Medium   | Defaulting a new diff     |
:           : for Extensible Listing    :          : endpoint to return only   :
:           : APIs                      :          : file names without        :
:           :                           :          : requiring the client to   :
:           :                           :          : ask for that specific     :
:           :                           :          : format.                   :
| **T6-04** | Performance Justification | High     | Appending a permission    |
:           : for Payload-Embedded      :          : verification step to      :
:           : Permission Checks         :          : every response object     :
:           :                           :          : regardless of the         :
:           :                           :          : client's actual need for  :
:           :                           :          : that data.                :
| **T6-05** | Omission of Redundant     | Medium   | Falling back to the       |
:           : Real-User Data in API     :          : primary user object if a  :
:           : Payloads                  :          : distinct real user isn't  :
:           :                           :          : found.                    :

--------------------------------------------------------------------------------

### Rules

#### T6-01: Explicit Boolean Rendering for Experimental UI Features

> **Rule:** Always explicitly serialize `false` for experimental UI feature
> flags to ensure strict UI gating, temporarily bypassing standard payload
> nullification.
>
> **What:** REST API responses should temporarily bypass the standard payload
> optimization (which drops `false` and `null` values) for experimental UI
> feature flags, explicitly returning `false` to ensure strict UI gating.
>
> **Applies To:** REST API JSON serializers (`ChangeJson`), UI Feature Flags.
>
> **Why:** To prevent an experimental AI review feature from leaking in the UI
> when permission was denied, the API was modified to explicitly serialize the
> `canAiReview = false` state rather than omitting it as the framework usually
> does for falsy values. Failing to adhere to this typically results in **UI
> Feature Leakage**.

**Trap 1: Using a `toBoolean` helper that converts `false` to `null` to minimize
payload size, unintentionally leaving the UI client ambiguous about whether the
feature is disabled or simply unconfigured.**

**Don't:**

```java
// BAD: Omitting false values from payload
info.canAiReview = toBoolean(permissionBackend.test(AI_REVIEW));
```

**Do:**

```java
// GOOD: Explicitly forcing false to be serialized
info.canAiReview = permissionBackend.test(AI_REVIEW) ? true : false;
```

**Exceptions:** This is a temporary technical debt exception strictly permitted
until the `experiments.UiFeature__enable_ai_chat` feature flag is sunsetted.

--------------------------------------------------------------------------------

#### T6-02: Hierarchical REST API Resource Routing

> **Rule:** Never expose sub-resources at the root level; always nest them under
> their specific context-defining parent resources.
>
> **What:** REST API endpoints must reflect strict hierarchical entity
> relationships. Sub-resources must be nested under their specific
> context-defining parent resources rather than being exposed at the root level.
>
> **Applies To:** REST API Controller definitions, API endpoint URL schemas, and
> resource mapping.
>
> **Why:** Designing flat API endpoints (e.g., requesting a file diff directly
> from the project root) created ambiguities, as the backend could not
> definitively validate the file's existence without knowing the specific commit
> context. Failing to adhere to this typically results in **Ambiguous Resource
> Resolution**.

**Trap 1: Exposing a sub-resource directly under a high-level container because
it seems more direct.**

**Don't:**

```text
// BAD: Missing the commit context required to resolve the file
GET /projects/{project}/files/{file}/diff?old={sha1}&new={sha1}
```

**Do:**

```text
// GOOD: Nesting the file diff under the explicit commit it belongs to
GET /projects/{project}/commits/{commit-id}/files/{file}/diff?base={sha1}
```

--------------------------------------------------------------------------------

#### T6-03: Explicit Query Parameters for Extensible Listing APIs

> **Rule:** Must require an explicit query parameter for REST endpoints designed
> to return sparse payloads to preserve future backwards-compatibility.
>
> **What:** When creating a REST API endpoint that deliberately returns a sparse
> payload (e.g., listing only file names), it must require an explicit query
> parameter indicating that reduced scope, allowing future backwards-compatible
> payload expansions.
>
> **Applies To:** REST API Endpoint design, list views, and differential payload
> returns.
>
> **Why:** Endpoints originally designed to return sparse data became locked
> into that format. When full data payloads were later needed, developers had to
> create entirely new endpoints because the default behavior could not be safely
> changed. Failing to adhere to this typically results in **API Backward
> Incompatibility**.

**Trap 1: Defaulting a new diff endpoint to return only file names without
requiring the client to ask for that specific format.**

**Don't:**

```text
// BAD: Locks the API into only ever returning filenames
GET /projects/{project}/commits/{commit-id}/diff
```

**Do:**

```text
// GOOD: Requires the client to acknowledge the limited scope
GET /projects/{project}/commits/{commit-id}/diff?nameOnly
```

**Exceptions:** Endpoints whose domain definition inherently restricts them to
simple lists (e.g., `GET /ids`).

--------------------------------------------------------------------------------

#### T6-04: Performance Justification for Payload-Embedded Permission Checks

> **Rule:** Always gate newly injected backend permission evaluations in heavily
> trafficked JSON payloads behind explicit client options to prevent global
> latency degradation.
>
> **What:** Injecting new backend permission evaluations into heavily trafficked
> REST API JSON payloads must be critically analyzed against the latency penalty
> and explicitly gated by granular request options.
>
> **Applies To:** REST API response serializers, specifically `ChangeJson`
> formatting pipelines.
>
> **Why:** Adding mandatory permission checks (like AI_REVIEW) to core change
> listing APIs increased the time complexity of the request for all users,
> introducing latency that could have been avoided by using a separate API or a
> request parameter gate. Failing to adhere to this typically results in **API
> Latency Degradation**.

**Trap 1: Appending a permission verification step to every response object
regardless of the client's actual need for that data.**

**Don't:**

```java
// BAD: Unconditional permission check slows down all queries
out.canAiReview = permissionBackend.user(user).test(AI_REVIEW);
```

**Do:**

```java
// GOOD: Gating the expensive check behind explicit client options and experiment flags
if (has(CURRENT_ACTIONS) && experiments.isEnabled(ENABLE_AI_CHAT)) {
  out.canAiReview = permissionBackend.user(user).test(AI_REVIEW);
}
```

--------------------------------------------------------------------------------

#### T6-05: Omission of Redundant Real-User Data in API Payloads

> **Rule:** Must omit the `real_updated_by` field (set to null) when serializing
> objects if it is identical to the primary `updated_by` field to conserve
> bandwidth.
>
> **What:** When serializing objects that support impersonation (like
> `ReviewerUpdateInfo`), the `real_updated_by` field must be omitted (set to
> null) if it is identical to the `updated_by` field, rather than duplicating
> the payload data.
>
> **Applies To:** REST API serialization layers, specifically `ChangeJson.java`
> and TypeScript interface definitions.
>
> **Why:** Returning the same account identity for both the primary actor and
> the real actor bloated the JSON payload. By omitting it, the API clearly
> signals when impersonation has not occurred while conserving bandwidth.
> Failing to adhere to this typically results in **Payload Bloat**.

**Trap 1: Falling back to the primary user object if a distinct real user isn't
found.**

**Don't:**

```java
// BAD: Duplicating the object if no distinct real user exists
new ReviewerUpdateInfo(
  c.date(),
  accountLoader.get(c.updatedBy()),
  c.realUpdatedBy().map(accountLoader::get).orElseGet(() -> accountLoader.get(c.updatedBy())),
  ...
```

**Do:**

```java
// GOOD: Return null to omit the field entirely
new ReviewerUpdateInfo(
  c.date(),
  accountLoader.get(c.updatedBy()),
  c.realUpdatedBy().map(accountLoader::get).orElse(null),
  ...
```

--------------------------------------------------------------------------------

### Cross-Domain Dependencies

*   **Upstream:** T4 | Access Control & Impersonation Security - *API
    serialization layers invoke permission checks and impersonation contexts
    that must be explicitly scoped to prevent payload bloat and latency
    degradation.*

## Chapter: Build Infrastructure & Dependency Alignment

**Context:** This chapter governs the configuration, versioning, and migration
of the build system infrastructure, focusing heavily on Bazel modules (`bzlmod`)
and dependency graphs. It establishes constraints to ensure strict version
alignment, prevent compliance pipeline stalls, and maintain reliable developer
bootstrapping environments.

### Summary

| Rule ID   | Principle / Constraint   | Priority | Primary Symptom / Trap     |
| :-------- | :----------------------- | :------- | :------------------------- |
| **T7-01** | Bazelisk Version         | High     | Relying on deprecated      |
:           : Constraints for Bzlmod   :          : Bazelisk versions while    :
:           : Migrations               :          : altering legacy Bazel      :
:           :                          :          : boundary markers           :
:           :                          :          : (`WORKSPACE`).             :
| **T7-02** | Strict Version Alignment | High     | Pinning a specific older   |
:           : in Bazel Dependency      :          : minor version of a library :
:           : Graphs                   :          : for a single module        :
:           :                          :          : without checking global or :
:           :                          :          : internal mirror alignment. :
| **T7-03** | Synchronized Dependency  | Medium   | Deprecating a build        |
:           : Manifest Documentation   :          : configuration file while   :
:           :                          :          : leaving stale references   :
:           :                          :          : to its usage in textual    :
:           :                          :          : documentation.             :
| **T7-04** | String Typing for Bazel  | High     | Passing a native boolean   |
:           : amend_artifact           :          : `True` to a strictly       :
:           : force_version            :          : string-typed attribute in  :
:           :                          :          : a Bazel macro.             :
| **T7-05** | Segregation of External  | Medium   | Placing non-internal       |
:           : Dependencies for         :          : dependencies alongside     :
:           : Compliance Bypassing     :          : standard organizational    :
:           :                          :          : dependencies in global     :
:           :                          :          : dependency files.          :

--------------------------------------------------------------------------------

### Rules

#### T7-01: Bazelisk Version Constraints for Bzlmod Migrations

> **Rule:** Always upgrade `bazelisk` to at least v1.27.0 when migrating to
> `bzlmod` and removing or renaming the root `WORKSPACE` file.
>
> **What:** When migrating to Bazel Modules (`bzlmod`) and removing or renaming
> the root `WORKSPACE` file, `bazelisk` must be upgraded to at least v1.27.0 to
> correctly detect the `.bazelversion` file.
>
> **Applies To:** Build System / CI pipelines / Developer environment
> bootstrapping.
>
> **Why:** During the migration to Bzlmod, the `WORKSPACE` file was renamed to
> `WORKSPACE.bzlmod`. Older versions of `bazelisk` failed to locate the project
> root without a file strictly named `WORKSPACE`, ignoring the pinned
> `.bazelversion` and downloading the newest incompatible Bazel binary (e.g.,
> v9.0.1). Failing to adhere to this typically results in **Build Failure /
> Incorrect Toolchain**.

**Trap 1: Relying on deprecated Bazelisk versions while altering legacy Bazel
boundary markers (`WORKSPACE`).**

**Don't:**

*   Attempting to build a Bzlmod-enabled project with an outdated `bazelisk`
    that defaults to Bazel 9.x when `WORKSPACE` is missing.

**Do:**

*   Require `bazelisk >= 1.27.0` which resolves `.bazelversion` even in purely
    `bzlmod`-driven repositories lacking a `WORKSPACE` file.

#### T7-02: Strict Version Alignment in Bazel Dependency Graphs

> **Rule:** Must explicitly align external JVM dependencies to a single
> validated minor version across the entire build graph.
>
> **What:** External JVM dependencies must strictly align with a single
> validated minor version to prevent build system conflicts in
> `rules_jvm_external` and ensure compatibility with internal monolithic
> dependency mirrors.
>
> **Applies To:** Bazel build configurations, `WORKSPACE`, and `deps.toml`
> defining external library versions.
>
> **Why:** Introducing conflicting minor versions of transitive libraries (e.g.,
> pulling Bytebuddy 1.18.4 while another module expects 1.18.5) caused the build
> system to trigger duplicate version checks and fail the build graph
> resolution. Failing to adhere to this typically results in **Build Failure /
> Duplicate Version**.

**Trap 1: Pinning a specific older minor version of a library for a single
module without checking global or internal mirror alignment.**

**Don't:**

*   Declaring `bytebuddy:1.18.4` in the JGit servlet dependency chain while the
    rest of the build relies on `1.18.5`.

**Do:**

*   Upgrading the local module dependency to match the globally available
    version: `bytebuddy:1.18.5`, ensuring a single version traverses the entire
    graph.

#### T7-03: Synchronized Dependency Manifest Documentation

> **Rule:** Always update developer documentation atomically in the exact same
> commit when modifying or deprecating dependency manifests.
>
> **What:** Build system developer documentation must be atomically updated in
> the exact same commit whenever dependency declaration manifests or build
> targets are migrated or deprecated.
>
> **Applies To:** Build system configuration (e.g., Bazel WORKSPACE/MODULE
> files) and corresponding developer-facing documentation (e.g., dev-bazel.txt).
>
> **Why:** During a migration of dependency management workflows, the legacy
> dependency configuration file was emptied, but the developer documentation
> still explicitly instructed engineers to reference the deprecated file path
> and run outdated Bazel pin targets. Failing to adhere to this typically
> results in **Developer Process Failure**.

**Trap 1: Deprecating a build configuration file while leaving stale references
to its usage in textual documentation.**

**Don't:**

*   Emptying legacy configuration files (e.g., `tools/deps.bzl`) but failing to
    update documentation containing old execution targets like `bazel run
    @gerrit_deps//:pin`.

**Do:**

*   Atomically updating documentation to reference the new configuration files
    (e.g., `tools/deps.toml`) and correctly mapped targets like `bazel run
    @external_deps//:pin` within the deprecation commit.

#### T7-04: String Typing for Bazel amend_artifact force_version

> **Rule:** Never pass a Starlark boolean to the `force_version` attribute in
> `rules_jvm_external`; it must be strictly typed as a string.
>
> **What:** The `force_version` attribute within the `rules_jvm_external` Bazel
> module extension must be strictly passed as a string, not a native Starlark
> boolean.
>
> **Applies To:** Bazel build scripts (`MODULE.bazel`) leveraging the
> `rules_jvm_external` extension for Maven dependency resolution.
>
> **Why:** An attempt was made to enforce root-level dependency precedence over
> layered modules using a boolean data type. The underlying extension API
> structurally requires a string representation of the boolean value. Failing to
> adhere to this typically results in **Bazel Evaluation Failure**.

**Trap 1: Passing a native boolean `True` to a strictly string-typed attribute
in a Bazel macro.**

**Don't:**

```python
maven.amend_artifact(
    name = "external_deps",
    coordinates = coord,
    force_version = True,
)
```

**Do:**

```python
maven.amend_artifact(
    name = "external_deps",
    coordinates = coord,
    force_version = "true",
)
```

#### T7-05: Segregation of External Dependencies for Compliance Bypassing

> **Rule:** Must isolate external dependencies into dedicated configuration
> files to avoid triggering unnecessary internal compliance reviews.
>
> **What:** Dependencies not utilized internally by the host organization must
> be isolated into dedicated build configurations to avoid triggering
> unnecessary compliance reviews.
>
> **Applies To:** Bazel workspace configurations, dependency management (e.g.,
> deps.bzl, nongoogle.bzl).
>
> **Why:** Routine version bumps to open-source libraries (e.g., H2 database)
> that were only used in external distributions triggered mandatory internal
> 'Library-Compliance' votes, blocking development velocity and complicating
> functional PRs. Failing to adhere to this typically results in **Build
> Pipeline Stalls**.

**Trap 1: Placing non-internal dependencies alongside standard organizational
dependencies in global dependency files.**

**Don't:**

*   Define the H2 database dependency within the global `tools/deps.bzl` file
    alongside core infrastructure libraries.

**Do:**

*   Move the H2 database dependency into a segregated `tools/nongoogle.bzl` file
    to explicitly demarcate its exclusion from internal compliance checks.

**Trap 2: Bundling dependency location moves with functional, logical code
changes.**

**Don't:**

*   Submitting a single patchset that upgrades the H2 database version, changes
    database logic, and moves the dependency to nongoogle.bzl.

**Do:**

*   Submit the structural move to `nongoogle.bzl` as an independent parent
    change to keep the functional logic updates completely decoupled.

**Exceptions:** Dependencies that share utilization across both internal
infrastructure and external deployments.

--------------------------------------------------------------------------------

### Cross-Domain Dependencies

*   **Upstream:** T13 | Compliance & CLA Enforcement - *Organizational
    compliance rules and automated pipelines directly dictate the necessity of
    structurally segregating external dependencies in the build graph.*

## Chapter: Asynchronous Notification Consistency

**Context:** Asynchronous notification pipelines must capture exact entity state
at the moment of initialization rather than fetching it dynamically during
execution. This guarantees data consistency and prevents delayed background
tasks from inadvertently processing future, out-of-band updates from persistent
storage.

### Summary

| Rule ID   | Principle / Constraint | Priority | Primary Symptom / Trap       |
| :-------- | :--------------------- | :------- | :--------------------------- |
| **T8-01** | Snapshot State Capture | High     | Passing database identifiers |
:           : for Asynchronous       :          : to a background task and     :
:           : Notifications          :          : reloading the entity during  :
:           :                        :          : execution.                   :

--------------------------------------------------------------------------------

### Rules

#### T8-01: Snapshot State Capture for Asynchronous Notifications

> **Rule:** Must materialize and pass the exact entity state synchronously to
> asynchronous background tasks before handoff.
>
> **What:** Asynchronous tasks (like email dispatchers) must capture the exact
> entity state at initialization instead of fetching it from the persistent
> store dynamically during execution.
>
> **Applies To:** Asynchronous notification pipelines and background task
> executors.
>
> **Why:** A delayed asynchronous email task would query NoteDb directly during
> execution. If another thread performed an update during the delay, the email
> would accidentally pull 'too-new' state (e.g., a mismatched subject line) that
> belonged to the subsequent transaction. Failing to adhere to this typically
> results in **Race Condition / Stale Data**.

**Trap 1: Passing database identifiers to a background task and reloading the
entity during execution.**

**Don't:**

```java
public ChangeEmailImpl(@Provided EmailArguments args, Project.NameKey project, Change.Id changeId) {
  // Anti-pattern: Storing IDs and reading the database asynchronously later
  this.changeId = changeId;
}
```

**Do:**

```java
public ChangeEmailImpl(@Provided EmailArguments args, Change change) {
  // Materialize state synchronously before handing off to the async thread
  this.changeData = args.changeDataFactory.create(change);
  this.change = changeData.change();
}
```

--------------------------------------------------------------------------------

### Cross-Domain Dependencies

*   **Upstream:** T2 | NoteDb Serialization & Schema Evolution - *NoteDb acts as
    the persistent storage layer that asynchronous notifications must avoid
    querying during delayed executions to prevent reading advanced, out-of-band
    states.*

## Chapter: Test Suite Configuration & Isolation

**Context:** This chapter dictates the implementation of parameterized
integration tests, strict Guice dependency injection, and granular flaky test
isolation. It ensures deterministic test coverage across multiple backend
configurations without leaking generic APIs or brittle global states.

### Summary

| Rule ID   | Principle / Constraint   | Priority | Primary Symptom / Trap     |
| :-------- | :----------------------- | :------- | :------------------------- |
| **T9-01** | Method-Level Granularity | Medium   | Using class-level          |
:           : for Integration Test     :          : isolation to fix a single  :
:           : Sandboxing               :          : flaky test, penalizing the :
:           :                          :          : execution time of the      :
:           :                          :          : entire suite.              :
| **T9-02** | Test Class Interface     | Medium   | Making a test class        |
:           : Segregation              :          : implement `Provider<T>`    :
:           :                          :          : merely to bind a variable  :
:           :                          :          : in Guice.                  :
| **T9-03** | Strict Propagation of    | High     | Ignoring the parameterized |
:           : Parameterized Test       :          : configuration in Guice     :
:           : Configurations           :          : module installation.       :
| **T9-04** | Direct Instance Binding  | Low      | Binding a locally          |
:           : in Guice Modules         :          : available instance by      :
:           :                          :          : wrapping it in a Guice     :
:           :                          :          : Provider.                  :
| **T9-05** | Framework-Driven Test    | High     | Flipping global static     |
:           : Parametrization          :          : variables inside an        :
:           :                          :          : inherited test class's     :
:           :                          :          : @Before method.            :
| **T9-06** | Declarative Dependency   | Medium   | Manually invoking the      |
:           : Injection via @Inject    :          : injector inside a method   :
:           : over Manual Resolution   :          : body.                      :
| **T9-07** | Deterministic Build      | Medium   | Checking for transient     |
:           : Environment Detection in :          : directories like 'build'   :
:           : Tests                    :          : or 'buck-out' to dictate   :
:           :                          :          : conditional test logic.    :

--------------------------------------------------------------------------------

### Rules

#### T9-01: Method-Level Granularity for Integration Test Sandboxing

> **Rule:** Always apply the `@Sandboxed` annotation strictly to specific flaky
> or state-dependent test methods rather than applying it globally to the test
> class.
>
> **What:** The `@Sandboxed` annotation must be applied directly to specific
> flaky or state-dependent test methods rather than at the class level.
>
> **Applies To:** Integration test framework (`AbstractDaemonTest`).
>
> **Why:** Applying the sandbox annotation to an entire class spins up a
> completely new database and daemon instance for every single test in the
> class, drastically increasing test execution time for tests that do not
> actually suffer from cross-test state interference. Failing to adhere to this
> typically results in **Test Suite Performance Degradation**.

**Trap 1: Using class-level isolation to fix a single flaky test, penalizing the
execution time of the entire suite.**

**Don't:**

```java
// BAD: Class-level sandboxing
@Sandboxed
public class SubmitRequirementPredicateIT extends AbstractDaemonTest {
  @Test public void testA() { ... }
  @Test public void testB() { ... }
}
```

**Do:**

```java
// GOOD: Method-level sandboxing
public class SubmitRequirementPredicateIT extends AbstractDaemonTest {
  @Test public void testA() { ... }

  @Test
  @Sandboxed
  public void flakyTestB() { ... }
}
```

--------------------------------------------------------------------------------

#### T9-02: Test Class Interface Segregation

> **Rule:** Never implement generic framework interfaces directly on test base
> classes to satisfy dependency injection bindings.
>
> **What:** Test base classes must not directly implement generic framework
> interfaces (like Guice's Provider<T>) to satisfy dependency injection
> bindings, as this leaks generic methods into the test class's public API.
>
> **Applies To:** Guice Dependency Injection, Unit/Integration Test
> Infrastructure.
>
> **Why:** Tests were modified to implement Guice Provider interfaces directly.
> This introduced overly generic methods (like `get()`) onto the base class,
> muddying the class's API footprint and risking unintended shadowing. Failing
> to adhere to this typically results in **Interface Pollution**.

**Trap 1: Making a test class implement `Provider<T>` merely to bind a variable
in Guice.**

**Don't:**

```java
public abstract class AbstractChangeNotesTest extends GerritBaseTests implements Provider<Config> {
  @ConfigSuite.Parameter
  public Config testConfig;

  @Override
  public Config get() {
    return testConfig != null ? testConfig : new Config();
  }
}
```

**Do:**

```java
// Use framework utilities like Providers.of() or nested classes instead of direct interface implementation.
bind(Config.class).annotatedWith(GerritServerConfig.class).toProvider(Providers.of(testConfig));
```

--------------------------------------------------------------------------------

#### T9-03: Strict Propagation of Parameterized Test Configurations

> **Rule:** Must explicitly inject the dynamically provided configuration object
> into the system under test to ensure valid parametrization.
>
> **What:** When utilizing a parameterized test suite, explicitly inject the
> provided configuration object into the system under test rather than
> instantiating a default state, which silently bypasses test parameters.
>
> **Applies To:** ConfigSuite runner; specifically Guice module installation and
> parameter injection.
>
> **Why:** A test setup block instantiated a new, empty Config rather than using
> the configuration variant provided by the test suite framework, completely
> nullifying the multi-backend test coverage. Failing to adhere to this
> typically results in **False Positive Test Coverage**.

**Trap 1: Ignoring the parameterized configuration in Guice module
installation.**

**Don't:**

```java
@ConfigSuite.Parameter
public Config testConfig;

// BAD: Installing with a default empty config, ignoring the test runner variations.
install(NoteDbModule.forTest(new Config()));
```

**Do:**

```java
@ConfigSuite.Parameter
public Config testConfig;

// GOOD: Passing the dynamically injected testConfig down into the module.
install(NoteDbModule.forTest(testConfig));
```

--------------------------------------------------------------------------------

#### T9-04: Direct Instance Binding in Guice Modules

> **Rule:** Always use the `.toInstance()` DSL when binding pre-instantiated
> objects in Guice modules.
>
> **What:** When binding a pre-existing object instance in a Guice module, use
> the direct `.toInstance()` DSL instead of wrapping it in redundant Provider
> abstractions.
>
> **Applies To:** Guice module configuration (`configure()` blocks).
>
> **Why:** Engineers wrapped pre-instantiated variables inside `Providers.of()`
> and bound them via `.toProvider()`, creating unnecessary object allocation and
> visually cluttered dependency setups. Failing to adhere to this typically
> results in **Boilerplate / Decreased Readability**.

**Trap 1: Binding a locally available instance by wrapping it in a Guice
Provider.**

**Don't:**

```java
bind(Config.class).annotatedWith(GerritServerConfig.class)
    .toProvider(Providers.of(testConfig));
```

**Do:**

```java
bind(Config.class).annotatedWith(GerritServerConfig.class)
    .toInstance(testConfig);
```

--------------------------------------------------------------------------------

#### T9-05: Framework-Driven Test Parametrization

> **Rule:** Must utilize declarative suite parameterization (e.g.,
> `@ConfigSuite`) to drive data variations, completely avoiding mutable static
> state and test hierarchy hacks.
>
> **What:** Test configurations intended to exercise multiple backend
> representations must use explicit framework parameterization (e.g.,
> ConfigSuite) rather than manual subclassing combined with mutable global
> state.
>
> **Applies To:** Test suites covering variable storage backends (e.g., NoteDb
> legacy format vs. JSON).
>
> **Why:** Developers previously verified new data formats by manually creating
> child test classes that flipped a static global boolean. This broke test
> isolation and created brittle, hard-to-follow test hierarchies. Failing to
> adhere to this typically results in **Test State Leakage / Brittle
> Inheritance**.

**Trap 1: Flipping global static variables inside an inherited test class's
@Before method.**

**Don't:**

```java
public class ChangeNotesJsonTest extends ChangeNotesTest {
  @Before
  public void setJson() {
    ChangeNoteUtil.writeJsonDefault = true;
  }
}
```

**Do:**

```java
@RunWith(ConfigSuite.class)
public abstract class AbstractChangeNotesTest {
  @ConfigSuite.Default
  public static Config legacyConfig() {
    // return legacy config
  }

  @ConfigSuite.Config
  public static Config jsonConfig() {
    // return json config
  }
}
```

--------------------------------------------------------------------------------

#### T9-06: Declarative Dependency Injection via @Inject over Manual Resolution

> **Rule:** Always enforce automated component lifecycles using `@Inject`
> annotations rather than querying the Guice Injector directly inside method
> bodies.
>
> **What:** Rely on the framework's automatic member injection lifecycle (e.g.,
> `@Inject`) rather than manually fetching dependencies via the Injector locator
> pattern.
>
> **Applies To:** Guice Dependency Injection within Test instances and Service
> classes.
>
> **Why:** Tests routinely invoked `injector.getInstance()` inside helper
> methods. This obfuscated dependency requirements and broke cleanly when the
> test runner reset the injector under varying configurations. Failing to adhere
> to this typically results in **Hidden Dependencies / Lifecycle Desync**.

**Trap 1: Manually invoking the injector inside a method body.**

**Don't:**

```java
void someTestMethod() {
  ChangeNoteUtil noteUtil = injector.getInstance(ChangeNoteUtil.class);
  // use noteUtil
}
```

**Do:**

```java
@Inject
private ChangeNoteUtil noteUtil;

void someTestMethod() {
  // use noteUtil directly
}
```

--------------------------------------------------------------------------------

#### T9-07: Deterministic Build Environment Detection in Tests

> **Rule:** Must establish build environment parameters using static source
> control metadata files, strictly ignoring volatile build output directories.
>
> **What:** Test setup frameworks must not infer project environments or build
> systems based on transient output directories that may not yet exist.
>
> **Applies To:** Integration and Plugin test acceptance frameworks.
>
> **Why:** The test framework erroneously classified Buck projects as Maven
> projects if the repository was freshly cloned and the `buck-out` directory
> hadn't been generated by an initial build yet, causing tests to misconfigure
> the environment. Failing to adhere to this typically results in **Flaky
> Environment Initialization**.

**Trap 1: Checking for transient directories like 'build' or 'buck-out' to
dictate conditional test logic.**

**Don't:**

```java
boolean isMaven = !Files.exists(pluginRoot.resolve("buck-out"));
```

**Do:**

*   Rely strictly on deterministic source control metadata (e.g., checking for a
    `pom.xml` or `BUCK` file) or explicitly scope out untested environments.

--------------------------------------------------------------------------------

### Cross-Domain Dependencies

*   **Upstream:** T7 | Build Infrastructure & Dependency Alignment -
    *Deterministic test environment detection relies on the core build system
    declarations defined here.*
*   **Downstream:** T2 | NoteDb Serialization & Schema Evolution - *NoteDb
    legacy versus JSON backend variations natively consume the parameterized
    suite structures enforced by this domain.*

## Chapter: Diff Engine Computation Limits

**Context:** Explicitly tracking timeouts and fallback states for expensive file
diff computations guarantees that frontend clients are alerted to incomplete
operations. This prevents silent data omission and ensures accurate
representation of modified files in the user interface.

### Summary

| Rule ID    | Principle / Constraint | Priority | Primary Symptom / Trap      |
| :--------- | :--------------------- | :------- | :-------------------------- |
| **T10-01** | Explicit Signaling of  | High     | Discarding files from the   |
:            : Computation Limits in  :          : result list or relying on   :
:            : File Diffs             :          : opaque negative cache hits  :
:            :                        :          : when computation limits are :
:            :                        :          : exceeded.                   :

--------------------------------------------------------------------------------

### Rules

#### T10-01: Explicit Signaling of Computation Limits in File Diffs

> **Rule:** Always explicitly flag timed-out file diffs in the API payload
> instead of silently omitting them or grouping them into generic negative
> caches.
>
> **What:** Expensive processes like file diff algorithms must respect strict
> timeouts and explicitly mark aborted files in the API response using a
> dedicated flag, rather than dropping the file or classifying it generically.
>
> **Applies To:** Diff algorithms, `FileInfo` REST API responses, and
> `FileDiffCache` outputs.
>
> **Why:** When a file's diff took too long to compute, the system previously
> marked it with a generic 'negative' cache flag or dropped it, which misled
> users into believing the file was unmodified instead of alerting them to the
> computation failure. Failing to adhere to this typically results in **Silent
> Data Omission / Misleading UI**.

**Trap 1: Discarding files from the result list or relying on opaque negative
cache hits when computation limits are exceeded.**

**Don't:**

```java
// BAD: Silently omitting the timed-out file
if (fileDiffOutput.isEmpty() || fileDiffOutput.isNegative()) {
  continue;
}
```

**Do:**

```java
// GOOD: Including the file but explicitly flagging it as too expensive
if (fileDiffOutput.isEmpty() && !fileDiffOutput.isNegative()) {
  continue;
}
// API serializes the result with tooExpensiveToCompute = true
```

--------------------------------------------------------------------------------

### Cross-Domain Dependencies

*   **Downstream:** T6 | REST API Resource Routing & Payload Optimization -
    *Serializing explicit computation limit flags directly dictates the schema
    and payload structure of REST API `FileInfo` responses.*

## Chapter: Concurrent Formatting & Caching

**Context:** *Parallelizing expensive API formatting requires strict adherence
to explicit thread-local context propagation, cache isolation for mutated
elements, and collection pre-allocation. Failing to manage concurrency strictly
leads to context leakage across parallel streams, memory overhead, and poisoned
UI states.*

### Summary

| Rule ID    | Principle / Constraint          | Priority | Primary Symptom / |
:            :                                 :          : Trap              :
| :--------- | :------------------------------ | :------- | :---------------- |
| **T11-01** | Explicit User Context           | High     | Calling a         |
:            : Propagation in Parallel Streams :          : Provider to fetch :
:            :                                 :          : the current user  :
:            :                                 :          : context directly  :
:            :                                 :          : inside the        :
:            :                                 :          : mapping function  :
:            :                                 :          : of a parallel     :
:            :                                 :          : stream.           :
| **T11-02** | Isolation of Mutated Query      | High     | Caching all       |
:            : Elements in Shared Caches       :          : elements of a     :
:            :                                 :          : query result      :
:            :                                 :          : indiscriminately  :
:            :                                 :          : before applying   :
:            :                                 :          : list-level        :
:            :                                 :          : pagination logic. :
| **T11-03** | Pre-allocation of Thread-Safe   | Medium   | Initializing a    |
:            : Caches for Large Queries        :          : ConcurrentHashMap :
:            :                                 :          : using the default :
:            :                                 :          : empty constructor :
:            :                                 :          : despite knowing   :
:            :                                 :          : the size of the   :
:            :                                 :          : input list.       :

--------------------------------------------------------------------------------

### Rules

#### T11-01: Explicit User Context Propagation in Parallel Streams

> **Rule:** Always resolve thread-local user contexts on the main execution
> thread and explicitly pass them into parallel worker threads.
>
> **What:** When processing data using Java parallel streams, thread-local user
> contexts (like `CurrentUser`) must be resolved on the main thread prior to
> stream execution and explicitly passed to the worker threads.
>
> **Applies To:** API response formatting, `parallelStream()` operations, and
> any Guice Provider resolution requiring request scope.
>
> **Why:** Relying on request-scoped Guice providers within parallel stream
> lambdas caused context leaks, where worker threads inherited the wrong
> thread-local state or threw out-of-scope exceptions. Failing to adhere to this
> typically results in **Context Loss / State Leakage**.

**Trap 1: Calling a Provider to fetch the current user context directly inside
the mapping function of a parallel stream.**

**Don't:**

```java
// BAD: Evaluating the provider dynamically inside a parallel worker thread
list.parallelStream().map(item -> {
  return format(item, userProvider.get());
}).collect(toList());
```

**Do:**

```java
// GOOD: Resolving context on the main thread and passing it explicitly
CurrentUser user = userProvider.get();
list.parallelStream().map(item -> {
  return format(item, user);
}).collect(toList());
```

--------------------------------------------------------------------------------

#### T11-02: Isolation of Mutated Query Elements in Shared Caches

> **Rule:** Never cache elements of a query result that are subject to
> post-processing mutation, such as list-terminating pagination flags.
>
> **What:** When caching sequential or parallel query results, elements that are
> susceptible to post-processing mutation (such as appending a pagination flag
> like `_moreChanges`) must be explicitly excluded from the shared cache.
>
> **Applies To:** API response formatters, caching layers, and thread-safe
> collections (`ConcurrentHashMap`) used during pagination.
>
> **Why:** Caching the final element of a paginated list caused the
> `_moreChanges` boolean to leak into subsequent, unrelated queries that
> retrieved the same entity from the cache, yielding incorrect pagination states
> in the UI. Failing to adhere to this typically results in **Cache Poisoning /
> UI Pagination Errors**.

**Trap 1: Caching all elements of a query result indiscriminately before
applying list-level pagination logic.**

**Don't:**

```java
// BAD: Caching all elements, then mutating the last one
for (ChangeData cd : changes) {
  ChangeInfo info = format(cd);
  cache.put(cd.getId(), info);
  changeInfos.add(info);
}
changeInfos.get(changeInfos.size() - 1)._moreChanges = true;
```

**Do:**

```java
// GOOD: Excluding the potentially mutated last element from the cache
for (int i = 0; i < changes.size(); i++) {
  boolean isCacheable = cacheQueryResults && (i != changes.size() - 1);
  ChangeInfo info = format(changes.get(i));
  if (isCacheable) {
    cache.put(changes.get(i).getId(), info);
  }
  changeInfos.add(info);
}
```

--------------------------------------------------------------------------------

#### T11-03: Pre-allocation of Thread-Safe Caches for Large Queries

> **Rule:** Must initialize expected capacities when instantiating thread-safe
> collections to process large parallel processing operations.
>
> **What:** When initializing a thread-safe map (`ConcurrentHashMap`) that will
> accommodate thousands of entries during parallel processing, explicitly
> provide the expected size/capacity to the constructor.
>
> **Applies To:** Concurrent processing layers and result caches for bulk data
> retrieval.
>
> **Why:** Failing to initialize capacities on heavily used collections led to
> unnecessary memory reallocation and rehashing overhead during parallel stream
> processing of large change lists. Failing to adhere to this typically results
> in **Memory Overhead / CPU Churn**.

**Trap 1: Initializing a ConcurrentHashMap using the default empty constructor
despite knowing the size of the input list.**

**Don't:**

```java
// BAD: Default capacity leads to frequent rehashing
Map<Change.Id, ChangeInfo> cache = new ConcurrentHashMap<>();
```

**Do:**

```java
// GOOD: Pre-allocating capacity based on known input size
Map<Change.Id, ChangeInfo> cache = new ConcurrentHashMap<>(in.size());
```

**Exceptions:** Queries where the result set is guaranteed to be trivial (e.g.,
< 10 items).

--------------------------------------------------------------------------------

### Cross-Domain Dependencies

*   **Upstream:** T9 | Test Suite Configuration & Isolation - *Guice Provider
    configurations dictate the request-scoped boundaries that necessitate
    explicit context extraction on the main thread.*
*   **Downstream:** T6 | REST API Resource Routing & Payload Optimization -
    *Thread-safe concurrent formatting directly accelerates the generation and
    optimization of the final JSON payloads delivered by the API.*

## Chapter: Protobuf Schema Management & Conversion

**Context:** This section governs the mapping between Java entities and Protobuf
messages, mandating automated reflection-based validation and standardized
converter abstractions to prevent cache inconsistencies and silent field loss.

### Summary

| Rule ID    | Principle /        | Priority | Primary Symptom / Trap          |
:            : Constraint         :          :                                 :
| :--------- | :----------------- | :------- | :------------------------------ |
| **T12-01** | Java Entity to     | Critical | Modifying a Java data class     |
:            : Protobuf           :          : without programmatically        :
:            : Round-Trip         :          : enforcing that the new field is :
:            : Validation         :          : mapped to the Protobuf schema.  :
| **T12-02** | Standardized       | Medium   | Manually querying Protobuf      |
:            : Protobuf           :          : FieldDescriptors by integer     :
:            : Serialization via  :          : index and explicitly copying    :
:            : SafeProtoConverter :          : values to a builder.            :

--------------------------------------------------------------------------------

### Rules

#### T12-01: Java Entity to Protobuf Round-Trip Validation

> **Rule:** Must verify any field additions to cached Java entities via
> reflection-based round-trip tests to guarantee absolute parity with their
> Protobuf serializers.
>
> **What:** Any field additions to Java entities persisted in caches must be
> verified via reflection-based round-trip unit tests to ensure absolute parity
> with their Protobuf serializers.
>
> **Applies To:** Data storage models, cache serializers (e.g.,
> `AccountProtoConverter`), and persistent entities.
>
> **Why:** Adding new fields (like avatar properties) to an entity without
> strictly validating the corresponding Protobuf converter caused those fields
> to be silently dropped when objects were retrieved from the cache. Failing to
> adhere to this typically results in **Silent Data Loss / Cache
> Inconsistency**.

**Trap 1: Modifying a Java data class without programmatically enforcing that
the new field is mapped to the Protobuf schema.**

**Don't:**

```java
// BAD: Updating entity without ensuring serialization mapping
public class Account {
  public String avatarEmail; // New field added, silently ignored by cache
}
```

**Do:**

```java
// GOOD: Using reflection in a test suite to detect missed fields
@Test
public void accountFieldsMatchExpected() {
  // Automatically fails if Account.class gains a field not handled by the converter
  assertAllFieldsMapped(Account.class, AccountProtoConverter.class);
}
```

**Exceptions:** Transient fields explicitly marked to be ignored during
serialization.

--------------------------------------------------------------------------------

#### T12-02: Standardized Protobuf Serialization via SafeProtoConverter

> **Rule:** Never use manual Protobuf-to-Java serialization with direct
> `FieldDescriptor` lookups; always utilize the standardized
> `SafeProtoConverter` abstraction.
>
> **What:** Manual Protobuf-to-Java serialization and deserialization using
> direct `FieldDescriptor` lookups and explicit builder mapping should be
> avoided in favor of the standardized `SafeProtoConverter` abstraction.
>
> **Applies To:** Cache serialization logic (e.g., `FileDiffOutput.Serializer`)
> and any mapping between Java data objects and Protobuf entities.
>
> **Why:** Manual field mapping for Protobuf entities requires verbose
> boilerplate that is prone to field omission, index misalignment, and caching
> corruption during schema evolution. Failing to adhere to this typically
> results in **Data Loss / Schema Misalignment**.

**Trap 1: Manually querying Protobuf FieldDescriptors by integer index and
explicitly copying values to a builder.**

**Don't:**

```java
private static final FieldDescriptor TOO_EXPENSIVE_DESCRIPTOR = FileDiffOutputProto.getDescriptor().findFieldByNumber(17);
if (proto.hasField(TOO_EXPENSIVE_DESCRIPTOR)) {
  builder.tooExpensiveToCompute(Optional.of(proto.getTooExpensiveToCompute()));
}
```

**Do:**

*   Implement and utilize a standard SafeProtoConverter utility to abstract away
    raw descriptor mappings.

```java
return SafeProtoConverterUtil.fromProto(proto);
```

--------------------------------------------------------------------------------

### Cross-Domain Dependencies

*   **Downstream:** T5 | Account Lifecycle & Vulnerability Mitigation - *The
    account cache relies on strictly validated Protobuf schemas to prevent
    silent data loss of user attributes.*
*   **Downstream:** T10 | Diff Engine Computation Limits - *File diff cache
    entities rely on standardized Protobuf converters to serialize expensive
    computation fallbacks.*

## Chapter: Compliance & CLA Enforcement

**Context:** Must strictly enforce Contributor License Agreement (CLA) checks
across all administrative REST API endpoints that modify project configurations
and access rules. This guarantees compliance consistency across both direct code
pushes and API-driven administrative actions.

### Summary

| Rule ID    | Principle / Constraint | Priority | Primary Symptom / Trap     |
| :--------- | :--------------------- | :------- | :------------------------- |
| **T13-01** | CLA Enforcement on     | Critical | Relying on standard source |
:            : Project Configuration  :          : code pushing paths for CLA :
:            : Endpoints              :          : enforcement while ignoring :
:            :                        :          : REST-based project         :
:            :                        :          : administration paths.      :

--------------------------------------------------------------------------------

### Rules

#### T13-01: CLA Enforcement on Project Configuration Endpoints

> **Rule:** Always verify Contributor License Agreements explicitly via
> `ContributorAgreementsChecker` before processing access review payloads.
>
> **What:** Contributor License Agreement (CLA) checks must be strictly enforced
> on administrative endpoints that create or modify project access rules.
>
> **Applies To:** REST API (`/projects/{project}/access:review`) and
> `RepoMetaDataUpdater`.
>
> **Why:** Bypassing the standard CLA requirements when proposing changes
> directly via the project access review API allowed unverified users to submit
> project-level configuration modifications. Failing to adhere to this typically
> results in **Compliance / Security Bypass**.

**Trap 1: Relying on standard source code pushing paths for CLA enforcement
while ignoring REST-based project administration paths.**

**Don't:**

*   Permitting REST API calls to `/access:review` to succeed if the user is
    authenticated, without checking their legal agreement status.

**Do:**

```java
// Verify CLA explicitly before applying access review payloads
contributorAgreementsChecker.check(user.getAccountId());
```

--------------------------------------------------------------------------------

### Cross-Domain Dependencies

*   **Upstream:** T4 | Access Control & Impersonation Security - *Base user
    permissions and identity must be established and validated before executing
    legal agreement compliance checks.*
*   **Downstream:** T6 | REST API Resource Routing & Payload Optimization -
    *REST endpoint handlers must integrate explicit compliance checkers prior to
    validating and persisting configuration payloads.*
