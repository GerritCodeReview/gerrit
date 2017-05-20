// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.mail;

import static com.google.gerrit.extensions.api.changes.NotifyHandling.NONE;
import static com.google.gerrit.extensions.api.changes.NotifyHandling.OWNER;
import static com.google.gerrit.extensions.api.changes.NotifyHandling.OWNER_REVIEWERS;
import static com.google.gerrit.server.account.WatchConfig.NotifyType.ALL_COMMENTS;
import static com.google.gerrit.server.account.WatchConfig.NotifyType.SUBMITTED_CHANGES;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractNotificationTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import com.google.gerrit.server.account.WatchConfig;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.Util;
import org.junit.Before;
import org.junit.Test;

public class MergedSenderIT extends AbstractNotificationTest {
  private TestAccount other;

  @Before
  public void grantSubmit() throws Exception {
    grant(project, "refs/*", Permission.SUBMIT, false, REGISTERED_USERS);
    ProjectConfig cfg = projectCache.get(project).getConfig();
    Util.allow(cfg, Permission.forLabel("Code-Review"), -2, +2, REGISTERED_USERS, "refs/*");
  }

  @Before
  public void createOther() throws Exception {
    other = accountCreator.create("other", "other@example.com", "other");
  }

  @Test
  public void mergeByOwner() throws Exception {
    StagedChange sc = stageChange(ALL_COMMENTS, SUBMITTED_CHANGES);
    merge(sc.changeId, sc.owner);
    assertThat(sender)
        .sent("merged", sc)
        .notTo(sc.owner)
        .to(sc.reviewerByEmail) // TODO(logan): This should probably be CC.
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS, SUBMITTED_CHANGES);
  }

  @Test
  public void mergeByOwnerCcingSelf() throws Exception {
    StagedChange sc = stageChange(ALL_COMMENTS, SUBMITTED_CHANGES);
    merge(sc.changeId, sc.owner, EmailStrategy.CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("merged", sc)
        .to(sc.owner)
        .to(sc.reviewerByEmail) // TODO(logan): This should probably be CC.
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS, SUBMITTED_CHANGES);
  }

  @Test
  public void mergeByReviewer() throws Exception {
    StagedChange sc = stageChange(ALL_COMMENTS, SUBMITTED_CHANGES);
    merge(sc.changeId, sc.reviewer);
    assertThat(sender)
        .sent("merged", sc)
        .notTo(sc.reviewer)
        .to(sc.owner)
        .to(sc.reviewerByEmail) // TODO(logan): This should probably be CC.
        .cc(sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS, SUBMITTED_CHANGES);
  }

  @Test
  public void mergeByReviewerCcingSelf() throws Exception {
    StagedChange sc = stageChange(ALL_COMMENTS, SUBMITTED_CHANGES);
    merge(sc.changeId, sc.reviewer, EmailStrategy.CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("merged", sc)
        .to(sc.owner)
        .to(sc.reviewerByEmail) // TODO(logan): This should probably be CC.
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS, SUBMITTED_CHANGES);
  }

  @Test
  public void mergeByOtherNotifyOwnerReviewers() throws Exception {
    StagedChange sc = stageChange(ALL_COMMENTS, SUBMITTED_CHANGES);
    merge(sc.changeId, other, OWNER_REVIEWERS);
    assertThat(sender)
        .sent("merged", sc)
        .to(sc.owner)
        .to(sc.reviewerByEmail) // TODO(logan): This should probably be CC.
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.ccerByEmail)
        .notTo(sc.starrer)
        .notTo(ALL_COMMENTS, SUBMITTED_CHANGES);
  }

  @Test
  public void mergeByOtherNotifyOwner() throws Exception {
    StagedChange sc = stageChange(ALL_COMMENTS, SUBMITTED_CHANGES);
    merge(sc.changeId, other, OWNER);
    assertThat(sender)
        .sent("merged", sc)
        .notTo(sc.reviewer, sc.ccer, sc.starrer, other)
        .notTo(sc.reviewerByEmail, sc.ccerByEmail)
        .notTo(ALL_COMMENTS, SUBMITTED_CHANGES);
  }

  @Test
  public void mergeByOtherCcingSelfNotifyOwner() throws Exception {
    StagedChange sc = stageChange(ALL_COMMENTS, SUBMITTED_CHANGES);
    setEmailStrategy(other, EmailStrategy.CC_ON_OWN_COMMENTS);
    merge(sc.changeId, other, OWNER);
    assertThat(sender)
        .sent("merged", sc)
        .to(sc.owner)
        .notTo(sc.reviewer, sc.ccer, sc.starrer, other)
        .notTo(sc.reviewerByEmail, sc.ccerByEmail)
        .notTo(ALL_COMMENTS, SUBMITTED_CHANGES);
  }

  @Test
  public void mergeByOtherNotifyNone() throws Exception {
    StagedChange sc = stageChange(ALL_COMMENTS, SUBMITTED_CHANGES);
    merge(sc.changeId, other, NONE);
    assertThat(sender).notSent();
  }

  @Test
  public void mergeByOtherCcingSelfNotifyNone() throws Exception {
    StagedChange sc = stageChange(ALL_COMMENTS, SUBMITTED_CHANGES);
    setEmailStrategy(other, EmailStrategy.CC_ON_OWN_COMMENTS);
    merge(sc.changeId, other, NONE);
    assertThat(sender).notSent();
  }

  private void merge(String changeId, TestAccount by) throws Exception {
    merge(changeId, by, EmailStrategy.ENABLED);
  }

  private void merge(String changeId, TestAccount by, EmailStrategy emailStrategy)
      throws Exception {
    setEmailStrategy(by, emailStrategy);
    setApiUser(by);
    gApi.changes().id(changeId).revision("current").submit();
  }

  private void merge(String changeId, TestAccount by, NotifyHandling notify) throws Exception {
    merge(changeId, by, EmailStrategy.ENABLED, notify);
  }

  private void merge(
      String changeId, TestAccount by, EmailStrategy emailStrategy, NotifyHandling notify)
      throws Exception {
    setEmailStrategy(by, emailStrategy);
    setApiUser(by);
    SubmitInput in = new SubmitInput();
    in.notify = notify;
    gApi.changes().id(changeId).revision("current").submit(in);
  }

  private StagedChange stageChange(WatchConfig.NotifyType... watches) throws Exception {
    StagedChange sc = stageReviewableChange(watches);
    setApiUser(sc.reviewer);
    gApi.changes().id(sc.changeId).revision("current").review(ReviewInput.approve());
    sender.clear();
    return sc;
  }
}
