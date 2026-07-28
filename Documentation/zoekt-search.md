# Zoekt-Based Code Search for Gerrit

## Use Cases

Gerrit has no built-in way to search code content across repositories and
branches. Users need to search across the repositories and branches they
are allowed to read, with results respecting Gerrit's existing project and
branch/ref access rules, including private or restricted refs, and the
solution must work for installations whose code corpus is larger than a
single search engine instance should own.

Primary use cases:

- A logged-in Gerrit user searches code from the Gerrit UI and sees only
  project/ref results they are allowed to read.
- A logged-in Gerrit user can see which of the projects and branches they
  are allowed to read are currently indexed, so they can tell whether an
  empty result means "no match" or "not indexed yet."
- The solution works for large Gerrit installations — many projects, many
  branches, and a code corpus and query load larger than a single search
  engine instance should own — and scales horizontally as an installation
  grows, rather than being limited to small deployments.

Non-goals:

- Replacing Gerrit's access control model with a separate authorization
  system.
- Exposing the underlying search engine directly to end users.

## Acceptance Criteria

- Users access search only through Gerrit, never directly against the
  underlying search engine.
- A Gerrit user's search results are always limited to projects and refs
  that user is allowed to read under Gerrit's real access control rules.
- A missing or invalid identity returns no matches, and never leaks match
  counts or the names of repositories/branches the caller cannot see.
