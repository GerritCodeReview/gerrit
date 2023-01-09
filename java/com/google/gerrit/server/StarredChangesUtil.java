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

package com.google.gerrit.server;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

import com.google.auto.value.AutoValue;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.git.GitUpdateFailureException;
import com.google.gerrit.git.LockFailureException;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

@Singleton
public class StarredChangesUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @AutoValue
  public abstract static class StarField {
    private static final String SEPARATOR = ":";

    @Nullable
    public static StarField parse(String s) {
      int p = s.indexOf(SEPARATOR);
      if (p >= 0) {
        Integer id = Ints.tryParse(s.substring(0, p));
        if (id == null) {
          return null;
        }
        Account.Id accountId = Account.id(id);
        String label = s.substring(p + 1);
        return create(accountId, label);
      }
      return null;
    }

    public static StarField create(Account.Id accountId, String label) {
      return new AutoValue_StarredChangesUtil_StarField(accountId, label);
    }

    public abstract Account.Id accountId();

    public abstract String label();

    @Override
    public final String toString() {
      return accountId() + SEPARATOR + label();
    }
  }

  public enum Operation {
    ADD,
    REMOVE
  }

  @AutoValue
  public abstract static class StarRef {
    private static final StarRef MISSING =
        new AutoValue_StarredChangesUtil_StarRef(null, Collections.emptyNavigableSet());

    private static StarRef create(Ref ref, Iterable<String> labels) {
      return new AutoValue_StarredChangesUtil_StarRef(
          requireNonNull(ref), ImmutableSortedSet.copyOf(labels));
    }

    @Nullable
    public abstract Ref ref();

    public abstract NavigableSet<String> labels();

    public ObjectId objectId() {
      return ref() != null ? ref().getObjectId() : ObjectId.zeroId();
    }
  }

  public static class IllegalLabelException extends Exception {
    private static final long serialVersionUID = 1L;

    IllegalLabelException(String message) {
      super(message);
    }
  }

  public static class InvalidLabelsException extends IllegalLabelException {
    private static final long serialVersionUID = 1L;

    InvalidLabelsException(Set<String> invalidLabels) {
      super(String.format("invalid labels: %s", Joiner.on(", ").join(invalidLabels)));
    }
  }

  public static class MutuallyExclusiveLabelsException extends IllegalLabelException {
    private static final long serialVersionUID = 1L;

    MutuallyExclusiveLabelsException(String label1, String label2) {
      super(
          String.format(
              "The labels %s and %s are mutually exclusive. Only one of them can be set.",
              label1, label2));
    }
  }

  public static final String DEFAULT_LABEL = "star";

  private final GitRepositoryManager repoManager;
  private final GitReferenceUpdated gitRefUpdated;
  private final AllUsersName allUsers;
  private final Provider<PersonIdent> serverIdent;

  @Inject
  StarredChangesUtil(
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      AllUsersName allUsers,
      @GerritPersonIdent Provider<PersonIdent> serverIdent) {
    this.repoManager = repoManager;
    this.gitRefUpdated = gitRefUpdated;
    this.allUsers = allUsers;
    this.serverIdent = serverIdent;
  }

  public NavigableSet<String> getLabels(Account.Id accountId, Change.Id changeId) {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      return readLabels(repo, RefNames.refsStarredChanges(changeId, accountId)).labels();
    } catch (IOException e) {
      throw new StorageException(
          String.format(
              "Reading stars from change %d for account %d failed",
              changeId.get(), accountId.get()),
          e);
    }
  }

  public void star(Account.Id accountId, Change.Id changeId, Operation op)
      throws IllegalLabelException {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      String refName = RefNames.refsStarredChanges(changeId, accountId);
      StarRef old = readLabels(repo, refName);

      NavigableSet<String> labels = new TreeSet<>(old.labels());
      switch (op) {
        case ADD:
          labels.add(DEFAULT_LABEL);
          break;
        case REMOVE:
          labels.remove(DEFAULT_LABEL);
          break;
      }

      if (labels.isEmpty()) {
        deleteRef(repo, refName, old.objectId());
      } else {
        updateLabels(repo, refName, old.objectId(), labels);
      }
    } catch (IOException e) {
      throw new StorageException(
          String.format("Star change %d for account %d failed", changeId.get(), accountId.get()),
          e);
    }
  }

  /**
   * Unstar the given change for all users.
   *
   * <p>Intended for use only when we're about to delete a change. For that reason, the change is
   * not reindexed.
   *
   * @param changeId change ID.
   * @throws IOException if an error occurred.
   */
  public void unstarAllForChangeDeletion(Change.Id changeId) throws IOException {
    try (Repository repo = repoManager.openRepository(allUsers);
        RevWalk rw = new RevWalk(repo)) {
      BatchRefUpdate batchUpdate = repo.getRefDatabase().newBatchUpdate();
      batchUpdate.setAllowNonFastForwards(true);
      batchUpdate.setRefLogIdent(serverIdent.get());
      batchUpdate.setRefLogMessage("Unstar change " + changeId.get(), true);
      for (Account.Id accountId : getStars(repo, changeId)) {
        String refName = RefNames.refsStarredChanges(changeId, accountId);
        Ref ref = repo.getRefDatabase().exactRef(refName);
        if (ref != null) {
          batchUpdate.addCommand(new ReceiveCommand(ref.getObjectId(), ObjectId.zeroId(), refName));
        }
      }
      batchUpdate.execute(rw, NullProgressMonitor.INSTANCE);
      for (ReceiveCommand command : batchUpdate.getCommands()) {
        if (command.getResult() != ReceiveCommand.Result.OK) {
          String message =
              String.format(
                  "Unstar change %d failed, ref %s could not be deleted: %s",
                  changeId.get(), command.getRefName(), command.getResult());
          if (command.getResult() == ReceiveCommand.Result.LOCK_FAILURE) {
            throw new LockFailureException(message, batchUpdate);
          }
          throw new GitUpdateFailureException(message, batchUpdate);
        }
      }
    }
  }

  public ImmutableMap<Account.Id, StarRef> byChange(Change.Id changeId) {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      ImmutableMap.Builder<Account.Id, StarRef> builder = ImmutableMap.builder();
      for (Account.Id accountId : getStars(repo, changeId)) {
        builder.put(accountId, readLabels(repo, RefNames.refsStarredChanges(changeId, accountId)));
      }
      return builder.build();
    } catch (IOException e) {
      throw new StorageException(
          String.format("Get accounts that starred change %d failed", changeId.get()), e);
    }
  }

  public ImmutableSet<Change.Id> byAccountId(Account.Id accountId, String label) {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      ImmutableSet.Builder<Change.Id> builder = ImmutableSet.builder();
      for (Ref ref : repo.getRefDatabase().getRefsByPrefix(RefNames.REFS_STARRED_CHANGES)) {
        Account.Id currentAccountId = Account.Id.fromRef(ref.getName());
        // Skip all refs that don't correspond with accountId.
        if (currentAccountId == null || !currentAccountId.equals(accountId)) {
          continue;
        }
        // Skip all refs that don't contain the required label.
        StarRef starRef = readLabels(repo, ref.getName());
        if (!starRef.labels().contains(label)) {
          continue;
        }

        // Skip invalid change ids.
        Change.Id changeId = Change.Id.fromAllUsersRef(ref.getName());
        if (changeId == null) {
          continue;
        }
        builder.add(changeId);
      }
      return builder.build();
    } catch (IOException e) {
      throw new StorageException(
          String.format("Get starred changes for account %d failed", accountId.get()), e);
    }
  }

  private static Set<Account.Id> getStars(Repository allUsers, Change.Id changeId)
      throws IOException {
    String prefix = RefNames.refsStarredChangesPrefix(changeId);
    RefDatabase refDb = allUsers.getRefDatabase();
    return refDb.getRefsByPrefix(prefix).stream()
        .map(r -> r.getName().substring(prefix.length()))
        .map(refPart -> Ints.tryParse(refPart))
        .filter(Objects::nonNull)
        .map(id -> Account.id(id))
        .collect(toSet());
  }

  public ObjectId getObjectId(Account.Id accountId, Change.Id changeId) {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      Ref ref = repo.exactRef(RefNames.refsStarredChanges(changeId, accountId));
      return ref != null ? ref.getObjectId() : ObjectId.zeroId();
    } catch (IOException e) {
      logger.atSevere().withCause(e).log(
          "Getting star object ID for account %d on change %d failed",
          accountId.get(), changeId.get());
      return ObjectId.zeroId();
    }
  }

  public static StarRef readLabels(Repository repo, String refName) throws IOException {
    try (TraceTimer traceTimer =
        TraceContext.newTimer(
            "Read star labels", Metadata.builder().noteDbRefName(refName).build())) {
      Ref ref = repo.exactRef(refName);
      return readLabels(repo, ref);
    }
  }

  public static StarRef readLabels(Repository repo, Ref ref) throws IOException {
    if (ref == null) {
      return StarRef.MISSING;
    }
    try (TraceTimer traceTimer =
            TraceContext.newTimer(
                String.format("Read star labels from %s (without ref lookup)", ref.getName()));
        ObjectReader reader = repo.newObjectReader()) {
      ObjectLoader obj = reader.open(ref.getObjectId(), Constants.OBJ_BLOB);
      return StarRef.create(
          ref,
          Splitter.on(CharMatcher.whitespace())
              .omitEmptyStrings()
              .split(new String(obj.getCachedBytes(Integer.MAX_VALUE), UTF_8)));
    }
  }

  public static ObjectId writeLabels(Repository repo, Collection<String> labels)
      throws IOException, InvalidLabelsException {
    validateLabels(labels);
    try (ObjectInserter oi = repo.newObjectInserter()) {
      ObjectId id =
          oi.insert(
              Constants.OBJ_BLOB,
              labels.stream().sorted().distinct().collect(joining("\n")).getBytes(UTF_8));
      oi.flush();
      return id;
    }
  }

  private static void validateLabels(Collection<String> labels) throws InvalidLabelsException {
    if (labels == null) {
      return;
    }

    NavigableSet<String> invalidLabels = new TreeSet<>();
    for (String label : labels) {
      if (CharMatcher.whitespace().matchesAnyOf(label)) {
        invalidLabels.add(label);
      }
    }
    if (!invalidLabels.isEmpty()) {
      throw new InvalidLabelsException(invalidLabels);
    }
  }

  private void updateLabels(
      Repository repo, String refName, ObjectId oldObjectId, Collection<String> labels)
      throws IOException, InvalidLabelsException {
    try (TraceTimer traceTimer =
            TraceContext.newTimer(
                "Update star labels",
                Metadata.builder().noteDbRefName(refName).resourceCount(labels.size()).build());
        RevWalk rw = new RevWalk(repo)) {
      RefUpdate u = repo.updateRef(refName);
      u.setExpectedOldObjectId(oldObjectId);
      u.setForceUpdate(true);
      u.setNewObjectId(writeLabels(repo, labels));
      u.setRefLogIdent(serverIdent.get());
      u.setRefLogMessage("Update star labels", true);
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
              String.format("Update star labels on ref %s failed", refName), u);
        case IO_FAILURE:
        case NOT_ATTEMPTED:
        case REJECTED:
        case REJECTED_CURRENT_BRANCH:
        case RENAMED:
        case REJECTED_MISSING_OBJECT:
        case REJECTED_OTHER_REASON:
        default:
          throw new StorageException(
              String.format("Update star labels on ref %s failed: %s", refName, result.name()));
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
            "Delete star labels", Metadata.builder().noteDbRefName(refName).build())) {
      RefUpdate u = repo.updateRef(refName);
      u.setForceUpdate(true);
      u.setExpectedOldObjectId(oldObjectId);
      u.setRefLogIdent(serverIdent.get());
      u.setRefLogMessage("Unstar change", true);
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
