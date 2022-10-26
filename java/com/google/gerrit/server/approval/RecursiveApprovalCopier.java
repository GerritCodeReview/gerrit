// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.approval;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.function.Consumer;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public class RecursiveApprovalCopier {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final BatchUpdate.Factory batchUpdateFactory;
  private final GitRepositoryManager repositoryManager;
  private final InternalUser.Factory internalUserFactory;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeNotes.Factory changeNotesFactory;

  private boolean failedForAtLeastOneProject;

  @Inject
  public RecursiveApprovalCopier(
      BatchUpdate.Factory batchUpdateFactory,
      GitRepositoryManager repositoryManager,
      InternalUser.Factory internalUserFactory,
      ApprovalsUtil approvalsUtil,
      ChangeNotes.Factory changeNotesFactory) {
    this.batchUpdateFactory = batchUpdateFactory;
    this.repositoryManager = repositoryManager;
    this.internalUserFactory = internalUserFactory;
    this.approvalsUtil = approvalsUtil;
    this.changeNotesFactory = changeNotesFactory;
  }

  public void persist()
      throws RepositoryNotFoundException, IOException, UpdateException, RestApiException {
    for (Project.NameKey project : repositoryManager.list()) {
      persist(project, null);
    }

    if (failedForAtLeastOneProject) {
      throw new RuntimeException("There were errors, check the logs for details");
    }
  }

  public void persist(Project.NameKey project, @Nullable Consumer<Change> labelsCopiedListener)
      throws IOException, UpdateException, RestApiException, RepositoryNotFoundException {
    try {
      persist(project, labelsCopiedListener, false);
    } catch (Exception e) {
      failedForAtLeastOneProject = true;
      logger.atSevere().withCause(e).log(
          "failed for project %s, will retry with the checkForCorruptMetaRefs option", project);
      persist(project, labelsCopiedListener, true);
    }
  }

  private void persist(
      Project.NameKey project,
      @Nullable Consumer<Change> labelsCopiedListener,
      boolean checkForCorruptMetaRefs)
      throws IOException, UpdateException, RestApiException, RepositoryNotFoundException {
    try (BatchUpdate bu =
            batchUpdateFactory.create(project, internalUserFactory.create(), TimeUtil.nowTs());
        Repository repository = repositoryManager.openRepository(project)) {
      for (Ref changeMetaRef :
          repository.getRefDatabase().getRefsByPrefix(RefNames.REFS_CHANGES).stream()
              .filter(r -> r.getName().endsWith(RefNames.META_SUFFIX))
              .collect(toImmutableList())) {
        Change.Id changeId = Change.Id.fromRef(changeMetaRef.getName());
        if (checkForCorruptMetaRefs && isCorrupt(project, changeId)) {
          logger.atSevere().log("skipping corrupt meta-ref %s", changeMetaRef.getName());
          continue;
        }
        bu.addOp(changeId, new PersistCopiedVotesOp(approvalsUtil, labelsCopiedListener));
      }
      bu.execute();
    }
  }

  private boolean isCorrupt(Project.NameKey project, Change.Id changeId) {
    Change c = ChangeNotes.Factory.newChange(project, changeId);
    try {
      changeNotesFactory.createForBatchUpdate(c, true);
      return false;
    } catch (Exception e) {
      logger.atSevere().withCause(e).log(e.getMessage());
      return true;
    }
  }

  public void persist(Change change) throws UpdateException, RestApiException {
    Project.NameKey project = change.getProject();
    try (BatchUpdate bu =
        batchUpdateFactory.create(project, internalUserFactory.create(), TimeUtil.nowTs())) {
      Change.Id changeId = change.getId();
      bu.addOp(changeId, new PersistCopiedVotesOp(approvalsUtil, null));
      bu.execute();
    }
  }

  private static class PersistCopiedVotesOp implements BatchUpdateOp {
    private final ApprovalsUtil approvalsUtil;
    private final Consumer<Change> listener;

    PersistCopiedVotesOp(
        ApprovalsUtil approvalsUtil, @Nullable Consumer<Change> labelsCopiedListener) {
      this.approvalsUtil = approvalsUtil;
      this.listener = labelsCopiedListener;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws IOException {
      Change change = ctx.getChange();
      ChangeUpdate update = ctx.getUpdate(change.currentPatchSetId());
      approvalsUtil.persistCopiedApprovals(
          ctx.getNotes(),
          ctx.getNotes().getCurrentPatchSet(),
          ctx.getRevWalk(),
          ctx.getRepoView().getConfig(),
          update);

      boolean labelsCopied = update.hasCopiedApprovals();

      if (labelsCopied && listener != null) {
        listener.accept(change);
      }

      return labelsCopied;
    }
  }
}
