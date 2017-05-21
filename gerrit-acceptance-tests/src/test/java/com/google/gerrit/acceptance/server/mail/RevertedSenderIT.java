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
import static com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy.CC_ON_OWN_COMMENTS;
import static com.google.gerrit.server.account.WatchConfig.NotifyType.ALL_COMMENTS;

import com.google.gerrit.acceptance.AbstractNotificationTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import org.junit.Before;
import org.junit.Test;

public class RevertedSenderIT extends AbstractNotificationTest {
  private TestAccount other;

  @Before
  public void createOther() throws Exception {
    other = accounts.create("other", "other@example.com", "other");
  }

  @Test
  public void revertChangeByOwnerInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageChange();
    revert(sc, sc.owner);
    assertThat(sender)
        .sent("newchange", sc)
        .notTo(sc.owner)
        .to(sc.reviewer, sc.ccer)
        .notTo(sc.starrer) // TODO(logan): Why not?
        .notTo(sc.reviewerByEmail, sc.ccerByEmail) // TODO(logan): Why not?
        .notTo(ALL_COMMENTS); // TODO(logan): Why not?

    assertThat(sender)
        .sent("revert", sc)
        .notTo(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .notTo(sc.starrer) // TODO(logan): Why not?
        .notTo(sc.reviewerByEmail, sc.ccerByEmail) // TODO(logan): Why not?
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void revertChangeByOwnerInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageChange();
    revert(sc, sc.owner);
    assertThat(sender)
        .sent("newchange", sc)
        .notTo(sc.owner)
        .to(sc.reviewer)
        .cc(sc.ccer)
        .notTo(sc.starrer) // TODO(logan): Why not?
        .notTo(sc.reviewerByEmail, sc.ccerByEmail) // TODO(logan): Why not?
        .notTo(ALL_COMMENTS); // TODO(logan): Why not?

    assertThat(sender)
        .sent("revert", sc)
        .notTo(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .notTo(sc.starrer) // TODO(logan): Why not?
        .notTo(sc.reviewerByEmail, sc.ccerByEmail) // TODO(logan): Why not?
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void revertChangeByOwnerCcingSelfInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageChange();
    revert(sc, sc.owner, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("newchange", sc)
        .to(sc.reviewer, sc.ccer)
        .cc(sc.owner)
        .notTo(sc.starrer) // TODO(logan): Why not?
        .notTo(sc.reviewerByEmail, sc.ccerByEmail) // TODO(logan): Why not?
        .notTo(ALL_COMMENTS); // TODO(logan): Why not?

    assertThat(sender)
        .sent("revert", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .notTo(sc.starrer) // TODO(logan): Why not?
        .notTo(sc.reviewerByEmail, sc.ccerByEmail) // TODO(logan): Why not?
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void revertChangeByOwnerCcingSelfInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageChange();
    revert(sc, sc.owner, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("newchange", sc)
        .to(sc.reviewer)
        .cc(sc.owner, sc.ccer)
        .notTo(sc.starrer) // TODO(logan): Why not?
        .notTo(sc.reviewerByEmail, sc.ccerByEmail) // TODO(logan): Why not?
        .notTo(ALL_COMMENTS); // TODO(logan): Why not?

    assertThat(sender)
        .sent("revert", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .notTo(sc.starrer) // TODO(logan): Why not?
        .notTo(sc.reviewerByEmail, sc.ccerByEmail) // TODO(logan): Why not?
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void revertChangeByOtherInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageChange();
    revert(sc, other);
    assertThat(sender)
        .sent("newchange", sc)
        .to(sc.owner, sc.reviewer, sc.ccer)
        .notTo(other)
        .notTo(sc.starrer) // TODO(logan): Why not?
        .notTo(sc.reviewerByEmail, sc.ccerByEmail) // TODO(logan): Why not?
        .notTo(ALL_COMMENTS); // TODO(logan): Why not?

    assertThat(sender)
        .sent("revert", sc)
        .cc(sc.owner, sc.reviewer, sc.ccer)
        .notTo(sc.starrer) // TODO(logan): Why not?
        .notTo(sc.reviewerByEmail, sc.ccerByEmail) // TODO(logan): Why not?
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void revertChangeByOtherInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageChange();
    revert(sc, other);
    assertThat(sender)
        .sent("newchange", sc)
        .to(sc.owner, sc.reviewer)
        .cc(sc.ccer)
        .notTo(other)
        .notTo(sc.starrer) // TODO(logan): Why not?
        .notTo(sc.reviewerByEmail, sc.ccerByEmail) // TODO(logan): Why not?
        .notTo(ALL_COMMENTS); // TODO(logan): Why not?

    assertThat(sender)
        .sent("revert", sc)
        .cc(sc.owner, sc.reviewer, sc.ccer)
        .notTo(sc.starrer) // TODO(logan): Why not?
        .notTo(sc.reviewerByEmail, sc.ccerByEmail) // TODO(logan): Why not?
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void revertChangeByOtherCcingSelfInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageChange();
    revert(sc, other, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("newchange", sc)
        .to(sc.owner, sc.reviewer, sc.ccer)
        .cc(other)
        .notTo(sc.starrer) // TODO(logan): Why not?
        .notTo(sc.reviewerByEmail, sc.ccerByEmail) // TODO(logan): Why not?
        .notTo(ALL_COMMENTS); // TODO(logan): Why not?

    assertThat(sender)
        .sent("revert", sc)
        .to(other)
        .cc(sc.owner, sc.reviewer, sc.ccer)
        .notTo(sc.starrer) // TODO(logan): Why not?
        .notTo(sc.reviewerByEmail, sc.ccerByEmail) // TODO(logan): Why not?
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void revertChangeByOtherCcingSelfInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageChange();
    revert(sc, other, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("newchange", sc)
        .to(sc.owner, sc.reviewer)
        .cc(sc.ccer, other)
        .notTo(sc.starrer) // TODO(logan): Why not?
        .notTo(sc.reviewerByEmail, sc.ccerByEmail) // TODO(logan): Why not?
        .notTo(ALL_COMMENTS); // TODO(logan): Why not?

    assertThat(sender)
        .sent("revert", sc)
        .to(other)
        .cc(sc.owner, sc.reviewer, sc.ccer)
        .notTo(sc.starrer) // TODO(logan): Why not?
        .notTo(sc.reviewerByEmail, sc.ccerByEmail) // TODO(logan): Why not?
        .bcc(ALL_COMMENTS);
  }

  private StagedChange stageChange() throws Exception {
    StagedChange sc = stageReviewableChange(ALL_COMMENTS);
    setApiUser(admin);
    gApi.changes().id(sc.changeId).revision("current").review(ReviewInput.approve());
    gApi.changes().id(sc.changeId).revision("current").submit();
    sender.clear();
    return sc;
  }

  private void revert(StagedChange sc, TestAccount by) throws Exception {
    revert(sc, by, EmailStrategy.ENABLED);
  }

  private void revert(StagedChange sc, TestAccount by, EmailStrategy emailStrategy)
      throws Exception {
    setEmailStrategy(by, emailStrategy);
    setApiUser(by);
    gApi.changes().id(sc.changeId).revert();
  }
}
