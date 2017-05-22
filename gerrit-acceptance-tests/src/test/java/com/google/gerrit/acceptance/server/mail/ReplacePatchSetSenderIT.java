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

import static com.google.gerrit.server.account.WatchConfig.NotifyType.NEW_PATCHSETS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractNotificationTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import org.junit.Before;
import org.junit.Test;

public class ReplacePatchSetSenderIT extends AbstractNotificationTest {
  private TestAccount other;

  @Before
  public void createOtherAndGrantPermissions() throws Exception {
    other = accounts.create("other", "other@example.com", "other");
    grant(project, "refs/*", Permission.FORGE_COMMITTER, false, REGISTERED_USERS);
  }

  @Test
  public void newPatchSetByOwnerOnReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master", sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.owner)
        .to(sc.reviewer)
        .to(sc.reviewerByEmail)
        .cc(sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS);
  }

  @Test
  public void newPatchSetByOtherOnReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master", other);
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.owner)
        .to(sc.reviewer, other)
        .to(sc.reviewerByEmail)
        .cc(sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS);
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeOwnerSelfCc() throws Exception {
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    setEmailStrategy(sc.owner, EmailStrategy.CC_ON_OWN_COMMENTS);
    pushTo(sc, "refs/for/master", other);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.owner, sc.reviewer, other)
        .to(sc.reviewerByEmail)
        .cc(sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS);
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeNotifyOwnerReviewers() throws Exception {
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master%notify=OWNER_REVIEWERS", other);
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.owner, sc.starrer, other)
        .to(sc.reviewer)
        .to(sc.reviewerByEmail)
        .cc(sc.ccer)
        .cc(sc.ccerByEmail)
        .notTo(NEW_PATCHSETS);
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeOwnerSelfCcNotifyOwnerReviewers()
      throws Exception {
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    setEmailStrategy(sc.owner, EmailStrategy.CC_ON_OWN_COMMENTS);
    pushTo(sc, "refs/for/master%notify=OWNER_REVIEWERS", other);
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.starrer, other)
        .to(sc.owner, sc.reviewer)
        .to(sc.reviewerByEmail)
        .cc(sc.ccer)
        .cc(sc.ccerByEmail)
        .notTo(NEW_PATCHSETS);
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master%notify=OWNER", other);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.reviewer) // TODO(logan): Why?
        .cc(sc.ccer) // TODO(logan): Why?
        .notTo(sc.owner, sc.starrer, other)
        .notTo(sc.reviewerByEmail, sc.ccerByEmail)
        .notTo(NEW_PATCHSETS);
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeOwnerSelfCcNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    setEmailStrategy(sc.owner, EmailStrategy.CC_ON_OWN_COMMENTS);
    pushTo(sc, "refs/for/master%notify=OWNER", other);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.owner)
        .to(sc.reviewer) // TODO(logan): Why?
        .cc(sc.ccer) // TODO(logan): Why?
        .notTo(sc.starrer, other)
        .notTo(sc.reviewerByEmail, sc.ccerByEmail)
        .notTo(NEW_PATCHSETS);
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master%notify=NONE", other);
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeOwnerSelfCcNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    setEmailStrategy(sc.owner, EmailStrategy.CC_ON_OWN_COMMENTS);
    pushTo(sc, "refs/for/master%notify=NONE", other);
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetByOwnerOnReviewableChangeToWip() throws Exception {
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master%wip", sc.owner);
    // TODO(logan): This shouldn't notify.
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.owner)
        .to(sc.reviewer)
        .to(sc.reviewerByEmail)
        .cc(sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS);
  }

  private void pushTo(StagedChange sc, String ref, TestAccount by) throws Exception {
    pushFactory.create(db, by.getIdent(), sc.repo, sc.changeId).to(ref).assertOkStatus();
  }

  private void setEmailStrategy(TestAccount account, EmailStrategy strategy) throws Exception {
    setApiUser(account);
    GeneralPreferencesInfo prefs = gApi.accounts().self().getPreferences();
    prefs.emailStrategy = strategy;
    gApi.accounts().self().setPreferences(prefs);
  }
}
