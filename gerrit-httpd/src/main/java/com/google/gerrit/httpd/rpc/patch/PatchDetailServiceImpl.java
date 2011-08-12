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

import com.google.gerrit.common.data.ReviewerResult;
import com.google.gerrit.common.data.ApprovalSummary;
import com.google.gerrit.common.data.ApprovalSummarySet;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.PatchDetailService;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.BaseServiceImplementation;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountDiffPreference;
import com.google.gerrit.reviewdb.AccountPatchReview;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Patch;
import com.google.gerrit.reviewdb.PatchLineComment;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Patch.Key;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ReplicationQueue;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.patch.PublishComments;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.io.IOException;
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
  private final RemoveReviewerHandler.Factory removeReviewerHandlerFactory;
  private final FunctionState.Factory functionStateFactory;
  private final PublishComments.Factory publishCommentsFactory;
  private final PatchScriptFactory.Factory patchScriptFactoryFactory;
  private final SaveDraft.Factory saveDraftFactory;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final GitRepositoryManager gitManager;
  private final ReplicationQueue replication;

  @Inject
  PatchDetailServiceImpl(final Provider<ReviewDb> schema,
      final Provider<CurrentUser> currentUser,
      final ApprovalTypes approvalTypes,
      final AccountInfoCacheFactory.Factory accountInfoCacheFactory,
      final AddReviewerHandler.Factory addReviewerHandlerFactory,
      final RemoveReviewerHandler.Factory removeReviewerHandlerFactory,
      final ChangeControl.Factory changeControlFactory,
      final FunctionState.Factory functionStateFactory,
      final PatchScriptFactory.Factory patchScriptFactoryFactory,
      final PublishComments.Factory publishCommentsFactory,
      final SaveDraft.Factory saveDraftFactory,
      final PatchSetInfoFactory patchSetInfoFactory,
      final GitRepositoryManager gitManager,
      final ReplicationQueue replication) {
    super(schema, currentUser);
    this.approvalTypes = approvalTypes;

    this.accountInfoCacheFactory = accountInfoCacheFactory;
    this.addReviewerHandlerFactory = addReviewerHandlerFactory;
    this.removeReviewerHandlerFactory = removeReviewerHandlerFactory;
    this.changeControlFactory = changeControlFactory;
    this.functionStateFactory = functionStateFactory;
    this.patchScriptFactoryFactory = patchScriptFactoryFactory;
    this.publishCommentsFactory = publishCommentsFactory;
    this.saveDraftFactory = saveDraftFactory;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.gitManager = gitManager;
    this.replication = replication;
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
        return VoidResult.INSTANCE;
      }
    });
  }

  public void deleteDraftPatchSet(final PatchSet.Id psid,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(ReviewDb db) throws OrmException, Failure {
        try {
          final ChangeControl cc = changeControlFactory.validateFor(psid.getParentKey());
          if (!cc.isOwner()) {
            throw new Failure(new NoSuchEntityException());
          }
          ChangeUtil.deleteDraftPatchSet(psid, gitManager, replication, patchSetInfoFactory, db);
        } catch (NoSuchChangeException e) {
          throw new Failure(new NoSuchChangeException(psid.getParentKey()));
        } catch (PatchSetInfoNotAvailableException e) {
          throw new Failure(e);
        } catch (IOException e) {
          throw new Failure(e);
        }
        return VoidResult.INSTANCE;
      }
    });
  }

  public void publishComments(final PatchSet.Id psid, final String msg,
      final Set<ApprovalCategoryValue.Id> tags,
      final AsyncCallback<VoidResult> cb) {
    Handler.wrap(publishCommentsFactory.create(psid, msg, tags)).to(cb);
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
        AccountPatchReview apr = db.accountPatchReviews().get(key);
        if (apr == null && reviewed) {
          db.accountPatchReviews().insert(
              Collections.singleton(new AccountPatchReview(patchKey, account)));
        } else if (apr != null && !reviewed) {
          db.accountPatchReviews().delete(Collections.singleton(apr));
        }
        return VoidResult.INSTANCE;
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
