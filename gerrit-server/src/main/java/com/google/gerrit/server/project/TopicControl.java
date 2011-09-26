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

package com.google.gerrit.server.project;

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeSet;
import com.google.gerrit.reviewdb.ChangeSetApproval;
import com.google.gerrit.reviewdb.ChangeSetElement;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.workflow.TopicCategoryFunction;
import com.google.gerrit.server.workflow.TopicFunctionState;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/** Access control management for a user accessing a single topic. */
public class TopicControl {
  public static class GenericFactory {
    private final ProjectControl.GenericFactory projectControl;

    @Inject
    GenericFactory(ProjectControl.GenericFactory p) {
      projectControl = p;
    }

    public TopicControl controlFor(Topic topic, CurrentUser user)
        throws NoSuchTopicException {
      final Project.NameKey projectKey = topic.getProject();
      try {
        return projectControl.controlFor(projectKey, user).controlFor(topic);
      } catch (NoSuchProjectException e) {
        throw new NoSuchTopicException(topic.getId(), e);
      }
    }
  }

  public static class Factory {
    private final ProjectControl.Factory projectControl;
    private final Provider<ReviewDb> db;

    @Inject
    Factory(final ProjectControl.Factory p, final Provider<ReviewDb> d) {
      projectControl = p;
      db = d;
    }

    public TopicControl controlFor(final Topic.Id id)
        throws NoSuchTopicException {
      final Topic topic;
      try {
        topic = db.get().topics().get(id);
        if (topic == null) {
          throw new NoSuchTopicException(id);
        }
      } catch (OrmException e) {
        throw new NoSuchTopicException(id, e);
      }
      return controlFor(topic);
    }

    public TopicControl controlFor(final Topic topic)
        throws NoSuchTopicException {
      try {
        final Project.NameKey projectKey = topic.getProject();
        return projectControl.validateFor(projectKey).controlFor(topic);
      } catch (NoSuchProjectException e) {
        throw new NoSuchTopicException(topic.getId(), e);
      }
    }

    public TopicControl validateFor(final Topic.Id id)
        throws NoSuchTopicException {
      return validate(controlFor(id));
    }

    public TopicControl validateFor(final Topic topic)
        throws NoSuchTopicException {
      return validate(controlFor(topic));
    }

    private static TopicControl validate(final TopicControl c)
        throws NoSuchTopicException {
      if (!c.isVisible()) {
        throw new NoSuchTopicException(c.getTopic().getId());
      }
      return c;
    }
  }

  private final RefControl refControl;
  private final Topic topic;

  TopicControl(final RefControl r, final Topic t) {
    this.refControl = r;
    this.topic = t;
  }

  public TopicControl forUser(final CurrentUser who) {
    return new TopicControl(getRefControl().forUser(who), getTopic());
  }

  public RefControl getRefControl() {
    return refControl;
  }

  public CurrentUser getCurrentUser() {
    return getRefControl().getCurrentUser();
  }

  public ProjectControl getProjectControl() {
    return getRefControl().getProjectControl();
  }

  public Project getProject() {
    return getProjectControl().getProject();
  }

  public Topic getTopic() {
    return topic;
  }

  /** Can this user see this topic? */
  public boolean isVisible() {
    return getRefControl().isVisible();
  }

  /** Can this user abandon this topic? */
  public boolean canAbandon() {
    return isOwner() // owner (aka creator) of the change can abandon
        || getRefControl().isOwner() // branch owner can abandon
        || getProjectControl().isOwner() // project owner can abandon
        || getCurrentUser().getCapabilities().canAdministrateServer() // site administers are god
    ;
  }

  /** Can this user restore this topic? */
  public boolean canRestore() {
    return canAbandon(); // Anyone who can abandon the change can restore it back
  }

  /** All value ranges of any allowed label permission. */
  public List<PermissionRange> getLabelRanges() {
    return getRefControl().getLabelRanges();
  }

  /** The range of permitted values associated with a label permission. */
  public PermissionRange getRange(String permission) {
    return getRefControl().getRange(permission);
  }

  /** Can this user add a change set to this topic? */
  public boolean canAddChangeSet() {
    return getRefControl().canUpload();
  }

  /** Is this user the owner of the topic? */
  public boolean isOwner() {
    if (getCurrentUser() instanceof IdentifiedUser) {
      final IdentifiedUser i = (IdentifiedUser) getCurrentUser();
      return i.getAccountId().equals(topic.getOwner());
    }
    return false;
  }

