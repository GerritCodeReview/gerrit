// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.testing;

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CONSISTENCY_CHECKER_FIX;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.HEAD_MODIFICATION;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.INSERT_CHANGES_AND_PATCH_SETS;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.SCHEMA_UPGRADE;

import com.google.common.collect.Sets;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.restapi.RestCollectionCreateView;
import com.google.gerrit.extensions.restapi.RestCollectionModifyView;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.RepositoryCaseMismatchException;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase;
import org.eclipse.jgit.internal.storage.dfs.DfsReftableBatchRefUpdate;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

/** Repository manager that uses in-memory repositories. */
public class InMemoryRepositoryManager implements GitRepositoryManager {
  public static InMemoryRepository newRepository(Project.NameKey name) {
    return new Repo(name);
  }

  public static class Description extends DfsRepositoryDescription {
    private final Project.NameKey name;

    private Description(Project.NameKey name) {
      super(name.get());
      this.name = name;
    }

    public Project.NameKey getProject() {
      return name;
    }
  }

  public static class Repo extends InMemoryRepository {
    private String description;

    private Repo(Project.NameKey name) {
      super(new Description(name));
      setPerformsAtomicTransactions(true);
    }

    @Override
    protected MemRefDatabase createRefDatabase() {
      return new MemRefDatabase() {
        @Override
        public BatchRefUpdate newBatchUpdate() {
          DfsObjDatabase odb = getRepository().getObjectDatabase();
          return new DfsReftableBatchRefUpdate(this, odb) {
            @Override
            public void execute(RevWalk rw, ProgressMonitor pm, List<String> options) {
              getCommands().stream().forEach(this::validateRefUpdateContext);
              super.execute(rw, pm, options);
            }

            private void validateRefUpdateContext(ReceiveCommand cmd) {
              // All special refs must be updated only within API_CALL or as a part of a known
              // worklfow (e.g. create change and patchset on upload or during init).
              if (isSpecialRef(cmd.getRefName())
                  || "refs/meta/gpg-keys".equals(cmd.getRefName())
                  || cmd.getRefName().startsWith("refs/cache-automerge")) {
                return;
              }
              if (cmd.getRefName().equals("HEAD") && RefUpdateContext.hasOpen(HEAD_MODIFICATION)) {
                return;
              }
              if (RefUpdateContext.hasOpen(SCHEMA_UPGRADE)) {
                return;
              }
              if ((RefNames.isRefsChanges(cmd.getRefName())
                      || RefNames.isRefsEdit(cmd.getRefName()))
                  && (RefUpdateContext.hasOpen(INSERT_CHANGES_AND_PATCH_SETS)
                      || RefUpdateContext.hasOpen(CONSISTENCY_CHECKER_FIX)
                      || isApiCall())) {
                return;
              }
              if (RefNames.isTagRef(cmd.getRefName())
                  && RefUpdateContext.hasOpen(RefUpdateType.TAG_MODIFICATION)) {
                return;
              }
              if (RefNames.REFS_REJECT_COMMITS.equals(cmd.getRefName()) && isApiCall()) {
                return;
              }
              if (RefNames.isRefsUsers(cmd.getRefName())
                  && (isApiCall()
                      || RefUpdateContext.hasOpen(RefUpdateType.ACCOUNT_CONFIG_COMMIT)
                      || RefUpdateContext.hasOpen(RefUpdateType.ACCOUNT_QUERIES_COMMIT))) {
                return;
              }
              if (RefNames.isExternalIdRef(cmd.getRefName())
                  && RefUpdateContext.hasOpen(RefUpdateType.EXTERNAL_ID_NOTES_COMMIT)) {
                return;
              }
              if (RefUpdateContext.hasOpen(RefUpdateType.INIT_REPO)
                  || RefUpdateContext.hasOpen(RefUpdateType.INTERNAL_ACTION)
                  || RefUpdateContext.hasOpen(RefUpdateType.DIRECT_PUSH)
                  || RefUpdateContext.hasOpen(RefUpdateType.TEST_SETUP)) {
                return;
              }
              if (RefUpdateContext.hasOpen(RefUpdateType.MERGE_CHANGE)
                  || RefUpdateContext.hasOpen(RefUpdateType.BRANCH_MODIFICATION)
                  || (RefUpdateContext.hasOpen(RefUpdateType.AFTER_CHANGE_SUBMITTED)
                      && RefUpdateContext.hasOpen(RefUpdateType.UPDATE_SUPERPROJECT))) {
                return;
              }
              if (isTestRepoCall()) {
                return;
              }

              //              if(isRestApiCall()) {
              //              }
              //
              checkState(false, cmd.getRefName());
              // checkState(RefUpdateContext.getOpenedContexts().stream().anyMatch(ctx ->
              // ctx.getUpdateType() == RefUpdateType.MERGE_CHANGE), cmd.getRefName());
            }

            private boolean isSpecialRef(String ref) {
              return RefNames.isVersionRef(ref)
                  || RefNames.isNoteDbMetaRef(ref)
                  || RefNames.isConfigRef(ref)
                  || RefNames.isSequenceRef(ref);
            }

            private boolean isApiCall() {
              return Arrays.stream(Thread.currentThread().getStackTrace())
                  .anyMatch(
                      elem -> {
                        try {
                          if (!elem.getMethodName().equals("apply")) {
                            return false;
                          }
                          Class c = Class.forName(elem.getClassName());

                          return RestCollectionModifyView.class.isAssignableFrom(c)
                              || RestModifyView.class.isAssignableFrom(c)
                              || RestCollectionCreateView.class.isAssignableFrom(c);
                        } catch (ClassNotFoundException e) {
                          return false;
                        }
                      });
            }

            private boolean isTestRepoCall() {
              return Arrays.stream(Thread.currentThread().getStackTrace())
                  .anyMatch(
                      elem -> elem.getClassName().equals("org.eclipse.jgit.junit.TestRepository"));
            }
          };
        }
      };
    }

