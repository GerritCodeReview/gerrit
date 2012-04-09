// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.patch;

import com.google.gerrit.common.data.ApprovalSummary;
import com.google.gerrit.common.data.ApprovalSummarySet;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.data.PatchDetailService;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.common.data.ReviewResult;
import com.google.gerrit.common.data.ReviewerResult;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.BaseServiceImplementation;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.httpd.rpc.changedetail.ChangeDetailFactory;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.AccountPatchReview;
import com.google.gerrit.reviewdb.client.ApprovalCategory;
import com.google.gerrit.reviewdb.client.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Patch.Key;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.changedetail.DeleteDraftPatchSet;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.patch.PublishComments;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtjsonrpc.common.VoidResult;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class PatchDetailServiceImpl extends BaseServiceImplementation implements
    PatchDetailService {
  private final ApprovalTypes approvalTypes;

  private final AccountInfoCacheFactory.Factory accountInfoCacheFactory;
  private final AddReviewerHandler.Factory addReviewerHandlerFactory;
  private final ChangeControl.Factory changeControlFactory;
  private final DeleteDraftPatchSet.Factory deleteDraftPatchSetFactory;
  private final RemoveReviewerHandler.Factory removeReviewerHandlerFactory;
  private final FunctionState.Factory functionStateFactory;
  private final PublishComments.Factory publishCommentsFactory;
  private final PatchScriptFactory.Factory patchScriptFactoryFactory;
  private final SaveDraft.Factory saveDraftFactory;
  private final ChangeDetailFactory.Factory changeDetailFactory;

  @Inject
  PatchDetailServiceImpl(final Provider<ReviewDb> schema,
      final Provider<CurrentUser> currentUser,
      final ApprovalTypes approvalTypes,
      final AccountInfoCacheFactory.Factory accountInfoCacheFactory,
      final AddReviewerHandler.Factory addReviewerHandlerFactory,
      final RemoveReviewerHandler.Factory removeReviewerHandlerFactory,
      final ChangeControl.Factory changeControlFactory,
      final DeleteDraftPatchSet.Factory deleteDraftPatchSetFactory,
      final FunctionState.Factory functionStateFactory,
      final PatchScriptFactory.Factory patchScriptFactoryFactory,
      final PublishComments.Factory publishCommentsFactory,
      final SaveDraft.Factory saveDraftFactory,
      final ChangeDetailFactory.Factory changeDetailFactory) {
    super(schema, currentUser);
    this.approvalTypes = approvalTypes;

    this.accountInfoCacheFactory = accountInfoCacheFactory;
    this.addReviewerHandlerFactory = addReviewerHandlerFactory;
    this.removeReviewerHandlerFactory = removeReviewerHandlerFactory;
    this.changeControlFactory = changeControlFactory;
    this.deleteDraftPatchSetFactory = deleteDraftPatchSetFactory;
    this.functionStateFactory = functionStateFactory;
    this.patchScriptFactoryFactory = patchScriptFactoryFactory;
    this.publishCommentsFactory = publishCommentsFactory;
    this.saveDraftFactory = saveDraftFactory;
    this.changeDetailFactory = changeDetailFactory;
  }

  public void patchScript(final Patch.Key patchKey, final PatchSet.Id psa,
      final PatchSet.Id psb, final AccountDiffPreference dp,
      final AsyncCallback<PatchScript> callback) {
    if (psb == null) {
      callback.onFailure(new NoSuchEntityException());
      return;
    }
    patchScriptFactoryFactory.create(patchKey, psa, psb, dp).to(callback);
  }

  public void saveDraft(final PatchLineComment comment,
      final AsyncCallback<PatchLineComment> callback) {
    saveDraftFactory.create(comment).to(callback);
  }

  public void deleteDraft(final PatchLineComment.Key commentKey,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(ReviewDb db) throws OrmException, Failure {
        Change.Id id = commentKey.getParentKey().getParentKey().getParentKey();
        db.changes().beginTransaction(id);
        try {
          final PatchLineComment comment = db.patchComments().get(commentKey);
          if (comment == null) {
            throw new Failure(new NoSuchEntityException());
          }
          if (!getAccountId().equals(comment.getAuthor())) {
            throw new Failure(new NoSuchEntityException());
          }
          if (comment.getStatus() != PatchLineComment.Status.DRAFT) {
            throw new Failure(new IllegalStateException("Comment published"));
          }
          db.patchComments().delete(Collections.singleton(comment));
          db.commit();
          return VoidResult.INSTANCE;
        } finally {
          db.rollback();
        }
      }
    });
  }

  public void deleteDraftPatchSet(final PatchSet.Id psid,
      final AsyncCallback<ChangeDetail> callback) {
    run(callback, new Action<ChangeDetail>() {
      public ChangeDetail run(ReviewDb db) throws OrmException, Failure {
        ReviewResult result = null;
        try {
          result = deleteDraftPatchSetFactory.create(psid).call();
          if (result.getErrors().size() > 0) {
            throw new Failure(new NoSuchEntityException());
          }
          if (result.getChangeId() == null) {
            // the change was deleted because the draft patch set that was
            // deleted was the only patch set in the change
            return null;
          }
          return changeDetailFactory.create(result.getChangeId()).call();
        } catch (NoSuchChangeException e) {
          throw new Failure(new NoSuchChangeException(result.getChangeId()));
        } catch (NoSuchEntityException e) {
          throw new Failure(e);
        } catch (PatchSetInfoNotAvailableException e) {
          throw new Failure(e);
        }
      }
    });
  }

  public void publishComments(final PatchSet.Id psid, final String msg,
      final Set<ApprovalCategoryValue.Id> tags,
      final AsyncCallback<VoidResult> cb) {
    Handler.wrap(publishCommentsFactory.create(psid, msg, tags, false)).to(cb);
  }

  /**
   * Update the reviewed status for the file by user @code{account}
   */
  public void setReviewedByCurrentUser(final Key patchKey,
      final boolean reviewed, AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(ReviewDb db) throws OrmException {
        Account.Id account = getAccountId();
        AccountPatchReview.Key key =
            new AccountPatchReview.Key(patchKey, account);
        db.accounts().beginTransaction(account);
        try {
          AccountPatchReview apr = db.accountPatchReviews().get(key);
          if (apr == null && reviewed) {
            db.accountPatchReviews().insert(
                Collections.singleton(new AccountPatchReview(patchKey, account)));
          } else if (apr != null && !reviewed) {
            db.accountPatchReviews().delete(Collections.singleton(apr));
          }
          db.commit();
          return VoidResult.INSTANCE;
        } finally {
          db.rollback();
        }
      }
    });
  }

  public void addReviewers(final Change.Id id, final List<String> reviewers,
      final boolean confirmed, final AsyncCallback<ReviewerResult> callback) {
    addReviewerHandlerFactory.create(id, reviewers, confirmed).to(callback);
  }

  public void removeReviewer(final Change.Id id, final Account.Id reviewerId,
      final AsyncCallback<ReviewerResult> callback) {
    removeReviewerHandlerFactory.create(id, reviewerId).to(callback);
  }

  public void userApprovals(final Set<Change.Id> cids, final Account.Id aid,
      final AsyncCallback<ApprovalSummarySet> callback) {
    run(callback, new Action<ApprovalSummarySet>() {
      public ApprovalSummarySet run(ReviewDb db) throws OrmException {
        final Map<Change.Id, ApprovalSummary> approvals =
            new HashMap<Change.Id, ApprovalSummary>();
        final AccountInfoCacheFactory aicFactory =
            accountInfoCacheFactory.create();

        aicFactory.want(aid);
        for (final Change.Id id : cids) {
          try {
            final ChangeControl cc = changeControlFactory.validateFor(id);
            final Change change = cc.getChange();
            final PatchSet.Id ps_id = change.currentPatchSetId();
            final Map<ApprovalCategory.Id, PatchSetApproval> psas =
                new HashMap<ApprovalCategory.Id, PatchSetApproval>();
            final FunctionState fs =
                functionStateFactory.create(cc, ps_id, psas.values());

            for (final PatchSetApproval ca : db.patchSetApprovals()
                .byPatchSetUser(ps_id, aid)) {
              final ApprovalCategory.Id category = ca.getCategoryId();
              if (ApprovalCategory.SUBMIT.equals(category)) {
                continue;
              }
              if (change.getStatus().isOpen()) {
                fs.normalize(approvalTypes.byId(category), ca);
              }
              if (ca.getValue() == 0) {
                continue;
              }
              psas.put(category, ca);
            }

            approvals.put(id, new ApprovalSummary(psas.values()));
          } catch (NoSuchChangeException nsce) {
            /*
             * The user has no access to see this change, so we simply do not
             * provide any details about it.
             */
          }
        }
        return new ApprovalSummarySet(aicFactory.create(), approvals);
      }
    });
  }

  public void strongestApprovals(final Set<Change.Id> cids,
      final AsyncCallback<ApprovalSummarySet> callback) {
    run(callback, new Action<ApprovalSummarySet>() {
      public ApprovalSummarySet run(ReviewDb db) throws OrmException {
        final Map<Change.Id, ApprovalSummary> approvals =
            new HashMap<Change.Id, ApprovalSummary>();
        final AccountInfoCacheFactory aicFactory =
            accountInfoCacheFactory.create();

        for (final Change.Id id : cids) {
          try {
            final ChangeControl cc = changeControlFactory.validateFor(id);
            final Change change = cc.getChange();
            final PatchSet.Id ps_id = change.currentPatchSetId();
            final Map<ApprovalCategory.Id, PatchSetApproval> psas =
                new HashMap<ApprovalCategory.Id, PatchSetApproval>();
            final FunctionState fs =
                functionStateFactory.create(cc, ps_id, psas.values());

            for (PatchSetApproval ca : db.patchSetApprovals().byPatchSet(ps_id)) {
              final ApprovalCategory.Id category = ca.getCategoryId();
              if (ApprovalCategory.SUBMIT.equals(category)) {
                continue;
              }
              if (change.getStatus().isOpen()) {
                fs.normalize(approvalTypes.byId(category), ca);
              }
              if (ca.getValue() == 0) {
                continue;
              }
              boolean keep = true;
              if (psas.containsKey(category)) {
                final short oldValue = psas.get(category).getValue();
                final short newValue = ca.getValue();
                keep =
                    (Math.abs(oldValue) < Math.abs(newValue))
                        || ((Math.abs(oldValue) == Math.abs(newValue) && (newValue < oldValue)));
              }
              if (keep) {
                aicFactory.want(ca.getAccountId());
                psas.put(category, ca);
              }
            }

            approvals.put(id, new ApprovalSummary(psas.values()));
          } catch (NoSuchChangeException nsce) {
            /*
             * The user has no access to see this change, so we simply do not
             * provide any details about it.
             */
          }
        }

        return new ApprovalSummarySet(aicFactory.create(), approvals);
      }
    });
  }
}
