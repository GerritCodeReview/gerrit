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
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;
import static org.eclipse.jgit.lib.Constants.EMPTY_TREE_ID;

import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.DeleteZombieComments;
import com.google.gerrit.server.DraftCommentsReader;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;

public class DeleteZombieCommentsRefs extends DeleteZombieComments<Ref> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // Number of refs deleted at once in a batch ref-update.
  // Log progress after deleting every CHUNK_SIZE refs
  private static final int CHUNK_SIZE = 3000;

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsers;

  @Nullable private final ChangeUpdate.Factory changeUpdateFactory;
  @Nullable private final IdentifiedUser.GenericFactory userFactory;

  private Repository allUsersRepo;

  public interface Factory {
    DeleteZombieCommentsRefs create(int cleanupPercentage);

    DeleteZombieCommentsRefs create(int cleanupPercentage, boolean dryRun);
  }

  @AssistedInject
  public DeleteZombieCommentsRefs(
      @Assisted Integer cleanupPercentage,
      GitRepositoryManager repoManager,
      AllUsersName allUsers,
      DraftCommentsReader draftCommentsReader,
      ChangeNotes.Factory changeNotesFactory,
      CommentsUtil commentsUtil,
      ChangeUpdate.Factory changeUpdateFactory,
      IdentifiedUser.GenericFactory userFactory) {
    this(
        cleanupPercentage,
        /* dryRun= */ true,
        (msg) -> {},
        repoManager,
        allUsers,
        draftCommentsReader,
        changeNotesFactory,
        commentsUtil,
        changeUpdateFactory,
        userFactory);
  }

  @AssistedInject
  public DeleteZombieCommentsRefs(
      @Assisted Integer cleanupPercentage,
      @Assisted boolean dryRun,
      GitRepositoryManager repoManager,
      AllUsersName allUsers,
      DraftCommentsReader draftCommentsReader,
      ChangeNotes.Factory changeNotesFactory,
      CommentsUtil commentsUtil,
      ChangeUpdate.Factory changeUpdateFactory,
      IdentifiedUser.GenericFactory userFactory) {
    this(
        cleanupPercentage,
        dryRun,
        (msg) -> {},
        repoManager,
        allUsers,
        draftCommentsReader,
        changeNotesFactory,
        commentsUtil,
        changeUpdateFactory,
        userFactory);
  }

  public DeleteZombieCommentsRefs(
      AllUsersName allUsers,
      GitRepositoryManager repoManager,
      Integer cleanupPercentage,
      Consumer<String> uiConsumer) {
    this(
        cleanupPercentage,
        /* dryRun= */ false,
        uiConsumer,
        repoManager,
        allUsers,
        null,
        null,
        null,
        null,
        null);
  }

  private DeleteZombieCommentsRefs(
      Integer cleanupPercentage,
      boolean dryRun,
      Consumer<String> uiConsumer,
      GitRepositoryManager repoManager,
      AllUsersName allUsers,
      @Nullable DraftCommentsReader draftCommentsReader,
      @Nullable ChangeNotes.Factory changeNotesFactory,
      @Nullable CommentsUtil commentsUtil,
      @Nullable ChangeUpdate.Factory changeUpdateFactory,
      @Nullable IdentifiedUser.GenericFactory userFactory) {
    super(
        cleanupPercentage,
        dryRun,
        uiConsumer,
        repoManager,
        draftCommentsReader,
        changeNotesFactory,
        commentsUtil);
    this.allUsers = allUsers;
    this.repoManager = repoManager;
    this.changeUpdateFactory = changeUpdateFactory;
    this.userFactory = userFactory;
  }

  @Override
  public void setup() throws IOException {
    allUsersRepo = repoManager.openRepository(allUsers);
  }

  @Override
  public void close() throws IOException {
    allUsersRepo.close();
  }

  @Override
  protected List<Ref> listAllDrafts() throws IOException {
    return allUsersRepo.getRefDatabase().getRefsByPrefix(REFS_DRAFT_COMMENTS);
  }

  @Override
  protected List<Ref> listEmptyDrafts() throws IOException {
    List<Ref> zombieRefs = filterZombieRefs(allUsersRepo, listAllDrafts());
    logInfo(
        String.format(
            "Found a total of %d zombie draft refs in %s repo.",
            zombieRefs.size(), allUsers.get()));
    return zombieRefs;
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

  /**
   * An earlier bug in the deletion of draft comments {@code
   * refs/draft-comments/$change_id_short/$change_id/$user_id} caused some draft refs to remain in
   * Git and not get deleted. These refs point to an empty tree. We delete such refs.
   */
  @Override
  protected void deleteEmptyDraftsByKey(Collection<Ref> refs) throws IOException {
    long zombieRefsCnt = refs.size();
    long deletedRefsCnt = 0;
    long startTime = System.currentTimeMillis();

    for (List<Ref> refsBatch : Iterables.partition(refs, CHUNK_SIZE)) {
      deleteZombieDraftsBatch(refsBatch);
      long elapsed = (System.currentTimeMillis() - startTime) / 1000;
      deletedRefsCnt += refsBatch.size();
      logProgress(deletedRefsCnt, zombieRefsCnt, elapsed);
    }
  }

  private void logProgress(long deletedRefsCount, long allRefsCount, long elapsed) {
    logInfo(
        String.format(
            "Deleted %d/%d zombie draft refs (%d seconds)",
            deletedRefsCount, allRefsCount, elapsed));
  }

  private void deleteZombieDraftsBatch(Collection<Ref> refsBatch) throws IOException {
    try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
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
  }

  @Override
  protected void deleteZombieDrafts(ListMultimap<Ref, HumanComment> drafts) throws IOException {
    for (Map.Entry<Ref, Collection<HumanComment>> e : drafts.asMap().entrySet()) {
      deleteZombieDraftsForChange(
          getAccountId(e.getKey()), getChangeNotes(getChangeId(e.getKey())), e.getValue());
    }
  }
  /**
   * Accepts a list of draft (zombie) comments for the same change and delete them by executing a
   * {@link ChangeUpdate} on NoteDb. The update is executed using the user account who created this
   * draft.
   */
  private void deleteZombieDraftsForChange(
      Account.Id accountId, ChangeNotes changeNotes, Collection<HumanComment> draftsToDelete)
      throws IOException {
    if (changeUpdateFactory == null || userFactory == null) {
      return;
    }
    ChangeUpdate changeUpdate =
        changeUpdateFactory.create(changeNotes, userFactory.create(accountId), TimeUtil.now());
    draftsToDelete.forEach(c -> changeUpdate.deleteComment(c));
    changeUpdate.commit();
    logger.atInfo().log(
        "Deleted zombie draft comments with UUIDs %s",
        draftsToDelete.stream().map(d -> d.key.uuid).collect(Collectors.toList()));
  }

  @Override
  protected Change.Id getChangeId(Ref ref) {
    return Change.Id.fromAllUsersRef(ref.getName());
  }

  @Override
  protected Account.Id getAccountId(Ref ref) {
    return Account.Id.fromRef(ref.getName());
  }

  @Override
  protected String loggable(Ref ref) {
    return ref.getName();
  }
}
