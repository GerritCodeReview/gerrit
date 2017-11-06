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
import static com.google.gerrit.server.group.db.GroupBundle.builder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.testing.GerritBaseTests;
import com.google.gerrit.testing.TestTimeUtil;
import java.sql.Timestamp;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GroupRebuilderTest extends GerritBaseTests {
  private static final TimeZone TZ = TimeZone.getTimeZone("America/Los_Angeles");

  private AtomicInteger idCounter;
  private Repository repo;
  private GroupRebuilder rebuilder;

  @Before
  public void setUp() {
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
    idCounter = new AtomicInteger();
    repo = new InMemoryRepository(new DfsRepositoryDescription(AllUsersNameProvider.DEFAULT));
    rebuilder =
        new GroupRebuilder(
            () -> new PersonIdent("Gerrit Server", "noreply@gerrit.com", TimeUtil.nowTs(), TZ),
            new AllUsersName(AllUsersNameProvider.DEFAULT),
            (project, repo, batch) ->
                new MetaDataUpdate(GitReferenceUpdated.DISABLED, project, repo, batch),
            (id, ident) ->
                new PersonIdent(
                    "Account " + id, id + "@example.com", ident.getWhen(), ident.getTimeZone()),
            id -> String.format("Account %s <%s@example.com>", id, id),
            uuid -> "Group " + uuid);
  }

  @After
  public void tearDown() {
    TestTimeUtil.useSystemTime();
  }

  @Test
  public void minimalGroupFields() throws Exception {
    AccountGroup g = newGroup("a");
    GroupBundle b = builder().group(g).build();

    rebuilder.rebuild(repo, b);

    assertThat(reload(g)).isEqualTo(b.toInternalGroup());
    assertThat(log(g)).containsExactly("Create group");
  }

  @Test
  public void allGroupFields() throws Exception {
    AccountGroup g = newGroup("a");
    g.setDescription("Description");
    g.setOwnerGroupUUID(new AccountGroup.UUID("owner"));
    g.setVisibleToAll(true);
    GroupBundle b = builder().group(g).build();

    rebuilder.rebuild(repo, b);

    assertThat(reload(g)).isEqualTo(b.toInternalGroup());
    assertThat(log(g)).containsExactly("Create group");
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

    rebuilder.rebuild(repo, b);

    assertThat(reload(g)).isEqualTo(b.toInternalGroup());
    assertThat(log(g))
        .containsExactly(
            "Create group",
            "Update group\n"
                + "\n"
                + "Add: Account 1 <1@example.com>\n"
                + "Add: Account 2 <2@example.com>\n"
                + "Add-group: Group x\n"
                + "Add-group: Group y")
        .inOrder();
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

    rebuilder.rebuild(repo, b);

    assertThat(reload(g)).isEqualTo(b.toInternalGroup());
    assertThat(log(g))
        .containsExactly(
            "Create group",
            "Update group\n\nAdd: Account 2 <2@example.com>",
            "Update group\n\nAdd: Account 1 <1@example.com>",
            "Update group\n\nRemove: Account 2 <2@example.com>")
        .inOrder();
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

    rebuilder.rebuild(repo, b);

    assertThat(reload(g)).isEqualTo(b.toInternalGroup());
    assertThat(log(g))
        .containsExactly(
            "Create group",
            "Update group\n\nAdd: Account 1 <1@example.com>",
            "Update group\n\nRemove: Account 1 <1@example.com>",
            "Update group\n\nAdd: Account 2 <2@example.com>")
        .inOrder();
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

    rebuilder.rebuild(repo, b);

    assertThat(reload(g)).isEqualTo(b.toInternalGroup());
    assertThat(log(g))
        .containsExactly(
            "Create group",
            "Update group\n\nAdd: Account 1 <1@example.com>",
            "Update group\n\nAdd: Account 2 <2@example.com>\nAdd: Account 3 <3@example.com>")
        .inOrder();
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

    rebuilder.rebuild(repo, b);

    assertThat(reload(g)).isEqualTo(b.toInternalGroup());
    assertThat(log(g))
        .containsExactly(
            "Create group",
            "Update group\n\nAdd-group: Group y",
            "Update group\n\nAdd-group: Group x",
            "Update group\n\nRemove-group: Group y")
        .inOrder();
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

    rebuilder.rebuild(repo, b);

    assertThat(reload(g)).isEqualTo(b.toInternalGroup());
    assertThat(log(g))
        .containsExactly(
            "Create group",
            "Update group\n\nAdd-group: Group x",
            "Update group\n\nAdd-group: Group y\nAdd-group: Group z")
        .inOrder();
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

    rebuilder.rebuild(repo, b);

    assertThat(reload(g)).isEqualTo(b.toInternalGroup());
    assertThat(log(g))
        .containsExactly(
            "Create group",
            "Update group\n"
                + "\n"
                + "Add: Account 1 <1@example.com>\n"
                + "Add: Account 2 <2@example.com>\n"
                + "Add: Account 3 <3@example.com>",
            "Update group\n\nRemove: Account 3 <3@example.com>",
            "Update group\n"
                + "\n"
                + "Add-group: Group x\n"
                + "Add-group: Group y\n"
                + "Add-group: Group z",
            "Update group\n\nRemove-group: Group z")
        .inOrder();
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

    rebuilder.rebuild(repo, b);

    assertThat(reload(g)).isEqualTo(b.toInternalGroup());
    assertThat(log(g))
        .containsExactly(
            "Create group",
            "Update group\n"
                + "\n"
                + "Add: Account 1 <1@example.com>\n"
                + "Add: Account 3 <3@example.com>",
            "Update group\n\nAdd-group: Group x\nAdd-group: Group z",
            "Update group\n\nAdd: Account 2 <2@example.com>",
            "Update group\n\nAdd-group: Group y")
        .inOrder();
  }

  private InternalGroup reload(AccountGroup g) throws Exception {
    return GroupConfig.loadForGroup(repo, g.getGroupUUID()).getLoadedGroup().get();
  }

  private AccountGroup newGroup(String name) {
    int id = idCounter.incrementAndGet();
    return new AccountGroup(
        new AccountGroup.NameKey(name),
        new AccountGroup.Id(id),
        new AccountGroup.UUID(name + "-" + id),
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

  private ImmutableList<String> log(AccountGroup g) throws Exception {
    try (RevWalk rw = new RevWalk(repo)) {
      Ref ref = repo.exactRef(RefNames.refsGroups(g.getGroupUUID()));
      if (ref == null) {
        return ImmutableList.of();
      }
      rw.sort(RevSort.REVERSE);
      rw.setRetainBody(true);
      rw.markStart(rw.parseCommit(ref.getObjectId()));
      return Streams.stream(rw).map(RevCommit::getFullMessage).collect(toImmutableList());
    }
  }
}
