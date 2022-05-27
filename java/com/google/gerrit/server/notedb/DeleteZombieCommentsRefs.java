// Copyright (C) 2020 The Android Open Source Project
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.entities.RefNames.REFS_DRAFT_COMMENTS;
import static org.eclipse.jgit.lib.Constants.EMPTY_TREE_ID;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * This class can be used to clean zombie draft comments refs. More context in <a
 * href="https://gerrit-review.googlesource.com/c/gerrit/+/246233">
 * https://gerrit-review.googlesource.com/c/gerrit/+/246233 </a>
 *
 * <p>The implementation has two cases for detecting zombie drafts:
 *
 * <ul>
 *   <li>An earlier bug in the deletion of draft comments {@code
 *       refs/draft-comments/$change_id_short/$change_id/$user_id} caused some draft refs to remain
 *       in Git and not get deleted. These refs point to an empty tree. We delete such refs.
 *   <li>Inspecting all draft-comment refs. Check for each draft if there exists a published comment
 *       with the same UUID. For now this runs in logging-only mode and does not remove these zombie
 *       drafts.
 * </uL>
 */
public class DeleteZombieCommentsRefs {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // Number of refs deleted at once in a batch ref-update.
  // Log progress after deleting every CHUNK_SIZE refs
  private static final int CHUNK_SIZE = 3000;

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsers;
  private final int cleanupPercentage;
  private final Consumer<String> uiConsumer;
  @Nullable private final DraftCommentNotes.Factory draftNotesFactory;
  @Nullable private final ChangeNotes.Factory changeNotesFactory;
  @Nullable private final CommentsUtil commentsUtil;

  public interface Factory {
    DeleteZombieCommentsRefs create(int cleanupPercentage);
  }

  @Inject
  public DeleteZombieCommentsRefs(
      AllUsersName allUsers,
      GitRepositoryManager repoManager,
      ChangeNotes.Factory changeNotesFactory,
      DraftCommentNotes.Factory draftNotesFactory,
      CommentsUtil commentsUtil,
      @Assisted Integer cleanupPercentage) {
    this(
        allUsers,
        repoManager,
        cleanupPercentage,
        (msg) -> {},
        changeNotesFactory,
        draftNotesFactory,
        commentsUtil);
  }

  public DeleteZombieCommentsRefs(
      AllUsersName allUsers,
      GitRepositoryManager repoManager,
      Integer cleanupPercentage,
      Consumer<String> uiConsumer) {
    this(allUsers, repoManager, cleanupPercentage, uiConsumer, null, null, null);
  }

  private DeleteZombieCommentsRefs(
      AllUsersName allUsers,
      GitRepositoryManager repoManager,
      Integer cleanupPercentage,
      Consumer<String> uiConsumer,
      @Nullable ChangeNotes.Factory changeNotesFactory,
      @Nullable DraftCommentNotes.Factory draftNotesFactory,
      @Nullable CommentsUtil commentsUtil) {
    this.allUsers = allUsers;
    this.repoManager = repoManager;
    this.cleanupPercentage = (cleanupPercentage == null) ? 100 : cleanupPercentage;
    this.uiConsumer = uiConsumer;
    this.draftNotesFactory = draftNotesFactory;
    this.changeNotesFactory = changeNotesFactory;
    this.commentsUtil = commentsUtil;
  }

  public void execute() throws IOException {
    deleteDraftRefsThatPointToEmptyTree();
    if (draftNotesFactory != null) {
      getNumberOfDraftsThatAreAlsoPublished();
    }
  }

  private void deleteDraftRefsThatPointToEmptyTree() throws IOException {
    try (Repository allUsersRepo = repoManager.openRepository(allUsers)) {
      List<Ref> draftRefs = allUsersRepo.getRefDatabase().getRefsByPrefix(REFS_DRAFT_COMMENTS);
      List<Ref> zombieRefs = filterZombieRefs(allUsersRepo, draftRefs);

      logInfo(
          String.format(
              "Found a total of %d zombie draft refs in %s repo.",
              zombieRefs.size(), allUsers.get()));

      logInfo(String.format("Cleanup percentage = %d", cleanupPercentage));
      zombieRefs =
          zombieRefs.stream()
              .filter(
                  ref -> Change.Id.fromAllUsersRef(ref.getName()).get() % 100 < cleanupPercentage)
              .collect(toImmutableList());
      logInfo(String.format("Number of zombie refs to be cleaned = %d", zombieRefs.size()));

      long zombieRefsCnt = zombieRefs.size();
      long deletedRefsCnt = 0;
      long startTime = System.currentTimeMillis();

      for (List<Ref> refsBatch : Iterables.partition(zombieRefs, CHUNK_SIZE)) {
        deleteBatchZombieRefs(allUsersRepo, refsBatch);
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        deletedRefsCnt += refsBatch.size();
        logProgress(deletedRefsCnt, zombieRefsCnt, elapsed);
      }
    }
  }

