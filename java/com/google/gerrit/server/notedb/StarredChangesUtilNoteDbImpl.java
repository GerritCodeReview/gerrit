// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.git.GitUpdateFailureException;
import com.google.gerrit.git.LockFailureException;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.StarredChangesReader;
import com.google.gerrit.server.StarredChangesWriter;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

@Singleton
public class StarredChangesUtilNoteDbImpl implements StarredChangesReader, StarredChangesWriter {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String DEFAULT_STAR_LABEL = "star";

  private final GitRepositoryManager repoManager;
  private final GitReferenceUpdated gitRefUpdated;
  private final AllUsersName allUsers;
  private final Provider<PersonIdent> serverIdent;

  @Inject
  StarredChangesUtilNoteDbImpl(
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      AllUsersName allUsers,
      @GerritPersonIdent Provider<PersonIdent> serverIdent) {
    this.repoManager = repoManager;
    this.gitRefUpdated = gitRefUpdated;
    this.allUsers = allUsers;
    this.serverIdent = serverIdent;
  }

  @Override
  public boolean isStarred(Account.Id accountId, Change.Id virtualId) {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      return getStarRef(repo, RefNames.refsStarredChanges(virtualId, accountId)).isPresent();
    } catch (IOException e) {
      throw new StorageException(
          String.format(
              "Reading stars from change %d for account %d failed",
              virtualId.get(), accountId.get()),
          e);
    }
  }

  @Override
  public void star(Account.Id accountId, Change.Id virtualId) {
    updateStar(accountId, virtualId, true);
  }

  @Override
  public void unstar(Account.Id accountId, Change.Id virtualId) {
    updateStar(accountId, virtualId, false);
  }

  private void updateStar(Account.Id accountId, Change.Id virtualId, boolean shouldAdd) {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      String refName = RefNames.refsStarredChanges(virtualId, accountId);
      if (shouldAdd) {
        addRef(repo, refName, null);
      } else {
        Optional<Ref> ref = getStarRef(repo, refName);
        if (ref.isPresent()) {
          deleteRef(repo, refName, ref.get().getObjectId());
        }
      }
    } catch (IOException e) {
      throw new StorageException(
          String.format("Star change %d for account %d failed", virtualId.get(), accountId.get()),
          e);
    }
  }

  @Override
  public Set<Change.Id> areStarred(List<Change.Id> changeIds, Account.Id caller) {
    if (changeIds.isEmpty()) {
      return ImmutableSet.of();
    }
    try (Repository repo = repoManager.openRepository(allUsers)) {
      return areStarred(repo, changeIds, caller);
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed getting starred changes for account %d within changes: %s",
          caller.get(), Joiner.on(", ").join(changeIds));
      return ImmutableSet.of();
    }
  }

  @Override
  public Set<Change.Id> areStarred(
      Repository allUsersRepo, List<Change.Id> virtualIds, Account.Id caller) {
    if (virtualIds.isEmpty()) {
      return ImmutableSet.of();
    }
    List<String> starRefs =
        virtualIds.stream()
            .map(c -> RefNames.refsStarredChanges(c, caller))
            .collect(Collectors.toList());
    try {
      return allUsersRepo
          .getRefDatabase()
          .exactRef(starRefs.toArray(new String[0]))
          .keySet()
          .stream()
          .map(StarredChangesUtilNoteDbImpl::parseChangeIdFromStarredChangeRef)
          .filter(Objects::nonNull)
          .collect(Collectors.toSet());
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed getting starred changes for account %d within changes: %s",
          caller.get(), Joiner.on(", ").join(virtualIds));
      return ImmutableSet.of();
    }
  }

  @Override
  public void unstarAllForChangeDeletion(Change.Id virtualId) throws IOException {
    try (Repository repo = repoManager.openRepository(allUsers);
        RevWalk rw = new RevWalk(repo)) {
      BatchRefUpdate batchUpdate = repo.getRefDatabase().newBatchUpdate();
      batchUpdate.setAllowNonFastForwards(true);
      batchUpdate.setRefLogIdent(serverIdent.get());
      batchUpdate.setRefLogMessage("Unstar change " + virtualId.get(), true);
      String prefix = RefNames.refsStarredChangesPrefix(virtualId);
      for (Ref ref : repo.getRefDatabase().getRefsByPrefix(prefix)) {
        batchUpdate.addCommand(
            new ReceiveCommand(ref.getObjectId(), ObjectId.zeroId(), ref.getName()));
      }
      if (batchUpdate.getCommands().isEmpty()) {
        return;
      }
      try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
        batchUpdate.execute(rw, NullProgressMonitor.INSTANCE);
      }
      for (ReceiveCommand command : batchUpdate.getCommands()) {
        if (command.getResult() != ReceiveCommand.Result.OK) {
          String message =
              String.format(
                  "Unstar change %d failed, ref %s could not be deleted: %s",
                  virtualId.get(), command.getRefName(), command.getResult());
          if (command.getResult() == ReceiveCommand.Result.LOCK_FAILURE) {
            throw new LockFailureException(message, batchUpdate);
          }
          throw new GitUpdateFailureException(message, batchUpdate);
        }
      }
    }
  }

  @Override
  public ImmutableList<Account.Id> byChange(Change.Id virtualId) {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      String prefix = RefNames.refsStarredChangesPrefix(virtualId);
      List<Ref> refs = repo.getRefDatabase().getRefsByPrefix(prefix);
      ImmutableList.Builder<Account.Id> builder =
          ImmutableList.builderWithExpectedSize(refs.size());
      for (Ref ref : refs) {
        Account.Id accountId = parseAccountIdFromStarredChangeRef(ref.getName(), prefix.length());
        if (accountId != null) {
          builder.add(accountId);
        }
      }
      return builder.build();
    } catch (IOException e) {
      throw new StorageException(
          String.format("Get accounts that starred change %d failed", virtualId.get()), e);
    }
  }

  @Override
  public ImmutableSet<Change.Id> byAccountId(Account.Id accountId) {
    return byAccountId(accountId, true);
  }

  @Override
  public ImmutableSet<Change.Id> byAccountId(Account.Id accountId, boolean skipInvalidChanges) {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      ImmutableSet.Builder<Change.Id> builder = ImmutableSet.builder();
      String accountSuffix = "/" + accountId.get();
      for (Ref ref : repo.getRefDatabase().getRefsByPrefix(RefNames.REFS_STARRED_CHANGES)) {
        String refName = ref.getName();
        if (!refName.endsWith(accountSuffix)) {
          continue;
        }

        Change.Id changeId = parseChangeIdForAccount(refName, accountSuffix);
        if (changeId == null) {
          if (skipInvalidChanges) {
            continue;
          }
          throw new StorageException(
              String.format("Invalid star ref %s for account %d", refName, accountId.get()));
        }
        builder.add(changeId);
      }
      return builder.build();
    } catch (IOException e) {
      throw new StorageException(
          String.format("Get starred changes for account %d failed", accountId.get()), e);
    }
  }

  @Nullable
  private static Change.Id parseChangeIdForAccount(String refName, String accountSuffix) {
    if (!refName.startsWith(RefNames.REFS_STARRED_CHANGES) || !refName.endsWith(accountSuffix)) {
      return null;
    }
    int len = refName.length();
    int changeEnd = len - accountSuffix.length();
    if (changeEnd <= 24) {
      return null;
    }
    char s0 = refName.charAt(21);
    char s1 = refName.charAt(22);
    if (s0 < '0' || s0 > '9' || s1 < '0' || s1 > '9' || refName.charAt(23) != '/') {
      return null;
    }
    int expectedShard = (s0 - '0') * 10 + (s1 - '0');

    int changeIdVal = 0;
    for (int i = 24; i < changeEnd; i++) {
      char c = refName.charAt(i);
      if (c < '0' || c > '9') {
        return null;
      }
      changeIdVal = changeIdVal * 10 + (c - '0');
    }
    if (changeIdVal % 100 != expectedShard) {
      return null;
    }
    return Change.id(changeIdVal);
  }

  @Nullable
  private static Change.Id parseChangeIdFromStarredChangeRef(String refName) {
    if (refName == null || !refName.startsWith(RefNames.REFS_STARRED_CHANGES)) {
      return null;
    }
    int len = refName.length();
    if (len < 25) {
      return null;
    }
    char s0 = refName.charAt(21);
    char s1 = refName.charAt(22);
    if (s0 < '0' || s0 > '9' || s1 < '0' || s1 > '9' || refName.charAt(23) != '/') {
      return null;
    }
    int expectedShard = (s0 - '0') * 10 + (s1 - '0');

    int changeIdVal = 0;
    int i = 24;
    while (i < len) {
      char c = refName.charAt(i);
      if (c == '/') {
        break;
      }
      if (c < '0' || c > '9') {
        return null;
      }
      changeIdVal = changeIdVal * 10 + (c - '0');
      i++;
    }
    if (i == 24 || i == len || refName.charAt(i) != '/') {
      return null;
    }
    if (changeIdVal % 100 != expectedShard) {
      return null;
    }
    int accountStart = i + 1;
    if (accountStart >= len) {
      return null;
    }
    for (int j = accountStart; j < len; j++) {
      char c = refName.charAt(j);
      if (c < '0' || c > '9') {
        return null;
      }
    }
    return Change.id(changeIdVal);
  }

  @Nullable
  private static Account.Id parseAccountIdFromStarredChangeRef(String refName, int offset) {
    int len = refName.length();
    if (offset >= len) {
      return null;
    }
    int accountIdVal = 0;
    for (int i = offset; i < len; i++) {
      char c = refName.charAt(i);
      if (c < '0' || c > '9') {
        return null;
      }
      accountIdVal = accountIdVal * 10 + (c - '0');
    }
    return Account.id(accountIdVal);
  }

  private static Optional<Ref> getStarRef(Repository repo, @Nullable String refName)
      throws IOException {
    if (refName == null) {
      return Optional.empty();
    }
    Ref ref = repo.exactRef(refName);
    return Optional.ofNullable(ref);
  }

  private static ObjectId writeStarredRefContent(Repository repo) throws IOException {
    try (ObjectInserter oi = repo.newObjectInserter()) {
      ObjectId id = oi.insert(Constants.OBJ_BLOB, DEFAULT_STAR_LABEL.getBytes(UTF_8));
      oi.flush();
      return id;
    }
  }

  private void addRef(Repository repo, String refName, ObjectId oldObjectId) throws IOException {
    try (TraceTimer traceTimer =
            TraceContext.newTimer(
                "Add star ref",
                Metadata.builder().noteDbRefName(refName).resourceCount(1).build());
        RevWalk rw = new RevWalk(repo)) {
      RefUpdate u = repo.updateRef(refName);
      u.setExpectedOldObjectId(oldObjectId);
      u.setForceUpdate(true);
      u.setNewObjectId(writeStarredRefContent(repo));
      u.setRefLogIdent(serverIdent.get());
      u.setRefLogMessage("Add star ref", true);
      try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
        RefUpdate.Result result = u.update(rw);
        switch (result) {
          case NEW:
          case FORCED:
          case NO_CHANGE:
          case FAST_FORWARD:
            gitRefUpdated.fire(allUsers, u, null);
            return;
          case LOCK_FAILURE:
            throw new LockFailureException(
                String.format("Add star ref on ref %s failed", refName), u);
          case IO_FAILURE:
          case NOT_ATTEMPTED:
          case REJECTED:
          case REJECTED_CURRENT_BRANCH:
          case RENAMED:
          case REJECTED_MISSING_OBJECT:
          case REJECTED_OTHER_REASON:
          default:
            throw new StorageException(
                String.format("Add star ref on ref %s failed: %s", refName, result.name()));
        }
      }
    }
  }

  private void deleteRef(Repository repo, String refName, ObjectId oldObjectId) throws IOException {
    if (ObjectId.zeroId().equals(oldObjectId)) {
      // ref doesn't exist
      return;
    }

    try (TraceTimer traceTimer =
        TraceContext.newTimer(
            "Delete star ref", Metadata.builder().noteDbRefName(refName).build())) {
      RefUpdate u = repo.updateRef(refName);
      u.setForceUpdate(true);
      u.setExpectedOldObjectId(oldObjectId);
      u.setNewObjectId(ObjectId.zeroId());
      u.setRefLogIdent(serverIdent.get());
      u.setRefLogMessage("Unstar change", true);
      try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
        RefUpdate.Result result = u.delete();
        switch (result) {
          case FORCED:
            gitRefUpdated.fire(allUsers, u, null);
            return;
          case LOCK_FAILURE:
            throw new LockFailureException(String.format("Delete star ref %s failed", refName), u);
          case NEW:
          case NO_CHANGE:
          case FAST_FORWARD:
          case IO_FAILURE:
          case NOT_ATTEMPTED:
          case REJECTED:
          case REJECTED_CURRENT_BRANCH:
          case RENAMED:
          case REJECTED_MISSING_OBJECT:
          case REJECTED_OTHER_REASON:
          default:
            throw new StorageException(
                String.format("Delete star ref %s failed: %s", refName, result.name()));
        }
      }
    }
  }
}
