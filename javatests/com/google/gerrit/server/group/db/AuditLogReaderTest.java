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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.server.account.GroupUUID;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.CommitUtil;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.group.InternalGroup;
import java.sql.Timestamp;
import java.util.Set;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link AuditLogReader}. */
public final class AuditLogReaderTest extends AbstractGroupTest {
  private Repository allUsersRepo;
  private AuditLogReader auditLogReader;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    allUsersRepo = repoManager.createRepository(allUsersName);
    auditLogReader = new AuditLogReader(SERVER_ID, repoManager, allUsersName);
  }

  @After
  public void tearDown() {
    allUsersRepo.close();
  }

  @Test
  public void createGroupAsUserIdent() throws Exception {
    createGroupAsUser(1, "test-group");
  }

  @Test
  public void createGroupAsServerIdent() throws Exception {
    createGroup(1, "test-group", serverIdent, null);
  }

  @Test
  public void addAndRemoveMember() throws Exception {
    InternalGroup group = createGroupAsUser(1, "test-group");
    AccountGroup.UUID uuid = group.getGroupUUID();

    AccountGroupMemberAudit expAudit1 =
        createExpMemberAudit(group.getId(), userId, userId, getTipTimestamp(allUsersRepo, uuid));
    assertThat(auditLogReader.getMembersAudit(uuid)).containsExactly(expAudit1);

    // User adds account 100002 to the group.
    Account.Id id = new Account.Id(100002);
    addMembers(uuid, ImmutableSet.of(id));

    AccountGroupMemberAudit expAudit2 =
        createExpMemberAudit(group.getId(), id, userId, getTipTimestamp(allUsersRepo, uuid));
    assertTipCommit(allUsersRepo, uuid, "Update group\n\nAdd: Account 100002 <100002@server-id>");
    assertThat(auditLogReader.getMembersAudit(uuid)).containsExactly(expAudit1, expAudit2);

    // User removes account 100002 from the group.
    removeMembers(uuid, ImmutableSet.of(id));
    assertTipCommit(
        allUsersRepo, uuid, "Update group\n\nRemove: Account 100002 <100002@server-id>");

    expAudit2.removed(userId, getTipTimestamp(allUsersRepo, uuid));
    assertThat(auditLogReader.getMembersAudit(uuid)).containsExactly(expAudit1, expAudit2);
  }

  @Test
  public void addMultiMembers() throws Exception {
    InternalGroup group = createGroupAsUser(1, "test-group");
    AccountGroup.Id groupId = group.getId();
    AccountGroup.UUID uuid = group.getGroupUUID();

    AccountGroupMemberAudit expAudit1 =
        createExpMemberAudit(groupId, userId, userId, getTipTimestamp(allUsersRepo, uuid));
    assertThat(auditLogReader.getMembersAudit(uuid)).containsExactly(expAudit1);

    Account.Id id1 = new Account.Id(100002);
    Account.Id id2 = new Account.Id(100003);
    addMembers(uuid, ImmutableSet.of(id1, id2));

    AccountGroupMemberAudit expAudit2 =
        createExpMemberAudit(groupId, id1, userId, getTipTimestamp(allUsersRepo, uuid));
    AccountGroupMemberAudit expAudit3 =
        createExpMemberAudit(groupId, id2, userId, getTipTimestamp(allUsersRepo, uuid));

    assertTipCommit(
        allUsersRepo,
        uuid,
        "Update group\n"
            + "\n"
            + "Add: Account 100002 <100002@server-id>\n"
            + "Add: Account 100003 <100003@server-id>");
    assertThat(auditLogReader.getMembersAudit(uuid))
        .containsExactly(expAudit1, expAudit2, expAudit3);
  }

  @Test
  public void addAndRemoveSubgroups() throws Exception {
    InternalGroup group = createGroupAsUser(1, "test-group");
    AccountGroup.UUID uuid = group.getGroupUUID();

    InternalGroup subgroup = createGroupAsUser(2, "test-group-2");
    AccountGroup.UUID subgroupUuid = subgroup.getGroupUUID();

    addSubgroups(uuid, ImmutableSet.of(subgroupUuid));
    assertTipCommit(
        allUsersRepo, uuid, String.format("Update group\n\nAdd-group: Group <%s>", subgroupUuid));

    AccountGroupByIdAud expAudit =
        createExpGroupAudit(
            group.getId(), subgroupUuid, userId, getTipTimestamp(allUsersRepo, uuid));
    assertThat(auditLogReader.getSubgroupsAudit(uuid)).containsExactly(expAudit);

    removeSubgroups(uuid, ImmutableSet.of(subgroupUuid));
    assertTipCommit(
        allUsersRepo,
        uuid,
        String.format("Update group\n\nRemove-group: Group <%s>", subgroupUuid));

    expAudit.removed(userId, getTipTimestamp(allUsersRepo, uuid));
    assertThat(auditLogReader.getSubgroupsAudit(uuid)).containsExactly(expAudit);
  }

  @Test
  public void addMultiSubgroups() throws Exception {
    InternalGroup group = createGroupAsUser(1, "test-group");
    AccountGroup.UUID uuid = group.getGroupUUID();

    InternalGroup subgroup1 = createGroupAsUser(2, "test-group-2");
    InternalGroup subgroup2 = createGroupAsUser(3, "test-group-3");
    AccountGroup.UUID subgroupUuid1 = subgroup1.getGroupUUID();
    AccountGroup.UUID subgroupUuid2 = subgroup2.getGroupUUID();

    addSubgroups(uuid, ImmutableSet.of(subgroupUuid1, subgroupUuid2));

    assertTipCommit(
        allUsersRepo,
        uuid,
        "Update group\n"
            + "\n"
            + String.format("Add-group: Group <%s>\n", subgroupUuid1)
            + String.format("Add-group: Group <%s>", subgroupUuid2));

    AccountGroupByIdAud expAudit1 =
        createExpGroupAudit(
            group.getId(), subgroupUuid1, userId, getTipTimestamp(allUsersRepo, uuid));
    AccountGroupByIdAud expAudit2 =
        createExpGroupAudit(
            group.getId(), subgroupUuid2, userId, getTipTimestamp(allUsersRepo, uuid));
    assertThat(auditLogReader.getSubgroupsAudit(uuid)).containsExactly(expAudit1, expAudit2);
  }

  @Test
  public void addAndRemoveMembersAndSubgroups() throws Exception {
    InternalGroup group = createGroupAsUser(1, "test-group");
    AccountGroup.Id groupId = group.getId();
    AccountGroup.UUID uuid = group.getGroupUUID();
    AccountGroupMemberAudit expMemberAudit =
        createExpMemberAudit(groupId, userId, userId, getTipTimestamp(allUsersRepo, uuid));
    assertThat(auditLogReader.getMembersAudit(uuid)).containsExactly(expMemberAudit);

    Account.Id id1 = new Account.Id(100002);
    Account.Id id2 = new Account.Id(100003);
    Account.Id id3 = new Account.Id(100004);
    InternalGroup subgroup1 = createGroupAsUser(2, "test-group-2");
    InternalGroup subgroup2 = createGroupAsUser(3, "test-group-3");
    InternalGroup subgroup3 = createGroupAsUser(4, "test-group-4");
    AccountGroup.UUID subgroupUuid1 = subgroup1.getGroupUUID();
    AccountGroup.UUID subgroupUuid2 = subgroup2.getGroupUUID();
    AccountGroup.UUID subgroupUuid3 = subgroup3.getGroupUUID();

    // Add two accounts.
    addMembers(uuid, ImmutableSet.of(id1, id2));
    assertTipCommit(
        allUsersRepo,
        uuid,
        "Update group\n"
            + "\n"
            + String.format("Add: Account %s <%s@server-id>\n", id1, id1)
            + String.format("Add: Account %s <%s@server-id>", id2, id2));
    AccountGroupMemberAudit expMemberAudit1 =
        createExpMemberAudit(groupId, id1, userId, getTipTimestamp(allUsersRepo, uuid));
    AccountGroupMemberAudit expMemberAudit2 =
        createExpMemberAudit(groupId, id2, userId, getTipTimestamp(allUsersRepo, uuid));
    assertThat(auditLogReader.getMembersAudit(uuid))
        .containsExactly(expMemberAudit, expMemberAudit1, expMemberAudit2);

    // Add one subgroup.
    addSubgroups(uuid, ImmutableSet.of(subgroupUuid1));
    assertTipCommit(
        allUsersRepo, uuid, String.format("Update group\n\nAdd-group: Group <%s>", subgroupUuid1));
    AccountGroupByIdAud expGroupAudit1 =
        createExpGroupAudit(
            group.getId(), subgroupUuid1, userId, getTipTimestamp(allUsersRepo, uuid));
    assertThat(auditLogReader.getSubgroupsAudit(uuid)).containsExactly(expGroupAudit1);

    // Remove one account.
    removeMembers(uuid, ImmutableSet.of(id2));
    assertTipCommit(
        allUsersRepo,
        uuid,
        String.format("Update group\n\nRemove: Account %s <%s@server-id>", id2, id2));
    expMemberAudit2.removed(userId, getTipTimestamp(allUsersRepo, uuid));
    assertThat(auditLogReader.getMembersAudit(uuid))
        .containsExactly(expMemberAudit, expMemberAudit1, expMemberAudit2);

    // Add two subgroups.
    addSubgroups(uuid, ImmutableSet.of(subgroupUuid2, subgroupUuid3));
    assertTipCommit(
        allUsersRepo,
        uuid,
        "Update group\n"
            + "\n"
            + String.format("Add-group: Group <%s>\n", subgroupUuid2)
            + String.format("Add-group: Group <%s>", subgroupUuid3));
    AccountGroupByIdAud expGroupAudit2 =
        createExpGroupAudit(
            group.getId(), subgroupUuid2, userId, getTipTimestamp(allUsersRepo, uuid));
    AccountGroupByIdAud expGroupAudit3 =
        createExpGroupAudit(
            group.getId(), subgroupUuid3, userId, getTipTimestamp(allUsersRepo, uuid));
    assertThat(auditLogReader.getSubgroupsAudit(uuid))
        .containsExactly(expGroupAudit1, expGroupAudit2, expGroupAudit3);

    // Add two account, including a removed account.
    addMembers(uuid, ImmutableSet.of(id2, id3));
    assertTipCommit(
        allUsersRepo,
        uuid,
        "Update group\n"
            + "\n"
            + String.format("Add: Account %s <%s@server-id>\n", id2, id2)
            + String.format("Add: Account %s <%s@server-id>", id3, id3));
    AccountGroupMemberAudit expMemberAudit4 =
        createExpMemberAudit(groupId, id2, userId, getTipTimestamp(allUsersRepo, uuid));
    AccountGroupMemberAudit expMemberAudit3 =
        createExpMemberAudit(groupId, id3, userId, getTipTimestamp(allUsersRepo, uuid));
    assertThat(auditLogReader.getMembersAudit(uuid))
        .containsExactly(
            expMemberAudit, expMemberAudit1, expMemberAudit2, expMemberAudit4, expMemberAudit3);

    // Remove two subgroups.
    removeSubgroups(uuid, ImmutableSet.of(subgroupUuid1, subgroupUuid3));
    assertTipCommit(
        allUsersRepo,
        uuid,
        "Update group\n"
            + "\n"
            + String.format("Remove-group: Group <%s>\n", subgroupUuid1)
            + String.format("Remove-group: Group <%s>", subgroupUuid3));
    expGroupAudit1.removed(userId, getTipTimestamp(allUsersRepo, uuid));
    expGroupAudit3.removed(userId, getTipTimestamp(allUsersRepo, uuid));
    assertThat(auditLogReader.getSubgroupsAudit(uuid))
        .containsExactly(expGroupAudit1, expGroupAudit2, expGroupAudit3);

    // Add back one removed subgroup.
    addSubgroups(uuid, ImmutableSet.of(subgroupUuid1));
    AccountGroupByIdAud expGroupAudit4 =
        createExpGroupAudit(
            group.getId(), subgroupUuid1, userId, getTipTimestamp(allUsersRepo, uuid));
    assertThat(auditLogReader.getSubgroupsAudit(uuid))
        .containsExactly(expGroupAudit1, expGroupAudit2, expGroupAudit3, expGroupAudit4);
  }

  private InternalGroup createGroupAsUser(int next, String groupName) throws Exception {
    return createGroup(next, groupName, userIdent, userId);
  }

  private InternalGroup createGroup(
      int next, String groupName, PersonIdent authorIdent, Account.Id authorId) throws Exception {
    AccountGroup.UUID uuid = GroupUUID.make(groupName, serverIdent);
    InternalGroupCreation groupCreation =
        InternalGroupCreation.builder()
            .setGroupUUID(uuid)
            .setNameKey(new AccountGroup.NameKey(groupName))
            .setId(new AccountGroup.Id(next))
            .setCreatedOn(TimeUtil.nowTs())
            .build();
    InternalGroupUpdate groupUpdate =
        authorIdent.equals(serverIdent)
            ? InternalGroupUpdate.builder().setDescription("Groups").build()
            : InternalGroupUpdate.builder()
                .setDescription("Groups")
                .setMemberModification(members -> ImmutableSet.of(authorId))
                .build();

    GroupConfig groupConfig = GroupConfig.createForNewGroup(allUsersRepo, groupCreation);
    groupConfig.setGroupUpdate(
        groupUpdate, AbstractGroupTest::getAccountNameEmail, AbstractGroupTest::getGroupName);

    RevCommit commit = groupConfig.commit(createMetaDataUpdate(authorIdent));
    assertCreateGroup(authorIdent, uuid, commit);
    return groupConfig
        .getLoadedGroup()
        .orElseThrow(() -> new IllegalStateException("create group failed"));
  }

  private void assertCreateGroup(PersonIdent authorIdent, AccountGroup.UUID uuid, RevCommit commit)
      throws Exception {
    if (authorIdent.equals(serverIdent)) {
      assertThat(auditLogReader.getMembersAudit(uuid)).hasSize(0);
      assertServerCommit(CommitUtil.toCommitInfo(commit), "Create group");
    } else {
      System.out.println(
          commit.getFullMessage()
              + "\n"
              + commit.getAuthorIdent()
              + "\n"
              + commit.getCommitterIdent());
      assertThat(auditLogReader.getMembersAudit(uuid)).hasSize(1);
      assertCommit(
          CommitUtil.toCommitInfo(commit),
          String.format("Create group\n\nAdd: Account %s <%s@%s>", userId, userId, SERVER_ID),
          getAccountName(userId),
          getAccountEmail(userId));
    }
  }

  private InternalGroup updateGroup(AccountGroup.UUID uuid, InternalGroupUpdate groupUpdate)
      throws Exception {
    GroupConfig groupConfig = GroupConfig.loadForGroup(allUsersRepo, uuid);
    groupConfig.setGroupUpdate(
        groupUpdate, AbstractGroupTest::getAccountNameEmail, AbstractGroupTest::getGroupName);

    MetaDataUpdate md =
        new MetaDataUpdate(GitReferenceUpdated.DISABLED, allUsersName, allUsersRepo);
    md.getCommitBuilder().setAuthor(userIdent);
    md.getCommitBuilder().setCommitter(serverIdent);

    groupConfig.commit(md);
    return groupConfig
        .getLoadedGroup()
        .orElseThrow(() -> new IllegalStateException("updated group failed"));
  }

  private InternalGroup addMembers(AccountGroup.UUID groupUuid, Set<Account.Id> ids)
      throws Exception {
    InternalGroupUpdate update =
        InternalGroupUpdate.builder()
            .setMemberModification(memberIds -> Sets.union(memberIds, ids))
            .build();
    return updateGroup(groupUuid, update);
  }

  private InternalGroup removeMembers(AccountGroup.UUID groupUuid, Set<Account.Id> ids)
      throws Exception {
    InternalGroupUpdate update =
        InternalGroupUpdate.builder()
            .setMemberModification(memberIds -> Sets.difference(memberIds, ids))
            .build();
    return updateGroup(groupUuid, update);
  }

  private InternalGroup addSubgroups(AccountGroup.UUID groupUuid, Set<AccountGroup.UUID> uuids)
      throws Exception {
    InternalGroupUpdate update =
        InternalGroupUpdate.builder()
            .setSubgroupModification(memberIds -> Sets.union(memberIds, uuids))
            .build();
    return updateGroup(groupUuid, update);
  }

  private InternalGroup removeSubgroups(AccountGroup.UUID groupUuid, Set<AccountGroup.UUID> uuids)
      throws Exception {
    InternalGroupUpdate update =
        InternalGroupUpdate.builder()
            .setSubgroupModification(memberIds -> Sets.difference(memberIds, uuids))
            .build();
    return updateGroup(groupUuid, update);
  }

  private MetaDataUpdate createMetaDataUpdate(PersonIdent authorIdent) {
    MetaDataUpdate md =
        new MetaDataUpdate(GitReferenceUpdated.DISABLED, allUsersName, allUsersRepo);
    md.getCommitBuilder().setAuthor(authorIdent);
    md.getCommitBuilder().setCommitter(serverIdent); // Committer is always the server identity.
    return md;
  }

  private AccountGroupMemberAudit createExpMemberAudit(
      AccountGroup.Id groupId, Account.Id id, Account.Id addedBy, Timestamp addedOn) {
    return new AccountGroupMemberAudit(
        new AccountGroupMemberAudit.Key(id, groupId, addedOn), addedBy);
  }

  private AccountGroupByIdAud createExpGroupAudit(
      AccountGroup.Id groupId, AccountGroup.UUID uuid, Account.Id addedBy, Timestamp addedOn) {
    return new AccountGroupByIdAud(new AccountGroupByIdAud.Key(groupId, uuid, addedOn), addedBy);
  }
}
