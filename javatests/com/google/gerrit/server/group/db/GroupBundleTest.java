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

package com.google.gerrit.server.group.db;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.server.group.db.GroupBundle.Source;
import com.google.gerrit.testing.GerritBaseTests;
import com.google.gerrit.testing.TestTimeUtil;
import java.sql.Timestamp;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GroupBundleTest extends GerritBaseTests {
  // This class just contains sanity checks that GroupBundle#compare correctly compares all parts of
  // the bundle. Most other test coverage should come via the slightly more realistic
  // GroupRebuilderTest.

  private static final String TIMEZONE_ID = "US/Eastern";

  private String systemTimeZoneProperty;
  private TimeZone systemTimeZone;
  private Timestamp ts;

  @Before
  public void setUp() {
    systemTimeZoneProperty = System.setProperty("user.timezone", TIMEZONE_ID);
    systemTimeZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone(TIMEZONE_ID));
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
    ts = TimeUtil.nowTs();
  }

  @After
  public void tearDown() {
    TestTimeUtil.useSystemTime();
    System.setProperty("user.timezone", systemTimeZoneProperty);
    TimeZone.setDefault(systemTimeZone);
  }

  @Test
  public void compareNonEqual() throws Exception {
    GroupBundle reviewDbBundle = newBundle().source(Source.REVIEW_DB).build();
    AccountGroup g2 = new AccountGroup(reviewDbBundle.group());
    g2.setDescription("Hello!");
    GroupBundle noteDbBundle = GroupBundle.builder().source(Source.NOTE_DB).group(g2).build();
    assertThat(GroupBundle.compare(reviewDbBundle, noteDbBundle))
        .containsExactly(
            "AccountGroups differ\n"
                + ("ReviewDb: AccountGroup{name=group, groupId=1, description=null,"
                    + " visibleToAll=false, groupUUID=group-1, ownerGroupUUID=group-1,"
                    + " createdOn=2009-09-30 17:00:00.0}\n")
                + ("NoteDb  : AccountGroup{name=group, groupId=1, description=Hello!,"
                    + " visibleToAll=false, groupUUID=group-1, ownerGroupUUID=group-1,"
                    + " createdOn=2009-09-30 17:00:00.0}"),
            "AccountGroupMembers differ\n"
                + "ReviewDb: [AccountGroupMember{key=1000,1}]\n"
                + "NoteDb  : []",
            "AccountGroupMemberAudits differ\n"
                + ("ReviewDb: [AccountGroupMemberAudit{key=1000,1, addedBy=2000, removedBy=null,"
                    + " removedOn=null}]\n")
                + "NoteDb  : []",
            "AccountGroupByIds differ\n"
                + "ReviewDb: [AccountGroupById{key=1,subgroup}]\n"
                + "NoteDb  : []",
            "AccountGroupByIdAudits differ\n"
                + ("ReviewDb: [AccountGroupByIdAud{key=1,subgroup, addedBy=3000, removedBy=null,"
                    + " removedOn=null}]\n")
                + "NoteDb  : []");
  }

  @Test
  public void compareEqual() throws Exception {
    GroupBundle reviewDbBundle = newBundle().source(Source.REVIEW_DB).build();
    GroupBundle noteDbBundle = newBundle().source(Source.NOTE_DB).build();
    assertThat(GroupBundle.compare(reviewDbBundle, noteDbBundle)).isEmpty();
  }

  private GroupBundle.Builder newBundle() {
    AccountGroup group =
        new AccountGroup(
            new AccountGroup.NameKey("group"),
            new AccountGroup.Id(1),
            new AccountGroup.UUID("group-1"),
            ts);
    AccountGroupMember member =
        new AccountGroupMember(new AccountGroupMember.Key(new Account.Id(1000), group.getId()));
    AccountGroupMemberAudit memberAudit =
        new AccountGroupMemberAudit(member, new Account.Id(2000), ts);
    AccountGroupById byId =
        new AccountGroupById(
            new AccountGroupById.Key(group.getId(), new AccountGroup.UUID("subgroup")));
    AccountGroupByIdAud byIdAudit = new AccountGroupByIdAud(byId, new Account.Id(3000), ts);
    return GroupBundle.builder()
        .group(group)
        .members(member)
        .memberAudit(memberAudit)
        .byId(byId)
        .byIdAudit(byIdAudit);
  }
}
