
Storage of Check Data in Gerrit

Status: (Draft)

Authors: ekempin@google.com

Last Updated: 2019-02-14

Objective
=========

Define how check data (checker configuration and check details) should be stored in Gerrit.

Requirements for the storage format:

*   Must scale for a high number of checkers per repository.
*   Accessing the data that is needed for the following actions must be fast:

*   Creating a check
*   Modifying/deleting a check
*   Viewing Check status on the changes dashboard
*   Viewing Check status on the change screen

*   Updating check details must be quick.
*   Updating check details by multiple requests in parallel for the same patch set must work.
*   Must not affect overall Gerrit performance.
*   It must be possible to update check details and robot comments atomically.
*   Should be consistent with how other Gerrit data is stored in NoteDb.
*   For checker configs the log of actions must be recorded. This is mainly required for debugging purposes so that for each change it can be found out which checkers existed at the point in time when a change was submitted.
*   For check details the log of actions should be recordable. For now this is not required and for performance reasons we may squash historical data for old runs, but if this becomes a requirement the storage design should be able to support it.

Background
==========

The Gerrit backend team is working on [first class support for CI integration](https://gerrit-review.googlesource.com/214733). For this Gerrit needs to store:

*   configurations for checker and
*   for all checkers that apply to a change check details on patch set level.

This document only discusses how the check data should be stored in Gerrit, not the purpose of the check data.

Gerrit uses the term checkerfor all tools that do checks on patch sets. This includes CI builds and code analysers.

The following sections give a brief overview over the check data that needs to be stored in Gerrit (for more details have a look at the aforementioned design document).

Primary Data to store
---------------------

### Checker Configuration

The configuration of a checker, defines for which repositories and changes the checker applies.

For each checker we have to store the following properties:

*   Checker UUID (must be unique per Gerrit host, should be globally unique)
*   Display name
*   URL
*   Checker Status (enabled/disabled/deleted)
*   Repository (only exact match for now, can be a prefix, regular expression or list later)
*   Change query matching the changes for which the checker applies
*   Flag whether the checker is required or optional for submit

### Check Details

A detailed status of a checker on a patch set.

For every patch set we have to store check details for each checker that applies to the change.

Check details have the following properties:

*   Checker UUID
*   Check UUID
*   Check State (not started, scheduled, running, succeeded, failed, not relevant)
*   URL

Derived Data
------------

### Combined Check State

The combined check state is the summary status of all checkers (including optional ones) that apply to a change, can be Failed, Warning, In Progress, Passedor Unknown.

The combined check state is computed per change from the check details of the current patch set taking the checker configs into account.

An alternative term for “combined check state” is “aggregated check state” (in this document we use “combined check state”, but other documents may relate to it as “aggregated check state”).

Scale
=====

For setups that make extensive use of CI/analyzer systems (like Android and Chrome), we project in the ~100s of checks per patch set. Our internal server base already exceeds this number of individual checks, hence we assume 500 checks for scale.

According to the design (see section “Integration per CI/analyzer system or per build/analyzer”) CI/analyzer systems can choose between a tight integration (one checker per build/analyzer) and a loose integration (one checker per CI/analyzer system). The number of checkers that we must expect per repository depends very much on which integration mode CI/analyzer systems choose. In a worst case (all CI/analyzer systems choose tight integration) we must be able to handle 500 checkers per repository. However, our current expectation is that CI systems choose the tight integration while analyzer systems opt for the loose integration. In this case the expected number of checkers per repository is much lower (5 builds + 5 analyzer systems = 10 checkers).

To evaluate the performance of the design we assume 5000 repositories per host. This means for the worst case the design should be able to scale up to 2.5 million checkers (500 checkers x 5000 repositories). For the more likely scenario where analyzer systems choose loose integration we only need to handle 50.000 checkers (10 checkers x 5000 repositories).

The total number of checkers will also be lower if checkers are configured to apply to multiple repositories, instead of having the same checker for each repository (not planned for the initial version, but may be considered later).

We are aware that huge numbers of refs in a repository lead to poor performance. This is why we should be careful with adding large numbers of new refs.

To estimate the impact of new refs we looked inside Google at our largest repositories. The top repositories have ~2.1 million and ~1.4 million refs. We conclude that having ref numbers of this magnitude in a repository is okay.

Since many of the possible designs add new refs per change it is also important to know the highest number of changes that we currently have per repository and host. The top repository has ~1 million changes, the second highest number of changes in a repository is ~0.4 million. Per host the highest number of changes are: ~5 million, ~5 million, ~3 million, ~2 million etc.

Check details needs to be stored per patch set and applying checkers. If a repository can have up to ~500 checkers and ~1 million changes, we can end up with 1.5 billion check details (assuming 3 patch sets per change, 500 x 1 million x 3).

Inside Google, a ref update takes ~1s, due to the globally consistent writes, and Git replication. Updating multiple refs within a batch is almost as fast as updating a single ref.

Overview
========

The check configurations should be stored in the All-Projectsrepository, each in a separate refs/checkers/<sharded-checker-UUID> ref.

The check details should be stored in the source repositories in a new ref per change, next to the existing change refs for patch sets, the NoteDb change metadata and robot comments.

Detailed Design
===============

Use Cases that need to access/update check data
-----------------------------------------------

This section describes the use cases that need to access check data.

### Uses cases that must perform well

1.  Display the check details on the change screen.
2.  Display the combined check state for each change on a dashboard.
3.  Display the check details for a change on a change dashboard (when clicking on the combined check state of a change to see check details).
4.  Reindex of a change.
5.  Get pending checks for a checker, across all changes, optionally filtered by check state.
6.  Set/update the check details on a patch set.
7.  Upload of a new patch set.
8.  Submit a change
9.  Retrigger one checker for a change.
10.  Retrigger all checkers for a change.

To ensure a good performance for these use cases the following operations must be fast:

*   find all checkers that apply to a change
*   get/set the check details of a checker for a patch set
*   get the combined checker state for one change
*   find all changes to which a checker applies
*   find all changes to which a checker applies by check state

### Uses cases where performance is not critical

1.  Add a checker.
2.  Update of a checker.
3.  Get/List checker.

Storage of Checker Configs
--------------------------

All checker configurations should be stored in the All-Projectsrepository, each in a separate refs/checkers/<sharded-checker-UUID>ref. Each checker ref contains a checker.config file that contains the checker configuration. The checker configuration is stored as a Git config file:

```
All-Projects :
|→ refs/checkers/<sharded-UUID-checker-1>
|           \\→ checker.config → \[checker\]
|                                 name = <name-1>
|                                 url = <URL-1>
|                                 repo = <repo-1>
|                                 query = branch:master
|                                 …
|→ refs/checkers/<sharded-UUID-checker-2>
|           \\→ checker.config → \[checker\]
|                                 name = <name-2>
|                                 url = <URL-2>
|                                 repo = <repo-2>
|                                 query = \*
|                                 …
\\→ refs/checkers/<sharded-UUID-checker-n>
            \\→ checker.config → \[checker\]
                                  name = <name-n>
                                  url = <URL-n>
                                  repo = <repo-m>
                                  query = ext:java ext:js
```

Using the checker UUID in the ref name implies that Gerrit controls the format of the UUID (e.g. it is ensured that it doesn’t contain problematic characters such as ‘/’). If this is not the case, we need to compute a SHA1 from the checker UUID and then use the SHA1 in the ref name.

To avoid that checker configs need to be read and parsed on every access they should be cached. The checker cache provides access to a checker configuration by checker UUID. If the checker configuration is not cached yet, it is read from the refs/checker/<sharded-UUID>ref in All-Projects. If a refs/checkers/<sharded-UUID> ref is updated the entry for that checker is evicted from the cache.

The cache should either be an in-memory cache or -if backed by persistent storage- a cache with immutable values.

To ensure efficient lookups of checkers that apply to a repository we explicitly store the list of matching checkers for each repository. For this there is one refs/meta/checkersnotes branch in All-Projects that maps repository SHA1s to sorted lists of matching checker UUID’s:

```
All-Projects → refs/meta/checkers:
|-- <SHA1-repo-1> → <UUID-checker-1>
|                   <UUID-checker-2>
|                             <UUID-checker-5>
|-- <SHA1-repo-2> → <UUID-checker-1>
|                   <UUID-checker-4>
```

The repository SHA1 is computed from the repository name. Since checkers with the status disabledand deleted are not relevant for most use-cases the checker lists should only contain UUIDs of active checkers.

The list of checkers that apply to a repository is atomically updated whenever a checker is added or updated (if the repositoryor the status property is changed), so that it is ensured that the checker lists are always up-to-date.

To make the accessto matching checkers for a repository even faster there may be a cache that provides access to the checker list for a repository. If the checker list is not cached yet, it is read from the Git node in refs/meta/checkers. If a new checker is added or the repository field of a checker is updated we must evict the lists for all repositories that are affected by this.

Reasons for this design:

*   Storing all checkers in a single repository guarantees that each checker UUID maps to exactly one checker config at a point in time.
*   The checker configs can be easily cached. There is no need to reload all checker configs if one checker config changes.
*   The checker configurations are kept separate from other project configuration and hence can be easily hidden from project owners and be guarded by own access controls.
*   It is possible to support checkers that apply to multiple or all repositories (not a requirement for now, but we might want this in future).
*   The log of changes is recorded in the Git history of the checker refs.

### Caveats

The All-Projectsrepository will contain one ref per checker. This means in the worst case these are ~2.5 million refs, a number that should be manageable (see Scale section above.

Finding the checkers that apply to a change must be fast. With the proposed design this can be achieved like this:

1.  Get the repository from the change and find all matching checkers for that repository (via cache or by reading the corresponding Git note in refs/meta/checkers).
2.  For each matching checker get the checker config (via the checker cache).
3.  For each checker config check if the change is matched by the contained change query. For a moderately complex change query this takes about ~0.3ms (~0.2ms without parsing the query, in case we have the parsed query cached). Since a repository should have no more than 500 matching checkers this step should not take more than ~150ms.

When a checker is added/updated we must update 2 refs in a batch (refs/checkers/<sharded-checker-UUID>and refs/meta/checkers), which takes ~1s. For checkers that apply to a single repository a single Git note in refs/meta/checkers needs to be updated. If a checker applies to repositories that match a prefix or regular expression (not supported from the beginning), we need to go through the list of all repositories to find the matching repositories and then update their Git notes. Since checkers may match all repositories, but most hosts have no more than 5000 repositories, in the worst case we need to update 5000 Git notes which seems doable.

If checkers can apply to multiple repositories we may need to create a checker list in refs/meta/checkerswhen a new repository is created (because an existing checker can apply to the new repository). For this we must once go through all checkers to find the matching checkers and populate the checker list in refs/meta/checkers. For optimal performance it would be good if the checker cache can hold all checker configs in memory. For extra safety we should also compute the matching checker list when we want to read it for a repository but a Git node for this repository is missing in refs/meta/checkers.

If many checkers are added/updated in parallel writes to the refs/meta/checkers ref can become a bottleneck (each ref update takes ~1s). In this case we can shard the ref so that the checker lists for the repositories are distributed over multiple refs (refs/meta/checkers/<repo-shard>). To mitigate this issue we also want to offer a REST API endpoint that allows to add multiple checkers at once. E.g. if a CI system would offer a way to configure hundreds of checkers for a repository by a single click, this API could be used to add all checkers at once and hence we would need to update the refs/meta/checkers ref only once, resulting in less update collisions for this ref.

### Alternatives Considered

1.  Same as the proposed design, but store the checker refs in a new All-Checkers repository:

*   All-Projectsis a critical repository. E.g. if we fail to read access rights from it the whole host can become unusable. Adding ~2.5 million checker refs can have side effects on reading the refs/meta/configbranch which contains the access rights. To avoid any risk of breaking All-Projectswe could keep the checker refs in a separate All-Checkersrepository. This is a new magic repository, which needs special treatment. This is extra effort and we think that realistically we will rather have only 50.000 checkers per host (see discussion in the Scale section) which can easily be stored in All-Projectswithout issues. This is why we decided to store the checker refs in All-Projectsfor now, and migrate them to a new All-Checkers repository only if the number of checkers grows too large. We think that such a migration can be easily done with only a little effort.

2.  Store the checker configs in the All-Projectsrepository in a new refs/meta/checkersref that contains a <checker-UUID>.config for each checker:

*   Caching the checker configs would be based on the SHA1 of the refs/meta/checkers ref, similar to how the external IDs cache works. This means that if the cache is found to be stale we would need to reload all checker configs. From external IDs we know that this storage scheme has bad long-tail latency. Since the max number of external IDs per host is much lower than the anticipated max number of checker in the worst case (~2.5 million), we can be sure that this approach wouldn’t scale well for this number of checkers.

3.  Store the checker config in the source repositoriesto which they apply (either in refs/meta/config or in a separate refs/meta/checkers ref):

*   Since the storage of the checker configs is distributed over multiple repositories it would be hard to guarantee that each checker UUID can always be resolved to exactly one checker config (e.g. 2 repositories may define checkers with the same UUID).

4.  Store each checker config in a checkers/<checker-UUID>file in the refs/meta/configbranch of the All-Projects repository:

*   Project owners would have access to this data (might be okay since this is the All-Projects projects and only administrators should have access to it).
*   The SHA1 of the refs/meta/config branch also changes if non-checker data is updated, leading to unnecessary invalidations of the checker config cache.
*   Changing one checker config would invalidate the whole checker config cache.

5.  Store the checker configs in a SQL powered database:

*   Storage would be inconsistent with other Gerrit data.
*   Requires re-introducing a database layer in Gerrit.

Storage of Check Details
------------------------

The check details for a change will be stored in the source repository in a refs/changes/<sharded-change-number>/checks notes branch. This notes branch contains a file for each patch set (commit SHA1). The files contain the check details for all checkers that apply for the patch set as JSON object:

```
<source repository> → refs/changes/<sharded-change-number>/checks:

|-- <SHA1-patch-set-1> → \[

|                          {

|                            “uuid”: “<UUID-checker-1>”,

|                            “status”: “failed”,

|                            “message”: “<message>”,

|                            “url”: “<URL>”,

|                            ...

|                          },

|                          {

|                            “uuid”: “<UUID-checker-2>”,

|                            “status”: “succeeded”,

|                            “message”: “<message>”,

|                            “url”: “<URL>”,

|                            ...

|                          },

|                          ...

|                        \]

\\-- <SHA1-patch-set-2> → \[

                           {

                             “uuid”: “<UUID-checker-1>”,

                             “status”: “running”.

                             “message”: “<message>”,

                             “url”: “<URL>”,

                             ...

                           },

                           {

                             “uuid”: “<UUID-checker-2>”,

                             “status”: “scheduled”,

                             “message”: “<message>”,

                             “url”: “<URL>”,

                             ...

                           },

                           ...

                         \]
```
Since this is a Git notes branch in practice it can happen that the files in this branch are sharded over multiple directories (as for all Git notes branches).

The commit message should contain footers for the patch set and the checker for which check details have been added/updated. This allows to filter the history by patch set and checker (if we decide to keep the history).

Reasons for this design:

*   Adds only a moderate number of refs (1 extra ref per change), these refs are distributed over all source repositories.
*   The naming and the location of the new ref with the check details is consistent with the existing change refs, the NoteDb change meta ref and the robot comments ref.
*   Allows atomic update of check details and robot comments since the refs where this data is stored live in the same repository.
*   Allows fast access of all check details for a change and patch set (requires reading one Git note from a notes branch).
*   The history of check details may be preserved (at the moment this is not required and for performance reasons we may squash the data on update for now).
*   If a repository has an excessive number of checkers or changes only the performance for this one repository is affected.

A drawback with this design is that colliding updates to the refs/changes/<sharded-change-number>/checksref are likely to happen if many checkers run in parallel for the same change and hence updates to the ref may fail with LOCK\_FAILURE. This can be mitigated by automatically retrying updates to this ref, but this may still not be good enough for changes that have a lot of checkers. We plan to solve this by sharding and compaction (see next section).

### Sharding and Compaction

If having a single ref for all check details of a change becomes a bottleneck, we want to automatically shard the check details over multiple refs.

For writing check details the following steps will be done:

1.  Write directly to the refs/changes/<sharded-change-number>/checks ref (unless a sharded ref for that checker already exists, see 3.).
2.  If this fails with LOCK\_FAILURE, retry with a for a short moment.
3.  If this still fails with LOCK\_FAILURE, write the check details to a sharded ref:  
    refs/changes/<sharded-change-num>/checks/<checker-shard>

*   If a sharded ref exists on write, the write updates the sharded ref.
*   If a sharded ref exists on read, compaction is done.

4.  If this still fails with LOCK\_FAILURE, retry for a longer moment.
5.  If this still fails with LOCK\_FAILURE, give up and fail or repeat from 3. with an additional sharding level.
6.  \[optional\] start asynchronous compaction

If sharded refs exist on read we always try to compact them first. This is required because:

*   For reading check details we must include the updates that were recorded in the sharded refs.

To split the check details over multiple refs we can include the first char/digit of the checker UUID as shard into the ref name: refs/changes/<sharded-change-num>/checks/<checker-shard>

The number of shards per change may be limited by defining a function on the checker UUID to generate the shard-number.

Reasons for sharding and compaction:

*   Avoids LOCK\_FAILURES on write of check details.
*   Does sharding only if it’s really needed and writes would actually fail.
*   Compaction into a single ref ensures that we avoid having too many refs.
*   It’s an optimization which can be implemented later when it’s actually needed.

### Caveats

With the proposed design we add 1 additional ref per change (in the compacted form). This would increase the number of refs in our top repository from ~2.1 million to ~3.1 million, which seems manageable.

A concern are LOCK\_FAILURESon parallel updates to the same ref. Each ref update takes about ~1s. This means if in a worst case ~500 checkers on a patch set run in parallel and all want to write their results to the same ref this can easily result in LOCK\_FAILURES(e.g. 100 of the checkers may find that there is nothing to do and return immediately). To meet this concern we automatically shard the updates of check details over multiple refs on need (see section Sharding and Compaction) above).

### Doing compaction takes some time and delays reads. If this gets an issue we can consider compacting in the background.

### Alternatives Considered

1.  Same as the current design but store the check details in the Git notes as Git config files:

*   From storing comments we have learned that JSON is a better format when it comes to storing larger amounts of structured data.
*   Using JSON is consistent with how comments are stored.

2.  Store all check details in a separate All-Checkers repository that has one ref per repository, change, patch set and checker (refs/checkers/<repo-name>/<sharded-change-num>/<ps-num>/<checker-UUID>).

*   The repository name cannot be part of the ref name, since that makes the ref name too long.
*   This results in too many refs.
*   Check details and robot comments cannot be updated atomically since the refs in which this data is stored live in different repositories.

3.  Same as 2., but compute some SHA1 out of repository name, change, patch set and checker: refs/checkers/<SHA1>

*   The ref name length is not a concern anymore, but the other disadvantages of 2. still apply.

4.  Store the check details in a separate All-Checkersrepository that has one ref per patch set: refs/checkers/<sharded-change-num>/<ps-num> refs

*   This results in too many refs.
*   Check details and robot comments cannot be updated atomically since the refs in which this data is stored live in different repositories.

5.  Same as 4., but store the refs in the source repositories:

*   This still results in too many refs.

6.  Same as 4., but store the refs in a separate sibling repository (e.g. <repo-name>.checkers).

*   This still results in too many refs.

7.  Store the check details in a relational DB:

*   Storage would be inconsistent with other Gerrit data.
*   Requires re-introduction of SQL layer in Gerrit.
*   Doesn’t allow atomic updates of check details and robot comments.

8.  For compaction: Write sharded refs while the change is open and do compaction on submit of the change (do this for all changes or only for changes for which many checkers apply):

*   May do sharding and compaction unnecessarily.

9.  A considered optimization for the proposed design was to compute the combined check state proactively on update of check details and store it as a footer in commits of the refs/changes/<sharded-change-num>/checks ref so that the combined check state is quickly available whenever needed. This idea was discarded as premature optimization. A problem is that the recorded combined check state can get stale. E.g. if a checker config is updated we must initialize the check details on newly matching changes and hence recompute the combined check state. These updates cannot be atomic with the update of the checker config (update of refs in different repositories) and hence there is a risk that the check details and the combined check state may not get updated for all changes. Also this optimization would make sharding and compaction more difficult (if sharded refs exists, the combined check state in the main ref is outdated).

Implementation of performance critical operations
-------------------------------------------------

The operations for which performance is critical are described above. This section discusses how they can be implemented with the proposed storage design:

1.  find all checkers that apply to a change:  
    There is a cache that allows to retrieve all checkers that apply for a repository. For each returned checker the config can be retrieved through another cache. For each of the returned checker configs it will be checked if the change matches the configured change query.
2.  get/set the check details of a checker for a patch set:  
    The name of the ref that contains the check details can be computed from the patch set and then the ref can be directly read or updated. Getting/Setting check details means reading/updating a Git note.
3.  get the combined check state for one change:  
    The name of the ref that contains the check details can be computed from the change. From this ref we need to load one Git note. In addition we need to find all checkers that apply to a change (see 1.) to know which ones block submit. From this input we are able to compute the combined check state.
4.  find all changes to which a checker applies:  
    Get the checker config from the cache and execute the change query from the config.
5.  find all changes to which a checker applies by checker state:  
    This needs to query and filter all check details of a checker. We either need a huge smart cache for this or a new index of all checks. Likely not required for the initial version.

Caveats
=======

As discussed in the Scale section the high amount of check data that must be stored is a concern. It’s important that the main use cases perform well with such an amount of data and that the overall Gerrit performance is not affected.

While we think that the proposed design is well scaleable (see detailed discussion in the Detailed Design section) the implementation of the storage should be replaceable with limited efforts. To allow this there should be a clean interface for accessing and updating check data. In a worst case we can implement the interface for another storage and then exchange the implementations.

