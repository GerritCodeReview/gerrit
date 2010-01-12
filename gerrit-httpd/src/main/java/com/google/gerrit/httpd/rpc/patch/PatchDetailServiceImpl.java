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

import com.google.gerrit.common.ChangeHookRunner;
import com.google.gerrit.common.data.AddReviewerResult;
import com.google.gerrit.common.data.ApprovalSummary;
import com.google.gerrit.common.data.ApprovalSummarySet;
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.CommentDetail;
import com.google.gerrit.common.data.PatchDetailService;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.common.data.PatchScriptSettings;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.BaseServiceImplementation;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountPatchReview;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeMessage;
import com.google.gerrit.reviewdb.Patch;
import com.google.gerrit.reviewdb.PatchLineComment;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Patch.Key;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.mail.CommentSender;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.OrmRunnable;
import com.google.gwtorm.client.Transaction;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class PatchDetailServiceImpl extends BaseServiceImplementation implements
    PatchDetailService {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final CommentSender.Factory commentSenderFactory;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final ApprovalTypes approvalTypes;

  private final AccountInfoCacheFactory.Factory accountInfoCacheFactory;
  private final AddReviewer.Factory addReviewerFactory;
  private final ChangeControl.Factory changeControlFactory;
  private final CommentDetailFactory.Factory commentDetailFactory;
  private final FunctionState.Factory functionStateFactory;
  private final PatchScriptFactory.Factory patchScriptFactoryFactory;
  private final SaveDraft.Factory saveDraftFactory;

  private final ChangeHookRunner hooks;

  private final Provider<IdentifiedUser> user;

  @Inject
  PatchDetailServiceImpl(final Provider<ReviewDb> schema,
      final Provider<CurrentUser> currentUser,
      final CommentSender.Factory commentSenderFactory,
      final PatchSetInfoFactory patchSetInfoFactory,
      final ApprovalTypes approvalTypes,
      final AccountInfoCacheFactory.Factory accountInfoCacheFactory,
      final AddReviewer.Factory addReviewerFactory,
      final ChangeControl.Factory changeControlFactory,
      final CommentDetailFactory.Factory commentDetailFactory,
      final FunctionState.Factory functionStateFactory,
      final PatchScriptFactory.Factory patchScriptFactoryFactory,
      final SaveDraft.Factory saveDraftFactory,
      final ChangeHookRunner hooks,
      final Provider<IdentifiedUser> user) {
    super(schema, currentUser);
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.commentSenderFactory = commentSenderFactory;
    this.approvalTypes = approvalTypes;

    this.accountInfoCacheFactory = accountInfoCacheFactory;
    this.addReviewerFactory = addReviewerFactory;
    this.changeControlFactory = changeControlFactory;
    this.commentDetailFactory = commentDetailFactory;
    this.functionStateFactory = functionStateFactory;
    this.patchScriptFactoryFactory = patchScriptFactoryFactory;
    this.saveDraftFactory = saveDraftFactory;
    this.hooks = hooks;
    this.user = user;
  }

  public void patchScript(final Patch.Key patchKey, final PatchSet.Id psa,
      final PatchSet.Id psb, final PatchScriptSettings s,
      final AsyncCallback<PatchScript> callback) {
    if (psb == null) {
      callback.onFailure(new NoSuchEntityException());
      return;
    }
    patchScriptFactoryFactory.create(patchKey, psa, psb, s).to(callback);
  }

  public void patchComments(final Patch.Key patchKey, final PatchSet.Id psa,
      final PatchSet.Id psb, final AsyncCallback<CommentDetail> callback) {
    if (psb == null) {
      callback.onFailure(new NoSuchEntityException());
      return;
    }
    commentDetailFactory.create(patchKey, psa, psb).to(callback);
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

  public void publishComments(final PatchSet.Id psid, final String message,
      final Set<ApprovalCategoryValue.Id> approvals,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(ReviewDb db) throws OrmException, Failure {
        final PublishResult r;

        r = db.run(new OrmRunnable<PublishResult, ReviewDb>() {
          public PublishResult run(ReviewDb db, Transaction txn, boolean retry)
              throws OrmException {
            return doPublishComments(psid, message, approvals, db, txn);
          }
        });

        try {
          final CommentSender cm;
          cm = commentSenderFactory.create(r.change);
          cm.setFrom(getAccountId());
          cm.setPatchSet(r.patchSet, patchSetInfoFactory.get(psid));
          cm.setChangeMessage(r.message);
          cm.setPatchLineComments(r.comments);
          cm.setReviewDb(db);
          cm.send();
        } catch (EmailException e) {
          log.error("Cannot send comments by email for patch set " + psid, e);
          throw new Failure(e);
        } catch (PatchSetInfoNotAvailableException e) {
          log.error("Failed to obtain PatchSetInfo for patch set " + psid, e);
          throw new Failure(e);
        }
        return VoidResult.INSTANCE;
      }
    });
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


  private static class PublishResult {
    Change change;
    PatchSet patchSet;
    ChangeMessage message;
    List<PatchLineComment> comments;
  }

  private PublishResult doPublishComments(final PatchSet.Id psid,
      final String messageText, final Set<ApprovalCategoryValue.Id> approvals,
      final ReviewDb db, final Transaction txn) throws OrmException {
    final PublishResult r = new PublishResult();
    final Account.Id me = getAccountId();
    r.change = db.changes().get(psid.getParentKey());
    r.patchSet = db.patchSets().get(psid);
    if (r.change == null || r.patchSet == null) {
      throw new OrmException(new NoSuchEntityException());
    }

    final boolean iscurrent = psid.equals(r.change.currentPatchSetId());
    r.comments = db.patchComments().draft(psid, me).toList();
    for (final PatchLineComment c : r.comments) {
      c.setStatus(PatchLineComment.Status.PUBLISHED);
      c.updated();
    }
    db.patchComments().update(r.comments, txn);

    final StringBuilder msgbuf = new StringBuilder();
    final Map<ApprovalCategory.Id, ApprovalCategoryValue.Id> values =
        new HashMap<ApprovalCategory.Id, ApprovalCategoryValue.Id>();
    for (final ApprovalCategoryValue.Id v : approvals) {
      values.put(v.getParentKey(), v);
    }

    final boolean applyApprovals = iscurrent && r.change.getStatus().isOpen();
    final Map<ApprovalCategory.Id, PatchSetApproval> have =
        new HashMap<ApprovalCategory.Id, PatchSetApproval>();
    for (PatchSetApproval a : db.patchSetApprovals().byPatchSetUser(psid, me)) {
      have.put(a.getCategoryId(), a);
    }

    final Map<String, Short> approvalsMap = new HashMap<String, Short>();

    for (final ApprovalType at : approvalTypes.getApprovalTypes()) {
      final ApprovalCategoryValue.Id v = values.get(at.getCategory().getId());
      if (v == null) {
        continue;
      }

      approvalsMap.put(v.getParentKey().get(), v.get());

      final ApprovalCategoryValue val = at.getValue(v.get());
      if (val == null) {
        continue;
      }

      PatchSetApproval mycatpp = have.remove(v.getParentKey());
      if (mycatpp == null) {
        if (msgbuf.length() > 0) {
          msgbuf.append("; ");
        }
        msgbuf.append(val.getName());
        if (applyApprovals) {
          mycatpp =
              new PatchSetApproval(new PatchSetApproval.Key(psid, me, v
                  .getParentKey()), v.get());
          db.patchSetApprovals().insert(Collections.singleton(mycatpp), txn);
        }

      } else if (mycatpp.getValue() != v.get()) {
        if (msgbuf.length() > 0) {
          msgbuf.append("; ");
        }
        msgbuf.append(val.getName());
        if (applyApprovals) {
          mycatpp.setValue(v.get());
          mycatpp.setGranted();
          db.patchSetApprovals().update(Collections.singleton(mycatpp), txn);
        }
      }
    }
    if (applyApprovals) {
      db.patchSetApprovals().delete(have.values(), txn);
    }

    if (msgbuf.length() > 0) {
      msgbuf.insert(0, "Patch Set " + psid.get() + ": ");
      msgbuf.append("\n\n");
    } else if (!iscurrent) {
      msgbuf.append("Patch Set " + psid.get() + ":\n\n");
    }
    if (messageText != null) {
      msgbuf.append(messageText);
    }
    if (msgbuf.length() > 0) {
      r.message =
          new ChangeMessage(new ChangeMessage.Key(r.change.getId(), ChangeUtil
              .messageUUID(db)), me);
      r.message.setMessage(msgbuf.toString());
      db.changeMessages().insert(Collections.singleton(r.message), txn);
    }

    ChangeUtil.updated(r.change);
    db.changes().update(Collections.singleton(r.change), txn);

    hooks.doCommentAddedHook(r.change, user.get().getAccount(), messageText, approvalsMap);
    return r;
  }

  public void addReviewers(final Change.Id id, final List<String> reviewers,
      final AsyncCallback<AddReviewerResult> callback) {
    addReviewerFactory.create(id, reviewers).to(callback);
  }

  public void userApprovals(final Set<Change.Id> cids, final Account.Id aid,
      final AsyncCallback<ApprovalSummarySet> callback) {
    run(callback, new Action<ApprovalSummarySet>() {
      public ApprovalSummarySet run(ReviewDb db)
        throws OrmException {
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
                functionStateFactory.create(change, ps_id, psas.values());

            for (final PatchSetApproval ca : db.patchSetApprovals()
                .byPatchSetUser(ps_id, aid)) {
              final ApprovalCategory.Id category = ca.getCategoryId();
              if (change.getStatus().isOpen()) {
                fs.normalize(approvalTypes.getApprovalType(category), ca);
              }
              if (ca.getValue() == 0
                  || ApprovalCategory.SUBMIT.equals(category)) {
                continue;
              }
              psas.put(category, ca);
            }

            approvals.put(id, new ApprovalSummary(psas.values()));
          } catch (NoSuchChangeException nsce) {
            /* The user has no access to see this change, so we
             * simply do not provide any details about it.
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
      public ApprovalSummarySet run(ReviewDb db)
          throws OrmException {
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
                functionStateFactory.create(change, ps_id, psas.values());

            for (PatchSetApproval ca : db.patchSetApprovals()
                .byPatchSet(ps_id)) {
              final ApprovalCategory.Id category = ca.getCategoryId();
              if (change.getStatus().isOpen()) {
                fs.normalize(approvalTypes.getApprovalType(category), ca);
              }
              if (ca.getValue() == 0
                  || ApprovalCategory.SUBMIT.equals(category)) {
                continue;
              }
              boolean keep = true;
              if (psas.containsKey(category)) {
                final short oldValue = psas.get(category).getValue();
                final short newValue = ca.getValue();
                keep = (Math.abs(oldValue) < Math.abs(newValue))
                    || ((Math.abs(oldValue) == Math.abs(newValue)
                        && (newValue < oldValue)));
              }
              if (keep) {
                aicFactory.want(ca.getAccountId());
                psas.put(category, ca);
              }
            }

            approvals.put(id, new ApprovalSummary(psas.values()));
          } catch (NoSuchChangeException nsce) {
            /* The user has no access to see this change, so we
             * simply do not provide any details about it.
             */
          }
        }

        return new ApprovalSummarySet(aicFactory.create(), approvals);
      }
    });
  }
}
