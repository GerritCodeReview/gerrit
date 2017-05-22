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

import com.google.gerrit.acceptance.AbstractNotificationTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.api.changes.AssigneeInput;
import org.junit.Before;
import org.junit.Test;

public class SetAssigneeSenderIT extends AbstractNotificationTest {
  private TestAccount assignee;

  @Before
  public void createAssignee() throws Exception {
    assignee = accounts.create("assignee", "assignee@example.com", "assignee");
  }

  @Test
  public void setAssigneeOnReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChange();
    assign(sc, assignee);
    assertThat(sender)
        .sent("setassignee", sc)
        .notTo(sc.owner, sc.reviewer, sc.ccer, sc.starrer)
        .to(sc.reviewerByEmail) // TODO(logan): This is probably not intended!
        .cc(sc.ccerByEmail) // TODO(logan): This is probably not intended!
        .to(assignee);
  }

  @Test
  public void setAssigneeToSelfOnReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChange();
    assign(sc, sc.owner);
    assertThat(sender)
        .sent("setassignee", sc)
        .notTo(sc.owner, sc.reviewer, sc.ccer, sc.starrer, assignee)
        .to(sc.reviewerByEmail) // TODO(logan): This is probably not intended!
        .cc(sc.ccerByEmail); // TODO(logan): This is probably not intended!
  }

  @Test
  public void changeAssigneeOnReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChange();
    TestAccount other = accounts.create("other", "other@example.com", "other");
    assign(sc, other);
    sender.clear();
    assign(sc, assignee);
    assertThat(sender)
        .sent("setassignee", sc)
        .notTo(sc.owner, sc.reviewer, sc.ccer, sc.starrer, other)
        .to(sc.reviewerByEmail) // TODO(logan): This is probably not intended!
        .cc(sc.ccerByEmail) // TODO(logan): This is probably not intended!
        .to(assignee);
  }

  @Test
  public void changeAssigneeToSelfOnReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChange();
    assign(sc, assignee);
    sender.clear();
    assign(sc, sc.owner);
    assertThat(sender)
        .sent("setassignee", sc)
        .notTo(sc.owner, sc.reviewer, sc.ccer, sc.starrer, assignee)
        .to(sc.reviewerByEmail) // TODO(logan): This is probably not intended!
        .cc(sc.ccerByEmail); // TODO(logan): This is probably not intended!
  }

  private void assign(StagedChange sc, TestAccount to) throws Exception {
    setApiUser(sc.owner);
    AssigneeInput in = new AssigneeInput();
    in.assignee = to.email;
    gApi.changes().id(sc.changeId).setAssignee(in);
  }
}
