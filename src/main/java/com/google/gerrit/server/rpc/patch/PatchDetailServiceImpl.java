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

package com.google.gerrit.server.rpc.patch;

import com.google.gerrit.client.data.ApprovalType;
import com.google.gerrit.client.data.ApprovalTypes;
import com.google.gerrit.client.data.PatchScript;
import com.google.gerrit.client.data.PatchScriptSettings;
import com.google.gerrit.client.patches.AddReviewerResult;
import com.google.gerrit.client.patches.CommentDetail;
import com.google.gerrit.client.patches.PatchDetailService;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountPatchReview;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.ChangeMessage;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.PatchSetApproval;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.Patch.Key;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.server.BaseServiceImplementation;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.mail.AddReviewerSender;
import com.google.gerrit.server.mail.CommentSender;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class PatchDetailServiceImpl extends BaseServiceImplementation implements
    PatchDetailService {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final CommentSender.Factory commentSenderFactory;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final ApprovalTypes approvalTypes;

  private final AbandonChange.Factory abandonChangeFactory;
  private final AddReviewer.Factory addReviewerFactory;
  private final CommentDetailFactory.Factory commentDetailFactory;
  private final PatchScriptFactory.Factory patchScriptFactoryFactory;
  private final SaveDraft.Factory saveDraftFactory;

  @Inject
  PatchDetailServiceImpl(final Provider<ReviewDb> schema,
      final Provider<CurrentUser> currentUser,
      final CommentSender.Factory commentSenderFactory,
      final PatchSetInfoFactory patchSetInfoFactory,
      final ApprovalTypes approvalTypes,
      final AbandonChange.Factory abandonChangeFactory,
      final AddReviewer.Factory addReviewerFactory,
      final CommentDetailFactory.Factory commentDetailFactory,
      final PatchScriptFactory.Factory patchScriptFactoryFactory,
      final SaveDraft.Factory saveDraftFactory) {
    super(schema, currentUser);
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.commentSenderFactory = commentSenderFactory;
    this.approvalTypes = approvalTypes;

    this.abandonChangeFactory = abandonChangeFactory;
    this.addReviewerFactory = addReviewerFactory;
    this.commentDetailFactory = commentDetailFactory;
    this.patchScriptFactoryFactory = patchScriptFactoryFactory;
    this.saveDraftFactory = saveDraftFactory;
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
    final Set<Patch.Key> patchKeys = new HashSet<Patch.Key>();
    for (final PatchLineComment c : r.comments) {
      patchKeys.add(c.getKey().getParentKey());
    }
    final Map<Patch.Key, Patch> patches =
        db.patches().toMap(db.patches().get(patchKeys));
    for (final PatchLineComment c : r.comments) {
      final Patch p = patches.get(c.getKey().getParentKey());
      if (p != null) {
        p.setCommentCount(p.getCommentCount() + 1);
      }
      c.setStatus(PatchLineComment.Status.PUBLISHED);
      c.updated();
    }
    db.patches().update(patches.values(), txn);
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
    for (final ApprovalType at : approvalTypes.getApprovalTypes()) {
      final ApprovalCategoryValue.Id v = values.get(at.getCategory().getId());
      if (v == null) {
        continue;
      }

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
    return r;
  }

  public void addReviewers(final Change.Id id, final List<String> reviewers,
      final AsyncCallback<AddReviewerResult> callback) {
    addReviewerFactory.create(id, reviewers).to(callback);
  }

  public void abandonChange(final PatchSet.Id patchSetId, final String message,
      final AsyncCallback<VoidResult> callback) {
    abandonChangeFactory.create(patchSetId, message).to(callback);
  }
}
