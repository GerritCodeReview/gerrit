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
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.project.Util.category;
import static com.google.gerrit.server.project.Util.value;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.events.CommentAddedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.Util;
import com.google.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class CommentAddedEventIT extends AbstractDaemonTest {

  @Inject private DynamicSet<CommentAddedListener> source;

  private final LabelType label =
      category("CustomLabel", value(1, "Positive"), value(0, "No score"), value(-1, "Negative"));

  private final LabelType pLabel =
      category("CustomLabel2", value(1, "Positive"), value(0, "No score"));

  private RegistrationHandle eventListenerRegistration;
  private CommentAddedListener.Event lastCommentAddedEvent;

  @Before
  public void setUp() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    AccountGroup.UUID anonymousUsers = SystemGroupBackend.getGroup(ANONYMOUS_USERS).getUUID();
    Util.allow(cfg, Permission.forLabel(label.getName()), -1, 1, anonymousUsers, "refs/heads/*");
    Util.allow(cfg, Permission.forLabel(pLabel.getName()), 0, 1, anonymousUsers, "refs/heads/*");
    saveProjectConfig(project, cfg);

    eventListenerRegistration =
        source.add(
            new CommentAddedListener() {
              @Override
              public void onCommentAdded(Event event) {
                lastCommentAddedEvent = event;
              }
            });
  }

  @After
  public void cleanup() {
    eventListenerRegistration.remove();
  }

  private void saveLabelConfig() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    cfg.getLabelSections().put(label.getName(), label);
    cfg.getLabelSections().put(pLabel.getName(), pLabel);
    saveProjectConfig(project, cfg);
  }

  /* Need to lookup info for the label under test since there can be multiple
   * labels defined.  By default Gerrit already has a Code-Review label.
   */
  private ApprovalValues getApprovalValues(LabelType label) {
    ApprovalValues res = new ApprovalValues();
    ApprovalInfo info = lastCommentAddedEvent.getApprovals().get(label.getName());
    if (info != null) {
      res.value = info.value;
    }
    info = lastCommentAddedEvent.getOldApprovals().get(label.getName());
    if (info != null) {
      res.oldValue = info.value;
    }
    return res;
  }

  @Test
  public void newChangeWithVote() throws Exception {
    saveLabelConfig();

    // push a new change with -1 vote
    PushOneCommit.Result r = createChange();
    ReviewInput reviewInput = new ReviewInput().label(label.getName(), (short) -1);
    revision(r).review(reviewInput);
    ApprovalValues attr = getApprovalValues(label);
    assertThat(attr.oldValue).isEqualTo(0);
    assertThat(attr.value).isEqualTo(-1);
    assertThat(lastCommentAddedEvent.getComment())
        .isEqualTo(String.format("Patch Set 1: %s-1", label.getName()));
  }

  @Test
  public void newPatchSetWithVote() throws Exception {
    saveLabelConfig();

    // push a new change
    PushOneCommit.Result r = createChange();
    ReviewInput reviewInput = new ReviewInput().message(label.getName());
    revision(r).review(reviewInput);

    // push a new revision with +1 vote
    ChangeInfo c = get(r.getChangeId());
    r = amendChange(c.changeId);
    reviewInput = new ReviewInput().label(label.getName(), (short) 1);
    revision(r).review(reviewInput);
    ApprovalValues attr = getApprovalValues(label);
    assertThat(attr.oldValue).isEqualTo(0);
    assertThat(attr.value).isEqualTo(1);
    assertThat(lastCommentAddedEvent.getComment())
        .isEqualTo(String.format("Patch Set 2: %s+1", label.getName()));
  }

  @Test
  public void reviewChange() throws Exception {
    saveLabelConfig();

    // push a change
    PushOneCommit.Result r = createChange();

    // review with message only, do not apply votes
    ReviewInput reviewInput = new ReviewInput().message(label.getName());
    revision(r).review(reviewInput);
    // reply message only so vote is shown as 0
    ApprovalValues attr = getApprovalValues(label);
    assertThat(attr.oldValue).isNull();
    assertThat(attr.value).isEqualTo(0);
    assertThat(lastCommentAddedEvent.getComment())
        .isEqualTo(String.format("Patch Set 1:\n\n%s", label.getName()));

    // transition from un-voted to -1 vote
    reviewInput = new ReviewInput().label(label.getName(), -1);
    revision(r).review(reviewInput);
    attr = getApprovalValues(label);
    assertThat(attr.oldValue).isEqualTo(0);
    assertThat(attr.value).isEqualTo(-1);
    assertThat(lastCommentAddedEvent.getComment())
        .isEqualTo(String.format("Patch Set 1: %s-1", label.getName()));

    // transition vote from -1 to 0
    reviewInput = new ReviewInput().label(label.getName(), 0);
    revision(r).review(reviewInput);
    attr = getApprovalValues(label);
    assertThat(attr.oldValue).isEqualTo(-1);
    assertThat(attr.value).isEqualTo(0);
    assertThat(lastCommentAddedEvent.getComment())
        .isEqualTo(String.format("Patch Set 1: -%s", label.getName()));

    // transition vote from 0 to 1
    reviewInput = new ReviewInput().label(label.getName(), 1);
    revision(r).review(reviewInput);
    attr = getApprovalValues(label);
    assertThat(attr.oldValue).isEqualTo(0);
    assertThat(attr.value).isEqualTo(1);
    assertThat(lastCommentAddedEvent.getComment())
        .isEqualTo(String.format("Patch Set 1: %s+1", label.getName()));

    // transition vote from 1 to -1
    reviewInput = new ReviewInput().label(label.getName(), -1);
    revision(r).review(reviewInput);
    attr = getApprovalValues(label);
    assertThat(attr.oldValue).isEqualTo(1);
    assertThat(attr.value).isEqualTo(-1);
    assertThat(lastCommentAddedEvent.getComment())
        .isEqualTo(String.format("Patch Set 1: %s-1", label.getName()));

    // review with message only, do not apply votes
    reviewInput = new ReviewInput().message(label.getName());
    revision(r).review(reviewInput);
    attr = getApprovalValues(label);
    assertThat(attr.oldValue).isNull(); // no vote change so not included
    assertThat(attr.value).isEqualTo(-1);
    assertThat(lastCommentAddedEvent.getComment())
        .isEqualTo(String.format("Patch Set 1:\n\n%s", label.getName()));
  }

  @Test
  public void reviewChange_MultipleVotes() throws Exception {
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    ReviewInput reviewInput = new ReviewInput().label(label.getName(), -1);
    reviewInput.message = label.getName();
    revision(r).review(reviewInput);

    ChangeInfo c = get(r.getChangeId());
    LabelInfo q = c.labels.get(label.getName());
    assertThat(q.all).hasSize(1);
    ApprovalValues labelAttr = getApprovalValues(label);
    assertThat(labelAttr.oldValue).isEqualTo(0);
    assertThat(labelAttr.value).isEqualTo(-1);
    assertThat(lastCommentAddedEvent.getComment())
        .isEqualTo(String.format("Patch Set 1: %s-1\n\n%s", label.getName(), label.getName()));

    // there should be 3 approval labels (label, pLabel, and CRVV)
    assertThat(lastCommentAddedEvent.getApprovals()).hasSize(3);

    // check the approvals that were not voted on
    ApprovalValues pLabelAttr = getApprovalValues(pLabel);
    assertThat(pLabelAttr.oldValue).isNull();
    assertThat(pLabelAttr.value).isEqualTo(0);

    LabelType crLabel = LabelType.withDefaultValues("Code-Review");
    ApprovalValues crlAttr = getApprovalValues(crLabel);
    assertThat(crlAttr.oldValue).isNull();
    assertThat(crlAttr.value).isEqualTo(0);

    // update pLabel approval
    reviewInput = new ReviewInput().label(pLabel.getName(), 1);
    reviewInput.message = pLabel.getName();
    revision(r).review(reviewInput);

    c = get(r.getChangeId());
    q = c.labels.get(label.getName());
    assertThat(q.all).hasSize(1);
    pLabelAttr = getApprovalValues(pLabel);
    assertThat(pLabelAttr.oldValue).isEqualTo(0);
    assertThat(pLabelAttr.value).isEqualTo(1);
    assertThat(lastCommentAddedEvent.getComment())
        .isEqualTo(String.format("Patch Set 1: %s+1\n\n%s", pLabel.getName(), pLabel.getName()));

    // check the approvals that were not voted on
    labelAttr = getApprovalValues(label);
    assertThat(labelAttr.oldValue).isNull();
    assertThat(labelAttr.value).isEqualTo(-1);

    crlAttr = getApprovalValues(crLabel);
    assertThat(crlAttr.oldValue).isNull();
    assertThat(crlAttr.value).isEqualTo(0);
  }

  private static class ApprovalValues {
    Integer value;
    Integer oldValue;
  }
}
