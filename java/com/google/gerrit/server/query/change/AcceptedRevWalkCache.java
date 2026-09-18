// Copyright (C) 2026 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.query.change;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.cache.PerThreadCache;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.submit.SubmitDryRun;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Query-local cache of the walks used to evaluate {@code conflicts:} predicates.
 *
 * <p>Query post-filter matching is synchronous in {@code QueryProcessor} and {@code
 * PaginatingSource}; cache loaders are also serialized here as a defensive boundary because a
 * RevWalk is not thread-safe. RevCommit instances are bound to their RevWalk, so the parsed
 * accepted commits can only be shared by reusing this walk.
 */
public class AcceptedRevWalkCache implements AutoCloseable {
  public static final class PerThread {
    private static final PerThreadCache.Key<AcceptedRevWalkCache> CACHE_KEY =
        PerThreadCache.Key.create(AcceptedRevWalkCache.class);

    private PerThread() {}

    /** Returns the cache for the current request, or {@code null} when no request cache exists. */
    @Nullable
    public static AcceptedRevWalkCache get(GitRepositoryManager repoManager) {
      PerThreadCache perThreadCache = PerThreadCache.get();
      if (perThreadCache == null) {
        return null;
      }
      return perThreadCache.getAndRegisterForClose(
          CACHE_KEY, () -> new AcceptedRevWalkCache(repoManager));
    }
  }

  @FunctionalInterface
  public interface WalkOperation<T> {
    T run(Repository repo, CodeReviewCommit.CodeReviewRevWalk rw, Set<RevCommit> alreadyAccepted)
        throws Exception;
  }

  private final GitRepositoryManager repoManager;
  private final Map<Project.NameKey, Entry> entries = new HashMap<>();

  @Inject
  public AcceptedRevWalkCache(GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
  }

  public synchronized <T> T run(Project.NameKey project, ObjectId tip, WalkOperation<T> operation)
      throws Exception {
    Entry entry =
        entries.computeIfAbsent(
            project,
            p -> {
              try {
                return new Entry(repoManager.openRepository(p));
              } catch (IOException e) {
                throw new StorageException(e);
              }
            });

    // reset(), unlike dispose(), retains parsed RevObjects. This lets subsequent candidates reuse
    // the walk-bound accepted commits while clearing all traversal state from the prior candidate.
    entry.rw.reset();
    try {
      return operation.run(entry.repo, entry.rw, entry.getAlreadyAccepted(tip));
    } finally {
      entry.rw.reset();
    }
  }

  @Override
  public synchronized void close() {
    for (Entry entry : entries.values()) {
      entry.close();
    }
    entries.clear();
  }

  @VisibleForTesting
  Entry entryForTesting(Project.NameKey project) {
    return entries.get(project);
  }

  static class Entry {
    private final Repository repo;

    // Package-visible and non-final so that tests can substitute a spy to verify reset() calls.
    CodeReviewCommit.CodeReviewRevWalk rw;

    private Set<RevCommit> alreadyAccepted;

    Entry(Repository repo) {
      this.repo = repo;
      rw = CodeReviewCommit.newRevWalk(repo);
      rw.setRetainBody(false);
    }

    Set<RevCommit> getAlreadyAccepted(ObjectId tip) {
      try {
        if (alreadyAccepted == null) {
          // Only look up the accepted object IDs (a potentially expensive ref scan) the first
          // time they are needed for this project.
          Set<RevCommit> parsed = new HashSet<>();
          SubmitDryRun.addCommits(SubmitDryRun.getAlreadyAccepted(repo), rw, parsed);
          alreadyAccepted = parsed;
        }
        Set<RevCommit> accepted = new HashSet<>(alreadyAccepted);
        if (tip != null) {
          accepted.add(rw.parseCommit(tip));
        }
        return accepted;
      } catch (StorageException | IOException e) {
        throw new StorageException("Failed to determine already accepted commits.", e);
      }
    }

    void close() {
      rw.close();
      repo.close();
    }
  }
}
