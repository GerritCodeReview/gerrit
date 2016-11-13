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

package com.google.gerrit.server.change;

import static com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage.REVIEW_DB;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.DeleteDraftPatchSet.Input;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.RepoContext;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.ReceiveCommand;

@Singleton
public class DeleteDraftPatchSet
    implements RestModifyView<RevisionResource, Input>, UiAction<RevisionResource> {
  public static class Input {}

  private final Provider<ReviewDb> db;
  private final BatchUpdate.Factory updateFactory;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final PatchSetUtil psUtil;
  private final Provider<DeleteChangeOp> deleteChangeOpProvider;
  private final DynamicItem<AccountPatchReviewStore> accountPatchReviewStore;
  private final boolean allowDrafts;

  @Inject
  public DeleteDraftPatchSet(
      Provider<ReviewDb> db,
      BatchUpdate.Factory updateFactory,
      PatchSetInfoFactory patchSetInfoFactory,
      PatchSetUtil psUtil,
      Provider<DeleteChangeOp> deleteChangeOpProvider,
      DynamicItem<AccountPatchReviewStore> accountPatchReviewStore,
      @GerritServerConfig Config cfg) {
    this.db = db;
    this.updateFactory = updateFactory;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.psUtil = psUtil;
    this.deleteChangeOpProvider = deleteChangeOpProvider;
    this.accountPatchReviewStore = accountPatchReviewStore;
    this.allowDrafts = cfg.getBoolean("change", "allowDrafts", true);
  }

  @Override
  public Response<?> apply(RevisionResource rsrc, Input input)
      throws RestApiException, UpdateException {
    try (BatchUpdate bu =
        updateFactory.create(db.get(), rsrc.getProject(), rsrc.getUser(), TimeUtil.nowTs())) {
      bu.setOrder(BatchUpdate.Order.DB_BEFORE_REPO);
      bu.addOp(rsrc.getChange().getId(), new Op(rsrc.getPatchSet().getId()));
      bu.execute();
    }
    return Response.none();
  }

  private class Op extends BatchUpdate.Op {
    private final PatchSet.Id psId;

    private Collection<PatchSet> patchSetsBeforeDeletion;
    private PatchSet patchSet;
    private DeleteChangeOp deleteChangeOp;

    private Op(PatchSet.Id psId) {
      this.psId = psId;
    }

    @Override
    public boolean updateChange(ChangeContext ctx)
        throws RestApiException, OrmException, IOException, NoSuchChangeException {
      patchSet = psUtil.get(ctx.getDb(), ctx.getNotes(), psId);
      if (patchSet == null) {
        return false; // Nothing to do.
      }
      if (!patchSet.isDraft()) {
        throw new ResourceConflictException("Patch set is not a draft");
      }
      if (!allowDrafts) {
        throw new MethodNotAllowedException("Draft workflow is disabled");
      }
      if (!ctx.getControl().canDelete(ctx.getDb(), Change.Status.DRAFT)) {
        throw new AuthException("Not permitted to delete this draft patch set");
      }

      patchSetsBeforeDeletion = psUtil.byChange(ctx.getDb(), ctx.getNotes());
      deleteDraftPatchSet(patchSet, ctx);
      deleteOrUpdateDraftChange(ctx);
      return true;
    }

    @Override
    public void updateRepo(RepoContext ctx) throws IOException {
      if (deleteChangeOp != null) {
        deleteChangeOp.updateRepo(ctx);
        return;
      }
      ctx.addRefUpdate(
          new ReceiveCommand(
              ObjectId.fromString(patchSet.getRevision().get()),
              ObjectId.zeroId(),
              patchSet.getRefName()));
    }

    private void deleteDraftPatchSet(PatchSet patchSet, ChangeContext ctx) throws OrmException {
      // For NoteDb itself, no need to delete these entities, as they are
      // automatically filtered out when patch sets are deleted.
      psUtil.delete(ctx.getDb(), ctx.getUpdate(patchSet.getId()), patchSet);

      accountPatchReviewStore.get().clearReviewed(psId);
      if (PrimaryStorage.of(ctx.getChange()) == REVIEW_DB) {
        // Avoid OrmConcurrencyException trying to delete non-existent entities.
        // Use the unwrap from DeleteChangeOp to handle BatchUpdateReviewDb.
        ReviewDb db = DeleteChangeOp.unwrap(ctx.getDb());
        db.changeMessages().delete(db.changeMessages().byPatchSet(psId));
        db.patchComments().delete(db.patchComments().byPatchSet(psId));
        db.patchSetApprovals().delete(db.patchSetApprovals().byPatchSet(psId));
      }
    }

    private void deleteOrUpdateDraftChange(ChangeContext ctx)
        throws OrmException, RestApiException, IOException, NoSuchChangeException {
      Change c = ctx.getChange();
      if (deletedOnlyPatchSet()) {
        deleteChangeOp = deleteChangeOpProvider.get();
        deleteChangeOp.updateChange(ctx);
        return;
      }
      if (c.currentPatchSetId().equals(psId)) {
        c.setCurrentPatchSet(previousPatchSetInfo(ctx));
      }
    }

    private boolean deletedOnlyPatchSet() {
      return patchSetsBeforeDeletion.size() == 1
          && patchSetsBeforeDeletion.iterator().next().getId().equals(psId);
    }

    private PatchSetInfo previousPatchSetInfo(ChangeContext ctx) throws OrmException {
      try {
        // TODO(dborowitz): Get this in a way that doesn't involve re-opening
        // the repo after the updateRepo phase.
        return patchSetInfoFactory.get(
            ctx.getDb(), ctx.getNotes(), new PatchSet.Id(psId.getParentKey(), psId.get() - 1));
      } catch (PatchSetInfoNotAvailableException e) {
        throw new OrmException(e);
      }
    }
  }

  @Override
  public UiAction.Description getDescription(RevisionResource rsrc) {
    try {
      int psCount = psUtil.byChange(db.get(), rsrc.getNotes()).size();
      return new UiAction.Description()
          .setLabel("Delete")
          .setTitle(String.format("Delete draft revision %d", rsrc.getPatchSet().getPatchSetId()))
          .setVisible(
              allowDrafts
                  && rsrc.getPatchSet().isDraft()
                  && rsrc.getControl().canDelete(db.get(), Change.Status.DRAFT)
                  && psCount > 1);
    } catch (OrmException e) {
      throw new IllegalStateException(e);
    }
  }
}
