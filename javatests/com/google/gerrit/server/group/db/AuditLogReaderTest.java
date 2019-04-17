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
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.server.account.GroupUUID;
import com.google.gerrit.server.group.InternalGroup;
import java.sql.Timestamp;
import java.util.Set;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link AuditLogReader}. */
public final class AuditLogReaderTest extends AbstractGroupTest {

  private AuditLogReader auditLogReader;

  @Before
  public void setUp() throws Exception {
    auditLogReader = new AuditLogReader(SERVER_ID, allUsersName);
  }

  @Test
  public void createGroupAsUserIdent() throws Exception {
    InternalGroup group = createGroupAsUser(1, "test-group");
    AccountGroup.UUID uuid = group.getGroupUUID();

    AccountGroupMemberAudit expAudit =
        createExpMemberAudit(group.getId(), userId, userId, getTipTimestamp(uuid));
    assertThat(auditLogReader.getMembersAudit(allUsersRepo, uuid)).containsExactly(expAudit);
  }

  @Test
  public void createGroupAsServerIdent() throws Exception {
    InternalGroup group = createGroup(1, "test-group", serverIdent, null);
    assertThat(auditLogReader.getMembersAudit(allUsersRepo, group.getGroupUUID())).hasSize(0);
  }

  @Test
  public void addAndRemoveMember() throws Exception {
    InternalGroup group = createGroupAsUser(1, "test-group");
    AccountGroup.UUID uuid = group.getGroupUUID();

    AccountGroupMemberAudit expAudit1 =
        createExpMemberAudit(group.getId(), userId, userId, getTipTimestamp(uuid));
    assertThat(auditLogReader.getMembersAudit(allUsersRepo, uuid)).containsExactly(expAudit1);

    // User adds account 100002 to the group.
    Account.Id id = new Account.Id(100002);
    addMembers(uuid, ImmutableSet.of(id));

    AccountGroupMemberAudit expAudit2 =
        createExpMemberAudit(group.getId(), id, userId, getTipTimestamp(uuid));
    assertThat(auditLogReader.getMembersAudit(allUsersRepo, uuid))
        .containsExactly(expAudit1, expAudit2)
        .inOrder();

    // User removes account 100002 from the group.
    removeMembers(uuid, ImmutableSet.of(id));

    expAudit2.removed(userId, getTipTimestamp(uuid));
    assertThat(auditLogReader.getMembersAudit(allUsersRepo, uuid))
        .containsExactly(expAudit1, expAudit2)
        .inOrder();
  }

  @Test
  public void addMultiMembers() throws Exception {
    InternalGroup group = createGroupAsUser(1, "test-group");
    AccountGroup.Id groupId = group.getId();
    AccountGroup.UUID uuid = group.getGroupUUID();

    AccountGroupMemberAudit expAudit1 =
        createExpMemberAudit(groupId, userId, userId, getTipTimestamp(uuid));
    assertThat(auditLogReader.getMembersAudit(allUsersRepo, uuid)).containsExactly(expAudit1);

    Account.Id id1 = new Account.Id(100002);
    Account.Id id2 = new Account.Id(100003);
    addMembers(uuid, ImmutableSet.of(id1, id2));

    AccountGroupMemberAudit expAudit2 =
        createExpMemberAudit(groupId, id1, userId, getTipTimestamp(uuid));
    AccountGroupMemberAudit expAudit3 =
        createExpMemberAudit(groupId, id2, userId, getTipTimestamp(uuid));

    assertThat(auditLogReader.getMembersAudit(allUsersRepo, uuid))
        .containsExactly(expAudit1, expAudit2, expAudit3)
        .inOrder();
  }

