// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.common.data.LabelFunction.ANY_WITH_BLOCK;
import static com.google.gerrit.common.data.LabelFunction.MAX_NO_BLOCK;
import static com.google.gerrit.common.data.LabelFunction.MAX_WITH_BLOCK;
import static com.google.gerrit.common.data.LabelFunction.NO_BLOCK;
import static com.google.gerrit.common.data.LabelFunction.NO_OP;
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_LABELS;
import static com.google.gerrit.extensions.client.ListChangesOption.LABELS;
import static com.google.gerrit.extensions.client.ListChangesOption.SUBMITTABLE;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.project.testing.Util.category;
import static com.google.gerrit.server.project.testing.Util.value;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.LabelFunction;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.events.CommentAddedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.testing.Util;
import com.google.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class CustomLabelIT extends AbstractDaemonTest {

  @Inject private DynamicSet<CommentAddedListener> source;

  private final LabelType label =
      category("CustomLabel", value(1, "Positive"), value(0, "No score"), value(-1, "Negative"));

  private final LabelType P = category("CustomLabel2", value(1, "Positive"), value(0, "No score"));

  private RegistrationHandle eventListenerRegistration;
  private CommentAddedListener.Event lastCommentAddedEvent;

  @Before
  public void setUp() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      AccountGroup.UUID anonymousUsers = systemGroupBackend.getGroup(ANONYMOUS_USERS).getUUID();
      Util.allow(
          u.getConfig(),
          Permission.forLabel(label.getName()),
          -1,
          1,
          anonymousUsers,
          "refs/heads/*");
      Util.allow(
          u.getConfig(), Permission.forLabel(P.getName()), 0, 1, anonymousUsers, "refs/heads/*");
      u.save();
    }

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
    db.close();
  }

  @Test
  public void customLabelNoOp_NegativeVoteNotBlock() throws Exception {
    label.setFunction(NO_OP);
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    revision(r).review(new ReviewInput().label(label.getName(), -1));
    ChangeInfo c = getWithLabels(r);
    LabelInfo q = c.labels.get(label.getName());
    assertThat(q.all).hasSize(1);
    assertThat(q.approved).isNull();
    assertThat(q.recommended).isNull();
    assertThat(q.disliked).isNull();
    assertThat(q.rejected).isNotNull();
    assertThat(q.blocking).isNull();
  }

  @Test
  public void customLabelNoBlock_NegativeVoteNotBlock() throws Exception {
    label.setFunction(NO_BLOCK);
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    revision(r).review(new ReviewInput().label(label.getName(), -1));
    ChangeInfo c = getWithLabels(r);
    LabelInfo q = c.labels.get(label.getName());
    assertThat(q.all).hasSize(1);
    assertThat(q.approved).isNull();
    assertThat(q.recommended).isNull();
    assertThat(q.disliked).isNull();
    assertThat(q.rejected).isNotNull();
    assertThat(q.blocking).isNull();
  }

  @Test
  public void customLabelMaxNoBlock_NegativeVoteNotBlock() throws Exception {
    label.setFunction(MAX_NO_BLOCK);
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    revision(r).review(new ReviewInput().label(label.getName(), -1));
    ChangeInfo c = getWithLabels(r);
    LabelInfo q = c.labels.get(label.getName());
    assertThat(q.all).hasSize(1);
    assertThat(q.approved).isNull();
    assertThat(q.recommended).isNull();
    assertThat(q.disliked).isNull();
    assertThat(q.rejected).isNotNull();
    assertThat(q.blocking).isNull();
  }

  @Test
  public void customLabelMaxNoBlock_MaxVoteSubmittable() throws Exception {
    label.setFunction(MAX_NO_BLOCK);
    P.setFunction(NO_OP);
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    assertThat(info(r.getChangeId()).submittable).isNull();
    revision(r).review(ReviewInput.approve().label(label.getName(), 1));

    ChangeInfo c = getWithLabels(r);
    assertThat(c.submittable).isTrue();
    LabelInfo q = c.labels.get(label.getName());
    assertThat(q.all).hasSize(1);
    assertThat(q.approved).isNotNull();
    assertThat(q.recommended).isNull();
    assertThat(q.disliked).isNull();
    assertThat(q.rejected).isNull();
    assertThat(q.blocking).isNull();
  }

  @Test
  public void customLabelAnyWithBlock_NegativeVoteBlock() throws Exception {
    label.setFunction(ANY_WITH_BLOCK);
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    revision(r).review(new ReviewInput().label(label.getName(), -1));
    ChangeInfo c = getWithLabels(r);
    LabelInfo q = c.labels.get(label.getName());
    assertThat(q.all).hasSize(1);
    assertThat(q.approved).isNull();
    assertThat(q.recommended).isNull();
    assertThat(q.disliked).isNull();
    assertThat(q.rejected).isNotNull();
    assertThat(q.blocking).isTrue();
  }

  @Test
  public void customLabelAnyWithBlock_Addreviewer_ZeroVote() throws Exception {
    P.setFunction(ANY_WITH_BLOCK);
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes().id(r.getChangeId()).addReviewer(in);

    ReviewInput input = new ReviewInput().label(P.getName(), 0);
    input.message = "foo";

    revision(r).review(input);
    ChangeInfo c = getWithLabels(r);
    LabelInfo q = c.labels.get(P.getName());
    assertThat(q.all).hasSize(2);
    assertThat(q.approved).isNull();
    assertThat(q.recommended).isNull();
    assertThat(q.disliked).isNull();
    assertThat(q.rejected).isNull();
    assertThat(q.blocking).isNull();
    assertThat(lastCommentAddedEvent.getComment()).isEqualTo("Patch Set 1:\n\n" + input.message);
  }

  @Test
  public void customLabelMaxWithBlock_NegativeVoteBlock() throws Exception {
    label.setFunction(MAX_WITH_BLOCK);
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    revision(r).review(new ReviewInput().label(label.getName(), -1));
    ChangeInfo c = getWithLabels(r);
    LabelInfo q = c.labels.get(label.getName());
    assertThat(q.all).hasSize(1);
    assertThat(q.approved).isNull();
    assertThat(q.recommended).isNull();
    assertThat(q.disliked).isNull();
    assertThat(q.rejected).isNotNull();
    assertThat(q.blocking).isTrue();
  }

  @Test
  public void customLabelMaxWithBlock_MaxVoteSubmittable() throws Exception {
    label.setFunction(MAX_WITH_BLOCK);
    P.setFunction(NO_OP);
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    assertThat(info(r.getChangeId()).submittable).isNull();
    revision(r).review(ReviewInput.approve().label(label.getName(), 1));

    ChangeInfo c = getWithLabels(r);
    assertThat(c.submittable).isTrue();
    LabelInfo q = c.labels.get(label.getName());
    assertThat(q.all).hasSize(1);
    assertThat(q.approved).isNotNull();
    assertThat(q.recommended).isNull();
    assertThat(q.disliked).isNull();
    assertThat(q.rejected).isNull();
    assertThat(q.blocking).isNull();
  }

  @Test
  public void customLabelMaxWithBlock_MaxVoteNegativeVoteBlock() throws Exception {
    label.setFunction(MAX_WITH_BLOCK);
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    revision(r).review(new ReviewInput().label(label.getName(), 1));
    revision(r).review(new ReviewInput().label(label.getName(), -1));
    ChangeInfo c = getWithLabels(r);
    LabelInfo q = c.labels.get(label.getName());
    assertThat(q.all).hasSize(1);
    assertThat(q.approved).isNull();
    assertThat(q.recommended).isNull();
    assertThat(q.disliked).isNull();
    assertThat(q.rejected).isNotNull();
    assertThat(q.blocking).isTrue();
  }

  @Test
  public void customLabel_DisallowPostSubmit() throws Exception {
    label.setFunction(NO_OP);
    label.setAllowPostSubmit(false);
    P.setFunction(NO_OP);
    saveLabelConfig();

    PushOneCommit.Result r = createChange();
    revision(r).review(ReviewInput.approve());
    revision(r).submit();

    ChangeInfo info = getWithLabels(r);
    assertPermitted(info, "Code-Review", 2);
    assertPermitted(info, P.getName(), 0, 1);
    assertPermitted(info, label.getName());

    ReviewInput in = new ReviewInput();
    in.label(P.getName(), P.getMax().getValue());
    revision(r).review(in);

    in = new ReviewInput();
    in.label(label.getName(), label.getMax().getValue());
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Voting on labels disallowed after submit: " + label.getName());
    revision(r).review(in);
  }

  @Test
  public void customLabelWithUserPermissionChange() throws Exception {
    String testLabel = "Test-Label";
    configLabel(
        project,
        testLabel,
        LabelFunction.MAX_WITH_BLOCK,
        value(2, "Looks good to me, approved"),
        value(1, "Looks good to me, but someone else must approve"),
        value(0, "No score"),
        value(-1, "I would prefer this is not merged as is"),
        value(-2, "This shall not be merged"));

    AccountGroup.UUID registered = SystemGroupBackend.REGISTERED_USERS;
    try (ProjectConfigUpdate u = updateProject(project)) {
      Util.allow(u.getConfig(), Permission.forLabel(testLabel), -2, +2, registered, "refs/heads/*");
      u.save();
    }

    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();

    // admin votes 'Test-Label +2' and 'Code-Review +2'.
    ReviewInput input = new ReviewInput();
    input.label(testLabel, 2);
    input.label("Code-Review", 2);
    revision(result).review(input);

    // Verify the value of 'Test-Label' is +2.
    assertLabelStatus(changeId, testLabel);

    // The change is submittable.
    assertThat(gApi.changes().id(changeId).get().submittable).isTrue();

    // Update admin's permitted range for 'Test-Label' to be -1...+1.
    try (ProjectConfigUpdate u = updateProject(project)) {
      Util.remove(u.getConfig(), Permission.forLabel(testLabel), registered, "refs/heads/*");
      Util.allow(u.getConfig(), Permission.forLabel(testLabel), -1, +1, registered, "refs/heads/*");
      u.save();
    }

    // Verify admin doesn't have +2 permission any more.
    assertPermitted(gApi.changes().id(changeId).get(), testLabel, -1, 0, 1);

    // Verify the value of 'Test-Label' is still +2.
    assertLabelStatus(changeId, testLabel);

    // Verify the change is still submittable.
    assertThat(gApi.changes().id(changeId).get().submittable).isTrue();
    gApi.changes().id(changeId).current().submit();
  }

  private void assertLabelStatus(String changeId, String testLabel) throws Exception {
    ChangeInfo changeInfo = getWithLabels(changeId);
    LabelInfo labelInfo = changeInfo.labels.get(testLabel);
    assertThat(labelInfo.all).hasSize(1);
    assertThat(labelInfo.approved).isNotNull();
    assertThat(labelInfo.recommended).isNull();
    assertThat(labelInfo.disliked).isNull();
    assertThat(labelInfo.rejected).isNull();
    assertThat(labelInfo.blocking).isNull();
  }

  private void saveLabelConfig() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().getLabelSections().put(label.getName(), label);
      u.getConfig().getLabelSections().put(P.getName(), P);
      u.save();
    }
  }

  private ChangeInfo getWithLabels(PushOneCommit.Result r) throws Exception {
    return getWithLabels(r.getChangeId());
  }

  private ChangeInfo getWithLabels(String changeId) throws Exception {
    return get(changeId, LABELS, DETAILED_LABELS, SUBMITTABLE);
  }
}
