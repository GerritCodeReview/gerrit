// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.common.ChangeHookRunner;
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeMessage;
import com.google.gerrit.reviewdb.PatchLineComment;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.mail.CommentSender;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

public class PublishComments implements Callable<VoidResult> {
  private static final Logger log =
      LoggerFactory.getLogger(PublishComments.class);

  public interface Factory {
    PublishComments create(PatchSet.Id patchSetId, String messageText,
        Set<ApprovalCategoryValue.Id> approvals);
  }

  private final ReviewDb db;
  private final IdentifiedUser user;
  private final ApprovalTypes types;
  private final CommentSender.Factory commentSenderFactory;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final ChangeControl.Factory changeControlFactory;
  private final FunctionState.Factory functionStateFactory;
  private final ChangeHookRunner hooks;

  private final PatchSet.Id patchSetId;
  private final String messageText;
  private final Set<ApprovalCategoryValue.Id> approvals;

  private Change change;
  private PatchSet patchSet;
  private ChangeMessage message;
  private List<PatchLineComment> drafts;

  @Inject
  PublishComments(final ReviewDb db, final IdentifiedUser user,
      final ApprovalTypes approvalTypes,
      final CommentSender.Factory commentSenderFactory,
      final PatchSetInfoFactory patchSetInfoFactory,
      final ChangeControl.Factory changeControlFactory,
      final FunctionState.Factory functionStateFactory,
      final ChangeHookRunner hooks,

      @Assisted final PatchSet.Id patchSetId,
      @Assisted final String messageText,
      @Assisted final Set<ApprovalCategoryValue.Id> approvals) {
    this.db = db;
    this.user = user;
    this.types = approvalTypes;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.commentSenderFactory = commentSenderFactory;
    this.changeControlFactory = changeControlFactory;
    this.functionStateFactory = functionStateFactory;
    this.hooks = hooks;

    this.patchSetId = patchSetId;
    this.messageText = messageText;
    this.approvals = approvals;
  }

  @Override
  public VoidResult call() throws NoSuchChangeException,
      InvalidChangeOperationException, OrmException {
    final Change.Id changeId = patchSetId.getParentKey();
    final ChangeControl ctl = changeControlFactory.validateFor(changeId);
    change = ctl.getChange();
    patchSet = db.patchSets().get(patchSetId);
    if (patchSet == null) {
      throw new NoSuchChangeException(changeId);
    }
    drafts = drafts();

    publishDrafts();

    final boolean isCurrent = patchSetId.equals(change.currentPatchSetId());
    if (isCurrent && change.getStatus().isOpen()) {
      publishApprovals(ctl);
    } else if (! approvals.isEmpty()) {
      throw new InvalidChangeOperationException("Change is closed");
    } else {
      publishMessageOnly();
    }

    touchChange();
    email();
    fireHook();
    return VoidResult.INSTANCE;
  }

  private void publishDrafts() throws OrmException {
    for (final PatchLineComment c : drafts) {
      c.setStatus(PatchLineComment.Status.PUBLISHED);
      c.updated();
    }
    db.patchComments().update(drafts);
  }

  private void publishApprovals(ChangeControl ctl) throws OrmException {
    ChangeUtil.updated(change);

    final Set<ApprovalCategory.Id> dirty = new HashSet<ApprovalCategory.Id>();
    final List<PatchSetApproval> ins = new ArrayList<PatchSetApproval>();
    final List<PatchSetApproval> upd = new ArrayList<PatchSetApproval>();
    final Collection<PatchSetApproval> all =
        db.patchSetApprovals().byPatchSet(patchSetId).toList();
    final Map<ApprovalCategory.Id, PatchSetApproval> mine = mine(all);

    // Ensure any new approvals are stored properly.
    //
    for (final ApprovalCategoryValue.Id want : approvals) {
      PatchSetApproval a = mine.get(want.getParentKey());
      if (a == null) {
        a = new PatchSetApproval(new PatchSetApproval.Key(//
            patchSetId, user.getAccountId(), want.getParentKey()), want.get());
        a.cache(change);
        ins.add(a);
        all.add(a);
        mine.put(a.getCategoryId(), a);
        dirty.add(a.getCategoryId());
      }
    }

    // Normalize all of the items the user is changing.
    //
    final FunctionState functionState =
        functionStateFactory.create(ctl, patchSetId, all);
    for (final ApprovalCategoryValue.Id want : approvals) {
      final PatchSetApproval a = mine.get(want.getParentKey());
      final short o = a.getValue();
      a.setValue(want.get());
      a.cache(change);
      if (!ApprovalCategory.SUBMIT.equals(a.getCategoryId())) {
        functionState.normalize(types.byId(a.getCategoryId()), a);
      }
      if (o != a.getValue()) {
        // Value changed, ensure we update the database.
        //
        a.setGranted();
        dirty.add(a.getCategoryId());
      }
      if (!ins.contains(a)) {
        upd.add(a);
      }
    }

    // Format a message explaining the actions taken.
    //
    final StringBuilder msgbuf = new StringBuilder();
    for (final ApprovalType at : types.getApprovalTypes()) {
      if (dirty.contains(at.getCategory().getId())) {
        final PatchSetApproval a = mine.get(at.getCategory().getId());
        if (a.getValue() == 0 && ins.contains(a)) {
          // Don't say "no score" for an initial entry.
          continue;
        }

        final ApprovalCategoryValue val = at.getValue(a);
        if (msgbuf.length() > 0) {
          msgbuf.append("; ");
        }
        if (val != null && val.getName() != null && !val.getName().isEmpty()) {
          msgbuf.append(val.getName());
        } else {
          msgbuf.append(at.getCategory().getName());
          msgbuf.append(" ");
          if (a.getValue() > 0) msgbuf.append('+');
          msgbuf.append(a.getValue());
        }
      }
    }

    // Update dashboards for everyone else.
    //
    for (PatchSetApproval a : all) {
      if (!user.getAccountId().equals(a.getAccountId())) {
        a.cache(change);
        upd.add(a);
      }
    }

    db.patchSetApprovals().update(upd);
    db.patchSetApprovals().insert(ins);

    summarizeInlineComments(msgbuf);
    message(msgbuf.toString());
  }

