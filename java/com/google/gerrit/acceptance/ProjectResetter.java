// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_USERS;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.RefState;
import com.google.gerrit.server.index.account.AccountIndexer;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.RefPatternMatcher;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

/**
 * Saves the states of given projects and resets the project states on close.
 *
 * <p>Saving the project states is done by saving the states of all refs in the project. On close
 * those refs are reset to the saved states. Refs that were newly created are deleted.
 *
 * <p>By providing ref patterns per project it can be controlled which refs should be reset on
 * close.
 *
 * <p>If resetting touches {@code refs/meta/config} branches the corresponding projects are evicted
 * from the project cache.
 *
 * <p>If resetting touches user branches or the {@code refs/meta/external-ids} branch the
 * corresponding accounts are evicted from the account cache and also if needed from the cache in
 * {@link AccountCreator}.
 *
 * <p>At the moment this class has the following limitations:
 *
 * <ul>
 *   <li>Resetting group branches doesn't evict the corresponding groups from the group cache.
 *   <li>Changes are not reindexed if change meta refs are reset.
 *   <li>Changes are not reindexed if starred-changes refs in All-Users are reset.
 *   <li>If accounts are deleted changes may still refer to these accounts (e.g. as reviewers).
 * </ul>
 *
 * Primarily this class is intended to reset the states of the All-Projects and All-Users projects
 * after each test. These projects rarely contain changes and it's currently not a problem if these
 * changes get stale. For creating changes each test gets a brand new project. Since this project is
 * not used outside of the test method that creates it, it doesn't need to be reset.
 */
public class ProjectResetter implements AutoCloseable {
  public static class Builder {
    public interface Factory {
      Builder builder();
    }

    private final GitRepositoryManager repoManager;
    private final AllUsersName allUsersName;
    @Nullable private final AccountCreator accountCreator;
    @Nullable private final AccountCache accountCache;
    @Nullable private final AccountIndexer accountIndexer;
    @Nullable private final ProjectCache projectCache;

    @Inject
    public Builder(
        GitRepositoryManager repoManager,
        AllUsersName allUsersName,
        @Nullable AccountCreator accountCreator,
        @Nullable AccountCache accountCache,
        @Nullable AccountIndexer accountIndexer,
        @Nullable ProjectCache projectCache) {
      this.repoManager = repoManager;
      this.allUsersName = allUsersName;
      this.accountCreator = accountCreator;
      this.accountCache = accountCache;
      this.accountIndexer = accountIndexer;
      this.projectCache = projectCache;
    }

    public ProjectResetter build(ProjectResetter.Config input) throws IOException {
      return new ProjectResetter(
          repoManager,
          allUsersName,
          accountCreator,
          accountCache,
          accountIndexer,
          projectCache,
          input.refsByProject);
    }
  }

  public static class Config {
    private final Multimap<Project.NameKey, String> refsByProject;

    public Config() {
      this.refsByProject = MultimapBuilder.hashKeys().arrayListValues().build();
    }

    public Config reset(Project.NameKey project, String... refPatterns) {
      List<String> refPatternList = Arrays.asList(refPatterns);
      if (refPatternList.isEmpty()) {
        refPatternList = ImmutableList.of(RefNames.REFS + "*");
      }
      refsByProject.putAll(project, refPatternList);
      return this;
    }
  }

  @Inject private GitRepositoryManager repoManager;
  @Inject private AllUsersName allUsersName;
  @Inject @Nullable private AccountCreator accountCreator;
  @Inject @Nullable private AccountCache accountCache;
  @Inject @Nullable private AccountIndexer accountIndexer;
  @Inject @Nullable private ProjectCache projectCache;

  private final Multimap<Project.NameKey, String> refsPatternByProject;
  private final Multimap<Project.NameKey, RefState> savedRefStatesByProject;

  private Multimap<Project.NameKey, String> keptRefsByProject;
  private Multimap<Project.NameKey, String> restoredRefsByProject;
  private Multimap<Project.NameKey, String> deletedRefsByProject;

  private ProjectResetter(
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      @Nullable AccountCreator accountCreator,
      @Nullable AccountCache accountCache,
      @Nullable AccountIndexer accountIndexer,
      @Nullable ProjectCache projectCache,
      Multimap<Project.NameKey, String> refPatternByProject)
      throws IOException {
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.accountCreator = accountCreator;
    this.accountCache = accountCache;
    this.accountIndexer = accountIndexer;
    this.projectCache = projectCache;
    this.refsPatternByProject = refPatternByProject;
    this.savedRefStatesByProject = readRefStates();
  }

  @Override
  public void close() throws Exception {
    keptRefsByProject = MultimapBuilder.hashKeys().arrayListValues().build();
    restoredRefsByProject = MultimapBuilder.hashKeys().arrayListValues().build();
    deletedRefsByProject = MultimapBuilder.hashKeys().arrayListValues().build();

    restoreRefs();
    deleteNewlyCreatedRefs();
    evictCachesAndReindex();
  }

  /** Read the states of all matching refs. */
  private Multimap<Project.NameKey, RefState> readRefStates() throws IOException {
    Multimap<Project.NameKey, RefState> refStatesByProject =
        MultimapBuilder.hashKeys().arrayListValues().build();
    for (Map.Entry<Project.NameKey, Collection<String>> e :
        refsPatternByProject.asMap().entrySet()) {
      try (Repository repo = repoManager.openRepository(e.getKey())) {
        Collection<Ref> refs = repo.getAllRefs().values();
        for (String refPattern : e.getValue()) {
          RefPatternMatcher matcher = RefPatternMatcher.getMatcher(refPattern);
          for (Ref ref : refs) {
            if (matcher.match(ref.getName(), null)) {
              refStatesByProject.put(e.getKey(), RefState.create(ref.getName(), ref.getObjectId()));
            }
          }
        }
      }
    }
    return refStatesByProject;
  }

