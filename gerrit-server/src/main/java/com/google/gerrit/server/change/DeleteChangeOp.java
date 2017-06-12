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
import static com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage.REVIEW_DB;

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
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

class DeleteChangeOp extends BatchUpdate.Op {
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
  DeleteChangeOp(
      PatchSetUtil psUtil,
      StarredChangesUtil starredChangesUtil,
      DynamicItem<AccountPatchReviewStore> accountPatchReviewStore,
      @GerritServerConfig Config cfg) {
    this.psUtil = psUtil;
    this.starredChangesUtil = starredChangesUtil;
    this.accountPatchReviewStore = accountPatchReviewStore;
    this.allowDrafts = allowDrafts(cfg);
  }

  @Override
  public boolean updateChange(ChangeContext ctx)
      throws RestApiException, OrmException, IOException, NoSuchChangeException {
    checkState(
        ctx.getOrder() == BatchUpdate.Order.DB_BEFORE_REPO,
        "must use DeleteChangeOp with DB_BEFORE_REPO");
    checkState(id == null, "cannot reuse DeleteChangeOp");

    id = ctx.getChange().getId();
    Collection<PatchSet> patchSets = psUtil.byChange(ctx.getDb(), ctx.getNotes());

    ensureDeletable(ctx, id, patchSets);
    // Cleaning up is only possible as long as the change and its elements are
    // still part of the database.
    cleanUpReferences(ctx, id, patchSets);
    deleteChangeElementsFromDb(ctx, id);

    ctx.deleteChange();
    return true;
  }

  private void ensureDeletable(ChangeContext ctx, Change.Id id, Collection<PatchSet> patchSets)
      throws ResourceConflictException, MethodNotAllowedException, OrmException, AuthException,
          IOException {
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

    if (!ctx.getControl().canDelete(ctx.getDb(), status)) {
      throw new AuthException("Deleting change " + id + " is not permitted");
    }

    if (status == Change.Status.DRAFT) {
      if (!allowDrafts && !ctx.getControl().isAdmin()) {
        throw new MethodNotAllowedException("Draft workflow is disabled");
      }
      for (PatchSet ps : patchSets) {
        if (!ps.isDraft()) {
          throw new ResourceConflictException(
              "Cannot delete draft change "
                  + id
                  + ": patch set "
                  + ps.getPatchSetId()
                  + " is not a draft");
        }
      }
    }
  }

  private boolean isPatchSetMerged(ChangeContext ctx, PatchSet patchSet) throws IOException {
    Repository repository = ctx.getRepository();
    Ref destinationRef = repository.exactRef(ctx.getChange().getDest().get());
    if (destinationRef == null) {
      return false;
    }

    RevWalk revWalk = ctx.getRevWalk();
    ObjectId objectId = ObjectId.fromString(patchSet.getRevision().get());
    RevCommit revCommit = revWalk.parseCommit(objectId);
    return IncludedInResolver.includedInOne(
        repository, revWalk, revCommit, Collections.singletonList(destinationRef));
  }

  private void deleteChangeElementsFromDb(ChangeContext ctx, Change.Id id) throws OrmException {
    if (PrimaryStorage.of(ctx.getChange()) != REVIEW_DB) {
      return;
    }
    // Avoid OrmConcurrencyException trying to delete non-existent entities.
    // Only delete from ReviewDb here; deletion from NoteDb is handled in
    // BatchUpdate.
    ReviewDb db = unwrap(ctx.getDb());
    db.patchComments().delete(db.patchComments().byChange(id));
    db.patchSetApprovals().delete(db.patchSetApprovals().byChange(id));
    db.patchSets().delete(db.patchSets().byChange(id));
    db.changeMessages().delete(db.changeMessages().byChange(id));
  }

  private void cleanUpReferences(ChangeContext ctx, Change.Id id, Collection<PatchSet> patchSets)
      throws OrmException, NoSuchChangeException {
    for (PatchSet ps : patchSets) {
      accountPatchReviewStore.get().clearReviewed(ps.getId());
    }

    // Non-atomic operation on Accounts table; not much we can do to make it
    // atomic.
    starredChangesUtil.unstarAll(ctx.getChange().getProject(), id);
  }

  @Override
  public void updateRepo(RepoContext ctx) throws IOException {
    String prefix = new PatchSet.Id(id, 1).toRefName();
    prefix = prefix.substring(0, prefix.length() - 1);
    for (Ref ref : ctx.getRepository().getRefDatabase().getRefs(prefix).values()) {
      ctx.addRefUpdate(new ReceiveCommand(ref.getObjectId(), ObjectId.zeroId(), ref.getName()));
    }
  }
}