  /** @return true if the user is allowed to remove this reviewer. */
  public boolean canRemoveReviewer(ChangeSetApproval approval) {
    if (getTopic().getStatus().isOpen()) {
      // A user can always remove themselves.
      //
      if (getCurrentUser() instanceof IdentifiedUser) {
        final IdentifiedUser i = (IdentifiedUser) getCurrentUser();
        if (i.getAccountId().equals(approval.getAccountId())) {
          return true; // can remove self
        }
      }

      // The change owner may remove any zero or positive score.
      //
      if (isOwner() && 0 <= approval.getValue()) {
        return true;
      }

      // The branch owner, project owner, site admin can remove anyone.
      //
      if (getRefControl().isOwner() // branch owner
          || getProjectControl().isOwner() // project owner
          || getCurrentUser().getCapabilities().canAdministrateServer()) {
        return true;
      }
    }

    return false;
  }

  public List<SubmitRecord> canSubmit(ReviewDb db, ChangeSet.Id changeSetId,
      final ChangeControl.Factory changeControlFactory,
      final ApprovalTypes approvalTypes, final TopicFunctionState.Factory functionStateFactory)
      throws NoSuchChangeException, OrmException {
    if (topic.getStatus().isClosed()) {
      SubmitRecord rec = new SubmitRecord();
      rec.status = SubmitRecord.Status.CLOSED;
      return Collections.singletonList(rec);
    }

    if (!changeSetId.equals(topic.currentChangeSetId())) {
      SubmitRecord rec = new SubmitRecord();
      rec.status = SubmitRecord.Status.RULE_ERROR;
      rec.errorMessage = "Patch set " + changeSetId + " is not current";
      return Collections.singletonList(rec);
    }

    boolean doSubmit = true;
    final List<ChangeSetElement> topicChangeSet = db.changeSetElements().byChangeSet(changeSetId).toList();
    List<Change> changesInTopic = new ArrayList<Change>();
    for (ChangeSetElement cse : topicChangeSet) {
      changesInTopic.add(db.changes().get(cse.getChangeId()));
    }

    for (Change change : changesInTopic) {
      ChangeControl cc = changeControlFactory.controlFor(change);
      List<SubmitRecord> result = cc.canSubmit(db, change.currentPatchSetId(), true);
      if (result.isEmpty()) {
        throw new IllegalStateException("Cannot submit");
      }
      SubmitRecord rec = new SubmitRecord();
      if (result.get(0).status.equals(SubmitRecord.Status.NOT_READY)) {
        for (SubmitRecord.Label lbl : result.get(0).labels) {
          switch (lbl.status) {
            case OK:
            case NEED:
              break;
            default:
              rec.status = result.get(0).status;
              doSubmit = false;
          }
        }
      } else if (!result.get(0).status.equals(SubmitRecord.Status.OK)) {
        rec.status = result.get(0).status;
        doSubmit = false;
      }
    }

    // TODO This checks must be done using the new Prolog implementation
    // Now we need to properly check if the topic can be submitted
    //
    final List<ChangeSetApproval> all =
      db.changeSetApprovals().byChangeSet(changeSetId).toList();

    final TopicFunctionState fs =
      functionStateFactory.create(topic, changeSetId, all);

    List<SubmitRecord.Label> labels = new ArrayList<SubmitRecord.Label>();
    SubmitRecord rec = new SubmitRecord();
    rec.status = SubmitRecord.Status.NOT_READY;
    for (ApprovalType type : approvalTypes.getApprovalTypes()) {
      TopicCategoryFunction.forCategory(type.getCategory()).run(type, fs);
      SubmitRecord.Label label = new SubmitRecord.Label();
      label.label = type.getCategory().getLabelName();
      label.status = SubmitRecord.Label.Status.NEED;
      labels.add(label);
      for (final ChangeSetApproval csa : fs.getApprovals(type)) {
        if (!fs.isValid(type)) {
          rec.status = SubmitRecord.Status.NOT_READY;
          if (type.isMaxNegative(csa)) {
            label.status = SubmitRecord.Label.Status.REJECT;
            label.appliedBy = csa.getAccountId();
          }
        } else {
          if (type.isMaxPositive(csa)) {
            label.status = SubmitRecord.Label.Status.OK;
            label.appliedBy = csa.getAccountId();
          }
        }
      }
    }

    rec.labels = labels;

    if (doSubmit) {
      rec.status = SubmitRecord.Status.OK;
      for (SubmitRecord.Label lbl : labels) {
        if (!lbl.status.equals(SubmitRecord.Label.Status.OK)) {
          rec.status = SubmitRecord.Status.NOT_READY;
          break;
        }
      }
    } else rec.status = SubmitRecord.Status.NOT_READY;
    return Collections.singletonList(rec);
  }
}