  @Test
  public void addAndRemoveSubgroups() throws Exception {
    InternalGroup group = createGroupAsUser(1, "test-group");
    AccountGroup.UUID uuid = group.getGroupUUID();

    InternalGroup subgroup = createGroupAsUser(2, "test-group-2");
    AccountGroup.UUID subgroupUuid = subgroup.getGroupUUID();

    addSubgroups(uuid, ImmutableSet.of(subgroupUuid));

    AccountGroupByIdAud expAudit =
        createExpGroupAudit(group.getId(), subgroupUuid, userId, getTipTimestamp(uuid));
    assertThat(auditLogReader.getSubgroupsAudit(allUsersRepo, uuid)).containsExactly(expAudit);

    removeSubgroups(uuid, ImmutableSet.of(subgroupUuid));

    expAudit.removed(userId, getTipTimestamp(uuid));
    assertThat(auditLogReader.getSubgroupsAudit(allUsersRepo, uuid)).containsExactly(expAudit);
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

    AccountGroupByIdAud expAudit1 =
        createExpGroupAudit(group.getId(), subgroupUuid1, userId, getTipTimestamp(uuid));
    AccountGroupByIdAud expAudit2 =
        createExpGroupAudit(group.getId(), subgroupUuid2, userId, getTipTimestamp(uuid));
    assertThat(auditLogReader.getSubgroupsAudit(allUsersRepo, uuid))
        .containsExactly(expAudit1, expAudit2)
        .inOrder();
  }

  @Test
  public void addAndRemoveMembersAndSubgroups() throws Exception {
    InternalGroup group = createGroupAsUser(1, "test-group");
    AccountGroup.Id groupId = group.getId();
    AccountGroup.UUID uuid = group.getGroupUUID();
    AccountGroupMemberAudit expMemberAudit =
        createExpMemberAudit(groupId, userId, userId, getTipTimestamp(uuid));
    assertThat(auditLogReader.getMembersAudit(allUsersRepo, uuid)).containsExactly(expMemberAudit);

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
    AccountGroupMemberAudit expMemberAudit1 =
        createExpMemberAudit(groupId, id1, userId, getTipTimestamp(uuid));
    AccountGroupMemberAudit expMemberAudit2 =
        createExpMemberAudit(groupId, id2, userId, getTipTimestamp(uuid));
    assertThat(auditLogReader.getMembersAudit(allUsersRepo, uuid))
        .containsExactly(expMemberAudit, expMemberAudit1, expMemberAudit2)
        .inOrder();

    // Add one subgroup.
    addSubgroups(uuid, ImmutableSet.of(subgroupUuid1));
    AccountGroupByIdAud expGroupAudit1 =
        createExpGroupAudit(group.getId(), subgroupUuid1, userId, getTipTimestamp(uuid));
    assertThat(auditLogReader.getSubgroupsAudit(allUsersRepo, uuid))
        .containsExactly(expGroupAudit1);

    // Remove one account.
    removeMembers(uuid, ImmutableSet.of(id2));
    expMemberAudit2.removed(userId, getTipTimestamp(uuid));
    assertThat(auditLogReader.getMembersAudit(allUsersRepo, uuid))
        .containsExactly(expMemberAudit, expMemberAudit1, expMemberAudit2)
        .inOrder();

    // Add two subgroups.
    addSubgroups(uuid, ImmutableSet.of(subgroupUuid2, subgroupUuid3));
    AccountGroupByIdAud expGroupAudit2 =
        createExpGroupAudit(group.getId(), subgroupUuid2, userId, getTipTimestamp(uuid));
    AccountGroupByIdAud expGroupAudit3 =
        createExpGroupAudit(group.getId(), subgroupUuid3, userId, getTipTimestamp(uuid));
    assertThat(auditLogReader.getSubgroupsAudit(allUsersRepo, uuid))
        .containsExactly(expGroupAudit1, expGroupAudit2, expGroupAudit3)
        .inOrder();

    // Add two account, including a removed account.
    addMembers(uuid, ImmutableSet.of(id2, id3));
    AccountGroupMemberAudit expMemberAudit4 =
        createExpMemberAudit(groupId, id2, userId, getTipTimestamp(uuid));
    AccountGroupMemberAudit expMemberAudit3 =
        createExpMemberAudit(groupId, id3, userId, getTipTimestamp(uuid));
    assertThat(auditLogReader.getMembersAudit(allUsersRepo, uuid))
        .containsExactly(
            expMemberAudit, expMemberAudit1, expMemberAudit2, expMemberAudit4, expMemberAudit3)
        .inOrder();

    // Remove two subgroups.
    removeSubgroups(uuid, ImmutableSet.of(subgroupUuid1, subgroupUuid3));
    expGroupAudit1.removed(userId, getTipTimestamp(uuid));
    expGroupAudit3.removed(userId, getTipTimestamp(uuid));
    assertThat(auditLogReader.getSubgroupsAudit(allUsersRepo, uuid))
        .containsExactly(expGroupAudit1, expGroupAudit2, expGroupAudit3)
        .inOrder();

    // Add back one removed subgroup.
    addSubgroups(uuid, ImmutableSet.of(subgroupUuid1));
    AccountGroupByIdAud expGroupAudit4 =
        createExpGroupAudit(group.getId(), subgroupUuid1, userId, getTipTimestamp(uuid));
    assertThat(auditLogReader.getSubgroupsAudit(allUsersRepo, uuid))
        .containsExactly(expGroupAudit1, expGroupAudit2, expGroupAudit3, expGroupAudit4)
        .inOrder();
  }

