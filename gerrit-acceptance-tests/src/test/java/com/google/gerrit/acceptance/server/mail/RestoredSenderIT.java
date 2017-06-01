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

import static com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy.CC_ON_OWN_COMMENTS;
import static com.google.gerrit.server.account.WatchConfig.NotifyType.ALL_COMMENTS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractNotificationTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import org.junit.Before;
import org.junit.Test;

public class RestoredSenderIT extends AbstractNotificationTest {
  @Before
  public void grantPermissions() throws Exception {
    grant(project, "refs/heads/master", Permission.ABANDON, false, REGISTERED_USERS);
  }

  @Test
  public void restoreReviewableChange() throws Exception {
    StagedChange sc = stageAbandonedReviewableChange(ALL_COMMENTS);
    restore(sc.changeId, sc.owner);
    assertThat(sender)
        .sent("restore", sc)
        .notTo(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void restoreReviewableWipChange() throws Exception {
    StagedChange sc = stageAbandonedReviewableWipChange(ALL_COMMENTS);
    restore(sc.changeId, sc.owner);
    assertThat(sender)
        .sent("restore", sc)
        .notTo(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void restoreWipChange() throws Exception {
    StagedChange sc = stageAbandonedWipChange(ALL_COMMENTS);
    restore(sc.changeId, sc.owner);
    assertThat(sender)
        .sent("restore", sc)
        .notTo(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void restoreReviewableChangeByAdmin() throws Exception {
    StagedChange sc = stageAbandonedReviewableChange(ALL_COMMENTS);
    restore(sc.changeId, admin);
    assertThat(sender)
        .sent("restore", sc)
        .notTo(admin)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void restoreReviewableChangeByOwnerCcingSelf() throws Exception {
    StagedChange sc = stageAbandonedReviewableChange(ALL_COMMENTS);
    restore(sc.changeId, sc.owner, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("restore", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void restoreReviewableChangeByAdminCcingSelf() throws Exception {
    StagedChange sc = stageAbandonedReviewableChange(ALL_COMMENTS);
    restore(sc.changeId, admin, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("restore", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer, admin)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS);
  }

  private void restore(String changeId, TestAccount by) throws Exception {
    restore(changeId, by, EmailStrategy.ENABLED);
  }

  private void restore(String changeId, TestAccount by, EmailStrategy emailStrategy)
      throws Exception {
    setEmailStrategy(by, emailStrategy);
    setApiUser(by);
    gApi.changes().id(changeId).restore();
  }
}
