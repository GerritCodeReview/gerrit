// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.CreateGroupArgs;
import com.google.gerrit.server.account.PerformCreateGroup;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class QueryChangesByLabelIT extends AbstractDaemonTest {
  @Inject
  private PerformCreateGroup.Factory createGroupFactory;

  PushOneCommit.Result change1;
  PushOneCommit.Result change2;

  @Before
  public void setUp() throws Exception {
    /* Setup the following scenario:
     * user1 is in group1 and group3
     * user2 is in group2 and group3
     * user1 applies Code-Review+1 vote to change1
     * user2 applies Code-Review+1 vote to change2
     */

    createGroup("group1");
    createGroup("group2");
    createGroup("group3");

    TestAccount user1 = accounts.create("user1", "user1@example.com",
        "User1", "group1", "group3");
    TestAccount user2 = accounts.create("user2", "user2@example.com",
        "User2", "group2", "group3");

    change1 = createChange();
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes()
        .id(change1.getChangeId())
        .addReviewer(in);
    setApiUser(user1);
    revision(change1).review(ReviewInput.recommend());

    change2 = createChange();
    gApi.changes()
        .id(change1.getChangeId())
        .addReviewer(in);
    setApiUser(user2);
    revision(change2).review(ReviewInput.recommend());
  }

  @Test(expected=JsonSyntaxException.class)
  public void queryLabelWithNonExistingUser() throws Exception {
    newGson().fromJson(
        adminSession.get("/changes/?q=label:Code-Review,user=bogus")
            .getReader(),
        new TypeToken<List<ChangeInfo>>() {}
        .getType());
  }

  @Test(expected=JsonSyntaxException.class)
  public void queryLabelWithNonExistingGroup() throws Exception {
    newGson().fromJson(
        adminSession.get("/changes/?q=label:Code-Review,group=bogus")
            .getReader(),
        new TypeToken<List<ChangeInfo>>() {}
        .getType());
  }

  @Test
  public void queryLabelWithNoResults() throws Exception {
    List<ChangeInfo> changeInfos = newGson().fromJson(
        adminSession.get("/changes/?q=label:Code-Review-2")
            .getReader(),
        new TypeToken<List<ChangeInfo>>() {}
        .getType());
    assertThat(changeInfos).hasSize(0);
  }

  @Test
  public void queryLabelWithResults() throws Exception {
    List<ChangeInfo> changeInfos = newGson().fromJson(
        adminSession.get("/changes/?q=label:Code-Review")
            .getReader(),
        new TypeToken<List<ChangeInfo>>() {}
        .getType());
    assertThat(changeInfos).hasSize(2);
  }


  @Test
  public void queryLabelWithUser1() throws Exception {
    List<ChangeInfo> changeInfos = newGson().fromJson(
        adminSession.get("/changes/?q=label:Code-Review,user1")
            .getReader(),
        new TypeToken<List<ChangeInfo>>() {}
        .getType());
    assertThat(changeInfos).hasSize(1);
    assertThat(changeInfos.get(0).changeId).isEqualTo(change1.getChangeId());
  }

  @Test
  public void queryLabelWithUser2() throws Exception {
    List<ChangeInfo> changeInfos = newGson().fromJson(
        adminSession.get("/changes/?q=label:Code-Review,user=user2")
            .getReader(),
        new TypeToken<List<ChangeInfo>>() {}
        .getType());
    assertThat(changeInfos).hasSize(1);
    assertThat(changeInfos.get(0).changeId).isEqualTo(change2.getChangeId());
  }

  @Test
  public void queryLabelWithGroup1() throws Exception {
    List<ChangeInfo> changeInfos = newGson().fromJson(
        adminSession.get("/changes/?q=label:Code-Review,group1")
            .getReader(),
        new TypeToken<List<ChangeInfo>>() {}
        .getType());
    assertThat(changeInfos).hasSize(1);
    assertThat(changeInfos.get(0).changeId).isEqualTo(change1.getChangeId());
  }

  @Test
  public void queryLabelWithGroup2() throws Exception {
    List<ChangeInfo> changeInfos = newGson().fromJson(
        adminSession.get("/changes/?q=label:Code-Review,group=group2")
            .getReader(),
        new TypeToken<List<ChangeInfo>>() {}
        .getType());
    assertThat(changeInfos).hasSize(1);
    assertThat(changeInfos.get(0).changeId).isEqualTo(change2.getChangeId());
  }

  @Test
  public void queryLabelWithGroup3() throws Exception {
    List<ChangeInfo> changeInfos = newGson().fromJson(
        adminSession.get("/changes/?q=label:Code-Review,group=group3")
            .getReader(),
        new TypeToken<List<ChangeInfo>>() {}
        .getType());
    assertThat(changeInfos).hasSize(2);
  }

  private AccountGroup createGroup(String name) throws Exception {
    CreateGroupArgs args = new CreateGroupArgs();
    args.setGroupName(name);
    args.initialMembers = Collections.singleton(admin.getId());
    return createGroupFactory.create(args).createGroup();
  }
}
