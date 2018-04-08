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

package com.google.gerrit.server.schema;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.gerrit.extensions.common.testing.CommitInfoSubject.assertThat;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_GROUPNAMES;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.config.AllUsersNameProvider;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.group.db.AuditLogFormatter;
import com.google.gerrit.server.group.db.AuditLogReader;
import com.google.gerrit.server.group.db.GroupNameNotes;
import com.google.gerrit.server.update.RefUpdateUtil;
import com.google.gerrit.testing.GerritBaseTests;
import com.google.gerrit.testing.GitTestUtil;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GroupRebuilderTest extends GerritBaseTests {
  private static final TimeZone TZ = TimeZone.getTimeZone("America/Los_Angeles");
  private static final String SERVER_ID = "server-id";
  private static final String SERVER_NAME = "Gerrit Server";
  private static final String SERVER_EMAIL = "noreply@gerritcodereview.com";

  private AtomicInteger idCounter;
  private Repository repo;
  private GroupRebuilder rebuilder;
  private GroupBundle.Factory bundleFactory;

  @Before
  public void setUp() throws Exception {
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
    idCounter = new AtomicInteger();
    AllUsersName allUsersName = new AllUsersName(AllUsersNameProvider.DEFAULT);
    repo = new InMemoryRepositoryManager().createRepository(allUsersName);
    rebuilder =
        new GroupRebuilder(
            GroupRebuilderTest.newPersonIdent(),
            allUsersName,
            // Note that the expected name/email values in tests are not necessarily realistic,
            // since they use these trivial name/email functions.
            getAuditLogFormatter());
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

    assertMigratedCleanly(reload(g), b);
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

    assertMigratedCleanly(reload(g), b);
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
    assertMigratedCleanly(noteDbBundle, b);
    assertThat(noteDbBundle.group().getName()).isEmpty();
  }

  @Test
  public void nullGroupDescription() throws Exception {
    AccountGroup g = newGroup("a");
    g.setDescription(null);
    assertThat(g.getDescription()).isNull();
    GroupBundle b = builder().group(g).build();

    rebuilder.rebuild(repo, b, null);

    GroupBundle noteDbBundle = reload(g);
    assertMigratedCleanly(noteDbBundle, b);
    assertThat(noteDbBundle.group().getDescription()).isNull();
  }

  @Test
  public void emptyGroupDescription() throws Exception {
    AccountGroup g = newGroup("a");
    g.setDescription("");
    assertThat(g.getDescription()).isEmpty();
    GroupBundle b = builder().group(g).build();

    rebuilder.rebuild(repo, b, null);

    GroupBundle noteDbBundle = reload(g);
    assertMigratedCleanly(noteDbBundle, b);
    assertThat(noteDbBundle.group().getDescription()).isNull();
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

    assertMigratedCleanly(reload(g), b);
    ImmutableList<CommitInfo> log = log(g);
    assertThat(log).hasSize(2);
    assertServerCommit(log.get(0), "Create group");
    assertServerCommit(
        log.get(1),
        "Update group\n"
            + "\n"
            + "Add-group: Group x <x>\n"
            + "Add-group: Group y <y>\n"
            + "Add: Account 1 <1@server-id>\n"
            + "Add: Account 2 <2@server-id>");
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

    assertMigratedCleanly(reload(g), b);
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

    assertMigratedCleanly(reload(g), b);
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

    assertMigratedCleanly(reload(g), b);
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

    assertMigratedCleanly(reload(g), b);
    ImmutableList<CommitInfo> log = log(g);
    assertThat(log).hasSize(4);
    assertServerCommit(log.get(0), "Create group");
    assertCommit(log.get(1), "Update group\n\nAdd-group: Group y <y>", "Account 8", "8@server-id");
    assertCommit(log.get(2), "Update group\n\nAdd-group: Group x <x>", "Account 8", "8@server-id");
    assertCommit(
        log.get(3), "Update group\n\nRemove-group: Group y <y>", "Account 9", "9@server-id");
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

    assertMigratedCleanly(reload(g), b);
    ImmutableList<CommitInfo> log = log(g);
    assertThat(log).hasSize(3);
    assertServerCommit(log.get(0), "Create group");
    assertCommit(log.get(1), "Update group\n\nAdd-group: Group x <x>", "Account 8", "8@server-id");
    assertServerCommit(
        log.get(2), "Update group\n\nAdd-group: Group y <y>\nAdd-group: Group z <z>");
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

    assertMigratedCleanly(reload(g), b);
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
            + "Add-group: Group x <x>\n"
            + "Add-group: Group y <y>\n"
            + "Add-group: Group z <z>",
        "Account 8",
        "8@server-id");
    assertCommit(
        log.get(4), "Update group\n\nRemove-group: Group z <z>", "Account 8", "8@server-id");
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

    assertMigratedCleanly(reload(g), b);
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
        "Update group\n\nAdd-group: Group x <x>\nAdd-group: Group z <z>",
        "Account 8",
        "8@server-id");
    assertCommit(
        log.get(3), "Update group\n\nAdd: Account 2 <2@server-id>", "Account 9", "9@server-id");
    assertCommit(log.get(4), "Update group\n\nAdd-group: Group y <y>", "Account 9", "9@server-id");
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

    assertMigratedCleanly(reload(g), b);
    ImmutableList<CommitInfo> log = log(g);
    assertThat(log).hasSize(3);
    assertServerCommit(log.get(0), "Create group");
    assertCommit(log.get(1), "Update group\n\nAdd-group: Group x <x>", "Account 8", "8@server-id");
    assertServerCommit(
        log.get(2), "Update group\n\nAdd-group: Group y <y>\nAdd-group: Group z <z>");

    assertThat(log.stream().map(c -> c.committer.date).collect(toImmutableList()))
        .named("%s", log)
        .isOrdered();
    assertThat(TimeUtil.nowTs()).isLessThan(future);
  }

  @Test
  public void redundantMemberAuditsAreIgnored() throws Exception {
    AccountGroup g = newGroup("a");
    Timestamp t1 = TimeUtil.nowTs();
    Timestamp t2 = TimeUtil.nowTs();
    Timestamp t3 = TimeUtil.nowTs();
    Timestamp t4 = TimeUtil.nowTs();
    Timestamp t5 = TimeUtil.nowTs();
    GroupBundle b =
        builder()
            .group(g)
            .members(member(g, 2))
            .memberAudit(
                addMember(g, 1, 8, t1),
                addMember(g, 1, 8, t1),
                addMember(g, 1, 8, t3),
                addMember(g, 1, 9, t4),
                addAndRemoveMember(g, 1, 8, t2, 9, t5),
                addAndLegacyRemoveMember(g, 2, 9, t3),
                addMember(g, 2, 8, t1),
                addMember(g, 2, 9, t4),
                addMember(g, 1, 8, t5))
            .build();

    rebuilder.rebuild(repo, b, null);

    assertMigratedCleanly(reload(g), b);
    ImmutableList<CommitInfo> log = log(g);
    assertThat(log).hasSize(5);
    assertServerCommit(log.get(0), "Create group");
    assertCommit(
        log.get(1),
        "Update group\n\nAdd: Account 1 <1@server-id>\nAdd: Account 2 <2@server-id>",
        "Account 8",
        "8@server-id");
    assertCommit(
        log.get(2), "Update group\n\nRemove: Account 2 <2@server-id>", "Account 9", "9@server-id");
    assertCommit(
        log.get(3), "Update group\n\nAdd: Account 2 <2@server-id>", "Account 9", "9@server-id");
    assertCommit(
        log.get(4), "Update group\n\nRemove: Account 1 <1@server-id>", "Account 9", "9@server-id");
  }

  @Test
  public void additionsAndRemovalsWithinSameSecondCanBeMigrated() throws Exception {
    TestTimeUtil.resetWithClockStep(1, TimeUnit.MILLISECONDS);
    AccountGroup g = newGroup("a");
    Timestamp t1 = TimeUtil.nowTs();
    Timestamp t2 = TimeUtil.nowTs();
    Timestamp t3 = TimeUtil.nowTs();
    Timestamp t4 = TimeUtil.nowTs();
    Timestamp t5 = TimeUtil.nowTs();
    GroupBundle b =
        builder()
            .group(g)
            .members(member(g, 1))
            .memberAudit(
                addAndLegacyRemoveMember(g, 1, 8, t1),
                addMember(g, 1, 10, t2),
                addAndRemoveMember(g, 1, 8, t3, 9, t4),
                addMember(g, 1, 8, t5))
            .build();

    rebuilder.rebuild(repo, b, null);

    assertMigratedCleanly(reload(g), b);
    ImmutableList<CommitInfo> log = log(g);
    assertThat(log).hasSize(6);
    assertServerCommit(log.get(0), "Create group");
    assertCommit(
        log.get(1), "Update group\n\nAdd: Account 1 <1@server-id>", "Account 8", "8@server-id");
    assertCommit(
        log.get(2), "Update group\n\nRemove: Account 1 <1@server-id>", "Account 8", "8@server-id");
    assertCommit(
        log.get(3), "Update group\n\nAdd: Account 1 <1@server-id>", "Account 10", "10@server-id");
    assertCommit(
        log.get(4), "Update group\n\nRemove: Account 1 <1@server-id>", "Account 9", "9@server-id");
    assertCommit(
        log.get(5), "Update group\n\nAdd: Account 1 <1@server-id>", "Account 8", "8@server-id");
  }

  @Test
  public void redundantByIdAuditsAreIgnored() throws Exception {
    AccountGroup g = newGroup("a");
    Timestamp t1 = TimeUtil.nowTs();
    Timestamp t2 = TimeUtil.nowTs();
    Timestamp t3 = TimeUtil.nowTs();
    Timestamp t4 = TimeUtil.nowTs();
    Timestamp t5 = TimeUtil.nowTs();
    GroupBundle b =
        builder()
            .group(g)
            .byId()
            .byIdAudit(
                addById(g, "x", 8, t1),
                addById(g, "x", 8, t3),
                addById(g, "x", 9, t4),
                addAndRemoveById(g, "x", 8, t2, 9, t5))
            .build();

    rebuilder.rebuild(repo, b, null);

    assertMigratedCleanly(reload(g), b);
    ImmutableList<CommitInfo> log = log(g);
    assertThat(log).hasSize(3);
    assertServerCommit(log.get(0), "Create group");
    assertCommit(log.get(1), "Update group\n\nAdd-group: Group x <x>", "Account 8", "8@server-id");
    assertCommit(
        log.get(2), "Update group\n\nRemove-group: Group x <x>", "Account 9", "9@server-id");
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
      GroupNameNotes.updateAllGroups(repo, inserter, bru, refs, newPersonIdent());
      inserter.flush();
    }

    assertThat(log(g1)).isEmpty();
    assertThat(log(g2)).isEmpty();
    assertThat(logGroupNames()).isEmpty();

    RefUpdateUtil.executeChecked(bru, repo);

    assertThat(log(g1)).hasSize(1);
    assertThat(log(g2)).hasSize(1);
    assertThat(logGroupNames()).hasSize(1);
    assertMigratedCleanly(reload(g1), b1);
    assertMigratedCleanly(reload(g2), b2);

    GroupReference group1 = GroupReference.forGroup(g1);
    GroupReference group2 = GroupReference.forGroup(g2);
    assertThat(GroupNameNotes.loadAllGroups(repo)).containsExactly(group1, group2);
  }

  @Test
  public void groupNamesWithLeadingAndTrailingWhitespace() throws Exception {
    for (String leading : ImmutableList.of("", " ", "  ")) {
      for (String trailing : ImmutableList.of("", " ", "  ")) {
        AccountGroup g = newGroup(leading + "a" + trailing);
        GroupBundle b = builder().group(g).build();
        rebuilder.rebuild(repo, b, null);
        assertMigratedCleanly(reload(g), b);
      }
    }
  }

  @Test
  public void disallowExisting() throws Exception {
    AccountGroup g = newGroup("a");
    GroupBundle b = builder().group(g).build();

    rebuilder.rebuild(repo, b, null);
    assertMigratedCleanly(reload(g), b);
    String refName = RefNames.refsGroups(g.getGroupUUID());
    ObjectId oldId = repo.exactRef(refName).getObjectId();

    try {
      rebuilder.rebuild(repo, b, null);
      assert_().fail("expected OrmDuplicateKeyException");
    } catch (OrmDuplicateKeyException e) {
      // Expected.
    }

    assertThat(repo.exactRef(refName).getObjectId()).isEqualTo(oldId);
  }

  private GroupBundle reload(AccountGroup g) throws Exception {
    return bundleFactory.fromNoteDb(repo, g.getGroupUUID());
  }

  private void assertMigratedCleanly(GroupBundle noteDbBundle, GroupBundle expectedReviewDbBundle) {
    assertThat(GroupBundle.compareWithAudits(expectedReviewDbBundle, noteDbBundle)).isEmpty();
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
    return GitTestUtil.log(repo, RefNames.refsGroups(g.getGroupUUID()));
  }

  private ImmutableList<CommitInfo> logGroupNames() throws Exception {
    return GitTestUtil.log(repo, REFS_GROUPNAMES);
  }

  private static GroupBundle.Builder builder() {
    return GroupBundle.builder().source(GroupBundle.Source.REVIEW_DB);
  }

  private static PersonIdent newPersonIdent() {
    return new PersonIdent(SERVER_NAME, SERVER_EMAIL, TimeUtil.nowTs(), TZ);
  }

  private static void assertServerCommit(CommitInfo commitInfo, String expectedMessage) {
    assertCommit(commitInfo, expectedMessage, SERVER_NAME, SERVER_EMAIL);
  }

  private static void assertCommit(
      CommitInfo commitInfo, String expectedMessage, String expectedName, String expectedEmail) {
    assertThat(commitInfo).message().isEqualTo(expectedMessage);
    assertThat(commitInfo).author().name().isEqualTo(expectedName);
    assertThat(commitInfo).author().email().isEqualTo(expectedEmail);

    // Committer should always be the server, regardless of author.
    assertThat(commitInfo).committer().name().isEqualTo(SERVER_NAME);
    assertThat(commitInfo).committer().email().isEqualTo(SERVER_EMAIL);
    assertThat(commitInfo).committer().date().isEqualTo(commitInfo.author.date);
    assertThat(commitInfo).committer().tz().isEqualTo(commitInfo.author.tz);
  }

  private static AuditLogFormatter getAuditLogFormatter() {
    return AuditLogFormatter.create(
        GroupRebuilderTest::getAccount, GroupRebuilderTest::getGroup, SERVER_ID);
  }

  private static Optional<Account> getAccount(Account.Id id) {
    Account account = new Account(id, TimeUtil.nowTs());
    account.setFullName("Account " + id);
    return Optional.of(account);
  }

  private static Optional<GroupDescription.Basic> getGroup(AccountGroup.UUID uuid) {
    GroupDescription.Basic group =
        new GroupDescription.Basic() {
          @Override
          public AccountGroup.UUID getGroupUUID() {
            return uuid;
          }

          @Override
          public String getName() {
            return "Group " + uuid;
          }

          @Nullable
          @Override
          public String getEmailAddress() {
            return null;
          }

          @Nullable
          @Override
          public String getUrl() {
            return null;
          }
        };
    return Optional.of(group);
  }
}