- Because the set of readable projects/refs is derived ahead of time rather
  than checked synchronously on every query, there is a bounded, tunable
  delay between a Gerrit access-control change and that change taking
  effect in search results. Enforcement is pre-filter only — scope is
  computed and applied before search runs, not by re-checking each
  individual result against live Gerrit afterward — so this is a
  deliberate tradeoff, not a correctness gap (see [Background](#background)).
- The solution scales horizontally to large Gerrit installations, and
  growing or maintaining search capacity does not require downtime or a
  full rebuild of the index.
- A user can view which of their readable projects and branches are
  currently indexed, filtered the same way search results are — this
  listing never reveals projects/branches the user cannot read.

## Background

A dedicated code search engine is good at answering "which files across many
repositories match this query" quickly, but on its own it has no concept of
Gerrit users, sessions, or access control — it will return whatever a query
matches unless something tells it, per request, which projects and
branches that particular request is allowed to touch.

Gerrit, conversely, already knows exactly this: whether a given user can
read a given project and ref, using its own existing permission model. What
is missing is the piece that sits between the two — something that lives
inside Gerrit, knows who is asking, derives what they may read, and is the
only thing trusted to make that decision.

At any real scale, deriving a user's allowed scope by re-checking Gerrit's
access rules synchronously on every keystroke-driven search request does
not work — it would make search slow in proportion to how complex an
installation's access rules are, independent of how good the underlying
search engine itself is. The design below is built around computing and
caching that allowed scope ahead of time, and accepting a small, bounded
staleness window in exchange for search staying fast.

Because a single search engine instance cannot practically hold and serve
an arbitrarily large, constantly-changing code corpus, the design also
needs a way to spread the indexed content and query load across many
search instances, and to keep that fleet rebalanced and up to date without
service interruption — none of which the search engine provides on its
own.

## Possible Solution: Gerrit-Aware Search Platform

### Overview

Gerrit remains the identity and trust boundary for every search request.
A logged-in user's query flows from Gerrit's own UI, through a Gerrit-side
component that knows the user and their allowed scope, to a separate search
platform responsible for indexing code and executing scoped queries across
many search instances. The search platform is only ever told a query
together with the scope it's allowed to touch — it never sees, and never
needs to know, why that scope is what it is.

The full step-by-step walkthrough, for both the query path and the
indexing path, is in End-to-End Flow below.

![Zoekt-based code search high-level architecture](images/zoekt-search-architecture.drawio.png)

### Components

The design is built from a small number of responsibilities, split across
two independent systems: one on the Gerrit side, one forming a standalone
search platform.

**On the Gerrit side:**

- **Search plugin** — the only user-facing entry point and trust boundary.
  Requires a logged-in Gerrit user, resolves their identity through
  whichever authentication method the deployment already uses, attaches
  that user's allowed scope, and returns results in Gerrit's own UI, with
  each result linking out to Gitiles for viewing the matched file. Also
  exposes, for the same allowed scope, which of the user's readable
  projects and branches are currently indexed.
- **Visibility awareness** — continuously derives, from Gerrit's real
  access control rules, which projects and branches each group of users
  can read, and keeps that answer ready so a search request never has to
  wait on it.

**In the search platform (independent of Gerrit, reusable by any caller
that can supply a valid scope):**

- **Search gateway** — the entry point for scoped queries. Intersects the
  caller-supplied scope with any filters already in the query, routes to
  the right search instances, fans out, and merges results.
- **Search instances** — hold indexed content and execute the actual scoped
  search against it.
- **Indexing pipeline** — keeps indexed content up to date as repositories
  change, and publishes the result as a new index generation.
- **Artifact store** — holds published index generations durably, so
  search instances can fetch a generation independently of the indexing
  pipeline that produced it.
- **Control plane** — tracks which generation is current for which search
  instances and which projects/branches each generation covers, tracks
  health, and coordinates rollout of new generations without a full-fleet
  restart.

This split is deliberate: the search platform never needs to understand
Gerrit's users, sessions, or access rules — it only ever enforces whatever
scope it is handed. All Gerrit-specific knowledge stays on the Gerrit side.

### End-to-End Flow

**Query flow — a user searches:**

1. A logged-in Gerrit user submits a query through Gerrit's own UI.
2. The **search plugin** resolves the user's identity and asks
   **visibility awareness** for that user's currently known allowed scope
   — normally an already-computed answer, occasionally a short, bounded
   wait if this is the first time that user's group has ever been asked.
3. The search plugin forwards the query together with the allowed scope to
   the **search gateway**. The scope always travels separately from the
   query text, so a user's own query can never be used to widen it.
4. The **search gateway** intersects any project/branch filters already in
   the query with the supplied scope, and routes the narrowed query to the
   relevant **search instances**.
5. Each search instance evaluates the query only against the content it
   was told is in scope, and returns its matches.
6. The search gateway merges, ranks, and returns the combined results to
   the search plugin, which renders them back to the user.

Three outcomes are always distinguished: normal results; "no results"
(covering both a genuinely empty match and a user with no readable scope —
deliberately indistinguishable, so a lack of access can never be inferred
from the response); and "search unavailable" (the search platform itself
could not be reached), so an outage is visible rather than mistaken for an
empty result.

**Coverage flow — a user checks what's indexed:**

1. A logged-in Gerrit user asks the search plugin which of their readable
   projects/branches are indexed.
2. The search plugin asks **visibility awareness** for the user's allowed
   scope, the same way it does for a search query.
3. The search plugin asks the **search gateway** which projects/branches
   in that scope the **control plane** currently reports as indexed, and
   returns the intersection to the user — never revealing coverage for a
   project/branch outside the user's allowed scope.

**Indexing flow — keeping the index current:**

1. A change to a Gerrit repository (a push, submit, or merge) becomes
   available to the indexing pipeline through Gerrit's existing repository
   replication, with no separate export step.
2. The **indexing pipeline** picks up changed content — driven both by
   Gerrit's own change notifications and by a periodic sweep that catches
   anything missed — and rebuilds the affected index content according to
   the deployment's branch-coverage policy (see [Branch Policy](#branch-policy)).
3. Newly built index content is published to the **artifact store** as an
   immutable generation, and the **control plane** records that the
   generation now exists and which search instances should have it.
4. **Search instances** watch the control plane for new generations, pull
   the generation's content from the artifact store, and validate it
   locally before serving it — on their own schedule, with no coordinated,
   all-at-once switch-over, so the fleet converges as each instance
   catches up.

### Branch Policy

Which branches get indexed and access-checked per project is a deployment
decision, not a fixed requirement of this design. A deployment can start
by indexing only default branches and expand from there — to a broader
active-branch set, to specific long-lived branches, or further — once it
has measured the actual demand, storage cost, and access-check cost of
doing so for its own repositories. Nothing in this design requires
starting anywhere other than the smallest option.

### Scalability

Indexed content and query load are spread across many search instances,
and that fleet can grow, shrink, or be rebalanced independently of the
Gerrit-side components — a busier search platform does not require
touching Gerrit, and vice versa.

The visibility-awareness component scales with the number of Gerrit access
groups and projects, not with the number of users or the rate of search
requests, since it computes and caches an answer per group rather than per
user or per request.

The largest scale risk for any given deployment is how many branches it
chooses to index — a large or unevenly distributed branch count drives
indexing, storage, and access-derivation cost independently of query
volume, which is why branch coverage is treated as a deployment policy
decision to be measured, not a fixed default (see [Branch Policy](#branch-policy)).

Visibility-derivation cost and refresh interval must be tuned per
deployment: measure the cost of deriving allowed scope from Gerrit's access
rules, and tune how often it is refreshed against that measurement, rather
than assuming one interval fits every installation. A new or previously
unseen account/group adds a bounded, synchronous derivation cost to its
first query — trading a slower first request for not returning empty
results to a new user — and that timeout should be tuned per deployment
as well.

### Alternatives Considered

**Expose the underlying search engine directly to users**

Simpler to deploy, but rejected outright: a general-purpose search engine
has no concept of Gerrit sessions or access rules, so exposing it directly
would mean either no access control on search results, or duplicating
Gerrit's access model inside a system that was never designed to hold it.

**Have the search platform enforce Gerrit's access rules itself**

Would require the search platform to understand Gerrit's permission model
in depth, risking the two implementations drifting apart over time, and
tightly coupling a platform that should be able to serve any caller to one
specific host application's security model. Keeping all access-control
knowledge on the Gerrit side, and having the search platform simply
enforce whatever scope it's handed, keeps the two systems cleanly
separated.

**Off-the-shelf commercial or hosted code search products**

Worth evaluating independently on their own merits (Gerrit integration,
access-control parity, scale, operational fit), but out of scope for this
design, which assumes building and operating the search platform
in-house.

### Pros and Cons

Pros:

- Because the search platform's gateway only ever consumes a
  caller-supplied scope, it is not limited to serving the Gerrit plugin —
  any future caller that can supply a valid scope could reuse the same
  platform without new Gerrit-specific work on the search platform side.
- Gerrit and the search platform can each be upgraded, scaled, or rolled
  back on their own schedule, without coordinating a joint release.

Cons:

- Debugging a bad result is harder than with a single embedded system: a
  wrong or missing result could stem from a stale visibility scope, a
  gateway routing issue, or an indexing problem, and diagnosing it means
  correlating state across two independently operated systems.
- Every query takes on an extra network hop (plugin to gateway to search
  instances) that a search engine embedded directly in Gerrit would not
  have.

### Implementation Plan

1. Land this design.
2. Build the Gerrit-side search plugin and visibility-awareness component.
3. Build the search platform's gateway, indexing pipeline, control plane,
   and search instances, and validate against a subset of real
   repositories.
4. Roll out search starting with default-branch coverage.
5. Measure real branch activity and access-derivation cost, and expand
   branch coverage per Branch Policy based on those measurements.

## Conclusion

Proceed with a Gerrit-aware search platform, not direct exposure of the
underlying search engine.

The recommended initial deployment is:

- The Gerrit search plugin as the sole user-facing entry point.
- A private search platform that only ever receives project/branch-scoped
  queries, never a raw, unscoped query.
- Default-branch indexing first, expanding only after measuring real
  demand and cost.
- Search capacity and index rollout managed independently of Gerrit itself.

This gives Gerrit users access-control-respecting code search while
keeping the underlying search platform free to scale on its own terms.
