// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.event;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_LABELS;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.project.testing.TestLabels.label;
import static com.google.gerrit.server.project.testing.TestLabels.value;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.events.CommentAddedListener;
import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class CommentAddedEventIT extends AbstractDaemonTest {

  @Inject private ProjectOperations projectOperations;
  @Inject private ExtensionRegistry extensionRegistry;

  private final LabelType label =
      label("CustomLabel", value(1, "Positive"), value(0, "No score"), value(-1, "Negative"));

  private final LabelType pLabel =
      label("CustomLabel2", value(1, "Positive"), value(0, "No score"));

  @Before
  public void setUp() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel(label.getName()).ref("refs/heads/*").group(ANONYMOUS_USERS).range(-1, 1))
        .add(allowLabel(pLabel.getName()).ref("refs/heads/*").group(ANONYMOUS_USERS).range(0, 1))
        .update();
  }

  private void saveLabelConfig() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().upsertLabelType(label);
      u.getConfig().upsertLabelType(pLabel);
      u.save();
    }
  }

  private static class TestListener implements CommentAddedListener {
    private CommentAddedListener.Event lastCommentAddedEvent;

    @Override
    public void onCommentAdded(Event event) {
      lastCommentAddedEvent = event;
    }

    public CommentAddedListener.Event getLastCommentAddedEvent() {
      assertThat(lastCommentAddedEvent).isNotNull();
      return lastCommentAddedEvent;
    }
  }

  /* Need to lookup info for the label under test since there can be multiple
   * labels defined.  By default Gerrit already has a Code-Review label.
   */
  private ApprovalValues getApprovalValues(LabelType label, TestListener listener) {
    ApprovalValues res = new ApprovalValues();
    ApprovalInfo info = listener.getLastCommentAddedEvent().getApprovals().get(label.getName());
    if (info != null) {
      res.value = info.value;
    }
    info = listener.getLastCommentAddedEvent().getOldApprovals().get(label.getName());
    if (info != null) {
      res.oldValue = info.value;
    }
    return res;
  }

  @Test
  public void newChangeWithVote() throws Exception {
    saveLabelConfig();

    TestListener listener = new TestListener();
    try (Registration registration = extensionRegistry.newRegistration().add(listener)) {
      // push a new change with -1 vote
      PushOneCommit.Result r = createChange();
      ReviewInput reviewInput = new ReviewInput().label(label.getName(), (short) -1);
      revision(r).review(reviewInput);
      ApprovalValues attr = getApprovalValues(label, listener);
      assertThat(attr.oldValue).isEqualTo(0);
      assertThat(attr.value).isEqualTo(-1);
      assertThat(listener.getLastCommentAddedEvent().getComment())
          .isEqualTo(String.format("Patch Set 1: %s-1", label.getName()));
    }
  }

  @Test
  public void newPatchSetWithVote() throws Exception {
    saveLabelConfig();

    // push a new change
    PushOneCommit.Result r = createChange();
    ReviewInput reviewInput = new ReviewInput().message(label.getName());
    revision(r).review(reviewInput);
    TestListener listener = new TestListener();
    try (Registration registration = extensionRegistry.newRegistration().add(listener)) {
      // push a new revision with +1 vote
      ChangeInfo c = info(r.getChangeId());
      r = amendChange(c.changeId);
      reviewInput = new ReviewInput().label(label.getName(), (short) 1);
      revision(r).review(reviewInput);
      ApprovalValues attr = getApprovalValues(label, listener);
      assertThat(attr.oldValue).isEqualTo(0);
      assertThat(attr.value).isEqualTo(1);
      assertThat(listener.getLastCommentAddedEvent().getComment())
          .isEqualTo(String.format("Patch Set 2: %s+1", label.getName()));
    }
  }

  @Test
  public void reviewChange() throws Exception {
    saveLabelConfig();

    // push a change
    PushOneCommit.Result r = createChange();

    TestListener listener = new TestListener();
    try (Registration registration = extensionRegistry.newRegistration().add(listener)) {
      // review with message only, do not apply votes
      ReviewInput reviewInput = new ReviewInput().message(label.getName());
      revision(r).review(reviewInput);
      // reply message only so vote is shown as 0
      ApprovalValues attr = getApprovalValues(label, listener);
      assertThat(attr.oldValue).isNull();
      assertThat(attr.value).isEqualTo(0);
      assertThat(listener.getLastCommentAddedEvent().getComment())
          .isEqualTo(String.format("Patch Set 1:\n\n%s", label.getName()));

      // transition from un-voted to -1 vote
      reviewInput = new ReviewInput().label(label.getName(), -1);
      revision(r).review(reviewInput);
      attr = getApprovalValues(label, listener);
      assertThat(attr.oldValue).isEqualTo(0);
      assertThat(attr.value).isEqualTo(-1);
      assertThat(listener.getLastCommentAddedEvent().getComment())
          .isEqualTo(String.format("Patch Set 1: %s-1", label.getName()));

      // transition vote from -1 to 0
      reviewInput = new ReviewInput().label(label.getName(), 0);
      revision(r).review(reviewInput);
      attr = getApprovalValues(label, listener);
      assertThat(attr.oldValue).isEqualTo(-1);
      assertThat(attr.value).isEqualTo(0);
      assertThat(listener.getLastCommentAddedEvent().getComment())
          .isEqualTo(String.format("Patch Set 1: -%s", label.getName()));

      // transition vote from 0 to 1
      reviewInput = new ReviewInput().label(label.getName(), 1);
      revision(r).review(reviewInput);
      attr = getApprovalValues(label, listener);
      assertThat(attr.oldValue).isEqualTo(0);
      assertThat(attr.value).isEqualTo(1);
      assertThat(listener.getLastCommentAddedEvent().getComment())
          .isEqualTo(String.format("Patch Set 1: %s+1", label.getName()));

      // transition vote from 1 to -1
      reviewInput = new ReviewInput().label(label.getName(), -1);
      revision(r).review(reviewInput);
      attr = getApprovalValues(label, listener);
      assertThat(attr.oldValue).isEqualTo(1);
      assertThat(attr.value).isEqualTo(-1);
      assertThat(listener.getLastCommentAddedEvent().getComment())
          .isEqualTo(String.format("Patch Set 1: %s-1", label.getName()));

      // review with message only, do not apply votes
      reviewInput = new ReviewInput().message(label.getName());
      revision(r).review(reviewInput);
      attr = getApprovalValues(label, listener);
      assertThat(attr.oldValue).isNull(); // no vote change so not included
      assertThat(attr.value).isEqualTo(-1);
      assertThat(listener.getLastCommentAddedEvent().getComment())
          .isEqualTo(String.format("Patch Set 1:\n\n%s", label.getName()));
    }
  }

  @Test
  public void reviewChange_MultipleVotes() throws Exception {
    TestListener listener = new TestListener();
    try (Registration registration = extensionRegistry.newRegistration().add(listener)) {
      saveLabelConfig();
      PushOneCommit.Result r = createChange();
      ReviewInput reviewInput = new ReviewInput().label(label.getName(), -1);
      reviewInput.message = label.getName();
      revision(r).review(reviewInput);

      ChangeInfo c = get(r.getChangeId(), DETAILED_LABELS);
      LabelInfo q = c.labels.get(label.getName());
      assertThat(q.all).hasSize(1);
      ApprovalValues labelAttr = getApprovalValues(label, listener);
      assertThat(labelAttr.oldValue).isEqualTo(0);
      assertThat(labelAttr.value).isEqualTo(-1);
      assertThat(listener.getLastCommentAddedEvent().getComment())
          .isEqualTo(String.format("Patch Set 1: %s-1\n\n%s", label.getName(), label.getName()));

      // there should be 3 approval labels (label, pLabel, and CRVV)
      assertThat(listener.getLastCommentAddedEvent().getApprovals()).hasSize(3);

      // check the approvals that were not voted on
      ApprovalValues pLabelAttr = getApprovalValues(pLabel, listener);
      assertThat(pLabelAttr.oldValue).isNull();
      assertThat(pLabelAttr.value).isEqualTo(0);

      LabelType crLabel = LabelType.withDefaultValues("Code-Review");
      ApprovalValues crlAttr = getApprovalValues(crLabel, listener);
      assertThat(crlAttr.oldValue).isNull();
      assertThat(crlAttr.value).isEqualTo(0);

      // update pLabel approval
      reviewInput = new ReviewInput().label(pLabel.getName(), 1);
      reviewInput.message = pLabel.getName();
      revision(r).review(reviewInput);

      c = get(r.getChangeId(), DETAILED_LABELS);
      q = c.labels.get(label.getName());
      assertThat(q.all).hasSize(1);
      pLabelAttr = getApprovalValues(pLabel, listener);
      assertThat(pLabelAttr.oldValue).isEqualTo(0);
      assertThat(pLabelAttr.value).isEqualTo(1);
      assertThat(listener.getLastCommentAddedEvent().getComment())
          .isEqualTo(String.format("Patch Set 1: %s+1\n\n%s", pLabel.getName(), pLabel.getName()));

      // check the approvals that were not voted on
      labelAttr = getApprovalValues(label, listener);
      assertThat(labelAttr.oldValue).isNull();
      assertThat(labelAttr.value).isEqualTo(-1);

      crlAttr = getApprovalValues(crLabel, listener);
      assertThat(crlAttr.oldValue).isNull();
      assertThat(crlAttr.value).isEqualTo(0);
    }
  }

  private static class ApprovalValues {
    Integer value;
    Integer oldValue;
  }
}