  /**
   * For each draft comment, check if there exists a published comment with the same UUID and log a
   * warning if that's the case.
   */
  @VisibleForTesting
  public int getNumberOfDraftsThatAreAlsoPublished() throws IOException {
    try (Repository allUsersRepo = repoManager.openRepository(allUsers)) {
      Timestamp earliestZombieTs = null;
      Timestamp latestZombieTs = null;
      int numZombies = 0;
      List<Ref> draftRefs = allUsersRepo.getRefDatabase().getRefsByPrefix(REFS_DRAFT_COMMENTS);
      Set<ChangeUserIDsPair> visitedSet = new HashSet<>();
      Set<Change.Id> changeIds =
          draftRefs.stream()
              .map(d -> Change.Id.fromAllUsersRef(d.getName()))
              .collect(Collectors.toSet());
      Map<Change.Id, Project.NameKey> changeProjectMap = mapChangeIdsToProjects(changeIds);
      for (Ref draftRef : draftRefs) {
        try {
          Change.Id changeId = Change.Id.fromAllUsersRef(draftRef.getName());
          Account.Id accountId = Account.Id.fromRef(draftRef.getName());
          ChangeUserIDsPair changeUserIDsPair = ChangeUserIDsPair.create(changeId, accountId);
          if (!visitedSet.add(changeUserIDsPair)) {
            continue;
          }
          DraftCommentNotes draftNotes = draftNotesFactory.create(changeId, accountId).load();
          ChangeNotes notes =
              changeNotesFactory.createChecked(changeProjectMap.get(changeId), changeId);
          List<HumanComment> drafts = draftNotes.getComments().values().asList();
          List<HumanComment> published = commentsUtil.publishedHumanCommentsByChange(notes);
          Set<String> publishedIds = toUuid(published);
          List<HumanComment> zombieDrafts =
              drafts.stream()
                  .filter(draft -> publishedIds.contains(draft.key.uuid))
                  .collect(Collectors.toList());
          for (HumanComment zombieDraft : zombieDrafts) {
            earliestZombieTs = getEarlierTs(earliestZombieTs, zombieDraft.writtenOn);
            latestZombieTs = getLaterTs(latestZombieTs, zombieDraft.writtenOn);
          }
          zombieDrafts.forEach(
              zombieDraft ->
                  logger.atWarning().log(
                      "Draft comment with uuid '%s' of change %s, account %s, written on %s,"
                          + " is a zombie draft that is already published.",
                      zombieDraft.key.uuid, changeId, accountId, zombieDraft.writtenOn));
          numZombies += zombieDrafts.size();
        } catch (Exception e) {
          logger.atWarning().withCause(e).log("Failed to process ref %s", draftRef.getName());
        }
      }
      if (numZombies > 0) {
        logger.atWarning().log(
            "Detected %d additional zombie drafts (earliest at %s, latest at %s).",
            numZombies, earliestZombieTs, latestZombieTs);
      }
      return numZombies;
    }
  }

  @AutoValue
  abstract static class ChangeUserIDsPair {
    abstract Change.Id changeId();

    abstract Account.Id accountId();

    static ChangeUserIDsPair create(Change.Id changeId, Account.Id accountId) {
      return new AutoValue_DeleteZombieCommentsRefs_ChangeUserIDsPair(changeId, accountId);
    }
  }

  private Map<Change.Id, Project.NameKey> mapChangeIdsToProjects(Set<Change.Id> changeIds) {
    Map<Change.Id, Project.NameKey> result = new HashMap<>();
    Set<Change.Id> found = new HashSet<>();
    for (Project.NameKey project : repoManager.list()) {
      try (Repository repo = repoManager.openRepository(project)) {
        for (Change.Id changeId : Sets.difference(changeIds, found)) {
          Ref ref = repo.getRefDatabase().exactRef(RefNames.changeMetaRef(changeId));
          if (ref != null) {
            result.put(changeId, project);
            found.add(changeId);
          }
        }
      } catch (Exception e) {
        logger.atWarning().withCause(e).log("Failed to open repository for project '%s'.", project);
      }
    }
    if (found.size() != changeIds.size()) {
      logger.atWarning().log(
          "Failed to associate the following change Ids to a project: %s",
          Sets.difference(changeIds, found));
    }
    return result;
  }

  /** Map the list of input comments to their UUIDs. */
  private Set<String> toUuid(List<HumanComment> in) {
    return in.stream().map(c -> c.key.uuid).collect(Collectors.toSet());
  }

  private Timestamp getEarlierTs(@Nullable Timestamp t1, Timestamp t2) {
    if (t1 == null) {
      return t2;
    }
    return t1.before(t2) ? t1 : t2;
  }

  private Timestamp getLaterTs(@Nullable Timestamp t1, Timestamp t2) {
    if (t1 == null) {
      return t2;
    }
    return t1.after(t2) ? t1 : t2;
  }

  private void deleteBatchZombieRefs(Repository allUsersRepo, List<Ref> refsBatch)
      throws IOException {
    List<ReceiveCommand> deleteCommands =
        refsBatch.stream()
            .map(
                zombieRef ->
                    new ReceiveCommand(
                        zombieRef.getObjectId(), ObjectId.zeroId(), zombieRef.getName()))
            .collect(toImmutableList());
    BatchRefUpdate bru = allUsersRepo.getRefDatabase().newBatchUpdate();
    bru.setAtomic(true);
    bru.addCommand(deleteCommands);
    RefUpdateUtil.executeChecked(bru, allUsersRepo);
  }

  private List<Ref> filterZombieRefs(Repository allUsersRepo, List<Ref> allDraftRefs)
      throws IOException {
    List<Ref> zombieRefs = new ArrayList<>((int) (allDraftRefs.size() * 0.5));
    for (Ref ref : allDraftRefs) {
      if (isZombieRef(allUsersRepo, ref)) {
        zombieRefs.add(ref);
      }
    }
    return zombieRefs;
  }

  private boolean isZombieRef(Repository allUsersRepo, Ref ref) throws IOException {
    return allUsersRepo.parseCommit(ref.getObjectId()).getTree().getId().equals(EMPTY_TREE_ID);
  }

  private void logInfo(String message) {
    logger.atInfo().log("%s", message);
    uiConsumer.accept(message);
  }

  private void logProgress(long deletedRefsCount, long allRefsCount, long elapsed) {
    logInfo(
        String.format(
            "Deleted %d/%d zombie draft refs (%d seconds)",
            deletedRefsCount, allRefsCount, elapsed));
  }
}
