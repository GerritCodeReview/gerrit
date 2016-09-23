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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.changes.AssigneeInput;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.testutil.TestTimeUtil;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Iterator;
import java.util.Set;

@NoHttpd
public class AssigneeIT extends AbstractDaemonTest {

  @BeforeClass
  public static void setTimeForTesting() {
    TestTimeUtil.resetWithClockStep(1, SECONDS);
  }

  @AfterClass
  public static void restoreTime() {
    TestTimeUtil.useSystemTime();
  }

  @Test
  public void testGetNoAssignee() throws Exception {
    PushOneCommit.Result r = createChange();
    assertThat(getAssignee(r)).isNull();
  }

  @Test
  public void testAddGetAssignee() throws Exception {
    PushOneCommit.Result r = createChange();
    assertThat(setAssignee(r, user.email)._accountId)
        .isEqualTo(user.getId().get());
    assertThat(getAssignee(r)._accountId).isEqualTo(user.getId().get());
  }

  @Test
  public void testSetNewAssigneeWhenExists() throws Exception {
    PushOneCommit.Result r = createChange();
    setAssignee(r, user.email);
    assertThat(setAssignee(r, user.email)._accountId)
    .isEqualTo(user.getId().get());
  }

  @Test
  public void testGetPastAssignees() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    PushOneCommit.Result r = createChange();
    setAssignee(r, user.email);
    setAssignee(r, admin.email);
    Set<AccountInfo> assignees = getPastAssignees(r);
    assertThat(assignees).hasSize(2);
    Iterator<AccountInfo> itr = assignees.iterator();
    assertThat(itr.next()._accountId).isEqualTo(user.getId().get());
    assertThat(itr.next()._accountId).isEqualTo(admin.getId().get());
  }

  @Test
  public void testSetAlreadyExistingAssignee() throws Exception {
    PushOneCommit.Result r = createChange();
    setAssignee(r, user.email);
    assertThat(setAssignee(r, user.email)._accountId)
        .isEqualTo(user.getId().get());
  }

  @Test
  public void testDeleteAssignee() throws Exception {
    PushOneCommit.Result r = createChange();
    assertThat(setAssignee(r, user.email)._accountId)
        .isEqualTo(user.getId().get());
    assertThat(deleteAssignee(r)._accountId).isEqualTo(user.getId().get());
    assertThat(getAssignee(r)).isNull();
  }

  @Test
  public void testDeleteAssigneeWhenNoAssignee() throws Exception {
    PushOneCommit.Result r = createChange();
    assertThat(deleteAssignee(r)).isNull();
  }

  private AccountInfo getAssignee(PushOneCommit.Result r) throws Exception {
    return gApi.changes().id(r.getChange().getId().get()).getAssignee();
  }

  private Set<AccountInfo> getPastAssignees(PushOneCommit.Result r)
      throws Exception {
    return gApi.changes().id(r.getChange().getId().get()).getPastAssignees();
  }

  private AccountInfo setAssignee(PushOneCommit.Result r, String identifieer)
      throws Exception {
    AssigneeInput input = new AssigneeInput();
    input.assignee = identifieer;
    return gApi.changes().id(r.getChange().getId().get()).setAssignee(input);
  }

  private AccountInfo deleteAssignee(PushOneCommit.Result r) throws Exception {
    return gApi.changes().id(r.getChange().getId().get()).deleteAssignee();
  }
}