  private void publishMessageOnly() throws OrmException {
    StringBuilder msgbuf = new StringBuilder();
    summarizeInlineComments(msgbuf);
    message(msgbuf.toString());
  }

  private void message(String actions) throws OrmException {
    if ((actions == null || actions.isEmpty())
        && (messageText == null || messageText.isEmpty())) {
      // They had nothing to say?
      //
      return;
    }

    final StringBuilder msgbuf = new StringBuilder();
    msgbuf.append("Patch Set " + patchSetId.get() + ":");
    if (actions != null && !actions.isEmpty()) {
      msgbuf.append(" ");
      msgbuf.append(actions);
    }
    msgbuf.append("\n\n");
    msgbuf.append(messageText != null ? messageText : "");

    message = new ChangeMessage(new ChangeMessage.Key(change.getId(),//
        ChangeUtil.messageUUID(db)), user.getAccountId());
    message.setMessage(msgbuf.toString());
    message.setPatchSetId(patchSetId);
    db.changeMessages().insert(Collections.singleton(message));
  }

  private Map<ApprovalCategory.Id, PatchSetApproval> mine(
      Collection<PatchSetApproval> all) {
    Map<ApprovalCategory.Id, PatchSetApproval> r =
        new HashMap<ApprovalCategory.Id, PatchSetApproval>();
    for (PatchSetApproval a : all) {
      if (user.getAccountId().equals(a.getAccountId())) {
        r.put(a.getCategoryId(), a);
      }
    }
    return r;
  }

  private void touchChange() {
    try {
      ChangeUtil.touch(change, db);
    } catch (OrmException e) {
    }
  }

  private List<PatchLineComment> drafts() throws OrmException {
    return db.patchComments().draft(patchSetId, user.getAccountId()).toList();
  }

  private void email() {
    try {
      final CommentSender cm = commentSenderFactory.create(change);
      cm.setFrom(user.getAccountId());
      cm.setPatchSet(patchSet, patchSetInfoFactory.get(patchSetId));
      cm.setChangeMessage(message);
      cm.setPatchLineComments(drafts);
      cm.send();
    } catch (EmailException e) {
      log.error("Cannot send comments by email for patch set " + patchSetId, e);
    } catch (PatchSetInfoNotAvailableException e) {
      log.error("Failed to obtain PatchSetInfo for patch set " + patchSetId, e);
    }
  }

  private void fireHook() throws OrmException {
    final Map<ApprovalCategory.Id, ApprovalCategoryValue.Id> changed =
        new HashMap<ApprovalCategory.Id, ApprovalCategoryValue.Id>();
    for (ApprovalCategoryValue.Id v : approvals) {
      changed.put(v.getParentKey(), v);
    }

    hooks.doCommentAddedHook(change, user.getAccount(), patchSet, messageText, changed, db);
  }

  private void summarizeInlineComments(StringBuilder in) {
    if (!drafts.isEmpty()) {
      if (in.length() != 0) {
        in.append("\n\n");
      }
      if (drafts.size() == 1) {
        in.append("(1 inline comment)");
      } else {
        in.append("(" + drafts.size() + " inline comments)");
      }
    }
  }
}