  private void restoreRefs() throws IOException {
    for (Map.Entry<Project.NameKey, Collection<RefState>> e :
        savedRefStatesByProject.asMap().entrySet()) {
      try (Repository repo = repoManager.openRepository(e.getKey())) {
        for (RefState refState : e.getValue()) {
          if (refState.match(repo)) {
            keptRefsByProject.put(e.getKey(), refState.ref());
            continue;
          }
          Ref ref = repo.exactRef(refState.ref());
          RefUpdate updateRef = repo.updateRef(refState.ref());
          updateRef.setExpectedOldObjectId(ref != null ? ref.getObjectId() : ObjectId.zeroId());
          updateRef.setNewObjectId(refState.id());
          updateRef.setForceUpdate(true);
          RefUpdate.Result result = updateRef.update();
          checkState(
              result == RefUpdate.Result.FORCED || result == RefUpdate.Result.NEW,
              "resetting branch %s in %s failed",
              refState.ref(),
              e.getKey());
          restoredRefsByProject.put(e.getKey(), refState.ref());
        }
      }
    }
  }

  private void deleteNewlyCreatedRefs() throws IOException {
    for (Map.Entry<Project.NameKey, Collection<String>> e :
        refsPatternByProject.asMap().entrySet()) {
      try (Repository repo = repoManager.openRepository(e.getKey())) {
        Collection<Ref> nonRestoredRefs =
            repo.getAllRefs()
                .values()
                .stream()
                .filter(
                    r ->
                        !keptRefsByProject.containsEntry(e.getKey(), r.getName())
                            && !restoredRefsByProject.containsEntry(e.getKey(), r.getName()))
                .collect(toSet());
        for (String refPattern : e.getValue()) {
          RefPatternMatcher matcher = RefPatternMatcher.getMatcher(refPattern);
          for (Ref ref : nonRestoredRefs) {
            if (matcher.match(ref.getName(), null)
                && !deletedRefsByProject.containsEntry(e.getKey(), ref.getName())) {
              RefUpdate updateRef = repo.updateRef(ref.getName());
              updateRef.setExpectedOldObjectId(ref.getObjectId());
              updateRef.setNewObjectId(ObjectId.zeroId());
              updateRef.setForceUpdate(true);
              RefUpdate.Result result = updateRef.delete();
              checkState(
                  result == RefUpdate.Result.FORCED,
                  "deleting branch %s in %s failed",
                  ref.getName(),
                  e.getKey());
              deletedRefsByProject.put(e.getKey(), ref.getName());
            }
          }
        }
      }
    }
  }

  private void evictCachesAndReindex() throws IOException {
    evictAndReindexProjects();
    evictAndReindexAccounts();

    // TODO(ekempin): Evict groups from cache if group refs were modified.
    // TODO(ekempin): Reindex changes if starred-changes refs in All-Users were modified.
  }

  /** Evict projects for which the config was changed. */
  private void evictAndReindexProjects() throws IOException {
    if (projectCache == null) {
      return;
    }

    for (Project.NameKey project :
        Sets.union(
            projectsWithConfigChanges(restoredRefsByProject),
            projectsWithConfigChanges(deletedRefsByProject))) {
      projectCache.evict(project);
    }
  }

  private Set<Project.NameKey> projectsWithConfigChanges(
      Multimap<Project.NameKey, String> projects) {
    return projects
        .entries()
        .stream()
        .filter(e -> e.getValue().equals(RefNames.REFS_CONFIG))
        .map(Map.Entry::getKey)
        .collect(toSet());
  }

  /** Evict accounts that were modified. */
  private void evictAndReindexAccounts() throws IOException {
    Set<Account.Id> deletedAccounts = accountIds(deletedRefsByProject.get(allUsersName));
    if (accountCreator != null) {
      accountCreator.evict(deletedAccounts);
    }
    if (accountCache != null || accountIndexer != null) {
      Set<Account.Id> modifiedAccounts =
          new HashSet<>(accountIds(restoredRefsByProject.get(allUsersName)));

      if (restoredRefsByProject.get(allUsersName).contains(RefNames.REFS_EXTERNAL_IDS)
          || deletedRefsByProject.get(allUsersName).contains(RefNames.REFS_EXTERNAL_IDS)) {
        // The external IDs have been modified but we don't know which accounts were affected.
        // Make sure all accounts are evicted and reindexed.
        try (Repository repo = repoManager.openRepository(allUsersName)) {
          for (Account.Id id :
              accountIds(
                  repo.getAllRefs().values().stream().map(r -> r.getName()).collect(toSet()))) {
            evictAndReindexAccount(id);
          }
        }

        // Remove deleted accounts from the cache and index.
        for (Account.Id id : deletedAccounts) {
          evictAndReindexAccount(id);
        }
      } else {
        // Evict and reindex all modified and deleted accounts.
        for (Account.Id id : Sets.union(modifiedAccounts, deletedAccounts)) {
          evictAndReindexAccount(id);
        }
      }
    }
  }

  private void evictAndReindexAccount(Account.Id accountId) throws IOException {
    if (accountCache != null) {
      accountCache.evict(accountId);
    }

    if (accountIndexer != null) {
      accountIndexer.index(accountId);
    }
  }

  private Set<Account.Id> accountIds(Collection<String> refs) {
    return refs.stream()
        .filter(r -> r.startsWith(REFS_USERS))
        .map(r -> Account.Id.fromRef(r))
        .filter(Objects::nonNull)
        .collect(toSet());
  }
}
