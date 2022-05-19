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
import static org.eclipse.jgit.lib.Constants.EMPTY_TREE_ID;

import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
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
 * <p>An earlier bug in the deletion of draft comments {@code
 * refs/draft-comments/$change_id_short/$change_id/$user_id} caused some draft refs to remain in Git
 * and not get deleted. These refs point to an empty tree.
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
  private final ChangeNotes.Factory notesFactory;
  private final CommentsUtil commentsUtil;

  public interface Factory {
    DeleteZombieCommentsRefs create(int cleanupPercentage);
  }

  @Inject
  public DeleteZombieCommentsRefs(
      AllUsersName allUsers,
      GitRepositoryManager repoManager,
      @Assisted Integer cleanupPercentage) {
    this(allUsers, repoManager, cleanupPercentage, (msg) -> {});
  }

  public DeleteZombieCommentsRefs(
      AllUsersName allUsers,
      GitRepositoryManager repoManager,
      Integer cleanupPercentage,
      Consumer<String> uiConsumer) {
    this.allUsers = allUsers;
    this.repoManager = repoManager;
    this.cleanupPercentage = (cleanupPercentage == null) ? 100 : cleanupPercentage;
    this.uiConsumer = uiConsumer;
    this.notesFactory = null;
    this.commentsUtil = null;
  }

  public DeleteZombieCommentsRefs(
      AllUsersName allUsers,
      GitRepositoryManager repoManager,
      Integer cleanupPercentage,
      Consumer<String> uiConsumer,
      ChangeNotes.Factory changeNotesFactory,
      CommentsUtil commentsUtil) {
    this.allUsers = allUsers;
    this.repoManager = repoManager;
    this.cleanupPercentage = (cleanupPercentage == null) ? 100 : cleanupPercentage;
    this.uiConsumer = uiConsumer;
    this.notesFactory = changeNotesFactory;
    this.commentsUtil = commentsUtil;
  }

  public void execute() throws IOException {
    deleteDraftRefsThatPointToEmptyTree();
    if (notesFactory != null) {
      logWarningForDraftsThatArePublished();
    }
  }

  private void deleteDraftRefsThatPointToEmptyTree() throws IOException {
    try (Repository allUsersRepo = repoManager.openRepository(allUsers)) {
      List<Ref> draftRefs =
          allUsersRepo.getRefDatabase().getRefsByPrefix(RefNames.REFS_DRAFT_COMMENTS);
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
  private void logWarningForDraftsThatArePublished() throws IOException {
    try (Repository allUsersRepo = repoManager.openRepository(allUsers)) {
      List<Ref> draftRefs =
          allUsersRepo.getRefDatabase().getRefsByPrefix(RefNames.REFS_DRAFT_COMMENTS);
      Map<Change.Id, List<Ref>> draftsByChangeId = draftsByChangeId(draftRefs);
      int numZombies = 0;
      for (Change.Id changeId : draftsByChangeId.keySet()) {
        ChangeNotes notes = notesFactory.createCheckedUsingIndexLookup(changeId);
        List<HumanComment> draftComments = commentsUtil.draftByChange(notes);
        Map<String, HumanComment> publishedComments =
            commentsById(commentsUtil.publishedHumanCommentsByChange(notes));
        for (HumanComment draft : draftComments) {
          if (publishedComments.containsKey(draft.key.uuid)) {
            logger.atWarning().log(
                "Draft comment with uuid '%s' of change %s"
                    + " is a zombie draft that is already published.",
                draft.key.uuid, changeId);
            numZombies += 1;
          }
        }
      }
      if (numZombies > 0) {
        logger.atWarning().log("Detected %d additional zombie drafts.", numZombies);
      }
    }
  }

  /** Group refs using change id. */
  private Map<Change.Id, List<Ref>> draftsByChangeId(List<Ref> draftRefs) {
    Map<Change.Id, List<Ref>> byChangeId = new HashMap<>();
    for (Ref ref : draftRefs) {
      Change.Id changeId = Change.Id.fromAllUsersRef(ref.getName());
      if (!byChangeId.containsKey(changeId)) {
        byChangeId.put(changeId, new ArrayList<>());
      }
      byChangeId.get(changeId).add(ref);
    }
    return byChangeId;
  }

  /** Group comments by their id. */
  private Map<String, HumanComment> commentsById(List<HumanComment> in) {
    return in.stream().collect(Collectors.toMap(c -> c.key.uuid, Function.identity()));
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
