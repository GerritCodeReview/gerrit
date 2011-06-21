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

package com.google.gerrit.server;

import static com.google.gerrit.reviewdb.ApprovalCategory.SUBMIT;

import com.google.gerrit.common.ChangeHookRunner;
import com.google.gerrit.reviewdb.AbstractEntity;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeSet;
import com.google.gerrit.reviewdb.ChangeSetApproval;
import com.google.gerrit.reviewdb.ChangeSetElement;
import com.google.gerrit.reviewdb.ChangeSetInfo;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.reviewdb.TopicMessage;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeOp;
import com.google.gerrit.server.git.MergeQueue;
import com.google.gerrit.server.git.ReplicationQueue;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchTopicException;
import com.google.gerrit.server.project.TopicControl;
import com.google.gerrit.server.mail.AbandonedSender;
import com.google.gerrit.server.mail.AddReviewerSender;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.mail.RestoredSender;
import com.google.gerrit.server.mail.RevertedSender;
import com.google.gwtorm.client.AtomicUpdate;
import com.google.gwtorm.client.OrmConcurrencyException;
import com.google.gwtorm.client.OrmException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.PersonIdent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class TopicUtil {

  public static void touch(final Topic topic, ReviewDb db)
      throws OrmException {
    try {
      updated(topic);
      db.topics().update(Collections.singleton(topic));
    } catch (OrmConcurrencyException e) {
      // Ignore a concurrent update, we just wanted to tag it as newer.
    }
  }

  public static void updated(final Topic t) {
    t.resetLastUpdatedOn();
    computeSortKey(t);
  }

  public static void submit(final ChangeSet.Id changeSetId,
      final IdentifiedUser user, final ReviewDb db,
      final MergeOp.Factory opFactory, final MergeQueue merger)
      throws OrmException {
    final Topic.Id topicId = changeSetId.getParentKey();
    final ChangeSetApproval approval = createSubmitApproval(changeSetId, user, db);

    db.changeSetApprovals().upsert(Collections.singleton(approval));

    final Topic updatedTopic = db.topics().atomicUpdate(topicId,
        new AtomicUpdate<Topic>() {
      @Override
      public Topic update(Topic topic) {
        if (topic.getStatus() == Topic.Status.NEW) {
          topic.setStatus(Topic.Status.SUBMITTED);
          TopicUtil.updated(topic);
        }
        return topic;
      }
    });

    if (updatedTopic.getStatus() == Topic.Status.SUBMITTED) {
      // Submit the changes belonging to the Topic
      //
      List<Change> toSubmit = db.changes().byTopicOpenAll(topicId).toList();
      for(Change c : toSubmit) {
        ChangeUtil.submit(c.currentPatchSetId(), user, db, opFactory, merger);
      }
    }
  }

  public static ChangeSetApproval createSubmitApproval(
      final ChangeSet.Id changeSetId, final IdentifiedUser user, final ReviewDb db
      ) throws OrmException {
    final List<ChangeSetApproval> allApprovals =
        new ArrayList<ChangeSetApproval>(db.changeSetApprovals().byChangeSet(
            changeSetId).toList());

    final ChangeSetApproval.Key akey =
        new ChangeSetApproval.Key(changeSetId, user.getAccountId(), SUBMIT);

    for (final ChangeSetApproval approval : allApprovals) {
      if (akey.equals(approval.getKey())) {
        approval.setValue((short) 1);
        approval.setGranted();
        return approval;
      }
    }
    return new ChangeSetApproval(akey, (short) 1);
  }

  public static void abandon(final ChangeSet.Id changeSetId,
      final IdentifiedUser user, final String message, final ReviewDb db,
      final AbandonedSender.Factory abandonedSenderFactory,
      final ChangeHookRunner hooks) throws NoSuchTopicException,
      NoSuchChangeException, InvalidChangeOperationException,
      EmailException, OrmException  {
    final Topic.Id topicId = changeSetId.getParentKey();
    final ChangeSet changeSet = db.changeSets().get(changeSetId);
    if (changeSet == null) {
      throw new NoSuchTopicException(topicId);
    }

    final TopicMessage tmsg =
        new TopicMessage(new TopicMessage.Key(topicId, ChangeUtil
            .messageUUID(db)), user.getAccountId());
    final StringBuilder msgBuf =
        new StringBuilder("Change Set " + changeSetId.get() + ": Abandoned");
    if (message != null && message.length() > 0) {
      msgBuf.append("\n\n");
      msgBuf.append(message);
    }
    tmsg.setMessage(msgBuf.toString());

    final Topic updatedTopic = db.topics().atomicUpdate(topicId,
        new AtomicUpdate<Topic>() {
      @Override
      public Topic update(Topic topic) {
        if (topic.getStatus().isOpen()
            && topic.currentChangeSetId().equals(changeSetId)) {
          topic.setStatus(Change.Status.ABANDONED);
          TopicUtil.updated(topic);
          return topic;
        } else {
          return null;
        }
      }
    });

    Change lastChange = null;
    if (updatedTopic == null) {
      throw new InvalidChangeOperationException(
          "Topic is no longer open or changeset is not latest");
    } else {
      // Abandon the changes belonging to the Topic
      //
      List<Change> toAbandon = db.changes().byTopicOpenAll(topicId).toList();
      for(Change c : toAbandon) {
        ChangeUtil.abandon(c.currentPatchSetId(), user, message, db,
            abandonedSenderFactory, hooks, false);
      }
      lastChange = toAbandon.get(toAbandon.size() - 1);
    }

    db.topicMessages().insert(Collections.singleton(tmsg));

    final List<ChangeSetApproval> approvals =
        db.changeSetApprovals().byTopic(topicId).toList();
    for (ChangeSetApproval a : approvals) {
      a.cache(updatedTopic);
    }
    db.changeSetApprovals().update(approvals);

    // Email the reviewers
    // TODO Topic support
    // Meanwhile, sending mails in "behalf" of the last change of the topic
    if (lastChange != null) {
      final AbandonedSender cm = abandonedSenderFactory.create(lastChange);
      cm.setFrom(user.getAccountId());
      cm.setTopicMessage(tmsg);
      cm.send();
    }
  }

  public static void revert(final ChangeSet.Id changeSetId,
      final IdentifiedUser user, final String message, final ReviewDb db,
      final RevertedSender.Factory revertedSenderFactory,
      final ChangeHookRunner hooks, GitRepositoryManager gitManager,
      final PatchSetInfoFactory patchSetInfoFactory,
      final ReplicationQueue replication, PersonIdent myIdent)
      throws NoSuchChangeException, NoSuchTopicException, EmailException,
      OrmException, MissingObjectException, IncorrectObjectTypeException,
      IOException, PatchSetInfoNotAvailableException {

    final Topic.Id topicId = changeSetId.getParentKey();
    final Topic topic = db.topics().get(topicId);
    final ChangeSet changeSet = db.changeSets().get(changeSetId);
    if (changeSet == null) {
      throw new NoSuchTopicException(topicId);
    }

    final Topic newTopic = createTopic(user.getAccountId(), db, topic.getTopic(),
        topic.getDest(), message);
    final ChangeSet.Id newChangeSetId = newTopic.currentChangeSetId();
    // Revert the changes belonging to the Topic
    //
    Change lastChange = null;
    final List<ChangeSetElement> changeSetElements = db.changeSetElements().byChangeSet(changeSetId).toList();
    List<Change> toRevert = new ArrayList<Change>();
    for (ChangeSetElement cse : changeSetElements) {
      toRevert.add(db.changes().get(cse.getChangeId()));
    }
    for(Change c : toRevert) {
      ChangeUtil.revert(c.currentPatchSetId(), user, "Revert " + c.getSubject(), db,
          revertedSenderFactory, hooks, gitManager, patchSetInfoFactory,
          replication, myIdent, false, newChangeSetId,
          newTopic.getTopic(), newTopic.getId(), toRevert.indexOf(c));
    }
    lastChange = toRevert.get(toRevert.size() - 1);

    final TopicMessage tmsg =
      new TopicMessage(new TopicMessage.Key(topicId,
          ChangeUtil.messageUUID(db)), user.getAccountId());
    final StringBuilder msgBuf =
      new StringBuilder("Change Set " + changeSetId.get() + ": Reverted");
    msgBuf.append("\n\n");
    msgBuf.append("This changeset was reverted in topic: " + newTopic.getTopicId());

    tmsg.setMessage(msgBuf.toString());
    db.topicMessages().insert(Collections.singleton(tmsg));

    // TODO Topic support for RevertedSender
    // Meanwhile, sending mails in "behalf" of the last change of the topic
    final RevertedSender cm = revertedSenderFactory.create(lastChange);
    cm.setFrom(user.getAccountId());
    cm.setTopicMessage(tmsg);
    cm.send();
  }

  public static ChangeSetInfo createChangeSet(final Account.Id me,
      final Topic parentTopic, final ReviewDb db,
      final String message) throws OrmException {
    final Topic.Id topicId = parentTopic.getId();
    final ChangeSet cs = new ChangeSet(parentTopic.currChangeSetId());

    cs.setCreatedOn(parentTopic.getCreatedOn());
    cs.setUploader(me);
    cs.setTopicId(topicId);
    cs.setMessage(message);
    db.changeSets().insert(Collections.singleton(cs));

    final ChangeSetInfo csi = new ChangeSetInfo(cs.getId());
    csi.setMessage(message);
    csi.setSubject(parentTopic.getTopic());

    return csi;
  }

  public static Topic createTopic(final Account.Id me, final ReviewDb db,
      final String destTopicName, final Branch.NameKey destBranch,
      final String message) throws OrmException {
    final Topic.Id topicId = new Topic.Id(db.nextTopicId());
    final Topic.Key topicKey = new Topic.Key("T" + topicId.toString() + destTopicName);

    Topic parentTopic = new Topic(topicKey, topicId, me, destBranch);
    parentTopic.setTopic(destTopicName);
    parentTopic.setSortKey("lastUpdatedOn");
    parentTopic.nextChangeSetId();

    // Now we need to create the changeSet
    // and associate it to the Topic
    //
    final ChangeSetInfo csi = createChangeSet(me, parentTopic, db, message);
    parentTopic.setCurrentChangeSet(csi);

    db.topics().insert(Collections.singleton(parentTopic));
    return parentTopic;
  }

  public static void restore(final ChangeSet.Id changeSetId,
      final IdentifiedUser user, final String message, final ReviewDb db,
      final RestoredSender.Factory restoredSenderFactory,
      final ChangeHookRunner hooks) throws NoSuchChangeException,
      NoSuchTopicException, InvalidChangeOperationException,
      EmailException, OrmException {
    final Topic.Id topicId = changeSetId.getParentKey();
    final ChangeSet changeSet = db.changeSets().get(changeSetId);
    if (changeSet == null) {
      throw new NoSuchTopicException(topicId);
    }

    final TopicMessage tmsg =
        new TopicMessage(new TopicMessage.Key(topicId, ChangeUtil
            .messageUUID(db)), user.getAccountId());
    final StringBuilder msgBuf =
        new StringBuilder("Change Set " + changeSetId.get() + ": Restored");
    if (message != null && message.length() > 0) {
      msgBuf.append("\n\n");
      msgBuf.append(message);
    }
    tmsg.setMessage(msgBuf.toString());

    final Topic updatedTopic = db.topics().atomicUpdate(topicId,
        new AtomicUpdate<Topic>() {
      @Override
      public Topic update(Topic topic) {
        if (topic.getStatus() == Topic.Status.ABANDONED
            && topic.currentChangeSetId().equals(changeSetId)) {
          topic.setStatus(Topic.Status.NEW);
          TopicUtil.updated(topic);
          return topic;
        } else {
          return null;
        }
      }
    });

    Change lastChange = null;
    if (updatedTopic == null) {
      throw new InvalidChangeOperationException(
          "Topic is not abandoned or changeset is not latest");
    } else {
      // Restore the changes belonging to the Topic
      //
      final List<ChangeSetElement> cseList = db.changeSetElements().byChangeSet(changeSetId).toList();
      for(ChangeSetElement cse : cseList) {
        lastChange = db.changes().get(cse.getChangeId());
        ChangeUtil.restore(lastChange.currentPatchSetId(), user, message, db,
            restoredSenderFactory, hooks, false);
      }
    }

    db.topicMessages().insert(Collections.singleton(tmsg));

    final List<ChangeSetApproval> approvals =
        db.changeSetApprovals().byTopic(topicId).toList();
    for (ChangeSetApproval a : approvals) {
      a.cache(updatedTopic);
    }
    db.changeSetApprovals().update(approvals);

    // TODO Topic support in AbandonedSender
    // Meanwhile, sending mails in "behalf" of the last change of the topic
    if (lastChange != null) {
      final RestoredSender cm = restoredSenderFactory.create(lastChange);
      cm.setFrom(user.getAccountId());
      cm.setTopicMessage(tmsg);
      cm.send();
    }
  }

  public static void computeSortKey(final Topic t) {
    long lastUpdated = t.getLastUpdatedOn().getTime();
    int id = t.getId().get();
    t.setSortKey(ChangeUtil.sortKey(lastUpdated, id));
  }

  public static void addReviewers(final Set<Account.Id> reviewerIds, final ReviewDb db,
      final TopicControl control, final ApprovalCategory.Id addReviewerCategoryId,
      final IdentifiedUser currentUser, final AddReviewerSender.Factory addReviewerSenderFactory)
  throws OrmException, EmailException {

    // Add the reviewers to each of the changes belonging to the changeset
    //
    final ChangeSet.Id csid = control.getTopic().currentChangeSetId();
    List<ChangeSetElement> changeSetElements = db.changeSetElements().byChangeSet(csid).toList();
    List<Change> changes = new ArrayList<Change>();
    for (ChangeSetElement cse : changeSetElements) {
      changes.add(db.changes().get(cse.getChangeId()));
    }

    for (Change c : changes) {
      ChangeUtil.addReviewers(reviewerIds, db, c.currentPatchSetId(), addReviewerCategoryId, currentUser);
    }

    // Add the reviewers to the database
    //
    final Set<Account.Id> added = new HashSet<Account.Id>();
    final List<ChangeSetApproval> toInsert = new ArrayList<ChangeSetApproval>();

    for (final Account.Id reviewer : reviewerIds) {
      if (!exists(csid, reviewer, db)) {
        // This reviewer has not entered an approval for this topic yet.
        //
        final ChangeSetApproval myca = dummyApproval(csid, reviewer, addReviewerCategoryId);
        toInsert.add(myca);
        added.add(reviewer);
      }
    }
    db.changeSetApprovals().insert(toInsert);

    // Email the reviewers
    //
    // The user knows they added themselves, don't bother emailing them.
    added.remove(currentUser.getAccountId());
    if (!added.isEmpty()) {
      final AddReviewerSender cm;
      cm = addReviewerSenderFactory.create(changes.get(changes.size() - 1));
      cm.setFrom(currentUser.getAccountId());
      cm.addReviewers(added);
      cm.send();
    }
  }

  private static boolean exists(final ChangeSet.Id changeSetId,
      final Account.Id reviewerId, final ReviewDb db) throws OrmException {
    return db.changeSetApprovals().byChangeSetUser(changeSetId, reviewerId)
        .iterator().hasNext();
  }

  private static ChangeSetApproval dummyApproval(final ChangeSet.Id changeSetId,
      final Account.Id reviewerId, ApprovalCategory.Id addReviewerCategoryId) {
    return new ChangeSetApproval(new ChangeSetApproval.Key(changeSetId,
        reviewerId, addReviewerCategoryId), (short) 0);
  }

  /**
   * Create a new ChangeSet to be filled with some new changes
   *
   * @param result Topic to be modified
   * @param db
   * @param me
   * @return The updated topic
   * @throws OrmException
   */
  public static Topic setUpTopic(final Topic result, final ReviewDb db,
      final Account.Id me) throws OrmException {

    final ChangeSet.Id previousId = result.currentChangeSetId();
    result.nextChangeSetId();

    final ChangeSetInfo csi = createChangeSet(me, result, db,
        db.changeSets().get(previousId).getMessage());
    result.setCurrentChangeSet(csi);
    touch(result, db);
    return result;
  }

  /**
   * Finds an active topic matching the topic name in the given project & branch
   *
   * @param topicName
   * @param db
   * @param bKey
   * @param pKey
   * @return If it finds the Topic we are looking for, it returns it. Otherwise
   * it returns null
   * @throws OrmException
   */
  public static Topic findActiveTopic(final String topicName, final ReviewDb db,
      final Branch.NameKey bKey, final Project.NameKey pKey) throws OrmException {
    List<Topic> tList = db.topics().byBranchProject(bKey, pKey).toList();

    if (tList.size() >= 1) {
      for (Topic t : tList) {
        final AbstractEntity.Status tStatus = t.getStatus();
        if (t.getTopic().equals(topicName)) {
          if (tStatus.equals(AbstractEntity.Status.ABANDONED) ||
            tStatus.equals(AbstractEntity.Status.MERGED)) continue;
          // If we don't have a mess in our DB, we must have only
          // one topic with the same String in a different status than
          // MERGED or ABANDONED
          //
          else return t;
        }
      }
    }
    return null;
  }
}
