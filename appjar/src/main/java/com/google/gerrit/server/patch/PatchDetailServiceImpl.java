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

package com.google.gerrit.server.patch;

import com.google.gerrit.client.data.ApprovalType;
import com.google.gerrit.client.data.SideBySidePatchDetail;
import com.google.gerrit.client.data.UnifiedPatchDetail;
import com.google.gerrit.client.patches.PatchDetailService;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.ChangeApproval;
import com.google.gerrit.client.reviewdb.ChangeMessage;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.BaseServiceImplementation;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.git.RepositoryCache;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritJsonServlet;
import com.google.gerrit.server.GerritServer;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.OrmRunnable;
import com.google.gwtorm.client.Transaction;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.Message.RecipientType;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;

public class PatchDetailServiceImpl extends BaseServiceImplementation implements
    PatchDetailService {
  private final GerritServer server;

  public PatchDetailServiceImpl(final GerritServer gs) {
    server = gs;
  }

  public void sideBySidePatchDetail(final Patch.Key key,
      final AsyncCallback<SideBySidePatchDetail> callback) {
    final RepositoryCache rc = server.getRepositoryCache();
    if (rc == null) {
      callback.onFailure(new Exception("No Repository Cache configured"));
      return;
    }
    run(callback, new SideBySidePatchDetailAction(rc, key));
  }

  public void unifiedPatchDetail(final Patch.Key key,
      final AsyncCallback<UnifiedPatchDetail> callback) {
    run(callback, new UnifiedPatchDetailAction(key));
  }

  public void myDrafts(final Patch.Key key,
      final AsyncCallback<List<PatchLineComment>> callback) {
    run(callback, new Action<List<PatchLineComment>>() {
      public List<PatchLineComment> run(ReviewDb db) throws OrmException {
        return db.patchComments().draft(key, Common.getAccountId()).toList();
      }
    });
  }

  public void saveDraft(final PatchLineComment comment,
      final AsyncCallback<PatchLineComment> callback) {
    run(callback, new Action<PatchLineComment>() {
      public PatchLineComment run(ReviewDb db) throws OrmException, Failure {
        if (comment.getStatus() != PatchLineComment.Status.DRAFT) {
          throw new Failure(new IllegalStateException("Comment published"));
        }

        final Patch patch = db.patches().get(comment.getKey().getParentKey());
        final Change change;
        if (patch == null) {
          throw new Failure(new NoSuchEntityException());
        }
        change = db.changes().get(patch.getKey().getParentKey().getParentKey());
        assertCanRead(change);

        final Account.Id me = Common.getAccountId();
        if (comment.getKey().get() == null) {
          final PatchLineComment nc =
              new PatchLineComment(new PatchLineComment.Key(patch.getKey(),
                  ChangeUtil.messageUUID(db)), comment.getLine(), me);
          nc.setSide(comment.getSide());
          nc.setMessage(comment.getMessage());
          db.patchComments().insert(Collections.singleton(nc));
          return nc;

        } else {
          if (!me.equals(comment.getAuthor())) {
            throw new Failure(new NoSuchEntityException());
          }
          comment.updated();
          db.patchComments().update(Collections.singleton(comment));
          return comment;
        }
      }
    });
  }

  public void deleteDraft(final PatchLineComment.Key commentKey,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(ReviewDb db) throws OrmException, Failure {
        final PatchLineComment comment = db.patchComments().get(commentKey);
        if (comment == null) {
          throw new Failure(new NoSuchEntityException());
        }
        if (!Common.getAccountId().equals(comment.getAuthor())) {
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

        final javax.mail.Session out = server.getOutgoingMail();
        if (out == null) {
          return VoidResult.INSTANCE;
        }

        final HttpServletRequest req =
            GerritJsonServlet.getCurrentCall().getHttpServletRequest();
        String gerritHost = null;
        if (server.getCanonicalURL() != null) {
          try {
            gerritHost = new URL(server.getCanonicalURL()).getHost();
          } catch (MalformedURLException e) {
            gerritHost = null;
          }
        }
        if (gerritHost == null) {
          gerritHost = req.getServerName();
        }

        final StringBuilder listid = new StringBuilder();
        final StringBuilder subj = new StringBuilder();
        final StringBuilder body = new StringBuilder();

        final Account myAcct =
            Common.getAccountCache().get(Common.getAccountId());
        final String myEmail = myAcct.getPreferredEmail();
        final String projName = r.change.getDest().getParentKey().get();

        listid.append("gerrit-comment-");
        listid.append(projName.replace('/', '-'));
        listid.append("@");
        listid.append(gerritHost);

        subj.append("Change ");
        subj.append(r.change.getChangeId());
        subj.append(": (");
        subj.append(projName);
        subj.append(") ");
        subj.append(r.change.getSubject());

        if (r.message != null) {
          body.append(r.message.getMessage().trim());
          body.append("\n\n");
        }

        if (!r.comments.isEmpty()) {
          body.append("Comments on Patch Set ");
          body.append(psid.get());
          body.append(":\n\n");
        }

        String priorFile = "";
        for (final PatchLineComment c : r.comments) {
          final String fn = c.getKey().getParentKey().get();
          if (!fn.equals(priorFile)) {
            body.append("..................................................\n");
            body.append("File ");
            body.append(fn);
            body.append("\n");
            priorFile = fn;
          }
          body.append("Line ");
          body.append(c.getLine());
          body.append("\n");
          body.append(c.getMessage().trim());
          body.append("\n\n");
        }

        if (body.length() == 0) {
          // We have no meaningful content in the body; don't send email.
          //
          return VoidResult.INSTANCE;
        }

        body.append("--\n");
        body.append("To respond visit ");
        if (server.getCanonicalURL() != null) {
          body.append(server.getCanonicalURL());
        } else {
          final StringBuffer url = req.getRequestURL();
          url.setLength(url.lastIndexOf("/")); // cut "PatchDetailService"
          url.setLength(url.lastIndexOf("/")); // cut "rpc"
          body.append(url);
          body.append("/");
        }
        body.append(r.change.getChangeId());
        body.append("\n");

        try {
          final InternetAddress myAddr =
              new InternetAddress(myEmail, myAcct.getFullName());
          final String listidStr = listid.toString();
          final HashSet<String> rcpt = new HashSet<String>();
          final MimeMessage msg = new MimeMessage(out);
          msg.setFrom(myAddr);

          // Always to the owner.
          sendTo(RecipientType.TO, r.change.getOwner(), rcpt, msg);


          if (rcpt.add(myEmail)) {
            // Always CC anything we send on behalf of the user, unless
            // the user is already in the destination list because they
            // are the owner and are replying to themselves
            //
            msg.addRecipient(Message.RecipientType.CC, myAddr);
          }
          // CC anyone else who has posted an approval mark on this change
          //
          for (final ChangeApproval ap : db.changeApprovals().byChange(
              r.change.getId())) {
            sendTo(RecipientType.CC, ap.getAccountId(), rcpt, msg);
          }

          // Set a reasonable list id to filters can sort messages
          //
          msg.addHeader("Mailing-List", "list " + listidStr);
          msg.addHeader("List-Id", "<" + listidStr.replace('@', '.') + ">");
          msg.setSubject(subj.toString());
          msg.setSentDate(new Date());
          msg.setText(body.toString());
          Transport.send(msg);
        } catch (MessagingException e) {
          throw new Failure(e);
        } catch (UnsupportedEncodingException e) {
          throw new Failure(e);
        }
        return VoidResult.INSTANCE;
      }
    });
  }

  private static class PublishResult {
    Change change;
    ChangeMessage message;
    List<PatchLineComment> comments;
  }

  private PublishResult doPublishComments(final PatchSet.Id psid,
      final String messageText, final Set<ApprovalCategoryValue.Id> approvals,
      final ReviewDb db, final Transaction txn) throws OrmException {
    final PublishResult r = new PublishResult();
    final Account.Id me = Common.getAccountId();
    r.change = db.changes().get(psid.getParentKey());
    if (r.change == null) {
      throw new OrmException(new NoSuchEntityException());
    }

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

    final Map<ApprovalCategory.Id, ChangeApproval> have =
        new HashMap<ApprovalCategory.Id, ChangeApproval>();
    for (final ChangeApproval a : db.changeApprovals().byChangeUser(
        r.change.getId(), me)) {
      have.put(a.getCategoryId(), a);
    }
    for (final ApprovalType at : Common.getGerritConfig().getApprovalTypes()) {
      final ApprovalCategoryValue.Id v = values.get(at.getCategory().getId());
      if (v == null) {
        continue;
      }

      final ApprovalCategoryValue val = at.getValue(v.get());
      if (val == null) {
        continue;
      }

      ChangeApproval mycatpp = have.remove(v.getParentKey());
      if (mycatpp == null) {
        if (msgbuf.length() > 0) {
          msgbuf.append("; ");
        }
        msgbuf.append(val.getName());
        mycatpp =
            new ChangeApproval(new ChangeApproval.Key(r.change.getId(), me, v
                .getParentKey()), v.get());
        db.changeApprovals().insert(Collections.singleton(mycatpp), txn);
      } else if (mycatpp.getValue() != v.get()) {
        if (msgbuf.length() > 0) {
          msgbuf.append("; ");
        }
        msgbuf.append(val.getName());
        mycatpp.setValue(v.get());
        mycatpp.setGranted();
        db.changeApprovals().update(Collections.singleton(mycatpp), txn);
      }
    }
    if (msgbuf.length() > 0) {
      msgbuf.insert(0, "Patch Set " + psid.get() + ": ");
      msgbuf.append("\n");
    }
    db.changeApprovals().delete(have.values(), txn);
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

  private static void sendTo(final RecipientType rt, final Account.Id to,
      final HashSet<String> rcpt, final MimeMessage msg)
      throws MessagingException {
    final Account a = Common.getAccountCache().get(to);
    if (a == null) {
      return;
    }

    final String e = a.getPreferredEmail();
    if (e != null && rcpt.add(e)) {
      try {
        final String an = a.getFullName();
        msg.addRecipient(rt, new InternetAddress(e, an));
      } catch (UnsupportedEncodingException e1) {
        msg.addRecipient(rt, new InternetAddress(e));
      }
    }
  }
}
