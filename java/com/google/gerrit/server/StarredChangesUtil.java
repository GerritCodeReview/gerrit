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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

import com.google.auto.value.AutoValue;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.config.GerritPersonIdent;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class StarredChangesUtil {
  @AutoValue
  public abstract static class StarField {
    private static final String SEPARATOR = ":";

    public static StarField parse(String s) {
      int p = s.indexOf(SEPARATOR);
      if (p >= 0) {
        Integer id = Ints.tryParse(s.substring(0, p));
        if (id == null) {
          return null;
        }
        Account.Id accountId = new Account.Id(id);
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
    public String toString() {
      return accountId() + SEPARATOR + label();
    }
  }

  @AutoValue
  public abstract static class StarRef {
    private static final StarRef MISSING =
        new AutoValue_StarredChangesUtil_StarRef(null, ImmutableSortedSet.of());

    private static StarRef create(Ref ref, Iterable<String> labels) {
      return new AutoValue_StarredChangesUtil_StarRef(
          checkNotNull(ref), ImmutableSortedSet.copyOf(labels));
    }

    @Nullable
    public abstract Ref ref();

    public abstract ImmutableSortedSet<String> labels();

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

  private static final Logger log = LoggerFactory.getLogger(StarredChangesUtil.class);

  public static final String DEFAULT_LABEL = "star";
  public static final String IGNORE_LABEL = "ignore";
  public static final String REVIEWED_LABEL = "reviewed";
  public static final String UNREVIEWED_LABEL = "unreviewed";
  public static final ImmutableSortedSet<String> DEFAULT_LABELS =
      ImmutableSortedSet.of(DEFAULT_LABEL);

  private final GitRepositoryManager repoManager;
  private final GitReferenceUpdated gitRefUpdated;
  private final AllUsersName allUsers;
  private final Provider<ReviewDb> dbProvider;
  private final PersonIdent serverIdent;
  private final ChangeIndexer indexer;
  private final Provider<InternalChangeQuery> queryProvider;

  @Inject
  StarredChangesUtil(
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      AllUsersName allUsers,
      Provider<ReviewDb> dbProvider,
      @GerritPersonIdent PersonIdent serverIdent,
      ChangeIndexer indexer,
      Provider<InternalChangeQuery> queryProvider) {
    this.repoManager = repoManager;
    this.gitRefUpdated = gitRefUpdated;
    this.allUsers = allUsers;
    this.dbProvider = dbProvider;
    this.serverIdent = serverIdent;
    this.indexer = indexer;
    this.queryProvider = queryProvider;
  }

  public ImmutableSortedSet<String> getLabels(Account.Id accountId, Change.Id changeId)
      throws OrmException {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      return readLabels(repo, RefNames.refsStarredChanges(changeId, accountId)).labels();
    } catch (IOException e) {
      throw new OrmException(
          String.format(
              "Reading stars from change %d for account %d failed",
              changeId.get(), accountId.get()),
          e);
    }
  }

  public ImmutableSortedSet<String> star(
      Account.Id accountId,
      Project.NameKey project,
      Change.Id changeId,
      Set<String> labelsToAdd,
      Set<String> labelsToRemove)
      throws OrmException, IllegalLabelException {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      String refName = RefNames.refsStarredChanges(changeId, accountId);
      StarRef old = readLabels(repo, refName);

      Set<String> labels = new HashSet<>(old.labels());
      if (labelsToAdd != null) {
        labels.addAll(labelsToAdd);
      }
      if (labelsToRemove != null) {
        labels.removeAll(labelsToRemove);
      }

      if (labels.isEmpty()) {
        deleteRef(repo, refName, old.objectId());
      } else {
        checkMutuallyExclusiveLabels(labels);
        updateLabels(repo, refName, old.objectId(), labels);
      }

      indexer.index(dbProvider.get(), project, changeId);
      return ImmutableSortedSet.copyOf(labels);
    } catch (IOException e) {
      throw new OrmException(
          String.format("Star change %d for account %d failed", changeId.get(), accountId.get()),
          e);
    }
  }

  public void unstarAll(Project.NameKey project, Change.Id changeId) throws OrmException {
    try (Repository repo = repoManager.openRepository(allUsers);
        RevWalk rw = new RevWalk(repo)) {
      BatchRefUpdate batchUpdate = repo.getRefDatabase().newBatchUpdate();
      batchUpdate.setAllowNonFastForwards(true);
      batchUpdate.setRefLogIdent(serverIdent);
      batchUpdate.setRefLogMessage("Unstar change " + changeId.get(), true);
      for (Account.Id accountId : byChangeFromIndex(changeId).keySet()) {
        String refName = RefNames.refsStarredChanges(changeId, accountId);
        Ref ref = repo.getRefDatabase().getRef(refName);
        batchUpdate.addCommand(new ReceiveCommand(ref.getObjectId(), ObjectId.zeroId(), refName));
      }
      batchUpdate.execute(rw, NullProgressMonitor.INSTANCE);
      for (ReceiveCommand command : batchUpdate.getCommands()) {
        if (command.getResult() != ReceiveCommand.Result.OK) {
          throw new IOException(
              String.format(
                  "Unstar change %d failed, ref %s could not be deleted: %s",
                  changeId.get(), command.getRefName(), command.getResult()));
        }
      }
      indexer.index(dbProvider.get(), project, changeId);
    } catch (IOException e) {
      throw new OrmException(String.format("Unstar change %d failed", changeId.get()), e);
    }
  }

  public ImmutableMap<Account.Id, StarRef> byChange(Change.Id changeId) throws OrmException {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      ImmutableMap.Builder<Account.Id, StarRef> builder = ImmutableMap.builder();
      for (String refPart : getRefNames(repo, RefNames.refsStarredChangesPrefix(changeId))) {
        Integer id = Ints.tryParse(refPart);
        if (id == null) {
          continue;
        }
        Account.Id accountId = new Account.Id(id);
        builder.put(accountId, readLabels(repo, RefNames.refsStarredChanges(changeId, accountId)));
      }
      return builder.build();
    } catch (IOException e) {
      throw new OrmException(
          String.format("Get accounts that starred change %d failed", changeId.get()), e);
    }
  }

  public ImmutableListMultimap<Account.Id, String> byChangeFromIndex(Change.Id changeId)
      throws OrmException {
    List<ChangeData> changeData =
        queryProvider
            .get()
            .setRequestedFields(ChangeField.ID, ChangeField.STAR)
            .byLegacyChangeId(changeId);
    if (changeData.size() != 1) {
      throw new NoSuchChangeException(changeId);
    }
    return changeData.get(0).stars();
  }

  private static Set<String> getRefNames(Repository repo, String prefix) throws IOException {
    RefDatabase refDb = repo.getRefDatabase();
    return refDb.getRefs(prefix).keySet();
  }

  public ObjectId getObjectId(Account.Id accountId, Change.Id changeId) {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      Ref ref = repo.exactRef(RefNames.refsStarredChanges(changeId, accountId));
      return ref != null ? ref.getObjectId() : ObjectId.zeroId();
    } catch (IOException e) {
      log.error(
          String.format(
              "Getting star object ID for account %d on change %d failed",
              accountId.get(), changeId.get()),
          e);
      return ObjectId.zeroId();
    }
  }

  public void ignore(ChangeResource rsrc) throws OrmException, IllegalLabelException {
    star(
        rsrc.getUser().asIdentifiedUser().getAccountId(),
        rsrc.getProject(),
        rsrc.getChange().getId(),
        ImmutableSet.of(IGNORE_LABEL),
        ImmutableSet.of());
  }

  public void unignore(ChangeResource rsrc) throws OrmException, IllegalLabelException {
    star(
        rsrc.getUser().asIdentifiedUser().getAccountId(),
        rsrc.getProject(),
        rsrc.getChange().getId(),
        ImmutableSet.of(),
        ImmutableSet.of(IGNORE_LABEL));
  }

  public boolean isIgnoredBy(Change.Id changeId, Account.Id accountId) throws OrmException {
    return getLabels(accountId, changeId).contains(IGNORE_LABEL);
  }

  public boolean isIgnored(ChangeResource rsrc) throws OrmException {
    return isIgnoredBy(rsrc.getChange().getId(), rsrc.getUser().asIdentifiedUser().getAccountId());
  }

  private static String getReviewedLabel(Change change) {
    return getReviewedLabel(change.currentPatchSetId().get());
  }

  private static String getReviewedLabel(int ps) {
    return REVIEWED_LABEL + "/" + ps;
  }

  private static String getUnreviewedLabel(Change change) {
    return getUnreviewedLabel(change.currentPatchSetId().get());
  }

  private static String getUnreviewedLabel(int ps) {
    return UNREVIEWED_LABEL + "/" + ps;
  }

  public void markAsReviewed(ChangeResource rsrc) throws OrmException, IllegalLabelException {
    star(
        rsrc.getUser().asIdentifiedUser().getAccountId(),
        rsrc.getProject(),
        rsrc.getChange().getId(),
        ImmutableSet.of(getReviewedLabel(rsrc.getChange())),
        ImmutableSet.of(getUnreviewedLabel(rsrc.getChange())));
  }

  public void markAsUnreviewed(ChangeResource rsrc) throws OrmException, IllegalLabelException {
    star(
        rsrc.getUser().asIdentifiedUser().getAccountId(),
        rsrc.getProject(),
        rsrc.getChange().getId(),
        ImmutableSet.of(getUnreviewedLabel(rsrc.getChange())),
        ImmutableSet.of(getReviewedLabel(rsrc.getChange())));
  }

  public static StarRef readLabels(Repository repo, String refName) throws IOException {
    Ref ref = repo.exactRef(refName);
    if (ref == null) {
      return StarRef.MISSING;
    }

    try (ObjectReader reader = repo.newObjectReader()) {
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

  private static void checkMutuallyExclusiveLabels(Set<String> labels)
      throws MutuallyExclusiveLabelsException {
    if (labels.containsAll(ImmutableSet.of(DEFAULT_LABEL, IGNORE_LABEL))) {
      throw new MutuallyExclusiveLabelsException(DEFAULT_LABEL, IGNORE_LABEL);
    }

    Set<Integer> reviewedPatchSets = getStarredPatchSets(labels, REVIEWED_LABEL);
    Set<Integer> unreviewedPatchSets = getStarredPatchSets(labels, UNREVIEWED_LABEL);
    Optional<Integer> ps =
        Sets.intersection(reviewedPatchSets, unreviewedPatchSets).stream().findFirst();
    if (ps.isPresent()) {
      throw new MutuallyExclusiveLabelsException(
          getReviewedLabel(ps.get()), getUnreviewedLabel(ps.get()));
    }
  }

  public static Set<Integer> getStarredPatchSets(Set<String> labels, String label) {
    return labels
        .stream()
        .filter(l -> l.startsWith(label + "/"))
        .filter(l -> Ints.tryParse(l.substring(label.length() + 1)) != null)
        .map(l -> Integer.valueOf(l.substring(label.length() + 1)))
        .collect(toSet());
  }

  private static void validateLabels(Collection<String> labels) throws InvalidLabelsException {
    if (labels == null) {
      return;
    }

    SortedSet<String> invalidLabels = new TreeSet<>();
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
      throws IOException, OrmException, InvalidLabelsException {
    try (RevWalk rw = new RevWalk(repo)) {
      RefUpdate u = repo.updateRef(refName);
      u.setExpectedOldObjectId(oldObjectId);
      u.setForceUpdate(true);
      u.setNewObjectId(writeLabels(repo, labels));
      u.setRefLogIdent(serverIdent);
      u.setRefLogMessage("Update star labels", true);
      RefUpdate.Result result = u.update(rw);
      switch (result) {
        case NEW:
        case FORCED:
        case NO_CHANGE:
        case FAST_FORWARD:
          gitRefUpdated.fire(allUsers, u, null);
          return;
        case IO_FAILURE:
        case LOCK_FAILURE:
        case NOT_ATTEMPTED:
        case REJECTED:
        case REJECTED_CURRENT_BRANCH:
        case RENAMED:
        case REJECTED_MISSING_OBJECT:
        case REJECTED_OTHER_REASON:
        default:
          throw new OrmException(
              String.format("Update star labels on ref %s failed: %s", refName, result.name()));
      }
    }
  }

  private void deleteRef(Repository repo, String refName, ObjectId oldObjectId)
      throws IOException, OrmException {
    RefUpdate u = repo.updateRef(refName);
    u.setForceUpdate(true);
    u.setExpectedOldObjectId(oldObjectId);
    u.setRefLogIdent(serverIdent);
    u.setRefLogMessage("Unstar change", true);
    RefUpdate.Result result = u.delete();
    switch (result) {
      case FORCED:
        gitRefUpdated.fire(allUsers, u, null);
        return;
      case NEW:
      case NO_CHANGE:
      case FAST_FORWARD:
      case IO_FAILURE:
      case LOCK_FAILURE:
      case NOT_ATTEMPTED:
      case REJECTED:
      case REJECTED_CURRENT_BRANCH:
      case RENAMED:
      case REJECTED_MISSING_OBJECT:
      case REJECTED_OTHER_REASON:
      default:
        throw new OrmException(
            String.format("Delete star ref %s failed: %s", refName, result.name()));
    }
  }
}
