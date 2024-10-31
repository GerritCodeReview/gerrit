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
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.ACCOUNTS_UPDATE;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.BAN_COMMIT;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.BRANCH_MODIFICATION;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.DIRECT_PUSH;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.GPG_KEYS_MODIFICATION;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.GROUPS_UPDATE;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.HEAD_MODIFICATION;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.INIT_REPO;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.MERGE_CHANGE;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.OFFLINE_OPERATION;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.PLUGIN;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.REPO_SEQ;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.TAG_MODIFICATION;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.VERSIONED_META_DATA_CHANGE;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.gpg.PublicKeyStore;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.RepositoryCaseMismatchException;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType;
import com.google.inject.Inject;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.function.Predicate;
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

    /** Validates that a given ref is updated within the expected context. */
    private static class RefUpdateContextValidator {
      /**
       * A configured singleton for ref context validation.
       *
       * <p>Each ref must match no more than 1 special ref from the list below. If ref is not
       * matched to any special ref predicate, then it is checked against the standard rules - check
       * the code of the {@link #validateRefUpdateContext} for details.
       */
      public static final RefUpdateContextValidator INSTANCE =
          new RefUpdateContextValidator()
              .addSpecialRef(RefNames::isSequenceRef, REPO_SEQ)
              .addSpecialRef(RefNames.HEAD::equals, HEAD_MODIFICATION)
              .addSpecialRef(RefNames::isRefsChanges, CHANGE_MODIFICATION, MERGE_CHANGE)
              .addSpecialRef(RefNames::isAutoMergeRef, CHANGE_MODIFICATION)
              .addSpecialRef(RefNames::isRefsEdit, CHANGE_MODIFICATION, MERGE_CHANGE)
              .addSpecialRef(RefNames::isTagRef, TAG_MODIFICATION)
              .addSpecialRef(RefNames::isRejectCommitsRef, BAN_COMMIT)
              .addSpecialRef(
                  name -> RefNames.isRefsUsers(name) && !RefNames.isRefsEdit(name),
                  VERSIONED_META_DATA_CHANGE,
                  ACCOUNTS_UPDATE,
                  MERGE_CHANGE)
              .addSpecialRef(
                  RefNames::isConfigRef,
                  VERSIONED_META_DATA_CHANGE,
                  BRANCH_MODIFICATION,
                  MERGE_CHANGE)
              .addSpecialRef(RefNames::isExternalIdRef, VERSIONED_META_DATA_CHANGE, ACCOUNTS_UPDATE)
              .addSpecialRef(PublicKeyStore.REFS_GPG_KEYS::equals, GPG_KEYS_MODIFICATION)
              .addSpecialRef(RefNames::isRefsDraftsComments, CHANGE_MODIFICATION)
              .addSpecialRef(RefNames::isRefsStarredChanges, CHANGE_MODIFICATION)
              // A user can create a change for updating a group and then merge it.
              // The GroupsIT#pushToGroupBranchForReviewForNonAllUsersRepoAndSubmit test verifies
              // this scenario.
              .addSpecialRef(RefNames::isGroupRef, GROUPS_UPDATE, MERGE_CHANGE);

      private List<Entry<Predicate<String>, ImmutableList<RefUpdateType>>> specialRefs =
          new ArrayList<>();

      private RefUpdateContextValidator() {}

      public void validateRefUpdateContext(ReceiveCommand cmd) {
        String refName = cmd.getRefName();

        if (RefUpdateContextCollector.enabled()) {
          RefUpdateContextCollector.register(refName, RefUpdateContext.getOpenedContexts());
        }
        if (TestActionRefUpdateContext.isOpen()
            || RefUpdateContext.hasOpen(OFFLINE_OPERATION)
            || RefUpdateContext.hasOpen(INIT_REPO)
            || RefUpdateContext.hasOpen(DIRECT_PUSH)) {
          // The action can touch any refs in these contexts.
          return;
        }

        Optional<ImmutableList<RefUpdateType>> allowedRefUpdateTypes =
            RefUpdateContextValidator.INSTANCE.getAllowedRefUpdateTypes(refName);

        if (allowedRefUpdateTypes.isPresent()) {
          checkState(
              allowedRefUpdateTypes.get().stream().anyMatch(RefUpdateContext::hasOpen)
                  || isTestRepoCall(),
              "Special ref '%s' is updated outside of the expected operation. Wrap code in the"
                  + " correct RefUpdateContext or fix allowed update types",
              refName);
          return;
        }
        // It is not one of the special ref - update is possible only within specific contexts.
        checkState(
            RefUpdateContext.hasOpen(MERGE_CHANGE)
                || RefUpdateContext.hasOpen(RefUpdateType.BRANCH_MODIFICATION)
                || RefUpdateContext.hasOpen(RefUpdateType.UPDATE_SUPERPROJECT)
                // Plugin can update any ref
                || RefUpdateContext.hasOpen(PLUGIN)
                || isTestRepoCall(),
            "Ordinary ref '%s' is updated outside of the expected operation. Wrap code in the"
                + " correct RefUpdateContext or add the ref as a special ref.",
            refName);
      }

      private RefUpdateContextValidator addSpecialRef(
          Predicate<String> refNamePredicate, RefUpdateType... validRefUpdateTypes) {
        specialRefs.add(
            new SimpleImmutableEntry<>(
                refNamePredicate, ImmutableList.copyOf(validRefUpdateTypes)));
        return this;
      }

      private Optional<ImmutableList<RefUpdateType>> getAllowedRefUpdateTypes(String refName) {
        List<ImmutableList<RefUpdateType>> allowedTypes =
            specialRefs.stream()
                .filter(entry -> entry.getKey().test(refName))
                .map(Entry::getValue)
                .collect(toList());
        checkState(
            allowedTypes.size() <= 1,
            "refName matches more than 1 predicate. Please fix the specialRefs list, so each"
                + " reference has no more than one match.");
        if (allowedTypes.size() == 0) {
          return Optional.empty();
        }
        return Optional.of(allowedTypes.get(0));
      }

      /**
       * Returns true if a ref is updated using one of the method in {@link
       * org.eclipse.jgit.junit.TestRepository}.
       *
       * <p>The {@link org.eclipse.jgit.junit.TestRepository} used only in tests and allows to
       * change refs directly. Wrapping each usage in a test context requires a lot of modification,
       * so instead we allow any ref updates, which are made using through this class.
       */
      private boolean isTestRepoCall() {
        return Arrays.stream(Thread.currentThread().getStackTrace())
            .anyMatch(elem -> elem.getClassName().equals("org.eclipse.jgit.junit.TestRepository"));
      }
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
              getCommands().stream()
                  .forEach(RefUpdateContextValidator.INSTANCE::validateRefUpdateContext);
              super.execute(rw, pm, options);
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
    return name.get().toLowerCase(Locale.US);
  }
}
