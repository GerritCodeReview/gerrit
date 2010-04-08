package com.google.gerrit.httpd.rpc.patch;

import com.google.gerrit.common.data.ReviewerResult;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.httpd.rpc.changedetail.ChangeDetailFactory;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Change.Id;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.internal.Lists;

import java.util.List;

/**
 * Implement the remote logic that removes a reviewer from a change.
 *
 * @author Cedric Beust (cbeust@google.com
 */
class RemoveReviewer extends Handler<ReviewerResult> {
  interface Factory {
    RemoveReviewer create(Change.Id changeId, Account.Id reviewerId);
  }

  private final Account.Id reviewerId;
  private final ReviewDb db;
  private final Id changeId;
  private final ChangeDetailFactory.Factory changeDetailFactory;

  @Inject
  RemoveReviewer(final ReviewDb db, final ChangeDetailFactory.Factory changeDetailFactory,
      @Assisted Change.Id changeId, @Assisted Account.Id reviewerId) {
    this.db = db;
    this.changeId = changeId;
    this.reviewerId = reviewerId;
    this.changeDetailFactory = changeDetailFactory;
  }

  @Override
  public ReviewerResult call() throws Exception {
    ReviewerResult result = new ReviewerResult();
    List<Account.Id> accounts = Lists.newArrayList();
    Iterable<PatchSetApproval> patchSets = db.patchSetApprovals().byChange(changeId);
    List<PatchSetApproval> toDelete = Lists.newArrayList();
    for (PatchSetApproval psa : patchSets) {
      if (psa.getAccountId().equals(reviewerId)) {
        toDelete.add(psa);
      }
    }
    try {
      db.patchSetApprovals().delete(toDelete);
    } catch(OrmException ex) {
      result.addError(new ReviewerResult.Error(
          ReviewerResult.Error.Type.COULD_NOT_REMOVE, "Could not remove reviewer " + reviewerId));
    }

    // Note: call setChange() after the deletion has been made or it will still
    // contain the reviewer we want to delete.
    result.setChange(changeDetailFactory.create(changeId).call());
    return result;
  }

}
