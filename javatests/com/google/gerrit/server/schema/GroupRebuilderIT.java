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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.extensions.common.testing.CommitInfoSubject.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerId;
import com.google.gerrit.server.config.GerritServerIdProvider;
import com.google.gerrit.server.git.CommitUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.group.db.AuditLogFormatter;
import com.google.gerrit.server.notedb.GroupsMigration;
import com.google.gerrit.testing.GerritBaseTests;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.gerrit.testing.TestTimeUtil.TempClockStep;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class GroupRebuilderIT extends GerritBaseTests {

  private static Config createConfigWithServerId() {
    Config config = new Config();
    config.setString(GerritServerIdProvider.SECTION, null, GerritServerIdProvider.KEY, "1234567");
    return config;
  }

  @Rule
  public InMemoryTestEnvironment testEnv =
      new InMemoryTestEnvironment(GroupRebuilderIT::createConfigWithServerId);

  @Inject private GroupsMigration migration;
  @Inject private GerritApi gApi;
  @Inject private ReviewDb db;
  @Inject private GitRepositoryManager repoManager;
  @Inject private AllUsersName allUsersName;
  @Inject private IdentifiedUser currentUser;
  @Inject private @GerritServerId String serverId;
  @Inject private AccountCache accountCache;
  @Inject private @ServerInitiated AccountsUpdate accountsUpdate;
  @Inject private GroupBackend groupBackend;
  @Inject private GroupBundle.Factory bundleFactory;
  @Inject private @GerritPersonIdent Provider<PersonIdent> serverIdent;

  private GroupRebuilder rebuilder;

  @Before
  public void setup() throws Exception {
    // This test is explicitly testing the migration from ReviewDb to NoteDb, and handles reading
    // from NoteDb manually. It should work regardless of the value of noteDb.groups.write, however.
    assume().that(migration.readFromNoteDb()).isFalse();

    accountsUpdate.update(
        "Set Name for CurrentUser", currentUser.getAccountId(), u -> u.setFullName("current"));

    AuditLogFormatter auditLogFormatter =
        AuditLogFormatter.createBackedBy(accountCache, groupBackend, serverId);
    rebuilder = new GroupRebuilder(serverIdent.get(), allUsersName, auditLogFormatter);
  }

  @Before
  public void setTimeForTesting() {
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
  }

  @After
  public void resetTime() {
    TestTimeUtil.useSystemTime();
  }

  @Test
  public void basicGroupProperties() throws Exception {
    GroupInfo createdGroup = gApi.groups().create("group").get();
    GroupBundle reviewDbBundle =
        GroupBundle.Factory.fromReviewDb(db, new AccountGroup.UUID(createdGroup.id));

    deleteGroupRefs(reviewDbBundle);
    assertMigratedCleanly(rebuild(reviewDbBundle), reviewDbBundle);
  }

  @Test
  public void logFormat() throws Exception {
    AccountInfo user1 = createAccount("user1");
    AccountInfo user2 = createAccount("user2");
    GroupInfo group1 = gApi.groups().create("group1").get();
    GroupInfo group2 = gApi.groups().create("group2").get();

    try (TempClockStep step = TestTimeUtil.freezeClock()) {
      gApi.groups()
          .id(group1.id)
          .addMembers(Integer.toString(user1._accountId), Integer.toString(user2._accountId));
    }
    TimeUtil.nowTs();

    try (TempClockStep step = TestTimeUtil.freezeClock()) {
      gApi.groups().id(group1.id).addGroups(group2.id, SystemGroupBackend.REGISTERED_USERS.get());
    }

    GroupBundle reviewDbBundle =
        GroupBundle.Factory.fromReviewDb(db, new AccountGroup.UUID(group1.id));
    deleteGroupRefs(reviewDbBundle);

    GroupBundle noteDbBundle = rebuild(reviewDbBundle);
    assertMigratedCleanly(noteDbBundle, reviewDbBundle);

    ImmutableList<CommitInfo> log = log(group1);
    assertThat(log).hasSize(4);

    assertThat(log.get(0)).message().isEqualTo("Create group");
    assertThat(log.get(0)).author().name().isEqualTo(serverIdent.get().getName());
    assertThat(log.get(0)).author().email().isEqualTo(serverIdent.get().getEmailAddress());
    assertThat(log.get(0)).author().date().isEqualTo(noteDbBundle.group().getCreatedOn());
    assertThat(log.get(0)).author().tz().isEqualTo(serverIdent.get().getTimeZoneOffset());
    assertThat(log.get(0)).committer().isEqualTo(log.get(0).author);

    assertThat(log.get(1))
        .message()
        .isEqualTo(
            "Update group\n\nAdd: "
                + currentUser.getName()
                + " <"
                + currentUser.getAccountId()
                + "@"
                + serverId
                + ">");
    assertThat(log.get(1)).author().name().isEqualTo(currentUser.getName());
    assertThat(log.get(1)).author().email().isEqualTo(currentUser.getAccountId() + "@" + serverId);
    assertThat(log.get(1)).committer().hasSameDateAs(log.get(1).author);

    assertThat(log.get(2))
        .message()
        .isEqualTo(
            "Update group\n"
                + "\n"
                + ("Add: user1 <" + user1._accountId + "@" + serverId + ">\n")
                + ("Add: user2 <" + user2._accountId + "@" + serverId + ">"));
    assertThat(log.get(2)).author().name().isEqualTo(currentUser.getName());
    assertThat(log.get(2)).author().email().isEqualTo(currentUser.getAccountId() + "@" + serverId);
    assertThat(log.get(2)).committer().hasSameDateAs(log.get(2).author);

    assertThat(log.get(3))
        .message()
        .isEqualTo(
            "Update group\n"
                + "\n"
                + ("Add-group: " + group2.name + " <" + group2.id + ">\n")
                + ("Add-group: Registered Users <global:Registered-Users>"));
    assertThat(log.get(3)).author().name().isEqualTo(currentUser.getName());
    assertThat(log.get(3)).author().email().isEqualTo(currentUser.getAccountId() + "@" + serverId);
    assertThat(log.get(3)).committer().hasSameDateAs(log.get(3).author);
  }

  @Test
  public void unknownGroupUuid() throws Exception {
    GroupInfo group = gApi.groups().create("group").get();

    AccountGroup.UUID subgroupUuid = new AccountGroup.UUID("mybackend:foo");

    AccountGroupById byId =
        new AccountGroupById(
            new AccountGroupById.Key(new AccountGroup.Id(group.groupId), subgroupUuid));
    assertThat(groupBackend.handles(byId.getIncludeUUID())).isFalse();
    db.accountGroupById().insert(Collections.singleton(byId));

    AccountGroupByIdAud audit =
        new AccountGroupByIdAud(byId, currentUser.getAccountId(), TimeUtil.nowTs());
    db.accountGroupByIdAud().insert(Collections.singleton(audit));

    GroupBundle reviewDbBundle =
        GroupBundle.Factory.fromReviewDb(db, new AccountGroup.UUID(group.id));
    deleteGroupRefs(reviewDbBundle);

    GroupBundle noteDbBundle = rebuild(reviewDbBundle);
    assertMigratedCleanly(noteDbBundle, reviewDbBundle);

    ImmutableList<CommitInfo> log = log(group);
    assertThat(log).hasSize(3);

    assertThat(log.get(0)).message().isEqualTo("Create group");
    assertThat(log.get(1))
        .message()
        .isEqualTo(
            "Update group\n\nAdd: "
                + currentUser.getName()
                + " <"
                + currentUser.getAccountId()
                + "@"
                + serverId
                + ">");
    assertThat(log.get(2))
        .message()
        .isEqualTo("Update group\n\nAdd-group: mybackend:foo <mybackend:foo>");
  }

  private void deleteGroupRefs(GroupBundle bundle) throws Exception {
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      String refName = RefNames.refsGroups(bundle.uuid());
      RefUpdate ru = repo.updateRef(refName);
      ru.setForceUpdate(true);
      Ref oldRef = repo.exactRef(refName);
      if (oldRef == null) {
        return;
      }
      ru.setExpectedOldObjectId(oldRef.getObjectId());
      ru.setNewObjectId(ObjectId.zeroId());
      assertThat(ru.delete()).isEqualTo(RefUpdate.Result.FORCED);
    }
  }

  private GroupBundle rebuild(GroupBundle reviewDbBundle) throws Exception {
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      rebuilder.rebuild(repo, reviewDbBundle, null);
      return bundleFactory.fromNoteDb(repo, reviewDbBundle.uuid());
    }
  }

  private void assertMigratedCleanly(GroupBundle noteDbBundle, GroupBundle expectedReviewDbBundle) {
    assertThat(GroupBundle.compareWithAudits(expectedReviewDbBundle, noteDbBundle)).isEmpty();
  }

  private AccountInfo createAccount(String name) throws RestApiException {
    AccountInput accountInput = new AccountInput();
    accountInput.username = name;
    accountInput.name = name;
    return gApi.accounts().create(accountInput).get();
  }

  private ImmutableList<CommitInfo> log(GroupInfo g) throws Exception {
    ImmutableList.Builder<CommitInfo> result = ImmutableList.builder();
    List<Date> commitDates = new ArrayList<>();
    try (Repository repo = repoManager.openRepository(allUsersName);
        RevWalk rw = new RevWalk(repo)) {
      Ref ref = repo.exactRef(RefNames.refsGroups(new AccountGroup.UUID(g.id)));
      if (ref != null) {
        rw.sort(RevSort.REVERSE);
        rw.setRetainBody(true);
        rw.markStart(rw.parseCommit(ref.getObjectId()));
        for (RevCommit c : rw) {
          result.add(CommitUtil.toCommitInfo(c));
          commitDates.add(c.getCommitterIdent().getWhen());
        }
      }
    }
    assertThat(commitDates).named("commit timestamps for %s", result).isOrdered();
    return result.build();
  }
}