    @Override
    public Description getDescription() {
      return (Description) super.getDescription();
    }

    @Override
    public String getGitwebDescription() {
      return description;
    }

    @Override
    public void setGitwebDescription(String d) {
      description = d;
    }
  }

  private final Map<String, Repo> repos;

  @Inject
  public InMemoryRepositoryManager() {
    this.repos = new HashMap<>();
  }

  @Override
  public synchronized Status getRepositoryStatus(NameKey name) {
    try {
      get(name);
      return Status.ACTIVE;
    } catch (RepositoryNotFoundException e) {
      return Status.NON_EXISTENT;
    }
  }

  @Override
  public synchronized Repo openRepository(Project.NameKey name) throws RepositoryNotFoundException {
    return get(name);
  }

  @Override
  public synchronized Repo createRepository(Project.NameKey name)
      throws RepositoryCaseMismatchException, RepositoryNotFoundException {
    Repo repo;
    try {
      repo = get(name);
      if (!repo.getDescription().getRepositoryName().equals(name.get())) {
        throw new RepositoryCaseMismatchException(name);
      }
    } catch (RepositoryNotFoundException e) {
      repo = new Repo(name);
      repos.put(normalize(name), repo);
    }
    return repo;
  }

  @Override
  public synchronized NavigableSet<Project.NameKey> list() {
    NavigableSet<Project.NameKey> names = Sets.newTreeSet();
    for (DfsRepository repo : repos.values()) {
      names.add(Project.nameKey(repo.getDescription().getRepositoryName()));
    }
    return Collections.unmodifiableNavigableSet(names);
  }

  public synchronized void deleteRepository(Project.NameKey name) {
    repos.remove(normalize(name));
  }

  private synchronized Repo get(Project.NameKey name) throws RepositoryNotFoundException {
    Repo repo = repos.get(normalize(name));
    if (repo != null) {
      repo.incrementOpen();
      return repo;
    }
    throw new RepositoryNotFoundException(name.get());
  }

  private static String normalize(Project.NameKey name) {
    return name.get().toLowerCase();
  }
}
