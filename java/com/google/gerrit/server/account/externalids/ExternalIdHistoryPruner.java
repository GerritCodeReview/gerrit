// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.account.externalids;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.cache.Cache;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.common.UsedAt.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.git.LockFailureException;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Allows pruning the Git history of refs/meta/external-ids.
 *
 * <p>Since deleting an external ID just creates a new commit to remove the ID but keeps around
 * history on {@code refs/meta/external-ids}, this class is a useful utility to remove commits
 * outside a configured retention window.
 */
@Singleton
public class ExternalIdHistoryPruner {

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final Provider<PersonIdent> serverIdent;
  private final Cache<ObjectId, AllExternalIds> extIdsByAccount;
  private final Duration historyToKeep;

  @Inject
  ExternalIdHistoryPruner(
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      @GerritPersonIdent Provider<PersonIdent> serverIdent,
      @Named(ExternalIdCacheImpl.CACHE_NAME) Cache<ObjectId, AllExternalIds> extIdsByAccount,
      @GerritServerConfig Config config) {
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.serverIdent = serverIdent;
    this.extIdsByAccount = extIdsByAccount;
    this.historyToKeep =
        Duration.ofDays(
            config.getTimeUnit("accounts", null, "externalIdHistoryRetention", 60, TimeUnit.DAYS));
  }

  /**
   * Prunes {@code refs/meta/external-ids} by removing any commit older than the configured
   * retention window. The first commit inside the retention window will have no parents but keep
   * its metadata and still point to the same tree.
   */
  @UsedAt(Project.GOOGLE)
  public void prune() throws IOException {
    try (Repository repo = repoManager.openRepository(allUsersName);
        RevWalk walk = new RevWalk(repo);
        ObjectInserter ins = repo.newObjectInserter()) {
      Ref externalIdRef = repo.exactRef(RefNames.REFS_EXTERNAL_IDS);
      if (externalIdRef == null) {
        return;
      }
      walk.markStart(walk.parseCommit(externalIdRef.getObjectId()));
      RevCommit commit;

      Instant oldestTimestampToKeep = Instant.now().minus(historyToKeep);

      List<RevCommit> commitsToRetain = new ArrayList<>();
      boolean shouldPrune = false;
      while ((commit = walk.next()) != null) {
        checkState(commit.getParentCount() < 2, "external ID branch must have linear history");
        if (commit.getCommitterIdent().getWhenAsInstant().isBefore(oldestTimestampToKeep)) {
          if (walk.next() != null && commitsToRetain.isEmpty()) {
            // There are more old commits, so we want to prune
            commitsToRetain.add(commit);
          }
          shouldPrune = true;
          break;
        }
        commitsToRetain.add(commit);
      }

      if (!shouldPrune || commitsToRetain.isEmpty()) {
        return;
      }

      // Traverse from oldest commit to latest
      Collections.reverse(commitsToRetain);

      // Write new commits with same trees
      ObjectId parent = null;
      for (RevCommit original : commitsToRetain) {
        CommitBuilder cb = new CommitBuilder();
        cb.setTreeId(original.getTree());
        cb.setCommitter(original.getCommitterIdent());
        cb.setAuthor(original.getAuthorIdent());
        cb.setMessage(original.getFullMessage());
        if (parent != null) {
          cb.setParentId(parent);
        }
        parent = ins.insert(cb);
      }

      ins.flush();

      // Update ref
      ObjectId expectedOldId = Iterables.getLast(commitsToRetain).toObjectId();
      RefUpdate u = repo.updateRef(RefNames.REFS_EXTERNAL_IDS);
      u.setForceUpdate(true);
      u.setExpectedOldObjectId(expectedOldId);
      u.setRefLogIdent(serverIdent.get());
      u.setRefLogMessage("Prune external id History", true);
      u.setNewObjectId(parent);
      RefUpdate.Result result = u.update();
      switch (result) {
        case FORCED:
          // The update was successful, so optimistically cache the new state.
          AllExternalIds oldCachedState = extIdsByAccount.getIfPresent(expectedOldId);
          if (oldCachedState != null) {
            extIdsByAccount.put(u.getNewObjectId(), oldCachedState);
          }
          return;
        case LOCK_FAILURE:
          throw new LockFailureException(String.format("Pruning external id history failed"), u);
        default:
          throw new StorageException(
              String.format("Pruning external id history failed with %s", result.name()));
      }
    }
  }
}
