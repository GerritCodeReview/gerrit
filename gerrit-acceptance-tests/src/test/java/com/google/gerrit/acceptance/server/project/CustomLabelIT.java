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
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.project.Util.category;
import static com.google.gerrit.server.project.Util.value;
import static com.google.gerrit.testutil.GerritServerTests.isNoteDbTestEnabled;

import static org.junit.Assert.fail;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.EventListener;
import com.google.gerrit.common.EventSource;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.data.ApprovalAttribute;
import com.google.gerrit.server.events.CommentAddedEvent;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.Util;
import com.google.inject.Inject;

import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class CustomLabelIT extends AbstractDaemonTest {

  @Inject
  private IdentifiedUser.GenericFactory factory;

  @Inject
  private EventSource source;

  private final LabelType label = category("CustomLabel",
      value(1, "Positive"),
      value(0, "No score"),
      value(-1, "Negative"));

  private final LabelType P = category("CustomLabel2",
      value(1, "Positive"),
      value(0, "No score"));

  private CommentAddedEvent lastCommentAddedEvent;

  @Before
  public void setUp() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    AccountGroup.UUID anonymousUsers =
        SystemGroupBackend.getGroup(ANONYMOUS_USERS).getUUID();
    Util.allow(cfg, Permission.forLabel(label.getName()), -1, 1, anonymousUsers,
        "refs/heads/*");
    Util.allow(cfg, Permission.forLabel(P.getName()), 0, 1, anonymousUsers,
        "refs/heads/*");
    saveProjectConfig(project, cfg);

    CurrentUser listenerUser = factory.create(user.id);
    source.addEventListener(new EventListener() {
      @Override
      public void onEvent(Event event) {
        if (event instanceof CommentAddedEvent) {
          lastCommentAddedEvent = (CommentAddedEvent) event;
        }
      }
    }, listenerUser);
  }

  @Test
  public void customLabelNoOp_NegativeVoteNotBlock() throws Exception {
    label.setFunctionName("NoOp");
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    revision(r).review(new ReviewInput().label(label.getName(), -1));
    ChangeInfo c = get(r.getChangeId());
    LabelInfo q = c.labels.get(label.getName());
    assertThat(q.all).hasSize(1);
    assertThat(q.rejected).isNotNull();
    assertThat(q.blocking).isNull();
  }

  @Test
  public void customLabelNoBlock_NegativeVoteNotBlock() throws Exception {
    label.setFunctionName("NoBlock");
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    revision(r).review(new ReviewInput().label(label.getName(), -1));
    ChangeInfo c = get(r.getChangeId());
    LabelInfo q = c.labels.get(label.getName());
    assertThat(q.all).hasSize(1);
    assertThat(q.rejected).isNotNull();
    assertThat(q.blocking).isNull();
  }

  @Test
  public void customLabelMaxNoBlock_NegativeVoteNotBlock() throws Exception {
    label.setFunctionName("MaxNoBlock");
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    revision(r).review(new ReviewInput().label(label.getName(), -1));
    ChangeInfo c = get(r.getChangeId());
    LabelInfo q = c.labels.get(label.getName());
    assertThat(q.all).hasSize(1);
    assertThat(q.rejected).isNotNull();
    assertThat(q.blocking).isNull();
  }

  @Test
  public void customLabelAnyWithBlock_NegativeVoteBlock() throws Exception {
    label.setFunctionName("AnyWithBlock");
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    revision(r).review(new ReviewInput().label(label.getName(), -1));
    ChangeInfo c = get(r.getChangeId());
    LabelInfo q = c.labels.get(label.getName());
    assertThat(q.all).hasSize(1);
    assertThat(q.disliked).isNull();
    assertThat(q.rejected).isNotNull();
    assertThat(q.blocking).isTrue();
  }

  @Test
  public void customLabelAnyWithBlock_Addreviewer_ZeroVote() throws Exception {
    P.setFunctionName("AnyWithBlock");
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes()
        .id(r.getChangeId())
        .addReviewer(in);

    ReviewInput label = new ReviewInput().label(P.getName(), 0);
    label.message = "foo";

    revision(r).review(label);
    ChangeInfo c = get(r.getChangeId());
    LabelInfo q = c.labels.get(P.getName());
    assertThat(q.all).hasSize(isNoteDbTestEnabled() ? 1 : 2);
    assertThat(q.disliked).isNull();
    assertThat(q.rejected).isNull();
    assertThat(q.blocking).isNull();
    assertThat(lastCommentAddedEvent.comment).isEqualTo("Patch Set 1:\n\nfoo");
  }

  @Test
  public void customLabel_MultipleVotes() throws Exception {
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    ReviewInput vote = new ReviewInput().label(label.getName(), -1);
    vote.message = label.getName();
    revision(r).review(vote);

    ChangeInfo c = get(r.getChangeId());
    LabelInfo q = c.labels.get(label.getName());
    assertThat(q.all).hasSize(1);
    assertThat(lastCommentAddedEvent.comment).isEqualTo(
        String.format("Patch Set 1: %s-1\n\n%s",
            label.getName(), label.getName()));

    vote = new ReviewInput().label(P.getName(), 1);
    vote.message = P.getName();
    revision(r).review(vote);

    c = get(r.getChangeId());
    q = c.labels.get(label.getName());
    assertThat(q.all).hasSize(1);
    assertThat(lastCommentAddedEvent.comment).isEqualTo(
        String.format("Patch Set 1: %s+1\n\n%s",
            P.getName(), P.getName()));

    assertThat(lastCommentAddedEvent.approvals).hasLength(2);
    for (ApprovalAttribute approval : lastCommentAddedEvent.approvals) {
      if (approval.type.equals(label.getName())) {
        assertThat(approval.value).isEqualTo("-1");
      } else if (approval.type.equals(P.getName())) {
        assertThat(approval.value).isEqualTo("1");
      } else {
        fail("Unexpected label: " + approval.type);
      }
    }
  }

  @Test
  public void customLabelMaxWithBlock_NegativeVoteBlock() throws Exception {
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    revision(r).review(new ReviewInput().label(label.getName(), -1));
    ChangeInfo c = get(r.getChangeId());
    LabelInfo q = c.labels.get(label.getName());
    assertThat(q.all).hasSize(1);
    assertThat(q.disliked).isNull();
    assertThat(q.rejected).isNotNull();
    assertThat(q.blocking).isTrue();
  }

  private void saveLabelConfig() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    cfg.getLabelSections().put(label.getName(), label);
    cfg.getLabelSections().put(P.getName(), P);
    saveProjectConfig(project, cfg);
  }
}
