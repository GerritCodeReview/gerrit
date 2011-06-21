// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.topic;

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.ChangeSet;
import com.google.gerrit.reviewdb.ChangeSetApproval;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.reviewdb.TopicMessage;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.TopicUtil;
import com.google.gerrit.server.mail.CommentSender;
import com.google.gerrit.server.project.NoSuchTopicException;
import com.google.gerrit.server.project.TopicControl;
import com.google.gerrit.server.workflow.TopicFunctionState;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

public class PublishTopicComments implements Callable<VoidResult> {

  public interface Factory {
    PublishTopicComments create(ChangeSet.Id changeSetId, String messageText,
        Set<ApprovalCategoryValue.Id> approvals);
  }

  private final ReviewDb db;
  private final IdentifiedUser user;
  private final ApprovalTypes types;
  private final CommentSender.Factory commentSenderFactory;
  private final TopicControl.Factory topicControlFactory;
  private final TopicFunctionState.Factory functionStateFactory;

  private final ChangeSet.Id changeSetId;
  private final String messageText;
  private final Set<ApprovalCategoryValue.Id> approvals;

  private Topic topic;
  private ChangeSet changeSet;
  private TopicMessage message;

  @Inject
  PublishTopicComments(final ReviewDb db, final IdentifiedUser user,
      final ApprovalTypes approvalTypes,
      final CommentSender.Factory commentSenderFactory,
      final TopicControl.Factory topicControlFactory,
      final TopicFunctionState.Factory functionStateFactory,
      @Assisted final ChangeSet.Id changeSetId,
      @Assisted final String messageText,
      @Assisted final Set<ApprovalCategoryValue.Id> approvals) {
    this.db = db;
    this.user = user;
    this.types = approvalTypes;
    this.commentSenderFactory = commentSenderFactory;
    this.topicControlFactory = topicControlFactory;
    this.functionStateFactory = functionStateFactory;

    this.changeSetId = changeSetId;
    this.messageText = messageText;
    this.approvals = approvals;
  }

  @Override
  public VoidResult call() throws NoSuchTopicException, OrmException {
    final Topic.Id topicId = changeSetId.getParentKey();
    final TopicControl ctl = topicControlFactory.validateFor(topicId);
    topic = ctl.getTopic();
    changeSet = db.changeSets().get(changeSetId);
    if (changeSet == null) {
      throw new NoSuchTopicException(topicId);
    }

    final boolean isCurrent = changeSetId.equals(topic.currentChangeSetId());
    if (isCurrent && topic.getStatus().isOpen()) {
      publishApprovals();
    } else {
      publishMessageOnly();
    }

    touchTopic();
    email();
    return VoidResult.INSTANCE;
  }

  private void publishApprovals() throws OrmException {
    TopicUtil.updated(topic);

    final Set<ApprovalCategory.Id> dirty = new HashSet<ApprovalCategory.Id>();
    final List<ChangeSetApproval> ins = new ArrayList<ChangeSetApproval>();
    final List<ChangeSetApproval> upd = new ArrayList<ChangeSetApproval>();
    final Collection<ChangeSetApproval> all =
        db.changeSetApprovals().byChangeSet(changeSetId).toList();
    final Map<ApprovalCategory.Id, ChangeSetApproval> mine = mine(all);

    // Ensure any new approvals are stored properly.
    //
    for (final ApprovalCategoryValue.Id want : approvals) {
      ChangeSetApproval a = mine.get(want.getParentKey());
      if (a == null) {
        a = new ChangeSetApproval(new ChangeSetApproval.Key(//
            changeSetId, user.getAccountId(), want.getParentKey()), want.get());
        a.cache(topic);
        ins.add(a);
        all.add(a);
        mine.put(a.getCategoryId(), a);
        dirty.add(a.getCategoryId());
      }
    }

    // Normalize all of the items the user is changing.
    //
    final TopicFunctionState functionState =
        functionStateFactory.create(topic, changeSetId, all);
    for (final ApprovalCategoryValue.Id want : approvals) {
      final ChangeSetApproval a = mine.get(want.getParentKey());
      final short o = a.getValue();
      a.setValue(want.get());
      a.cache(topic);
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
        final ChangeSetApproval a = mine.get(at.getCategory().getId());
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
    for (ChangeSetApproval a : all) {
      if (!user.getAccountId().equals(a.getAccountId())) {
        a.cache(topic);
        upd.add(a);
      }
    }

    db.changeSetApprovals().update(upd);
    db.changeSetApprovals().insert(ins);

    message(msgbuf.toString());
  }

  private void publishMessageOnly() throws OrmException {
    StringBuilder msgbuf = new StringBuilder();
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
    msgbuf.append("Change Set " + changeSetId.get() + ":");
    if (actions != null && !actions.isEmpty()) {
      msgbuf.append(" ");
      msgbuf.append(actions);
    }
    msgbuf.append("\n\n");
    msgbuf.append(messageText != null ? messageText : "");

    message = new TopicMessage(new TopicMessage.Key(topic.getId(),//
        ChangeUtil.messageUUID(db)), user.getAccountId());
    message.setMessage(msgbuf.toString());
    db.topicMessages().insert(Collections.singleton(message));
  }

  private Map<ApprovalCategory.Id, ChangeSetApproval> mine(
      Collection<ChangeSetApproval> all) {
    Map<ApprovalCategory.Id, ChangeSetApproval> r =
        new HashMap<ApprovalCategory.Id, ChangeSetApproval>();
    for (ChangeSetApproval a : all) {
      if (user.getAccountId().equals(a.getAccountId())) {
        r.put(a.getCategoryId(), a);
      }
    }
    return r;
  }

  private void touchTopic() {
    try {
      TopicUtil.touch(topic, db);
    } catch (OrmException e) {
    }
  }

  private void email() {
//    try {
//      // TODO we need a topic equivalent
//      final CommentSender cm = commentSenderFactory.create(topic);
//      cm.setFrom(user.getAccountId());
//      cm.setPatchSet(patchSet, patchSetInfoFactory.get(patchSetId));
//      cm.setChangeMessage(message);
//      cm.setPatchLineComments(drafts);
//      cm.send();
//    } catch (EmailException e) {
//      log.error("Cannot send comments by email for patch set " + patchSetId, e);
//    } catch (PatchSetInfoNotAvailableException e) {
//      log.error("Failed to obtain PatchSetInfo for patch set " + patchSetId, e);
//    }
  }
}
