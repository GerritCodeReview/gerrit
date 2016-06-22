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

package com.google.gerrit.server.change;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.RepoContext;
import com.google.gerrit.server.git.BatchUpdateReviewDb;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.ReceiveCommand;

import java.io.IOException;
import java.util.List;

class DeleteDraftChangeOp extends BatchUpdate.Op {
  static boolean allowDrafts(Config cfg) {
    return cfg.getBoolean("change", "allowDrafts", true);
  }

  static ReviewDb unwrap(ReviewDb db) {
    // This is special. We want to delete exactly the rows that are present in
    // the database, even when reading everything else from NoteDb, so we need
    // to bypass the write-only wrapper.
    if (db instanceof BatchUpdateReviewDb) {
      db = ((BatchUpdateReviewDb) db).unsafeGetDelegate();
    }
    return ReviewDbUtil.unwrapDb(db);
  }


  private final PatchSetUtil psUtil;
  private final StarredChangesUtil starredChangesUtil;
  private final DynamicItem<AccountPatchReviewStore> accountPatchReviewStore;
  private final boolean allowDrafts;

  private Change.Id id;

  @Inject
  DeleteDraftChangeOp(PatchSetUtil psUtil,
      StarredChangesUtil starredChangesUtil,
      DynamicItem<AccountPatchReviewStore> accountPatchReviewStore,
      @GerritServerConfig Config cfg) {
    this.psUtil = psUtil;
    this.starredChangesUtil = starredChangesUtil;
    this.accountPatchReviewStore = accountPatchReviewStore;
    this.allowDrafts = allowDrafts(cfg);
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws RestApiException,
      OrmException, IOException, NoSuchChangeException {
    checkState(ctx.getOrder() == BatchUpdate.Order.DB_BEFORE_REPO,
        "must use DeleteDraftChangeOp with DB_BEFORE_REPO");
    checkState(id == null, "cannot reuse DeleteDraftChangeOp");

    Change change = ctx.getChange();
    id = change.getId();

    ReviewDb db = unwrap(ctx.getDb());
    if (change.getStatus() != Change.Status.DRAFT) {
      throw new ResourceConflictException("Change is not a draft: " + id);
    }
    if (!allowDrafts) {
      throw new MethodNotAllowedException("Draft workflow is disabled");
    }
    if (!ctx.getControl().canDeleteDraft(ctx.getDb())) {
      throw new AuthException("Not permitted to delete this draft change");
    }
    List<PatchSet> patchSets = ImmutableList.copyOf(
        psUtil.byChange(ctx.getDb(), ctx.getNotes()));
    for (PatchSet ps : patchSets) {
      if (!ps.isDraft()) {
        throw new ResourceConflictException("Cannot delete draft change " + id
            + ": patch set " + ps.getPatchSetId() + " is not a draft");
      }
      accountPatchReviewStore.get().clearReviewed(ps.getId());
    }

    // Only delete from ReviewDb here; deletion from NoteDb is handled in
    // BatchUpdate.
    db.patchComments().delete(db.patchComments().byChange(id));
    db.patchSetApprovals().delete(db.patchSetApprovals().byChange(id));
    db.patchSets().delete(db.patchSets().byChange(id));
    db.changeMessages().delete(db.changeMessages().byChange(id));

    // Non-atomic operation on Accounts table; not much we can do to make it
    // atomic.
    starredChangesUtil.unstarAll(change.getProject(), id);

    ctx.deleteChange();
    return true;
  }

  @Override
  public void updateRepo(RepoContext ctx) throws IOException {
    String prefix = new PatchSet.Id(id, 1).toRefName();
    prefix = prefix.substring(0, prefix.length() - 1);
    for (Ref ref
        : ctx.getRepository().getRefDatabase().getRefs(prefix).values()) {
      ctx.addRefUpdate(
          new ReceiveCommand(
            ref.getObjectId(), ObjectId.zeroId(), ref.getName()));
    }
  }
}