  private InternalGroup createGroupAsUser(int next, String groupName) throws Exception {
    return createGroup(next, groupName, userIdent, userId);
  }

  private InternalGroup createGroup(
      int next, String groupName, PersonIdent authorIdent, Account.Id authorId) throws Exception {
    InternalGroupCreation groupCreation =
        InternalGroupCreation.builder()
            .setGroupUUID(GroupUUID.make(groupName, serverIdent))
            .setNameKey(new AccountGroup.NameKey(groupName))
            .setId(new AccountGroup.Id(next))
            .build();
    InternalGroupUpdate groupUpdate =
        authorIdent.equals(serverIdent)
            ? InternalGroupUpdate.builder().setDescription("Groups").build()
            : InternalGroupUpdate.builder()
                .setDescription("Groups")
                .setMemberModification(members -> ImmutableSet.of(authorId))
                .build();

    GroupConfig groupConfig =
        GroupConfig.createForNewGroup(allUsersName, allUsersRepo, groupCreation);
    groupConfig.setGroupUpdate(groupUpdate, getAuditLogFormatter());

    groupConfig.commit(createMetaDataUpdate(authorIdent));
    return groupConfig
        .getLoadedGroup()
        .orElseThrow(() -> new IllegalStateException("create group failed"));
  }

  private void updateGroup(AccountGroup.UUID uuid, InternalGroupUpdate groupUpdate)
      throws Exception {
    GroupConfig groupConfig = GroupConfig.loadForGroup(allUsersName, allUsersRepo, uuid);
    groupConfig.setGroupUpdate(groupUpdate, getAuditLogFormatter());
    groupConfig.commit(createMetaDataUpdate(userIdent));
  }

  private void addMembers(AccountGroup.UUID groupUuid, Set<Account.Id> ids) throws Exception {
    InternalGroupUpdate update =
        InternalGroupUpdate.builder()
            .setMemberModification(memberIds -> Sets.union(memberIds, ids))
            .build();
    updateGroup(groupUuid, update);
  }

  private void removeMembers(AccountGroup.UUID groupUuid, Set<Account.Id> ids) throws Exception {
    InternalGroupUpdate update =
        InternalGroupUpdate.builder()
            .setMemberModification(memberIds -> Sets.difference(memberIds, ids))
            .build();
    updateGroup(groupUuid, update);
  }

  private void addSubgroups(AccountGroup.UUID groupUuid, Set<AccountGroup.UUID> uuids)
      throws Exception {
    InternalGroupUpdate update =
        InternalGroupUpdate.builder()
            .setSubgroupModification(memberIds -> Sets.union(memberIds, uuids))
            .build();
    updateGroup(groupUuid, update);
  }

  private void removeSubgroups(AccountGroup.UUID groupUuid, Set<AccountGroup.UUID> uuids)
      throws Exception {
    InternalGroupUpdate update =
        InternalGroupUpdate.builder()
            .setSubgroupModification(memberIds -> Sets.difference(memberIds, uuids))
            .build();
    updateGroup(groupUuid, update);
  }

  private static AccountGroupMemberAudit createExpMemberAudit(
      AccountGroup.Id groupId, Account.Id id, Account.Id addedBy, Timestamp addedOn) {
    return new AccountGroupMemberAudit(AccountGroupMemberAudit.key(id, groupId, addedOn), addedBy);
  }

  private static AccountGroupByIdAud createExpGroupAudit(
      AccountGroup.Id groupId, AccountGroup.UUID uuid, Account.Id addedBy, Timestamp addedOn) {
    return new AccountGroupByIdAud(AccountGroupByIdAud.key(groupId, uuid, addedOn), addedBy);
  }
}
