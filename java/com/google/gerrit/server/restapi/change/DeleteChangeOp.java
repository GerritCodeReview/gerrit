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

package com.google.gerrit.server.restapi.change;

import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.change.AccountPatchReviewStore;
import com.google.gerrit.server.extensions.events.ChangeDeleted;
import com.google.gerrit.server.plugincontext.PluginItemContext;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.BatchUpdateReviewDb;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Order;
import com.google.gerrit.server.update.RepoContext;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;

class DeleteChangeOp implements BatchUpdateOp {
  private final PatchSetUtil psUtil;
  private final StarredChangesUtil starredChangesUtil;
  private final PluginItemContext<AccountPatchReviewStore> accountPatchReviewStore;
  private final ChangeDeleted changeDeleted;

  private Change.Id id;

  @Inject
  DeleteChangeOp(
      PatchSetUtil psUtil,
      StarredChangesUtil starredChangesUtil,
      PluginItemContext<AccountPatchReviewStore> accountPatchReviewStore,
      ChangeDeleted changeDeleted) {
    this.psUtil = psUtil;
    this.starredChangesUtil = starredChangesUtil;
    this.accountPatchReviewStore = accountPatchReviewStore;
    this.changeDeleted = changeDeleted;
  }

  @Override
  public boolean updateChange(ChangeContext ctx)
      throws RestApiException, OrmException, IOException, NoSuchChangeException {
    checkState(
        ctx.getOrder() == Order.DB_BEFORE_REPO, "must use DeleteChangeOp with DB_BEFORE_REPO");
    checkState(id == null, "cannot reuse DeleteChangeOp");

    id = ctx.getChange().getId();
    Collection<PatchSet> patchSets = psUtil.byChange(ctx.getDb(), ctx.getNotes());

    ensureDeletable(ctx, id, patchSets);
    // Cleaning up is only possible as long as the change and its elements are
    // still part of the database.
    cleanUpReferences(ctx, id, patchSets);
    deleteChangeElementsFromDb(ctx, id);

    ctx.deleteChange();
    changeDeleted.fire(ctx.getChange(), ctx.getAccount(), ctx.getWhen());
    return true;
  }

  private void ensureDeletable(ChangeContext ctx, Change.Id id, Collection<PatchSet> patchSets)
      throws ResourceConflictException, MethodNotAllowedException, IOException {
    Change.Status status = ctx.getChange().getStatus();
    if (status == Change.Status.MERGED) {
      throw new MethodNotAllowedException("Deleting merged change " + id + " is not allowed");
    }
    for (PatchSet patchSet : patchSets) {
      if (isPatchSetMerged(ctx, patchSet)) {
        throw new ResourceConflictException(
            String.format(
                "Cannot delete change %s: patch set %s is already merged",
                id, patchSet.getPatchSetId()));
      }
    }
  }

  private boolean isPatchSetMerged(ChangeContext ctx, PatchSet patchSet) throws IOException {
    Optional<ObjectId> destId = ctx.getRepoView().getRef(ctx.getChange().getDest().get());
    if (!destId.isPresent()) {
      return false;
    }

    RevWalk revWalk = ctx.getRevWalk();
    ObjectId objectId = ObjectId.fromString(patchSet.getRevision().get());
    return revWalk.isMergedInto(revWalk.parseCommit(objectId), revWalk.parseCommit(destId.get()));
  }

  private void deleteChangeElementsFromDb(ChangeContext ctx, Change.Id id) throws OrmException {
    // Only delete from ReviewDb here; deletion from NoteDb is handled in
    // BatchUpdate.
    //
    // This is special. We want to delete exactly the rows that are present in
    // the database, even when reading everything else from NoteDb, so we need
    // to bypass the write-only wrapper.
    ReviewDb db = BatchUpdateReviewDb.unwrap(ctx.getDb());
    db.patchComments().delete(db.patchComments().byChange(id));
    db.patchSetApprovals().delete(db.patchSetApprovals().byChange(id));
    db.patchSets().delete(db.patchSets().byChange(id));
    db.changeMessages().delete(db.changeMessages().byChange(id));
  }

  private void cleanUpReferences(ChangeContext ctx, Change.Id id, Collection<PatchSet> patchSets)
      throws OrmException, NoSuchChangeException {
    for (PatchSet ps : patchSets) {
      accountPatchReviewStore.run(s -> s.clearReviewed(ps.getId()), OrmException.class);
    }

    // Non-atomic operation on Accounts table; not much we can do to make it
    // atomic.
    starredChangesUtil.unstarAll(ctx.getChange().getProject(), id);
  }

  @Override
  public void updateRepo(RepoContext ctx) throws IOException {
    String prefix = new PatchSet.Id(id, 1).toRefName();
    prefix = prefix.substring(0, prefix.length() - 1);
    for (Map.Entry<String, ObjectId> e : ctx.getRepoView().getRefs(prefix).entrySet()) {
      ctx.addRefUpdate(e.getValue(), ObjectId.zeroId(), prefix + e.getKey());
    }
  }
}
