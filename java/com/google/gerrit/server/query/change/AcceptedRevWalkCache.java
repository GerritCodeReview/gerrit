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
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
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
  @FunctionalInterface
  public interface AcceptedObjectIds {
    Set<ObjectId> get(Repository repo) throws IOException;
  }

  @FunctionalInterface
  public interface WalkOperation<T> {
    T run(Repository repo, CodeReviewCommit.CodeReviewRevWalk rw, Set<RevCommit> alreadyAccepted)
        throws Exception;
  }

  private final GitRepositoryManager repoManager;
  private final Map<Project.NameKey, Entry> entries = new HashMap<>();
  private int resetCount;

  @Inject
  public AcceptedRevWalkCache(GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
  }

  public synchronized <T> T run(
      Project.NameKey project,
      AcceptedObjectIds acceptedObjectIds,
      ObjectId tip,
      WalkOperation<T> operation)
      throws Exception {
    Entry entry = entries.get(project);
    if (entry == null) {
      entry = new Entry(repoManager.openRepository(project));
      entries.put(project, entry);
    }

    // reset(), unlike dispose(), retains parsed RevObjects. This lets subsequent candidates reuse
    // the walk-bound accepted commits while clearing all traversal state from the prior candidate.
    entry.rw.reset();
    resetCount++;
    try {
      return operation.run(
          entry.repo, entry.rw, entry.getAlreadyAccepted(acceptedObjectIds.get(entry.repo), tip));
    } finally {
      entry.rw.reset();
      resetCount++;
    }
  }

  @VisibleForTesting
  int acceptedSetParseCount(Project.NameKey project) {
    Entry entry = entries.get(project);
    return entry == null ? 0 : entry.acceptedSetParseCount;
  }

  @VisibleForTesting
  int resetCount() {
    return resetCount;
  }

  @Override
  public synchronized void close() {
    for (Entry entry : entries.values()) {
      entry.close();
    }
    entries.clear();
  }

  private static class Entry {
    private final Repository repo;
    private final CodeReviewCommit.CodeReviewRevWalk rw;
    private Set<RevCommit> alreadyAccepted;
    private int acceptedSetParseCount;

    Entry(Repository repo) {
      this.repo = repo;
      rw = CodeReviewCommit.newRevWalk(repo);
      rw.setRetainBody(false);
    }

    Set<RevCommit> getAlreadyAccepted(Set<ObjectId> acceptedObjectIds, ObjectId tip) {
      try {
        if (alreadyAccepted == null) {
          Set<RevCommit> parsed = new HashSet<>();
          SubmitDryRun.addCommits(acceptedObjectIds, rw, parsed);
          alreadyAccepted = parsed;
          acceptedSetParseCount++;
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
