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

import com.google.gerrit.acceptance.AbstractNotificationTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.api.changes.AssigneeInput;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import org.junit.Before;
import org.junit.Test;

public class SetAssigneeSenderIT extends AbstractNotificationTest {
  private TestAccount assignee;

  @Before
  public void createAssignee() throws Exception {
    assignee = accountCreator.create("assignee", "assignee@example.com", "assignee");
  }

  @Test
  public void setAssigneeOnReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChange();
    assign(sc, sc.owner, sc.assignee);
    assertThat(sender)
        .sent("setassignee", sc)
        .notTo(sc.owner, sc.reviewer, sc.ccer, sc.starrer)
        .to(sc.reviewerByEmail) // TODO(logan): This is probably not intended!
        .cc(sc.ccerByEmail) // TODO(logan): This is probably not intended!
        .to(sc.assignee);
  }

  @Test
  public void setAssigneeOnReviewableChangeByOwnerCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange();
    assign(sc, sc.owner, sc.assignee, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("setassignee", sc)
        .notTo(sc.reviewer, sc.ccer, sc.starrer)
        .cc(sc.owner)
        .to(sc.reviewerByEmail) // TODO(logan): This is probably not intended!
        .cc(sc.ccerByEmail) // TODO(logan): This is probably not intended!
        .to(sc.assignee);
  }

  @Test
  public void setAssigneeOnReviewableChangeByAdmin() throws Exception {
    StagedChange sc = stageReviewableChange();
    assign(sc, admin, sc.assignee);
    assertThat(sender)
        .sent("setassignee", sc)
        .notTo(sc.owner, sc.reviewer, sc.ccer, sc.starrer, admin)
        .to(sc.reviewerByEmail) // TODO(logan): This is probably not intended!
        .cc(sc.ccerByEmail) // TODO(logan): This is probably not intended!
        .to(sc.assignee);
  }

  @Test
  public void setAssigneeOnReviewableChangeByAdminCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange();
    assign(sc, admin, sc.assignee, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("setassignee", sc)
        .notTo(sc.owner, sc.reviewer, sc.ccer, sc.starrer)
        .cc(admin)
        .to(sc.reviewerByEmail) // TODO(logan): This is probably not intended!
        .cc(sc.ccerByEmail) // TODO(logan): This is probably not intended!
        .to(sc.assignee);
  }

  @Test
  public void setAssigneeToSelfOnReviewableChangeInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageReviewableChange();
    assign(sc, sc.owner, sc.owner);
    assertThat(sender)
        .sent("setassignee", sc)
        .notTo(sc.owner, sc.reviewer, sc.ccer, sc.starrer, sc.assignee)
        .to(sc.reviewerByEmail) // TODO(logan): This is probably not intended!
        .cc(sc.ccerByEmail); // TODO(logan): This is probably not intended!
  }

  @Test
  public void setAssigneeToSelfOnReviewableChangeInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageReviewableChange();
    assign(sc, sc.owner, sc.owner);
    assertThat(sender).notSent();
  }

  @Test
  public void changeAssigneeOnReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChange();
    TestAccount other = accountCreator.create("other", "other@example.com", "other");
    assign(sc, sc.owner, other);
    sender.clear();
    assign(sc, sc.owner, sc.assignee);
    assertThat(sender)
        .sent("setassignee", sc)
        .notTo(sc.owner, sc.reviewer, sc.ccer, sc.starrer, other)
        .to(sc.reviewerByEmail) // TODO(logan): This is probably not intended!
        .cc(sc.ccerByEmail) // TODO(logan): This is probably not intended!
        .to(sc.assignee);
  }

  @Test
  public void changeAssigneeToSelfOnReviewableChangeInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageReviewableChange();
    assign(sc, sc.owner, sc.assignee);
    sender.clear();
    assign(sc, sc.owner, sc.owner);
    assertThat(sender)
        .sent("setassignee", sc)
        .notTo(sc.owner, sc.reviewer, sc.ccer, sc.starrer, sc.assignee)
        .to(sc.reviewerByEmail) // TODO(logan): This is probably not intended!
        .cc(sc.ccerByEmail); // TODO(logan): This is probably not intended!
  }

  @Test
  public void changeAssigneeToSelfOnReviewableChangeInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageReviewableChange();
    assign(sc, sc.owner, sc.assignee);
    sender.clear();
    assign(sc, sc.owner, sc.owner);
    assertThat(sender).notSent();
  }

  @Test
  public void setAssigneeOnReviewableWipChange() throws Exception {
    StagedChange sc = stageReviewableWipChange();
    assign(sc, sc.owner, sc.assignee);
    assertThat(sender)
        .sent("setassignee", sc)
        .notTo(sc.owner, sc.reviewer, sc.ccer, sc.starrer)
        .to(sc.reviewerByEmail) // TODO(logan): This is probably not intended!
        .cc(sc.ccerByEmail) // TODO(logan): This is probably not intended!
        .to(sc.assignee);
  }

  @Test
  public void setAssigneeOnWipChange() throws Exception {
    StagedChange sc = stageWipChange();
    assign(sc, sc.owner, sc.assignee);
    assertThat(sender)
        .sent("setassignee", sc)
        .notTo(sc.owner, sc.reviewer, sc.ccer, sc.starrer)
        .to(sc.reviewerByEmail) // TODO(logan): This is probably not intended!
        .cc(sc.ccerByEmail) // TODO(logan): This is probably not intended!
        .to(sc.assignee);
  }

  private void assign(StagedChange sc, TestAccount by, TestAccount to) throws Exception {
    assign(sc, by, to, EmailStrategy.ENABLED);
  }

  private void assign(StagedChange sc, TestAccount by, TestAccount to, EmailStrategy emailStrategy)
      throws Exception {
    setEmailStrategy(by, emailStrategy);
    setApiUser(by);
    AssigneeInput in = new AssigneeInput();
    in.assignee = to.email;
    gApi.changes().id(sc.changeId).setAssignee(in);
  }
}
