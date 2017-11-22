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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_GROUPNAMES;
import static com.google.gerrit.server.group.db.GroupBundle.builder;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.group.db.testing.GroupTestUtil;
import com.google.gerrit.server.update.RefUpdateUtil;
import com.google.gerrit.testing.TestTimeUtil;
import java.sql.Timestamp;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GroupRebuilderTest extends AbstractGroupTest {
  private AtomicInteger idCounter;
  private Repository repo;
  private GroupRebuilder rebuilder;
  private GroupBundle.Factory bundleFactory;

  @Before
  public void setUp() throws Exception {
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
    idCounter = new AtomicInteger();
    repo = repoManager.createRepository(allUsersName);
    rebuilder =
        new GroupRebuilder(
            GroupRebuilderTest::newPersonIdent,
            allUsersName,
            (project, repo, batch) ->
                new MetaDataUpdate(GitReferenceUpdated.DISABLED, project, repo, batch),
            // Note that the expected name/email values in tests are not necessarily realistic,
            // since they use these trivial name/email functions. GroupRebuilderIT checks the actual
            // values.
            AbstractGroupTest::newPersonIdent,
            AbstractGroupTest::getAccountNameEmail,
            AbstractGroupTest::getGroupName);
    bundleFactory = new GroupBundle.Factory(new AuditLogReader(SERVER_ID));
  }

  @After
  public void tearDown() {
    TestTimeUtil.useSystemTime();
  }

  @Test
  public void minimalGroupFields() throws Exception {
    AccountGroup g = newGroup("a");
    GroupBundle b = builder().group(g).build();

    rebuilder.rebuild(repo, b, null);

    assertThat(reload(g)).isEqualTo(b);
    ImmutableList<CommitInfo> log = log(g);
    assertThat(log).hasSize(1);
    assertCommit(log.get(0), "Create group", SERVER_NAME, SERVER_EMAIL);
    assertThat(logGroupNames()).isEmpty();
  }

  @Test
  public void allGroupFields() throws Exception {
    AccountGroup g = newGroup("a");
    g.setDescription("Description");
    g.setOwnerGroupUUID(new AccountGroup.UUID("owner"));
    g.setVisibleToAll(true);
    GroupBundle b = builder().group(g).build();

    rebuilder.rebuild(repo, b, null);

    assertThat(reload(g)).isEqualTo(b);
    ImmutableList<CommitInfo> log = log(g);
    assertThat(log).hasSize(1);
    assertServerCommit(log.get(0), "Create group");
  }

  @Test
  public void emptyGroupName() throws Exception {
    AccountGroup g = newGroup("");
    GroupBundle b = builder().group(g).build();

    rebuilder.rebuild(repo, b, null);

    GroupBundle noteDbBundle = reload(g);
    assertThat(noteDbBundle).isEqualTo(b);
    assertThat(noteDbBundle.group().getName()).isEmpty();
  }

  @Test
  public void membersAndSubgroups() throws Exception {
    AccountGroup g = newGroup("a");
    GroupBundle b =
        builder()
            .group(g)
            .members(member(g, 1), member(g, 2))
            .byId(byId(g, "x"), byId(g, "y"))
            .build();

    rebuilder.rebuild(repo, b, null);

    assertThat(reload(g)).isEqualTo(b);
    ImmutableList<CommitInfo> log = log(g);
    assertThat(log).hasSize(2);
    assertServerCommit(log.get(0), "Create group");
    assertServerCommit(
        log.get(1),
        "Update group\n"
            + "\n"
            + "Add: Account 1 <1@server-id>\n"
            + "Add: Account 2 <2@server-id>\n"
            + "Add-group: Group <x>\n"
            + "Add-group: Group <y>");
  }

  @Test
  public void memberAudit() throws Exception {
    AccountGroup g = newGroup("a");
    Timestamp t1 = TimeUtil.nowTs();
    Timestamp t2 = TimeUtil.nowTs();
    Timestamp t3 = TimeUtil.nowTs();
    GroupBundle b =
        builder()
            .group(g)
            .members(member(g, 1))
            .memberAudit(addMember(g, 1, 8, t2), addAndRemoveMember(g, 2, 8, t1, 9, t3))
            .build();

    rebuilder.rebuild(repo, b, null);

    assertThat(reload(g)).isEqualTo(b);
    ImmutableList<CommitInfo> log = log(g);
    assertThat(log).hasSize(4);
    assertServerCommit(log.get(0), "Create group");
    assertCommit(
        log.get(1), "Update group\n\nAdd: Account 2 <2@server-id>", "Account 8", "8@server-id");
    assertCommit(
        log.get(2), "Update group\n\nAdd: Account 1 <1@server-id>", "Account 8", "8@server-id");
    assertCommit(
        log.get(3), "Update group\n\nRemove: Account 2 <2@server-id>", "Account 9", "9@server-id");
  }

  @Test
  public void memberAuditLegacyRemoved() throws Exception {
    AccountGroup g = newGroup("a");
    GroupBundle b =
        builder()
            .group(g)
            .members(member(g, 2))
            .memberAudit(
                addAndLegacyRemoveMember(g, 1, 8, TimeUtil.nowTs()),
                addMember(g, 2, 8, TimeUtil.nowTs()))
            .build();

    rebuilder.rebuild(repo, b, null);

    assertThat(reload(g)).isEqualTo(b);
    ImmutableList<CommitInfo> log = log(g);
    assertThat(log).hasSize(4);
    assertServerCommit(log.get(0), "Create group");
    assertCommit(
        log.get(1), "Update group\n\nAdd: Account 1 <1@server-id>", "Account 8", "8@server-id");
    assertCommit(
        log.get(2), "Update group\n\nRemove: Account 1 <1@server-id>", "Account 8", "8@server-id");
    assertCommit(
        log.get(3), "Update group\n\nAdd: Account 2 <2@server-id>", "Account 8", "8@server-id");
  }

  @Test
  public void unauditedMembershipsAddedAtEnd() throws Exception {
    AccountGroup g = newGroup("a");
    GroupBundle b =
        builder()
            .group(g)
            .members(member(g, 1), member(g, 2), member(g, 3))
            .memberAudit(addMember(g, 1, 8, TimeUtil.nowTs()))
            .build();

    rebuilder.rebuild(repo, b, null);

    assertThat(reload(g)).isEqualTo(b);
    ImmutableList<CommitInfo> log = log(g);
    assertThat(log).hasSize(3);
    assertServerCommit(log.get(0), "Create group");
    assertCommit(
        log.get(1), "Update group\n\nAdd: Account 1 <1@server-id>", "Account 8", "8@server-id");
    assertServerCommit(
        log.get(2), "Update group\n\nAdd: Account 2 <2@server-id>\nAdd: Account 3 <3@server-id>");
  }

  @Test
  public void byIdAudit() throws Exception {
    AccountGroup g = newGroup("a");
    Timestamp t1 = TimeUtil.nowTs();
    Timestamp t2 = TimeUtil.nowTs();
    Timestamp t3 = TimeUtil.nowTs();
    GroupBundle b =
        builder()
            .group(g)
            .byId(byId(g, "x"))
            .byIdAudit(addById(g, "x", 8, t2), addAndRemoveById(g, "y", 8, t1, 9, t3))
            .build();

    rebuilder.rebuild(repo, b, null);

    assertThat(reload(g)).isEqualTo(b);
    ImmutableList<CommitInfo> log = log(g);
    assertThat(log).hasSize(4);
    assertServerCommit(log.get(0), "Create group");
    assertCommit(log.get(1), "Update group\n\nAdd-group: Group <y>", "Account 8", "8@server-id");
    assertCommit(log.get(2), "Update group\n\nAdd-group: Group <x>", "Account 8", "8@server-id");
    assertCommit(log.get(3), "Update group\n\nRemove-group: Group <y>", "Account 9", "9@server-id");
  }

  @Test
  public void unauditedByIdAddedAtEnd() throws Exception {
    AccountGroup g = newGroup("a");
    GroupBundle b =
        builder()
            .group(g)
            .byId(byId(g, "x"), byId(g, "y"), byId(g, "z"))
            .byIdAudit(addById(g, "x", 8, TimeUtil.nowTs()))
            .build();

    rebuilder.rebuild(repo, b, null);

    assertThat(reload(g)).isEqualTo(b);
    ImmutableList<CommitInfo> log = log(g);
    assertThat(log).hasSize(3);
    assertServerCommit(log.get(0), "Create group");
    assertCommit(log.get(1), "Update group\n\nAdd-group: Group <x>", "Account 8", "8@server-id");
    assertServerCommit(log.get(2), "Update group\n\nAdd-group: Group <y>\nAdd-group: Group <z>");
  }

  @Test
  public void auditsAtSameTimestampBrokenDownByType() throws Exception {
    AccountGroup g = newGroup("a");
    Timestamp ts = TimeUtil.nowTs();
    int user = 8;
    GroupBundle b =
        builder()
            .group(g)
            .members(member(g, 1), member(g, 2))
            .memberAudit(
                addMember(g, 1, user, ts),
                addMember(g, 2, user, ts),
                addAndRemoveMember(g, 3, user, ts, user, ts))
            .byId(byId(g, "x"), byId(g, "y"))
            .byIdAudit(
                addById(g, "x", user, ts),
                addById(g, "y", user, ts),
                addAndRemoveById(g, "z", user, ts, user, ts))
            .build();

    rebuilder.rebuild(repo, b, null);

    assertThat(reload(g)).isEqualTo(b);
    ImmutableList<CommitInfo> log = log(g);
    assertThat(log).hasSize(5);
    assertServerCommit(log.get(0), "Create group");
    assertCommit(
        log.get(1),
        "Update group\n"
            + "\n"
            + "Add: Account 1 <1@server-id>\n"
            + "Add: Account 2 <2@server-id>\n"
            + "Add: Account 3 <3@server-id>",
        "Account 8",
        "8@server-id");
    assertCommit(
        log.get(2), "Update group\n\nRemove: Account 3 <3@server-id>", "Account 8", "8@server-id");
    assertCommit(
        log.get(3),
        "Update group\n"
            + "\n"
            + "Add-group: Group <x>\n"
            + "Add-group: Group <y>\n"
            + "Add-group: Group <z>",
        "Account 8",
        "8@server-id");
    assertCommit(log.get(4), "Update group\n\nRemove-group: Group <z>", "Account 8", "8@server-id");
  }

  @Test
  public void auditsAtSameTimestampBrokenDownByUserAndType() throws Exception {
    AccountGroup g = newGroup("a");
    Timestamp ts = TimeUtil.nowTs();
    int user1 = 8;
    int user2 = 9;

    GroupBundle b =
        builder()
            .group(g)
            .members(member(g, 1), member(g, 2), member(g, 3))
            .memberAudit(
                addMember(g, 1, user1, ts), addMember(g, 2, user2, ts), addMember(g, 3, user1, ts))
            .byId(byId(g, "x"), byId(g, "y"), byId(g, "z"))
            .byIdAudit(
                addById(g, "x", user1, ts), addById(g, "y", user2, ts), addById(g, "z", user1, ts))
            .build();

    rebuilder.rebuild(repo, b, null);

    assertThat(reload(g)).isEqualTo(b);
    ImmutableList<CommitInfo> log = log(g);
    assertThat(log).hasSize(5);
    assertServerCommit(log.get(0), "Create group");
    assertCommit(
        log.get(1),
        "Update group\n" + "\n" + "Add: Account 1 <1@server-id>\n" + "Add: Account 3 <3@server-id>",
        "Account 8",
        "8@server-id");
    assertCommit(
        log.get(2),
        "Update group\n\nAdd-group: Group <x>\nAdd-group: Group <z>",
        "Account 8",
        "8@server-id");
    assertCommit(
        log.get(3), "Update group\n\nAdd: Account 2 <2@server-id>", "Account 9", "9@server-id");
    assertCommit(log.get(4), "Update group\n\nAdd-group: Group <y>", "Account 9", "9@server-id");
  }

  @Test
  public void fixupCommitPostDatesAllAuditEventsEvenIfAuditEventsAreInTheFuture() throws Exception {
    AccountGroup g = newGroup("a");
    IntStream.range(0, 20).forEach(i -> TimeUtil.nowTs());
    Timestamp future = TimeUtil.nowTs();
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);

    GroupBundle b =
        builder()
            .group(g)
            .byId(byId(g, "x"), byId(g, "y"), byId(g, "z"))
            .byIdAudit(addById(g, "x", 8, future))
            .build();

    rebuilder.rebuild(repo, b, null);

    assertThat(reload(g)).isEqualTo(b);
    ImmutableList<CommitInfo> log = log(g);
    assertThat(log).hasSize(3);
    assertServerCommit(log.get(0), "Create group");
    assertCommit(log.get(1), "Update group\n\nAdd-group: Group <x>", "Account 8", "8@server-id");
    assertServerCommit(log.get(2), "Update group\n\nAdd-group: Group <y>\nAdd-group: Group <z>");

    assertThat(log.stream().map(c -> c.committer.date).collect(toImmutableList()))
        .named("%s", log)
        .isOrdered();
    assertThat(TimeUtil.nowTs()).isLessThan(future);
  }

  @Test
  public void combineWithBatchGroupNameNotes() throws Exception {
    AccountGroup g1 = newGroup("a");
    AccountGroup g2 = newGroup("b");

    GroupBundle b1 = builder().group(g1).build();
    GroupBundle b2 = builder().group(g2).build();

    BatchRefUpdate bru = repo.getRefDatabase().newBatchUpdate();

    rebuilder.rebuild(repo, b1, bru);
    rebuilder.rebuild(repo, b2, bru);
    try (ObjectInserter inserter = repo.newObjectInserter()) {
      ImmutableList<GroupReference> refs =
          ImmutableList.of(GroupReference.forGroup(g1), GroupReference.forGroup(g2));
      GroupNameNotes.updateGroupNames(repo, inserter, bru, refs, newPersonIdent());
      inserter.flush();
    }

    assertThat(log(g1)).isEmpty();
    assertThat(log(g2)).isEmpty();
    assertThat(logGroupNames()).isEmpty();

    RefUpdateUtil.executeChecked(bru, repo);

    assertThat(log(g1)).hasSize(1);
    assertThat(log(g2)).hasSize(1);
    assertThat(logGroupNames()).hasSize(1);
    assertThat(reload(g1)).isEqualTo(b1);
    assertThat(reload(g2)).isEqualTo(b2);

    assertThat(GroupTestUtil.readNameToUuidMap(repo)).containsExactly("a", "a-1", "b", "b-2");
  }

  @Test
  public void groupNamesWithLeadingAndTrailingWhitespace() throws Exception {
    for (String leading : ImmutableList.of("", " ", "  ")) {
      for (String trailing : ImmutableList.of("", " ", "  ")) {
        AccountGroup g = newGroup(leading + "a" + trailing);
        GroupBundle b = builder().group(g).build();
        rebuilder.rebuild(repo, b, null);
        assertThat(reload(g)).isEqualTo(b);
      }
    }
  }

  private GroupBundle reload(AccountGroup g) throws Exception {
    return bundleFactory.fromNoteDb(repo, g.getGroupUUID());
  }

  private AccountGroup newGroup(String name) {
    int id = idCounter.incrementAndGet();
    return new AccountGroup(
        new AccountGroup.NameKey(name),
        new AccountGroup.Id(id),
        new AccountGroup.UUID(name.trim() + "-" + id),
        TimeUtil.nowTs());
  }

  private AccountGroupMember member(AccountGroup g, int accountId) {
    return new AccountGroupMember(new AccountGroupMember.Key(new Account.Id(accountId), g.getId()));
  }

  private AccountGroupMemberAudit addMember(
      AccountGroup g, int accountId, int adder, Timestamp addedOn) {
    return new AccountGroupMemberAudit(member(g, accountId), new Account.Id(adder), addedOn);
  }

  private AccountGroupMemberAudit addAndLegacyRemoveMember(
      AccountGroup g, int accountId, int adder, Timestamp addedOn) {
    AccountGroupMemberAudit a = addMember(g, accountId, adder, addedOn);
    a.removedLegacy();
    return a;
  }

  private AccountGroupMemberAudit addAndRemoveMember(
      AccountGroup g,
      int accountId,
      int adder,
      Timestamp addedOn,
      int removedBy,
      Timestamp removedOn) {
    AccountGroupMemberAudit a = addMember(g, accountId, adder, addedOn);
    a.removed(new Account.Id(removedBy), removedOn);
    return a;
  }

  private AccountGroupByIdAud addById(
      AccountGroup g, String subgroupUuid, int adder, Timestamp addedOn) {
    return new AccountGroupByIdAud(byId(g, subgroupUuid), new Account.Id(adder), addedOn);
  }

  private AccountGroupByIdAud addAndRemoveById(
      AccountGroup g,
      String subgroupUuid,
      int adder,
      Timestamp addedOn,
      int removedBy,
      Timestamp removedOn) {
    AccountGroupByIdAud a = addById(g, subgroupUuid, adder, addedOn);
    a.removed(new Account.Id(removedBy), removedOn);
    return a;
  }

  private AccountGroupById byId(AccountGroup g, String subgroupUuid) {
    return new AccountGroupById(
        new AccountGroupById.Key(g.getId(), new AccountGroup.UUID(subgroupUuid)));
  }

  private ImmutableList<CommitInfo> log(AccountGroup g) throws Exception {
    return GroupTestUtil.log(repo, RefNames.refsGroups(g.getGroupUUID()));
  }

  private ImmutableList<CommitInfo> logGroupNames() throws Exception {
    return GroupTestUtil.log(repo, REFS_GROUPNAMES);
  }
}
