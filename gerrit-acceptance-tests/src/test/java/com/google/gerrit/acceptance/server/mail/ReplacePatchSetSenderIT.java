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

import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.server.account.WatchConfig.NotifyType.NEW_PATCHSETS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractNotificationTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import org.junit.Before;
import org.junit.Test;

public class ReplacePatchSetSenderIT extends AbstractNotificationTest {
  private TestAccount other;

  @Before
  public void createOtherAndGrantPermissions() throws Exception {
    other = accountCreator.create("other", "other@example.com", "other");
    grant(project, "refs/*", Permission.FORGE_COMMITTER, false, REGISTERED_USERS);
  }

  @Test
  public void newPatchSetByOwnerOnReviewableChangeInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master", sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.owner)
        .to(sc.reviewer)
        .cc(sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS);
  }

  @Test
  public void newPatchSetByOwnerOnReviewableChangeInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master", sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.owner)
        .notTo(sc.reviewerByEmail, sc.ccerByEmail)
        .to(sc.reviewer, sc.ccer)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS);
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master", other);
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.owner) // TODO(logan): This email shouldn't come from the owner.
        .to(sc.reviewer, other)
        .cc(sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS);
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master", other);
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.owner) // TODO(logan): This email shouldn't come from the owner.
        .to(sc.reviewer, sc.ccer, other)
        .notTo(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS);
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeOwnerSelfCcInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master", other, EmailStrategy.CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.owner, sc.reviewer, other)
        .cc(sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS);
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeOwnerSelfCcInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master", other, EmailStrategy.CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.reviewerByEmail, sc.ccerByEmail)
        .to(sc.owner, sc.reviewer, sc.ccer, other)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS);
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeNotifyOwnerReviewersInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master%notify=OWNER_REVIEWERS", other);
    assertThat(sender)
        .sent("newpatchset", sc)
        // TODO(logan): This email shouldn't come from the owner.
        .notTo(sc.owner, sc.starrer, other)
        .to(sc.reviewer)
        .cc(sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .notTo(NEW_PATCHSETS);
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeNotifyOwnerReviewersInReviewDb()
      throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master%notify=OWNER_REVIEWERS", other);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.reviewer, sc.ccer)
        .notTo(sc.starrer, other)
        .notTo(sc.owner) // TODO(logan): This email shouldn't come from the owner.
        .notTo(sc.reviewerByEmail, sc.ccerByEmail)
        .notTo(NEW_PATCHSETS);
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeOwnerSelfCcNotifyOwnerReviewersInNoteDb()
      throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master%notify=OWNER_REVIEWERS", other, EmailStrategy.CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.starrer, other)
        .to(sc.owner, sc.reviewer)
        .cc(sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .notTo(NEW_PATCHSETS);
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeOwnerSelfCcNotifyOwnerReviewersInReviewDb()
      throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master%notify=OWNER_REVIEWERS", other, EmailStrategy.CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.starrer, other)
        .notTo(sc.reviewerByEmail, sc.ccerByEmail)
        .to(sc.owner, sc.reviewer, sc.ccer)
        .notTo(NEW_PATCHSETS);
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master%notify=OWNER", other);
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeOwnerSelfCcNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master%notify=OWNER", other, EmailStrategy.CC_ON_OWN_COMMENTS);
    // TODO(logan): This email shouldn't come from the owner, and that's why
    // no email is currently sent (owner isn't CCing self).
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master%notify=NONE", other);
    // TODO(logan): This email shouldn't come from the owner, and that's why
    // no email is currently sent (owner isn't CCing self).
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeOwnerSelfCcNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master%notify=NONE", other, EmailStrategy.CC_ON_OWN_COMMENTS);
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetByOwnerOnReviewableChangeToWip() throws Exception {
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master%wip", sc.owner);
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetOnWipChange() throws Exception {
    StagedChange sc = stageWipChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master%wip", sc.owner);
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetOnWipChangeNotifyAllInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageWipChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master%wip,notify=ALL", sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.owner)
        .to(sc.reviewer)
        .cc(sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS);
  }

  @Test
  public void newPatchSetOnWipChangeNotifyAllInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageWipChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master%wip,notify=ALL", sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.owner)
        .notTo(sc.reviewerByEmail, sc.ccerByEmail)
        .to(sc.reviewer, sc.ccer)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS);
  }

  @Test
  public void newPatchSetOnWipChangeToReadyInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageWipChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master%ready", sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.owner)
        .to(sc.reviewer)
        .cc(sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS);
  }

  @Test
  public void newPatchSetOnWipChangeToReadyInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageWipChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master%ready", sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.owner)
        .notTo(sc.reviewerByEmail, sc.ccerByEmail)
        .to(sc.reviewer, sc.ccer)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS);
  }

  @Test
  public void newPatchSetOnReviewableWipChange() throws Exception {
    StagedChange sc = stageReviewableWipChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master%wip", sc.owner);
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetOnReviewableChangeAddingReviewerInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    TestAccount newReviewer = sc.testAccount("newReviewer");
    pushTo(sc, "refs/for/master%r=" + newReviewer.username, sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.owner)
        .to(sc.reviewer, newReviewer)
        .cc(sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS);
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetOnReviewableChangeAddingReviewerInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    TestAccount newReviewer = sc.testAccount("newReviewer");
    pushTo(sc, "refs/for/master%r=" + newReviewer.username, sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.owner)
        .to(sc.reviewer, sc.ccer, newReviewer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS);
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetOnWipChangeAddingReviewer() throws Exception {
    StagedChange sc = stageWipChange(NEW_PATCHSETS);
    TestAccount newReviewer = sc.testAccount("newReviewer");
    pushTo(sc, "refs/for/master%r=" + newReviewer.username, sc.owner);
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetOnWipChangeAddingReviewerNotifyAllInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageWipChange(NEW_PATCHSETS);
    TestAccount newReviewer = sc.testAccount("newReviewer");
    pushTo(sc, "refs/for/master%notify=ALL,r=" + newReviewer.username, sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.owner)
        .to(sc.reviewer, newReviewer)
        .cc(sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS);
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetOnWipChangeAddingReviewerNotifyAllInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageWipChange(NEW_PATCHSETS);
    TestAccount newReviewer = sc.testAccount("newReviewer");
    pushTo(sc, "refs/for/master%notify=ALL,r=" + newReviewer.username, sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.owner)
        .to(sc.reviewer, sc.ccer, newReviewer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS);
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetOnWipChangeSettingReadyInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageWipChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master%ready", sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.owner)
        .to(sc.reviewer)
        .cc(sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS);
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetOnWipChangeSettingReadyInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageWipChange(NEW_PATCHSETS);
    pushTo(sc, "refs/for/master%ready", sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.owner)
        .to(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS);
    assertThat(sender).notSent();
  }

  private void pushTo(StagedChange sc, String ref, TestAccount by) throws Exception {
    pushTo(sc, ref, by, EmailStrategy.ENABLED);
  }

  private void pushTo(StagedChange sc, String ref, TestAccount by, EmailStrategy emailStrategy)
      throws Exception {
    setEmailStrategy(sc.owner, emailStrategy);
    pushFactory.create(db, by.getIdent(), sc.repo, sc.changeId).to(ref).assertOkStatus();
  }
}
