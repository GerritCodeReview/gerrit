// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.data.AccountInfoCacheFactory;
import com.google.gerrit.client.data.ChangeDetail;
import com.google.gerrit.client.data.PatchSetDetail;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.ChangeApproval;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.Account.Id;
import com.google.gerrit.client.rpc.BaseServiceImplementation;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.server.ChangeMail;
import com.google.gerrit.server.GerritJsonServlet;
import com.google.gerrit.server.GerritServer;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.OrmRunnable;
import com.google.gwtorm.client.Transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.mail.MessagingException;

public class ChangeDetailServiceImpl extends BaseServiceImplementation
    implements ChangeDetailService {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final GerritServer server;

  public ChangeDetailServiceImpl() throws Exception {
    server = GerritServer.getInstance();
  }

  public void changeDetail(final Change.Id id,
      final AsyncCallback<ChangeDetail> callback) {
    run(callback, new Action<ChangeDetail>() {
      public ChangeDetail run(final ReviewDb db) throws OrmException, Failure {
        final Change change = db.changes().get(id);
        if (change == null) {
          throw new Failure(new NoSuchEntityException());
        }
        assertCanRead(change);

        final boolean anon;
        if (Common.getAccountId() == null) {
          // Safe assumption, this wouldn't be allowed if it wasn't.
          //
          anon = true;
        } else {
          // Ask if the anonymous user can read this project; even if
          // we can that doesn't mean the anonymous user could.
          //
          anon = canRead(null, change.getDest().getParentKey());
        }
        final ChangeDetail d = new ChangeDetail();
        d.load(db, new AccountInfoCacheFactory(db), change, anon);
        return d;
      }
    });
  }

  public void patchSetDetail(final PatchSet.Id id,
      final AsyncCallback<PatchSetDetail> callback) {
    run(callback, new Action<PatchSetDetail>() {
      public PatchSetDetail run(final ReviewDb db) throws OrmException, Failure {
        final PatchSet ps = db.patchSets().get(id);
        if (ps == null) {
          throw new Failure(new NoSuchEntityException());
        }
        assertCanRead(db.changes().get(ps.getId().getParentKey()));

        final PatchSetDetail d = new PatchSetDetail();
        d.load(db, ps);
        return d;
      }
    });
  }

  public void patchSetPublishDetail(final PatchSet.Id id,
      final AsyncCallback<PatchSetPublishDetail> callback) {
    run(callback, new Action<PatchSetPublishDetail>() {
      public PatchSetPublishDetail run(final ReviewDb db) throws OrmException,
          Failure {
        final PatchSet ps = db.patchSets().get(id);
        final Change change = db.changes().get(ps.getId().getParentKey());
        if (ps == null || change == null) {
          throw new Failure(new NoSuchEntityException());
        }
        assertCanRead(change);

        final PatchSetPublishDetail d = new PatchSetPublishDetail();
        d.load(db, change, id);
        return d;
      }
    });
  }

  public void addReviewers(final List<String> reviewers, final Change.Id id,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(ReviewDb db) throws OrmException, Failure {
        final Set<Account.Id> reviewerIds = new HashSet<Account.Id>();

        for (final String email : reviewers) {
          final Account who = Account.find(db, email);
          if (who != null) {
            reviewerIds.add(who.getId());
          }
        }

        // Add the reviewer to the database
        db.run(new OrmRunnable<VoidResult, ReviewDb>() {
          public VoidResult run(ReviewDb db, Transaction txn, boolean retry)
              throws OrmException {
            return doAddReviewers(reviewerIds, id, db, txn);
          }
        });

        // Email the reviewer
        try {
          final ChangeMail cm = new ChangeMail(server, db.changes().get(id));
          cm.setFrom(Common.getAccountId());
          cm.setReviewDb(db);
          cm.addReviewers(reviewerIds);
          cm.setHttpServletRequest(GerritJsonServlet.getCurrentCall()
              .getHttpServletRequest());
          cm.sendRequestReview();
        } catch (MessagingException e) {
          log.error("Cannot send comments by email for change " + id, e);
          throw new Failure(e);
        }
        return VoidResult.INSTANCE;
      }
    });
  }

  private VoidResult doAddReviewers(final Set<Id> reviewerIds,
      final Change.Id id, final ReviewDb db, final Transaction txn)
      throws OrmException {
    for (Id reviewer : reviewerIds) {
      ChangeApproval myca =
          new ChangeApproval(new ChangeApproval.Key(id, reviewer,
              new ApprovalCategory.Id("CRVW")), (short) 0);
      db.changeApprovals().insert(Collections.singleton(myca), txn);
    }
    return VoidResult.INSTANCE;
  }
}
