---
title: " Gerrit Code Review - Metrics"
sidebar: gerritdoc_sidebar
permalink: metrics.html
---
Metrics about Gerrit’s internal state can be sent to external monitoring
systems via plugins. See the [plugin
documentation](dev-plugins.html#metrics) for details of plugin
implementations.

## Metrics

The following metrics are reported.

### General

  - `build/label`: Version of Gerrit server software.

  - `events`: Triggered events.

### Process

  - `proc/birth_timestamp`: Time at which the Gerrit process started.

  - `proc/uptime`: Uptime of the Gerrit process.

  - `proc/cpu/usage`: CPU time used by the Gerrit process.

  - `proc/num_open_fds`: Number of open file descriptors.

  - `proc/jvm/memory/heap_committed`: Amount of memory guaranteed for
    user objects.

  - `proc/jvm/memory/heap_used`: Amount of memory holding user objects.

  - `proc/jvm/memory/non_heap_committed`: Amount of memory guaranteed
    for classes, etc.

  - `proc/jvm/memory/non_heap_used`: Amount of memory holding classes,
    etc.

  - `proc/jvm/memory/object_pending_finalization_count`: Approximate
    number of objects needing finalization.

  - `proc/jvm/gc/count`: Number of GCs.

  - `proc/jvm/gc/time`: Approximate accumulated GC elapsed time.

  - `proc/jvm/thread/num_live`: Current live thread count.

### Caches

  - `caches/memory_cached`: Memory entries.

  - `caches/memory_hit_ratio`: Memory hit ratio.

  - `caches/memory_eviction_count`: Memory eviction count.

  - `caches/disk_cached`: Disk entries used by persistent cache.

  - `caches/disk_hit_ratio`: Disk hit ratio for persistent cache.

### HTTP

  - `http/server/error_count`: Rate of REST API error responses.

  - `http/server/success_count`: Rate of REST API success responses.

  - `http/server/rest_api/count`: Rate of REST API calls by view.

  - `http/server/rest_api/error_count`: Rate of REST API calls by view.

  - `http/server/rest_api/server_latency`: REST API call latency by
    view.

  - `http/server/rest_api/response_bytes`: Size of REST API response on
    network (may be gzip compressed) by view.

### Query

  - `query/query_latency`: Successful query latency, accumulated over
    the life of the process.

### SSH sessions

  - `sshd/sessions/connected`: Number of currently connected SSH
    sessions.

  - `sshd/sessions/created`: Rate of new SSH sessions.

  - `sshd/sessions/authentication_failures`: Rate of SSH authentication
    failures.

### SQL connections

  - `sql/connection_pool/connections`: SQL database connections.

### Topics

  - `topic/cross_project_submit`: number of cross-project topic
    submissions.

  - `topic/cross_project_submit_completed`: number of cross-project
    topic submissions that concluded successfully.

### JGit

  - `jgit/block_cache/cache_used`: Bytes of memory retained in JGit
    block cache.

  - `jgit/block_cache/open_files`: File handles held open by JGit block
    cache.

### Git

  - `git/upload-pack/request_count`: Total number of git-upload-pack
    requests.

  - `git/upload-pack/phase_counting`: Time spent in the *Counting…*
    phase.

  - `git/upload-pack/phase_compressing`: Time spent in the
    *Compressing…* phase.

  - `git/upload-pack/phase_writing`: Time spent transferring bytes to
    client.

  - `git/upload-pack/pack_bytes`: Distribution of sizes of packs sent to
    clients.

### BatchUpdate

  - `batch_update/execute_change_ops`: BatchUpdate change update
    latency, excluding reindexing

  - `batch_update/retry_attempt_counts`: Distribution of number of
    attempts made by RetryHelper (1 == single attempt, no retry)

  - `batch_update/retry_timeout_count`: Number of executions of
    RetryHelper that ultimately timed out

### NoteDb

  - `notedb/update_latency`: NoteDb update latency by table.

  - `notedb/stage_update_latency`: Latency for staging updates to NoteDb
    by table.

  - `notedb/read_latency`: NoteDb read latency by table.

  - `notedb/parse_latency`: NoteDb parse latency by table.

  - `notedb/auto_rebuild_latency`: NoteDb auto-rebuilding latency by
    table.

  - `notedb/auto_rebuild_failure_count`: NoteDb auto-rebuilding attempts
    that failed by table.

  - `notedb/external_id_update_count`: Total number of external ID
    updates.

  - `notedb/read_all_external_ids_latency`: Latency for reading all
    external ID’s from NoteDb.

### Reviewer Suggestion

  - `reviewer_suggestion/query_accounts`: Latency for querying accounts
    for reviewer suggestion.

  - `reviewer_suggestion/recommend_accounts`: Latency for recommending
    accounts for reviewer suggestion.

  - `reviewer_suggestion/load_accounts`: Latency for loading accounts
    for reviewer suggestion.

  - `reviewer_suggestion/query_groups`: Latency for querying groups for
    reviewer suggestion.

### Repo Sequences

  - `sequence/next_id_latency`: Latency of requesting IDs from repo
    sequences.

### Replication Plugin

  - `plugins/replication/replication_latency`: Time spent pushing to
    remote destination.

  - `plugins/replication/replication_delay`: Time spent waiting before
    pushing to remote destination.

  - `plugins/replication/replication_retries`: Number of retries when
    pushing to remote destination.

### License

  - `license/cla_check_count`: Total number of CLA check requests.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

