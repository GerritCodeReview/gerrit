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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.server.group.testing.InternalGroupSubject.internalGroups;
import static com.google.gerrit.truth.OptionalSubject.assertThat;
import static org.hamcrest.CoreMatchers.instanceOf;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.testing.InternalGroupSubject;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.GerritBaseTests;
import com.google.gerrit.truth.OptionalSubject;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.Optional;
import java.util.TimeZone;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

public class GroupConfigTest extends GerritBaseTests {
  private Project.NameKey projectName;
  private Repository repository;
  private TestRepository<?> testRepository;
  private final AccountGroup.UUID groupUuid = AccountGroup.uuid("users-XYZ");
  private final AccountGroup.NameKey groupName = AccountGroup.nameKey("users");
  private final AccountGroup.Id groupId = AccountGroup.id(123);
  private final AuditLogFormatter auditLogFormatter =
      AuditLogFormatter.createBackedBy(ImmutableSet.of(), ImmutableSet.of(), "server-id");
  private final TimeZone timeZone = TimeZone.getTimeZone("America/Los_Angeles");

  @Before
  public void setUp() throws Exception {
    projectName = new Project.NameKey("Test Repository");
    repository = new InMemoryRepository(new DfsRepositoryDescription("Test Repository"));
    testRepository = new TestRepository<>(repository);
  }

  @Test
  public void specifiedGroupUuidIsRespectedForNewGroup() throws Exception {
    InternalGroupCreation groupCreation =
        getPrefilledGroupCreationBuilder().setGroupUUID(groupUuid).build();
    createGroup(groupCreation);

    Optional<InternalGroup> group = loadGroup(groupUuid);
    assertThatGroup(group).value().groupUuid().isEqualTo(groupUuid);
  }

  @Test
  public void specifiedNameIsRespectedForNewGroup() throws Exception {
    InternalGroupCreation groupCreation =
        getPrefilledGroupCreationBuilder().setNameKey(groupName).build();
    createGroup(groupCreation);

    Optional<InternalGroup> group = loadGroup(groupCreation.getGroupUUID());
    assertThatGroup(group).value().nameKey().isEqualTo(groupName);
  }

  @Test
  public void nameOfGroupUpdateOverridesGroupCreation() throws Exception {
    AccountGroup.NameKey anotherName = AccountGroup.nameKey("Another name");

    InternalGroupCreation groupCreation =
        getPrefilledGroupCreationBuilder().setNameKey(groupName).build();
    InternalGroupUpdate groupUpdate = InternalGroupUpdate.builder().setName(anotherName).build();
    createGroup(groupCreation, groupUpdate);

    Optional<InternalGroup> group = loadGroup(groupCreation.getGroupUUID());
    assertThatGroup(group).value().nameKey().isEqualTo(anotherName);
  }

  @Test
  public void nameOfNewGroupMustNotBeEmpty() throws Exception {
    InternalGroupCreation groupCreation =
        getPrefilledGroupCreationBuilder().setNameKey(AccountGroup.nameKey("")).build();
    GroupConfig groupConfig = GroupConfig.createForNewGroup(projectName, repository, groupCreation);

    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      exception.expectCause(instanceOf(ConfigInvalidException.class));
      exception.expectMessage("Name of the group " + groupUuid);
      groupConfig.commit(metaDataUpdate);
    }
  }

  @Test
  public void specifiedIdIsRespectedForNewGroup() throws Exception {
    InternalGroupCreation groupCreation = getPrefilledGroupCreationBuilder().setId(groupId).build();
    createGroup(groupCreation);

    Optional<InternalGroup> group = loadGroup(groupCreation.getGroupUUID());
    assertThatGroup(group).value().id().isEqualTo(groupId);
  }

  @Test
  public void idOfNewGroupMustNotBeNegative() throws Exception {
    InternalGroupCreation groupCreation =
        getPrefilledGroupCreationBuilder().setId(AccountGroup.id(-2)).build();
    GroupConfig groupConfig = GroupConfig.createForNewGroup(projectName, repository, groupCreation);

    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      exception.expectCause(instanceOf(ConfigInvalidException.class));
      exception.expectMessage("ID of the group " + groupUuid);
      groupConfig.commit(metaDataUpdate);
    }
  }

  @Test
  public void descriptionDefaultsToNull() throws Exception {
    InternalGroupCreation groupCreation =
        InternalGroupCreation.builder()
            .setGroupUUID(groupUuid)
            .setNameKey(groupName)
            .setId(groupId)
            .build();
    createGroup(groupCreation);

    Optional<InternalGroup> group = loadGroup(groupCreation.getGroupUUID());
    assertThatGroup(group).value().description().isNull();
  }

  @Test
  public void specifiedDescriptionIsRespectedForNewGroup() throws Exception {
    String description = "This is a test group.";

    InternalGroupCreation groupCreation = getPrefilledGroupCreationBuilder().build();
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder().setDescription(description).build();
    createGroup(groupCreation, groupUpdate);

    Optional<InternalGroup> group = loadGroup(groupCreation.getGroupUUID());
    assertThatGroup(group).value().description().isEqualTo(description);
  }

  @Test
  public void emptyDescriptionForNewGroupIsIgnored() throws Exception {
    InternalGroupCreation groupCreation = getPrefilledGroupCreationBuilder().build();
    InternalGroupUpdate groupUpdate = InternalGroupUpdate.builder().setDescription("").build();
    createGroup(groupCreation, groupUpdate);

    Optional<InternalGroup> group = loadGroup(groupCreation.getGroupUUID());
    assertThatGroup(group).value().description().isNull();
  }

  @Test
  public void ownerGroupUuidDefaultsToGroupItself() throws Exception {
    InternalGroupCreation groupCreation =
        InternalGroupCreation.builder()
            .setGroupUUID(groupUuid)
            .setNameKey(groupName)
            .setId(groupId)
            .build();
    createGroup(groupCreation);

    Optional<InternalGroup> group = loadGroup(groupCreation.getGroupUUID());
    assertThatGroup(group).value().ownerGroupUuid().isEqualTo(groupUuid);
  }

  @Test
  public void specifiedOwnerGroupUuidIsRespectedForNewGroup() throws Exception {
    AccountGroup.UUID ownerGroupUuid = AccountGroup.uuid("anotherOwnerUuid");

    InternalGroupCreation groupCreation = getPrefilledGroupCreationBuilder().build();
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder().setOwnerGroupUUID(ownerGroupUuid).build();
    createGroup(groupCreation, groupUpdate);

    Optional<InternalGroup> group = loadGroup(groupCreation.getGroupUUID());
    assertThatGroup(group).value().ownerGroupUuid().isEqualTo(ownerGroupUuid);
  }

  @Test
  public void ownerGroupUuidOfNewGroupMustNotBeEmpty() throws Exception {
    InternalGroupCreation groupCreation = getPrefilledGroupCreationBuilder().build();
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder().setOwnerGroupUUID(AccountGroup.uuid("")).build();
    GroupConfig groupConfig = GroupConfig.createForNewGroup(projectName, repository, groupCreation);
    groupConfig.setGroupUpdate(groupUpdate, auditLogFormatter);

    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      exception.expectCause(instanceOf(ConfigInvalidException.class));
      exception.expectMessage("Owner UUID of the group " + groupUuid);
      groupConfig.commit(metaDataUpdate);
    }
  }

  @Test
  public void visibleToAllDefaultsToFalse() throws Exception {
    InternalGroupCreation groupCreation =
        InternalGroupCreation.builder()
            .setGroupUUID(groupUuid)
            .setNameKey(groupName)
            .setId(groupId)
            .build();
    createGroup(groupCreation);

    Optional<InternalGroup> group = loadGroup(groupCreation.getGroupUUID());
    assertThatGroup(group).value().visibleToAll().isFalse();
  }

  @Test
  public void specifiedVisibleToAllIsRespectedForNewGroup() throws Exception {
    InternalGroupCreation groupCreation = getPrefilledGroupCreationBuilder().build();
    InternalGroupUpdate groupUpdate = InternalGroupUpdate.builder().setVisibleToAll(true).build();
    createGroup(groupCreation, groupUpdate);

    Optional<InternalGroup> group = loadGroup(groupCreation.getGroupUUID());
    assertThatGroup(group).value().visibleToAll().isTrue();
  }

  @Test
  public void createdOnDefaultsToNow() throws Exception {
    // Git timestamps are only precise to the second.
    Timestamp testStart = TimeUtil.truncateToSecond(TimeUtil.nowTs());

    InternalGroupCreation groupCreation =
        InternalGroupCreation.builder()
            .setGroupUUID(groupUuid)
            .setNameKey(groupName)
            .setId(groupId)
            .build();
    createGroup(groupCreation);

    Optional<InternalGroup> group = loadGroup(groupCreation.getGroupUUID());
    assertThatGroup(group).value().createdOn().isAtLeast(testStart);
  }

  @Test
  public void specifiedCreatedOnIsRespectedForNewGroup() throws Exception {
    Timestamp createdOn = toTimestamp(LocalDate.of(2017, Month.DECEMBER, 11).atTime(13, 44, 10));

    InternalGroupCreation groupCreation = getPrefilledGroupCreationBuilder().build();
    InternalGroupUpdate groupUpdate = InternalGroupUpdate.builder().setUpdatedOn(createdOn).build();
    createGroup(groupCreation, groupUpdate);

    Optional<InternalGroup> group = loadGroup(groupCreation.getGroupUUID());
    assertThatGroup(group).value().createdOn().isEqualTo(createdOn);
  }

  @Test
  public void specifiedMembersAreRespectedForNewGroup() throws Exception {
    Account.Id member1 = Account.id(1);
    Account.Id member2 = Account.id(2);

    InternalGroupCreation groupCreation = getPrefilledGroupCreationBuilder().build();
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setMemberModification(members -> ImmutableSet.of(member1, member2))
            .build();
    createGroup(groupCreation, groupUpdate);

    Optional<InternalGroup> group = loadGroup(groupCreation.getGroupUUID());
    assertThatGroup(group).value().members().containsExactly(member1, member2);
  }

  @Test
  public void specifiedSubgroupsAreRespectedForNewGroup() throws Exception {
    AccountGroup.UUID subgroup1 = AccountGroup.uuid("subgroup1");
    AccountGroup.UUID subgroup2 = AccountGroup.uuid("subgroup2");

    InternalGroupCreation groupCreation = getPrefilledGroupCreationBuilder().build();
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setSubgroupModification(subgroups -> ImmutableSet.of(subgroup1, subgroup2))
            .build();
    createGroup(groupCreation, groupUpdate);

    Optional<InternalGroup> group = loadGroup(groupCreation.getGroupUUID());
    assertThatGroup(group).value().subgroups().containsExactly(subgroup1, subgroup2);
  }

  @Test
  public void nameInConfigMayBeUndefined() throws Exception {
    populateGroupConfig(groupUuid, "[group]\n\tid = 42\n\townerGroupUuid = owners\n");

    Optional<InternalGroup> group = loadGroup(groupUuid);
    assertThatGroup(group).value().name().isEmpty();
  }

  @Test
  public void nameInConfigMayBeEmpty() throws Exception {
    populateGroupConfig(groupUuid, "[group]\n\tname=\n\tid = 42\n\townerGroupUuid = owners\n");

    Optional<InternalGroup> group = loadGroup(groupUuid);
    assertThatGroup(group).value().name().isEmpty();
  }

  @Test
  public void idInConfigMustBeDefined() throws Exception {
    populateGroupConfig(groupUuid, "[group]\n\tname = users\n\townerGroupUuid = owners\n");

    exception.expect(ConfigInvalidException.class);
    exception.expectMessage("ID of the group " + groupUuid);
    GroupConfig.loadForGroup(projectName, repository, groupUuid);
  }

  @Test
  public void idInConfigMustNotBeNegative() throws Exception {
    populateGroupConfig(
        groupUuid, "[group]\n\tname = users\n\tid = -5\n\townerGroupUuid = owners\n");

    exception.expect(ConfigInvalidException.class);
    exception.expectMessage("ID of the group " + groupUuid);
    GroupConfig.loadForGroup(projectName, repository, groupUuid);
  }

  @Test
  public void descriptionInConfigMayBeUndefined() throws Exception {
    populateGroupConfig(groupUuid, "[group]\n\tid = 42\n\townerGroupUuid = owners\n");

    Optional<InternalGroup> group = loadGroup(groupUuid);
    assertThatGroup(group).value().description().isNull();
  }

  @Test
  public void descriptionInConfigMayBeEmpty() throws Exception {
    populateGroupConfig(
        groupUuid, "[group]\n\tdescription=\n\tid = 42\n\townerGroupUuid = owners\n");

    Optional<InternalGroup> group = loadGroup(groupUuid);
    assertThatGroup(group).value().description().isNull();
  }

  @Test
  public void ownerGroupUuidInConfigMustBeDefined() throws Exception {
    populateGroupConfig(groupUuid, "[group]\n\tname = users\n\tid = 42\n");

    exception.expect(ConfigInvalidException.class);
    exception.expectMessage("Owner UUID of the group " + groupUuid);
    GroupConfig.loadForGroup(projectName, repository, groupUuid);
  }

  @Test
  public void membersFileNeedNotExist() throws Exception {
    populateGroupConfig(groupUuid, "[group]\n\tname=users\n\tid = 42\n\townerGroupUuid = owners\n");

    Optional<InternalGroup> group = loadGroup(groupUuid);
    assertThatGroup(group).value().members().isEmpty();
  }

  @Test
  public void membersFileMayBeEmpty() throws Exception {
    populateGroupConfig(groupUuid, "[group]\n\tname=users\n\tid = 42\n\townerGroupUuid = owners\n");
    populateSubgroupsFile(groupUuid, "");

    Optional<InternalGroup> group = loadGroup(groupUuid);
    assertThatGroup(group).value().members().isEmpty();
  }

  @Test
  public void membersFileMayContainOnlyWhitespace() throws Exception {
    populateGroupConfig(groupUuid, "[group]\n\tname=users\n\tid = 42\n\townerGroupUuid = owners\n");
    populateMembersFile(groupUuid, "\n\t\n\n");

    Optional<InternalGroup> group = loadGroup(groupUuid);
    assertThatGroup(group).value().members().isEmpty();
  }

  @Test
  public void membersFileMayUseAnyLineBreakCharacters() throws Exception {
    populateGroupConfig(groupUuid, "[group]\n\tname=users\n\tid = 42\n\townerGroupUuid = owners\n");
    populateMembersFile(groupUuid, "1\n2\n3\r4\r\n5\u20296");

    Optional<InternalGroup> group = loadGroup(groupUuid);
    assertThatGroup(group)
        .value()
        .members()
        .containsExactly(
            Account.id(1),
            Account.id(2),
            Account.id(3),
            Account.id(4),
            Account.id(5),
            Account.id(6));
  }

  @Test
  public void membersFileMustContainIntegers() throws Exception {
    populateGroupConfig(groupUuid, "[group]\n\tname=users\n\tid = 42\n\townerGroupUuid = owners\n");
    populateMembersFile(groupUuid, "One");

    exception.expect(ConfigInvalidException.class);
    exception.expectMessage("Invalid file members");
    loadGroup(groupUuid);
  }

  @Test
  public void membersFileUsesLineBreaksToSeparateMembers() throws Exception {
    populateGroupConfig(groupUuid, "[group]\n\tname=users\n\tid = 42\n\townerGroupUuid = owners\n");
    populateMembersFile(groupUuid, "1\t2");

    exception.expect(ConfigInvalidException.class);
    exception.expectMessage("Invalid file members");
    loadGroup(groupUuid);
  }

  @Test
  public void subgroupsFileNeedNotExist() throws Exception {
    populateGroupConfig(groupUuid, "[group]\n\tname=users\n\tid = 42\n\townerGroupUuid = owners\n");

    Optional<InternalGroup> group = loadGroup(groupUuid);
    assertThatGroup(group).value().subgroups().isEmpty();
  }

  @Test
  public void subgroupsFileMayBeEmpty() throws Exception {
    populateGroupConfig(groupUuid, "[group]\n\tname=users\n\tid = 42\n\townerGroupUuid = owners\n");
    populateMembersFile(groupUuid, "");

    Optional<InternalGroup> group = loadGroup(groupUuid);
    assertThatGroup(group).value().subgroups().isEmpty();
  }

  @Test
  public void subgroupsFileMayContainOnlyWhitespace() throws Exception {
    populateGroupConfig(groupUuid, "[group]\n\tname=users\n\tid = 42\n\townerGroupUuid = owners\n");
    populateSubgroupsFile(groupUuid, "\n\t\n\n");

    Optional<InternalGroup> group = loadGroup(groupUuid);
    assertThatGroup(group).value().subgroups().isEmpty();
  }

  @Test
  public void subgroupsFileMayUseAnyLineBreakCharacters() throws Exception {
    populateGroupConfig(groupUuid, "[group]\n\tname=users\n\tid = 42\n\townerGroupUuid = owners\n");
    populateSubgroupsFile(groupUuid, "1\n2\n3\r4\r\n5\u20296");

    Optional<InternalGroup> group = loadGroup(groupUuid);
    assertThatGroup(group)
        .value()
        .subgroups()
        .containsExactly(
            AccountGroup.uuid("1"),
            AccountGroup.uuid("2"),
            AccountGroup.uuid("3"),
            AccountGroup.uuid("4"),
            AccountGroup.uuid("5"),
            AccountGroup.uuid("6"));
  }

  @Test
  public void subgroupsFileMayContainSubgroupsWithWhitespaceInUuid() throws Exception {
    populateGroupConfig(groupUuid, "[group]\n\tname=users\n\tid = 42\n\townerGroupUuid = owners\n");
    populateSubgroupsFile(groupUuid, "1\t2 3");

    Optional<InternalGroup> group = loadGroup(groupUuid);
    assertThatGroup(group).value().subgroups().containsExactly(AccountGroup.uuid("1\t2 3"));
  }

  @Test
  public void subgroupsFileUsesLineBreaksToSeparateSubgroups() throws Exception {
    populateGroupConfig(groupUuid, "[group]\n\tname=users\n\tid = 42\n\townerGroupUuid = owners\n");
    populateSubgroupsFile(groupUuid, "1\t2\n3");

    Optional<InternalGroup> group = loadGroup(groupUuid);
    assertThatGroup(group)
        .value()
        .subgroups()
        .containsExactly(AccountGroup.uuid("1\t2"), AccountGroup.uuid("3"));
  }

  @Test
  public void nameCanBeUpdated() throws Exception {
    createArbitraryGroup(groupUuid);
    AccountGroup.NameKey newName = AccountGroup.nameKey("New name");

    InternalGroupUpdate groupUpdate = InternalGroupUpdate.builder().setName(newName).build();
    updateGroup(groupUuid, groupUpdate);

    Optional<InternalGroup> group = loadGroup(groupUuid);
    assertThatGroup(group).value().nameKey().isEqualTo(newName);
  }

  @Test
  public void nameCannotBeUpdatedToEmptyString() throws Exception {
    createArbitraryGroup(groupUuid);

    GroupConfig groupConfig = GroupConfig.loadForGroup(projectName, repository, groupUuid);
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder().setName(AccountGroup.nameKey("")).build();
    groupConfig.setGroupUpdate(groupUpdate, auditLogFormatter);

    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      exception.expectCause(instanceOf(ConfigInvalidException.class));
      exception.expectMessage("Name of the group " + groupUuid);
      groupConfig.commit(metaDataUpdate);
    }
  }

  @Test
  public void nameCanBeUpdatedToEmptyStringIfExplicitlySpecified() throws Exception {
    createArbitraryGroup(groupUuid);
    AccountGroup.NameKey emptyName = AccountGroup.nameKey("");

    GroupConfig groupConfig = GroupConfig.loadForGroup(projectName, repository, groupUuid);
    groupConfig.setAllowSaveEmptyName();
    InternalGroupUpdate groupUpdate = InternalGroupUpdate.builder().setName(emptyName).build();
    groupConfig.setGroupUpdate(groupUpdate, auditLogFormatter);
    commit(groupConfig);

    Optional<InternalGroup> group = loadGroup(groupUuid);
    assertThatGroup(group).value().nameKey().isEqualTo(emptyName);
  }

  @Test
  public void descriptionCanBeUpdated() throws Exception {
    createArbitraryGroup(groupUuid);
    String newDescription = "New description";

    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder().setDescription(newDescription).build();
    updateGroup(groupUuid, groupUpdate);

    Optional<InternalGroup> group = loadGroup(groupUuid);
    assertThatGroup(group).value().description().isEqualTo(newDescription);
  }

  @Test
  public void descriptionCanBeRemoved() throws Exception {
    createArbitraryGroup(groupUuid);

    InternalGroupUpdate groupUpdate = InternalGroupUpdate.builder().setDescription("").build();
    Optional<InternalGroup> group = updateGroup(groupUuid, groupUpdate);

    assertThatGroup(group).value().description().isNull();
  }

  @Test
  public void ownerGroupUuidCanBeUpdated() throws Exception {
    createArbitraryGroup(groupUuid);
    AccountGroup.UUID newOwnerGroupUuid = AccountGroup.uuid("New owner");

    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder().setOwnerGroupUUID(newOwnerGroupUuid).build();
    updateGroup(groupUuid, groupUpdate);

    Optional<InternalGroup> group = loadGroup(groupUuid);
    assertThatGroup(group).value().ownerGroupUuid().isEqualTo(newOwnerGroupUuid);
  }

  @Test
  public void ownerGroupUuidCannotBeUpdatedToEmptyString() throws Exception {
    createArbitraryGroup(groupUuid);

    GroupConfig groupConfig = GroupConfig.loadForGroup(projectName, repository, groupUuid);
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder().setOwnerGroupUUID(AccountGroup.uuid("")).build();
    groupConfig.setGroupUpdate(groupUpdate, auditLogFormatter);

    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      exception.expectCause(instanceOf(ConfigInvalidException.class));
      exception.expectMessage("Owner UUID of the group " + groupUuid);
      groupConfig.commit(metaDataUpdate);
    }
  }

  @Test
  public void visibleToAllCanBeUpdated() throws Exception {
    createArbitraryGroup(groupUuid);
    boolean oldVisibleAll = loadGroup(groupUuid).map(InternalGroup::isVisibleToAll).orElse(false);

    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder().setVisibleToAll(!oldVisibleAll).build();
    updateGroup(groupUuid, groupUpdate);

    Optional<InternalGroup> group = loadGroup(groupUuid);
    assertThatGroup(group).value().visibleToAll().isEqualTo(!oldVisibleAll);
  }

  @Test
  public void createdOnIsNotAffectedByFurtherUpdates() throws Exception {
    Timestamp createdOn = toTimestamp(LocalDate.of(2017, Month.MAY, 11).atTime(13, 44, 10));
    Timestamp updatedOn = toTimestamp(LocalDate.of(2017, Month.DECEMBER, 12).atTime(10, 21, 49));

    InternalGroupCreation groupCreation = getPrefilledGroupCreationBuilder().build();
    InternalGroupUpdate initialGroupUpdate =
        InternalGroupUpdate.builder().setUpdatedOn(createdOn).build();
    createGroup(groupCreation, initialGroupUpdate);

    InternalGroupUpdate laterGroupUpdate =
        InternalGroupUpdate.builder()
            .setName(AccountGroup.nameKey("Another name"))
            .setUpdatedOn(updatedOn)
            .build();
    Optional<InternalGroup> group = updateGroup(groupCreation.getGroupUUID(), laterGroupUpdate);

    assertThatGroup(group).value().createdOn().isEqualTo(createdOn);
    Optional<InternalGroup> reloadedGroup = loadGroup(groupUuid);
    assertThatGroup(reloadedGroup).value().createdOn().isEqualTo(createdOn);
  }

  @Test
  public void membersCanBeAdded() throws Exception {
    createArbitraryGroup(groupUuid);
    Account.Id member1 = Account.id(1);
    Account.Id member2 = Account.id(2);

    InternalGroupUpdate groupUpdate1 =
        InternalGroupUpdate.builder()
            .setMemberModification(members -> ImmutableSet.of(member1))
            .build();
    updateGroup(groupUuid, groupUpdate1);

    InternalGroupUpdate groupUpdate2 =
        InternalGroupUpdate.builder()
            .setMemberModification(members -> Sets.union(members, ImmutableSet.of(member2)))
            .build();
    updateGroup(groupUuid, groupUpdate2);

    Optional<InternalGroup> group = loadGroup(groupUuid);
    assertThatGroup(group).value().members().containsExactly(member1, member2);
  }

  @Test
  public void membersCanBeDeleted() throws Exception {
    createArbitraryGroup(groupUuid);
    Account.Id member1 = Account.id(1);
    Account.Id member2 = Account.id(2);

    InternalGroupUpdate groupUpdate1 =
        InternalGroupUpdate.builder()
            .setMemberModification(members -> ImmutableSet.of(member1, member2))
            .build();
    updateGroup(groupUuid, groupUpdate1);

    InternalGroupUpdate groupUpdate2 =
        InternalGroupUpdate.builder()
            .setMemberModification(members -> Sets.difference(members, ImmutableSet.of(member1)))
            .build();
    updateGroup(groupUuid, groupUpdate2);

    Optional<InternalGroup> group = loadGroup(groupUuid);
    assertThatGroup(group).value().members().containsExactly(member2);
  }

  @Test
  public void subgroupsCanBeAdded() throws Exception {
    createArbitraryGroup(groupUuid);
    AccountGroup.UUID subgroup1 = AccountGroup.uuid("subgroups1");
    AccountGroup.UUID subgroup2 = AccountGroup.uuid("subgroups2");

    InternalGroupUpdate groupUpdate1 =
        InternalGroupUpdate.builder()
            .setSubgroupModification(subgroups -> ImmutableSet.of(subgroup1))
            .build();
    updateGroup(groupUuid, groupUpdate1);

    InternalGroupUpdate groupUpdate2 =
        InternalGroupUpdate.builder()
            .setSubgroupModification(subgroups -> Sets.union(subgroups, ImmutableSet.of(subgroup2)))
            .build();
    updateGroup(groupUuid, groupUpdate2);

    Optional<InternalGroup> group = loadGroup(groupUuid);
    assertThatGroup(group).value().subgroups().containsExactly(subgroup1, subgroup2);
  }

  @Test
  public void subgroupsCanBeDeleted() throws Exception {
    createArbitraryGroup(groupUuid);
    AccountGroup.UUID subgroup1 = AccountGroup.uuid("subgroups1");
    AccountGroup.UUID subgroup2 = AccountGroup.uuid("subgroups2");

    InternalGroupUpdate groupUpdate1 =
        InternalGroupUpdate.builder()
            .setSubgroupModification(members -> ImmutableSet.of(subgroup1, subgroup2))
            .build();
    updateGroup(groupUuid, groupUpdate1);

    InternalGroupUpdate groupUpdate2 =
        InternalGroupUpdate.builder()
            .setSubgroupModification(
                members -> Sets.difference(members, ImmutableSet.of(subgroup1)))
            .build();
    updateGroup(groupUuid, groupUpdate2);

    Optional<InternalGroup> group = loadGroup(groupUuid);
    assertThatGroup(group).value().subgroups().containsExactly(subgroup2);
  }

  @Test
  public void createdGroupIsLoadedAutomatically() throws Exception {
    InternalGroupCreation groupCreation = getPrefilledGroupCreationBuilder().build();
    Optional<InternalGroup> group = createGroup(groupCreation);

    assertThat(group).isPresent();
  }

  @Test
  public void loadedNewGroupWithMandatoryPropertiesDoesNotChangeOnReload() throws Exception {
    InternalGroupCreation groupCreation = getPrefilledGroupCreationBuilder().build();

    Optional<InternalGroup> createdGroup = createGroup(groupCreation);
    Optional<InternalGroup> reloadedGroup = loadGroup(groupCreation.getGroupUUID());

    assertThat(createdGroup).isEqualTo(reloadedGroup);
  }

  @Test
  public void loadedNewGroupWithAllPropertiesDoesNotChangeOnReload() throws Exception {
    InternalGroupCreation groupCreation = getPrefilledGroupCreationBuilder().build();
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setDescription("A test group")
            .setOwnerGroupUUID(AccountGroup.uuid("another owner"))
            .setVisibleToAll(true)
            .setName(AccountGroup.nameKey("Another name"))
            .setUpdatedOn(new Timestamp(92900892))
            .setMemberModification(members -> ImmutableSet.of(Account.id(1), Account.id(2)))
            .setSubgroupModification(subgroups -> ImmutableSet.of(AccountGroup.uuid("subgroup")))
            .build();

    Optional<InternalGroup> createdGroup = createGroup(groupCreation, groupUpdate);
    Optional<InternalGroup> reloadedGroup = loadGroup(groupCreation.getGroupUUID());

    assertThat(createdGroup).isEqualTo(reloadedGroup);
  }

  @Test
  public void loadedGroupAfterUpdatesForAllPropertiesDoesNotChangeOnReload() throws Exception {
    createArbitraryGroup(groupUuid);

    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setDescription("A test group")
            .setOwnerGroupUUID(AccountGroup.uuid("another owner"))
            .setVisibleToAll(true)
            .setName(AccountGroup.nameKey("Another name"))
            .setUpdatedOn(new Timestamp(92900892))
            .setMemberModification(members -> ImmutableSet.of(Account.id(1), Account.id(2)))
            .setSubgroupModification(subgroups -> ImmutableSet.of(AccountGroup.uuid("subgroup")))
            .build();

    Optional<InternalGroup> updatedGroup = updateGroup(groupUuid, groupUpdate);
    Optional<InternalGroup> reloadedGroup = loadGroup(groupUuid);

    assertThat(updatedGroup).isEqualTo(reloadedGroup);
  }

  @Test
  public void loadedGroupWithAllPropertiesAndUpdateOfSinglePropertyDoesNotChangeOnReload()
      throws Exception {
    // Create a group with all properties set.
    InternalGroupCreation groupCreation = getPrefilledGroupCreationBuilder().build();
    InternalGroupUpdate initialGroupUpdate =
        InternalGroupUpdate.builder()
            .setDescription("A test group")
            .setOwnerGroupUUID(AccountGroup.uuid("another owner"))
            .setVisibleToAll(true)
            .setName(AccountGroup.nameKey("Another name"))
            .setUpdatedOn(new Timestamp(92900892))
            .setMemberModification(members -> ImmutableSet.of(Account.id(1), Account.id(2)))
            .setSubgroupModification(subgroups -> ImmutableSet.of(AccountGroup.uuid("subgroup")))
            .build();
    createGroup(groupCreation, initialGroupUpdate);

    // Only update one of the properties.
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder().setName(AccountGroup.nameKey("Another name")).build();

    Optional<InternalGroup> updatedGroup = updateGroup(groupCreation.getGroupUUID(), groupUpdate);
    Optional<InternalGroup> reloadedGroup = loadGroup(groupCreation.getGroupUUID());

    assertThat(updatedGroup).isEqualTo(reloadedGroup);
  }

  @Test
  public void groupConfigMayBeReusedForFurtherUpdates() throws Exception {
    InternalGroupCreation groupCreation =
        getPrefilledGroupCreationBuilder().setGroupUUID(groupUuid).setId(groupId).build();
    GroupConfig groupConfig = GroupConfig.createForNewGroup(projectName, repository, groupCreation);
    commit(groupConfig);

    AccountGroup.NameKey name = AccountGroup.nameKey("Robots");
    InternalGroupUpdate groupUpdate1 = InternalGroupUpdate.builder().setName(name).build();
    groupConfig.setGroupUpdate(groupUpdate1, auditLogFormatter);
    commit(groupConfig);

    String description = "Test group for robots";
    InternalGroupUpdate groupUpdate2 =
        InternalGroupUpdate.builder().setDescription(description).build();
    groupConfig.setGroupUpdate(groupUpdate2, auditLogFormatter);
    commit(groupConfig);

    Optional<InternalGroup> group = loadGroup(groupUuid);
    assertThatGroup(group).value().id().isEqualTo(groupId);
    assertThatGroup(group).value().nameKey().isEqualTo(name);
    assertThatGroup(group).value().description().isEqualTo(description);
  }

  @Test
  public void newGroupIsRepresentedByARefPointingToARootCommit() throws Exception {
    createArbitraryGroup(groupUuid);

    Ref ref = repository.exactRef(RefNames.refsGroups(groupUuid));
    assertThat(ref.getObjectId()).isNotNull();

    try (RevWalk revWalk = new RevWalk(repository)) {
      RevCommit revCommit = revWalk.parseCommit(ref.getObjectId());
      assertThat(revCommit.getParentCount()).isEqualTo(0);
    }
  }

  @Test
  public void updatedGroupIsRepresentedByARefPointingToACommitSequence() throws Exception {
    createArbitraryGroup(groupUuid);

    RevCommit commitAfterCreation = getLatestCommitForGroup(groupUuid);

    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder().setName(AccountGroup.nameKey("Another name")).build();
    updateGroup(groupUuid, groupUpdate);

    RevCommit commitAfterUpdate = getLatestCommitForGroup(groupUuid);
    assertThat(commitAfterUpdate).isNotEqualTo(commitAfterCreation);
    assertThat(commitAfterUpdate.getParents()).asList().containsExactly(commitAfterCreation);
  }

  @Test
  public void newCommitIsNotCreatedForEmptyUpdate() throws Exception {
    createArbitraryGroup(groupUuid);

    InternalGroupUpdate groupUpdate = InternalGroupUpdate.builder().build();

    RevCommit commitBeforeUpdate = getLatestCommitForGroup(groupUuid);
    updateGroup(groupUuid, groupUpdate);
    RevCommit commitAfterUpdate = getLatestCommitForGroup(groupUuid);

    assertThat(commitAfterUpdate).isEqualTo(commitBeforeUpdate);
  }

  @Test
  public void newCommitIsNotCreatedForPureUpdatedOnUpdate() throws Exception {
    createArbitraryGroup(groupUuid);

    Timestamp updatedOn = toTimestamp(LocalDate.of(3017, Month.DECEMBER, 12).atTime(10, 21, 49));
    InternalGroupUpdate groupUpdate = InternalGroupUpdate.builder().setUpdatedOn(updatedOn).build();

    RevCommit commitBeforeUpdate = getLatestCommitForGroup(groupUuid);
    updateGroup(groupUuid, groupUpdate);
    RevCommit commitAfterUpdate = getLatestCommitForGroup(groupUuid);

    assertThat(commitAfterUpdate).isEqualTo(commitBeforeUpdate);
  }

  @Test
  public void newCommitIsNotCreatedForRedundantNameUpdate() throws Exception {
    createArbitraryGroup(groupUuid);

    InternalGroupUpdate groupUpdate = InternalGroupUpdate.builder().setName(groupName).build();
    updateGroup(groupUuid, groupUpdate);

    RevCommit commitBeforeUpdate = getLatestCommitForGroup(groupUuid);
    updateGroup(groupUuid, groupUpdate);
    RevCommit commitAfterUpdate = getLatestCommitForGroup(groupUuid);

    assertThat(commitAfterUpdate).isEqualTo(commitBeforeUpdate);
  }

  @Test
  public void newCommitIsNotCreatedForRedundantDescriptionUpdate() throws Exception {
    createArbitraryGroup(groupUuid);

    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder().setDescription("A test group").build();
    updateGroup(groupUuid, groupUpdate);

    RevCommit commitBeforeUpdate = getLatestCommitForGroup(groupUuid);
    updateGroup(groupUuid, groupUpdate);
    RevCommit commitAfterUpdate = getLatestCommitForGroup(groupUuid);

    assertThat(commitAfterUpdate).isEqualTo(commitBeforeUpdate);
  }

  @Test
  public void newCommitIsNotCreatedForRedundantVisibleToAllUpdate() throws Exception {
    createArbitraryGroup(groupUuid);

    InternalGroupUpdate groupUpdate = InternalGroupUpdate.builder().setVisibleToAll(true).build();
    updateGroup(groupUuid, groupUpdate);

    RevCommit commitBeforeUpdate = getLatestCommitForGroup(groupUuid);
    updateGroup(groupUuid, groupUpdate);
    RevCommit commitAfterUpdate = getLatestCommitForGroup(groupUuid);

    assertThat(commitAfterUpdate).isEqualTo(commitBeforeUpdate);
  }

  @Test
  public void newCommitIsNotCreatedForRedundantOwnerGroupUuidUpdate() throws Exception {
    createArbitraryGroup(groupUuid);

    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder().setOwnerGroupUUID(AccountGroup.uuid("Another owner")).build();
    updateGroup(groupUuid, groupUpdate);

    RevCommit commitBeforeUpdate = getLatestCommitForGroup(groupUuid);
    updateGroup(groupUuid, groupUpdate);
    RevCommit commitAfterUpdate = getLatestCommitForGroup(groupUuid);

    assertThat(commitAfterUpdate).isEqualTo(commitBeforeUpdate);
  }

  @Test
  public void newCommitIsNotCreatedForRedundantMemberUpdate() throws Exception {
    createArbitraryGroup(groupUuid);

    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setMemberModification(members -> Sets.union(members, ImmutableSet.of(Account.id(10))))
            .build();
    updateGroup(groupUuid, groupUpdate);

    RevCommit commitBeforeUpdate = getLatestCommitForGroup(groupUuid);
    updateGroup(groupUuid, groupUpdate);
    RevCommit commitAfterUpdate = getLatestCommitForGroup(groupUuid);

    assertThat(commitAfterUpdate).isEqualTo(commitBeforeUpdate);
  }

  @Test
  public void newCommitIsNotCreatedForRedundantSubgroupsUpdate() throws Exception {
    createArbitraryGroup(groupUuid);

    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setSubgroupModification(
                subgroups -> Sets.union(subgroups, ImmutableSet.of(AccountGroup.uuid("subgroup"))))
            .build();
    updateGroup(groupUuid, groupUpdate);

    RevCommit commitBeforeUpdate = getLatestCommitForGroup(groupUuid);
    updateGroup(groupUuid, groupUpdate);
    RevCommit commitAfterUpdate = getLatestCommitForGroup(groupUuid);

    assertThat(commitAfterUpdate).isEqualTo(commitBeforeUpdate);
  }

  @Test
  public void newCommitIsNotCreatedWhenCommittingGroupCreationTwice() throws Exception {
    InternalGroupCreation groupCreation =
        getPrefilledGroupCreationBuilder().setGroupUUID(groupUuid).build();

    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder().setName(AccountGroup.nameKey("Another name")).build();

    GroupConfig groupConfig = GroupConfig.createForNewGroup(projectName, repository, groupCreation);
    groupConfig.setGroupUpdate(groupUpdate, auditLogFormatter);
    commit(groupConfig);

    RevCommit commitBeforeSecondCommit = getLatestCommitForGroup(groupUuid);
    commit(groupConfig);
    RevCommit commitAfterSecondCommit = getLatestCommitForGroup(groupUuid);

    assertThat(commitAfterSecondCommit).isEqualTo(commitBeforeSecondCommit);
  }

  @Test
  public void newCommitIsNotCreatedWhenCommittingGroupUpdateTwice() throws Exception {
    createArbitraryGroup(groupUuid);

    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder().setDescription("A test group").build();

    GroupConfig groupConfig = GroupConfig.loadForGroup(projectName, repository, groupUuid);
    groupConfig.setGroupUpdate(groupUpdate, auditLogFormatter);
    commit(groupConfig);

    RevCommit commitBeforeSecondCommit = getLatestCommitForGroup(groupUuid);
    commit(groupConfig);
    RevCommit commitAfterSecondCommit = getLatestCommitForGroup(groupUuid);

    assertThat(commitAfterSecondCommit).isEqualTo(commitBeforeSecondCommit);
  }

  @Test
  public void commitTimeMatchesDefaultCreatedOnOfNewGroup() throws Exception {
    // Git timestamps are only precise to the second.
    long testStartAsSecondsSinceEpoch = TimeUtil.nowTs().getTime() / 1000;

    InternalGroupCreation groupCreation =
        InternalGroupCreation.builder()
            .setGroupUUID(groupUuid)
            .setNameKey(groupName)
            .setId(groupId)
            .build();
    createGroup(groupCreation);

    RevCommit revCommit = getLatestCommitForGroup(groupUuid);
    assertThat(revCommit.getCommitTime()).isAtLeast((int) testStartAsSecondsSinceEpoch);
  }

  @Test
  public void commitTimeMatchesSpecifiedCreatedOnOfNewGroup() throws Exception {
    // Git timestamps are only precise to the second.
    long createdOnAsSecondsSinceEpoch = 9082093;

    InternalGroupCreation groupCreation =
        InternalGroupCreation.builder()
            .setGroupUUID(groupUuid)
            .setNameKey(groupName)
            .setId(groupId)
            .build();
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setUpdatedOn(new Timestamp(createdOnAsSecondsSinceEpoch * 1000))
            .build();
    createGroup(groupCreation, groupUpdate);

    RevCommit revCommit = getLatestCommitForGroup(groupUuid);
    assertThat(revCommit.getCommitTime()).isEqualTo(createdOnAsSecondsSinceEpoch);
  }

  @Test
  public void timestampOfCommitterMatchesSpecifiedCreatedOnOfNewGroup() throws Exception {
    Timestamp committerTimestamp =
        toTimestamp(LocalDate.of(2017, Month.DECEMBER, 13).atTime(15, 5, 27));
    Timestamp createdOn = toTimestamp(LocalDate.of(2016, Month.MARCH, 11).atTime(23, 49, 11));

    InternalGroupCreation groupCreation =
        InternalGroupCreation.builder()
            .setGroupUUID(groupUuid)
            .setNameKey(groupName)
            .setId(groupId)
            .build();
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setName(AccountGroup.nameKey("Another name"))
            .setUpdatedOn(createdOn)
            .build();
    GroupConfig groupConfig = GroupConfig.createForNewGroup(projectName, repository, groupCreation);
    groupConfig.setGroupUpdate(groupUpdate, auditLogFormatter);

    PersonIdent committerIdent =
        new PersonIdent("Jane", "Jane@gerritcodereview.com", committerTimestamp, timeZone);
    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      metaDataUpdate.getCommitBuilder().setCommitter(committerIdent);
      groupConfig.commit(metaDataUpdate);
    }

    RevCommit revCommit = getLatestCommitForGroup(groupUuid);
    assertThat(revCommit.getCommitterIdent().getWhen()).isEqualTo(createdOn);
    assertThat(revCommit.getCommitterIdent().getTimeZone().getRawOffset())
        .isEqualTo(timeZone.getRawOffset());
  }

  @Test
  public void timestampOfAuthorMatchesSpecifiedCreatedOnOfNewGroup() throws Exception {
    Timestamp authorTimestamp =
        toTimestamp(LocalDate.of(2017, Month.DECEMBER, 13).atTime(15, 5, 27));
    Timestamp createdOn = toTimestamp(LocalDate.of(2016, Month.MARCH, 11).atTime(23, 49, 11));

    InternalGroupCreation groupCreation =
        InternalGroupCreation.builder()
            .setGroupUUID(groupUuid)
            .setNameKey(groupName)
            .setId(groupId)
            .build();
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setName(AccountGroup.nameKey("Another name"))
            .setUpdatedOn(createdOn)
            .build();
    GroupConfig groupConfig = GroupConfig.createForNewGroup(projectName, repository, groupCreation);
    groupConfig.setGroupUpdate(groupUpdate, auditLogFormatter);

    PersonIdent authorIdent =
        new PersonIdent("Jane", "Jane@gerritcodereview.com", authorTimestamp, timeZone);
    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      metaDataUpdate.getCommitBuilder().setAuthor(authorIdent);
      groupConfig.commit(metaDataUpdate);
    }

    RevCommit revCommit = getLatestCommitForGroup(groupUuid);
    assertThat(revCommit.getAuthorIdent().getWhen()).isEqualTo(createdOn);
    assertThat(revCommit.getAuthorIdent().getTimeZone().getRawOffset())
        .isEqualTo(timeZone.getRawOffset());
  }

  @Test
  public void commitTimeMatchesDefaultUpdatedOnOfUpdatedGroup() throws Exception {
    // Git timestamps are only precise to the second.
    long testStartAsSecondsSinceEpoch = TimeUtil.nowTs().getTime() / 1000;

    createArbitraryGroup(groupUuid);
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder().setName(AccountGroup.nameKey("Another name")).build();
    updateGroup(groupUuid, groupUpdate);

    RevCommit revCommit = getLatestCommitForGroup(groupUuid);
    assertThat(revCommit.getCommitTime()).isAtLeast((int) testStartAsSecondsSinceEpoch);
  }

  @Test
  public void commitTimeMatchesSpecifiedUpdatedOnOfUpdatedGroup() throws Exception {
    // Git timestamps are only precise to the second.
    long updatedOnAsSecondsSinceEpoch = 9082093;

    createArbitraryGroup(groupUuid);
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setName(AccountGroup.nameKey("Another name"))
            .setUpdatedOn(new Timestamp(updatedOnAsSecondsSinceEpoch * 1000))
            .build();
    updateGroup(groupUuid, groupUpdate);

    RevCommit revCommit = getLatestCommitForGroup(groupUuid);
    assertThat(revCommit.getCommitTime()).isEqualTo(updatedOnAsSecondsSinceEpoch);
  }

  @Test
  public void timestampOfCommitterMatchesSpecifiedUpdatedOnOfUpdatedGroup() throws Exception {
    Timestamp committerTimestamp =
        toTimestamp(LocalDate.of(2017, Month.DECEMBER, 13).atTime(15, 5, 27));
    Timestamp updatedOn = toTimestamp(LocalDate.of(2016, Month.MARCH, 11).atTime(23, 49, 11));

    createArbitraryGroup(groupUuid);
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setName(AccountGroup.nameKey("Another name"))
            .setUpdatedOn(updatedOn)
            .build();
    GroupConfig groupConfig = GroupConfig.loadForGroup(projectName, repository, groupUuid);
    groupConfig.setGroupUpdate(groupUpdate, auditLogFormatter);

    PersonIdent committerIdent =
        new PersonIdent("Jane", "Jane@gerritcodereview.com", committerTimestamp, timeZone);
    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      metaDataUpdate.getCommitBuilder().setCommitter(committerIdent);
      groupConfig.commit(metaDataUpdate);
    }

    RevCommit revCommit = getLatestCommitForGroup(groupUuid);
    assertThat(revCommit.getCommitterIdent().getWhen()).isEqualTo(updatedOn);
    assertThat(revCommit.getCommitterIdent().getTimeZone().getRawOffset())
        .isEqualTo(timeZone.getRawOffset());
  }

  @Test
  public void timestampOfAuthorMatchesSpecifiedUpdatedOnOfUpdatedGroup() throws Exception {
    Timestamp authorTimestamp =
        toTimestamp(LocalDate.of(2017, Month.DECEMBER, 13).atTime(15, 5, 27));
    Timestamp updatedOn = toTimestamp(LocalDate.of(2016, Month.MARCH, 11).atTime(23, 49, 11));

    createArbitraryGroup(groupUuid);
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setName(AccountGroup.nameKey("Another name"))
            .setUpdatedOn(updatedOn)
            .build();
    GroupConfig groupConfig = GroupConfig.loadForGroup(projectName, repository, groupUuid);
    groupConfig.setGroupUpdate(groupUpdate, auditLogFormatter);

    PersonIdent authorIdent =
        new PersonIdent("Jane", "Jane@gerritcodereview.com", authorTimestamp, timeZone);
    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      metaDataUpdate.getCommitBuilder().setAuthor(authorIdent);
      groupConfig.commit(metaDataUpdate);
    }

    RevCommit revCommit = getLatestCommitForGroup(groupUuid);
    assertThat(revCommit.getAuthorIdent().getWhen()).isEqualTo(updatedOn);
    assertThat(revCommit.getAuthorIdent().getTimeZone().getRawOffset())
        .isEqualTo(timeZone.getRawOffset());
  }

  @Test
  public void refStateOfLoadedGroupIsPopulatedWithCommitSha1() throws Exception {
    createArbitraryGroup(groupUuid);

    Optional<InternalGroup> group = loadGroup(groupUuid);

    RevCommit revCommit = getLatestCommitForGroup(groupUuid);
    assertThatGroup(group).value().refState().isEqualTo(revCommit.copy());
  }

  @Test
  public void groupCanBeLoadedAtASpecificRevision() throws Exception {
    createArbitraryGroup(groupUuid);

    AccountGroup.NameKey firstName = AccountGroup.nameKey("Bots");
    InternalGroupUpdate groupUpdate1 = InternalGroupUpdate.builder().setName(firstName).build();
    updateGroup(groupUuid, groupUpdate1);

    RevCommit commitAfterUpdate1 = getLatestCommitForGroup(groupUuid);

    InternalGroupUpdate groupUpdate2 =
        InternalGroupUpdate.builder().setName(AccountGroup.nameKey("Robots")).build();
    updateGroup(groupUuid, groupUpdate2);

    GroupConfig groupConfig =
        GroupConfig.loadForGroupSnapshot(
            projectName, repository, groupUuid, commitAfterUpdate1.copy());
    Optional<InternalGroup> group = groupConfig.getLoadedGroup();
    assertThatGroup(group).value().nameKey().isEqualTo(firstName);
    assertThatGroup(group).value().refState().isEqualTo(commitAfterUpdate1.copy());
  }

  @Test
  public void commitMessageOfNewGroupWithoutMembersOrSubgroupsContainsNoFooters() throws Exception {
    InternalGroupCreation groupCreation =
        getPrefilledGroupCreationBuilder().setGroupUUID(groupUuid).build();
    createGroup(groupCreation);

    RevCommit revCommit = getLatestCommitForGroup(groupUuid);
    assertThat(revCommit.getFullMessage()).isEqualTo("Create group");
  }

  @Test
  public void commitMessageOfNewGroupWithAdditionalNameSpecificationContainsNoFooters()
      throws Exception {
    InternalGroupCreation groupCreation =
        getPrefilledGroupCreationBuilder().setGroupUUID(groupUuid).build();
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder().setName(AccountGroup.nameKey("Another name")).build();
    createGroup(groupCreation, groupUpdate);

    RevCommit revCommit = getLatestCommitForGroup(groupUuid);
    assertThat(revCommit.getFullMessage()).isEqualTo("Create group");
  }

  @Test
  public void commitMessageOfNewGroupWithMembersContainsFooters() throws Exception {
    Account account13 = createAccount(Account.id(13), "John");
    Account account7 = createAccount(Account.id(7), "Jane");
    ImmutableSet<Account> accounts = ImmutableSet.of(account13, account7);

    AuditLogFormatter auditLogFormatter =
        AuditLogFormatter.createBackedBy(accounts, ImmutableSet.of(), "server-id");

    InternalGroupCreation groupCreation =
        getPrefilledGroupCreationBuilder().setGroupUUID(groupUuid).build();
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setMemberModification(members -> ImmutableSet.of(account13.getId(), account7.getId()))
            .build();

    GroupConfig groupConfig = GroupConfig.createForNewGroup(projectName, repository, groupCreation);
    groupConfig.setGroupUpdate(groupUpdate, auditLogFormatter);
    commit(groupConfig);

    RevCommit revCommit = getLatestCommitForGroup(groupUuid);
    assertThat(revCommit.getFullMessage())
        .isEqualTo("Create group\n\nAdd: Jane <7@server-id>\nAdd: John <13@server-id>");
  }

  @Test
  public void commitMessageOfNewGroupWithSubgroupsContainsFooters() throws Exception {
    GroupDescription.Basic group1 = createGroup(AccountGroup.uuid("129403"), "Bots");
    GroupDescription.Basic group2 = createGroup(AccountGroup.uuid("8903493"), "Verifiers");
    ImmutableSet<GroupDescription.Basic> groups = ImmutableSet.of(group1, group2);

    AuditLogFormatter auditLogFormatter =
        AuditLogFormatter.createBackedBy(ImmutableSet.of(), groups, "serverId");

    InternalGroupCreation groupCreation =
        getPrefilledGroupCreationBuilder().setGroupUUID(groupUuid).build();
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setSubgroupModification(
                subgroups -> ImmutableSet.of(group1.getGroupUUID(), group2.getGroupUUID()))
            .build();
    GroupConfig groupConfig = GroupConfig.createForNewGroup(projectName, repository, groupCreation);
    groupConfig.setGroupUpdate(groupUpdate, auditLogFormatter);
    commit(groupConfig);

    RevCommit revCommit = getLatestCommitForGroup(groupUuid);
    assertThat(revCommit.getFullMessage())
        .isEqualTo("Create group\n\nAdd-group: Bots <129403>\nAdd-group: Verifiers <8903493>");
  }

  @Test
  public void commitMessageOfMemberAdditionContainsFooters() throws Exception {
    Account account13 = createAccount(Account.id(13), "John");
    Account account7 = createAccount(Account.id(7), "Jane");
    ImmutableSet<Account> accounts = ImmutableSet.of(account13, account7);

    createArbitraryGroup(groupUuid);

    AuditLogFormatter auditLogFormatter =
        AuditLogFormatter.createBackedBy(accounts, ImmutableSet.of(), "GerritServer1");

    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setMemberModification(members -> ImmutableSet.of(account13.getId(), account7.getId()))
            .build();
    updateGroup(groupUuid, groupUpdate, auditLogFormatter);

    RevCommit revCommit = getLatestCommitForGroup(groupUuid);
    assertThat(revCommit.getFullMessage())
        .isEqualTo("Update group\n\nAdd: Jane <7@GerritServer1>\nAdd: John <13@GerritServer1>");
  }

  @Test
  public void commitMessageOfMemberRemovalContainsFooters() throws Exception {
    Account account13 = createAccount(Account.id(13), "John");
    Account account7 = createAccount(Account.id(7), "Jane");
    ImmutableSet<Account> accounts = ImmutableSet.of(account13, account7);

    createArbitraryGroup(groupUuid);

    AuditLogFormatter auditLogFormatter =
        AuditLogFormatter.createBackedBy(accounts, ImmutableSet.of(), "server-id");

    InternalGroupUpdate groupUpdate1 =
        InternalGroupUpdate.builder()
            .setMemberModification(members -> ImmutableSet.of(account13.getId(), account7.getId()))
            .build();
    updateGroup(groupUuid, groupUpdate1, auditLogFormatter);

    InternalGroupUpdate groupUpdate2 =
        InternalGroupUpdate.builder()
            .setMemberModification(members -> ImmutableSet.of(account7.getId()))
            .build();
    updateGroup(groupUuid, groupUpdate2, auditLogFormatter);

    RevCommit revCommit = getLatestCommitForGroup(groupUuid);
    assertThat(revCommit.getFullMessage()).isEqualTo("Update group\n\nRemove: John <13@server-id>");
  }

  @Test
  public void commitMessageOfSubgroupAdditionContainsFooters() throws Exception {
    GroupDescription.Basic group1 = createGroup(AccountGroup.uuid("129403"), "Bots");
    GroupDescription.Basic group2 = createGroup(AccountGroup.uuid("8903493"), "Verifiers");
    ImmutableSet<GroupDescription.Basic> groups = ImmutableSet.of(group1, group2);

    createArbitraryGroup(groupUuid);

    AuditLogFormatter auditLogFormatter =
        AuditLogFormatter.createBackedBy(ImmutableSet.of(), groups, "serverId");

    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setSubgroupModification(
                subgroups -> ImmutableSet.of(group1.getGroupUUID(), group2.getGroupUUID()))
            .build();
    updateGroup(groupUuid, groupUpdate, auditLogFormatter);

    RevCommit revCommit = getLatestCommitForGroup(groupUuid);
    assertThat(revCommit.getFullMessage())
        .isEqualTo("Update group\n\nAdd-group: Bots <129403>\nAdd-group: Verifiers <8903493>");
  }

  @Test
  public void commitMessageOfSubgroupRemovalContainsFooters() throws Exception {
    GroupDescription.Basic group1 = createGroup(AccountGroup.uuid("129403"), "Bots");
    GroupDescription.Basic group2 = createGroup(AccountGroup.uuid("8903493"), "Verifiers");
    ImmutableSet<GroupDescription.Basic> groups = ImmutableSet.of(group1, group2);

    createArbitraryGroup(groupUuid);

    AuditLogFormatter auditLogFormatter =
        AuditLogFormatter.createBackedBy(ImmutableSet.of(), groups, "serverId");

    InternalGroupUpdate groupUpdate1 =
        InternalGroupUpdate.builder()
            .setSubgroupModification(
                subgroups -> ImmutableSet.of(group1.getGroupUUID(), group2.getGroupUUID()))
            .build();
    updateGroup(groupUuid, groupUpdate1, auditLogFormatter);

    InternalGroupUpdate groupUpdate2 =
        InternalGroupUpdate.builder()
            .setSubgroupModification(subgroups -> ImmutableSet.of(group1.getGroupUUID()))
            .build();
    updateGroup(groupUuid, groupUpdate2, auditLogFormatter);

    RevCommit revCommit = getLatestCommitForGroup(groupUuid);
    assertThat(revCommit.getFullMessage())
        .isEqualTo("Update group\n\nRemove-group: Verifiers <8903493>");
  }

  @Test
  public void commitMessageOfGroupRenameContainsFooters() throws Exception {
    createArbitraryGroup(groupUuid);

    InternalGroupUpdate groupUpdate1 =
        InternalGroupUpdate.builder().setName(AccountGroup.nameKey("Old name")).build();
    updateGroup(groupUuid, groupUpdate1);

    InternalGroupUpdate groupUpdate2 =
        InternalGroupUpdate.builder().setName(AccountGroup.nameKey("New name")).build();
    updateGroup(groupUuid, groupUpdate2);

    RevCommit revCommit = getLatestCommitForGroup(groupUuid);
    assertThat(revCommit.getFullMessage())
        .isEqualTo("Update group\n\nRename from Old name to New name");
  }

  @Test
  public void commitMessageFootersCanBeMixed() throws Exception {
    Account account13 = createAccount(Account.id(13), "John");
    Account account7 = createAccount(Account.id(7), "Jane");
    ImmutableSet<Account> accounts = ImmutableSet.of(account13, account7);
    GroupDescription.Basic group1 = createGroup(AccountGroup.uuid("129403"), "Bots");
    GroupDescription.Basic group2 = createGroup(AccountGroup.uuid("8903493"), "Verifiers");
    ImmutableSet<GroupDescription.Basic> groups = ImmutableSet.of(group1, group2);

    createArbitraryGroup(groupUuid);

    AuditLogFormatter auditLogFormatter =
        AuditLogFormatter.createBackedBy(accounts, groups, "serverId");

    InternalGroupUpdate groupUpdate1 =
        InternalGroupUpdate.builder()
            .setName(AccountGroup.nameKey("Old name"))
            .setMemberModification(members -> ImmutableSet.of(account7.getId()))
            .setSubgroupModification(subgroups -> ImmutableSet.of(group2.getGroupUUID()))
            .build();
    updateGroup(groupUuid, groupUpdate1, auditLogFormatter);

    InternalGroupUpdate groupUpdate2 =
        InternalGroupUpdate.builder()
            .setName(AccountGroup.nameKey("New name"))
            .setMemberModification(members -> ImmutableSet.of(account13.getId()))
            .setSubgroupModification(subgroups -> ImmutableSet.of(group1.getGroupUUID()))
            .build();
    updateGroup(groupUuid, groupUpdate2, auditLogFormatter);

    RevCommit revCommit = getLatestCommitForGroup(groupUuid);
    assertThat(revCommit.getFullMessage())
        .isEqualTo(
            "Update group\n"
                + "\n"
                + "Add-group: Bots <129403>\n"
                + "Add: John <13@serverId>\n"
                + "Remove-group: Verifiers <8903493>\n"
                + "Remove: Jane <7@serverId>\n"
                + "Rename from Old name to New name");
  }

  private static Timestamp toTimestamp(LocalDateTime localDateTime) {
    return Timestamp.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
  }

  private void populateGroupConfig(AccountGroup.UUID uuid, String fileContent) throws Exception {
    testRepository
        .branch(RefNames.refsGroups(uuid))
        .commit()
        .message("Prepopulate group.config")
        .add(GroupConfig.GROUP_CONFIG_FILE, fileContent)
        .create();
  }

  private void populateMembersFile(AccountGroup.UUID uuid, String fileContent) throws Exception {
    testRepository
        .branch(RefNames.refsGroups(uuid))
        .commit()
        .message("Prepopulate members")
        .add(GroupConfig.MEMBERS_FILE, fileContent)
        .create();
  }

  private void populateSubgroupsFile(AccountGroup.UUID uuid, String fileContent) throws Exception {
    testRepository
        .branch(RefNames.refsGroups(uuid))
        .commit()
        .message("Prepopulate subgroups")
        .add(GroupConfig.SUBGROUPS_FILE, fileContent)
        .create();
  }

  private void createArbitraryGroup(AccountGroup.UUID uuid) throws Exception {
    InternalGroupCreation groupCreation =
        getPrefilledGroupCreationBuilder().setGroupUUID(uuid).build();
    createGroup(groupCreation);
  }

  private InternalGroupCreation.Builder getPrefilledGroupCreationBuilder() {
    return InternalGroupCreation.builder()
        .setGroupUUID(groupUuid)
        .setNameKey(groupName)
        .setId(groupId);
  }

  private Optional<InternalGroup> createGroup(InternalGroupCreation groupCreation)
      throws Exception {
    GroupConfig groupConfig = GroupConfig.createForNewGroup(projectName, repository, groupCreation);
    commit(groupConfig);
    return groupConfig.getLoadedGroup();
  }

  private Optional<InternalGroup> createGroup(
      InternalGroupCreation groupCreation, InternalGroupUpdate groupUpdate) throws Exception {
    GroupConfig groupConfig = GroupConfig.createForNewGroup(projectName, repository, groupCreation);
    groupConfig.setGroupUpdate(groupUpdate, auditLogFormatter);
    commit(groupConfig);
    return groupConfig.getLoadedGroup();
  }

  private Optional<InternalGroup> updateGroup(
      AccountGroup.UUID uuid, InternalGroupUpdate groupUpdate) throws Exception {
    return updateGroup(uuid, groupUpdate, auditLogFormatter);
  }

  private Optional<InternalGroup> updateGroup(
      AccountGroup.UUID uuid, InternalGroupUpdate groupUpdate, AuditLogFormatter auditLogFormatter)
      throws Exception {
    GroupConfig groupConfig = GroupConfig.loadForGroup(projectName, repository, uuid);
    groupConfig.setGroupUpdate(groupUpdate, auditLogFormatter);
    commit(groupConfig);
    return groupConfig.getLoadedGroup();
  }

  private Optional<InternalGroup> loadGroup(AccountGroup.UUID uuid) throws Exception {
    GroupConfig groupConfig = GroupConfig.loadForGroup(projectName, repository, uuid);
    return groupConfig.getLoadedGroup();
  }

  private void commit(GroupConfig groupConfig) throws IOException {
    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      groupConfig.commit(metaDataUpdate);
    }
  }

  private MetaDataUpdate createMetaDataUpdate() {
    PersonIdent serverIdent =
        new PersonIdent(
            "Gerrit Server", "noreply@gerritcodereview.com", TimeUtil.nowTs(), timeZone);

    MetaDataUpdate metaDataUpdate =
        new MetaDataUpdate(
            GitReferenceUpdated.DISABLED, new Project.NameKey("Test Repository"), repository);
    metaDataUpdate.getCommitBuilder().setCommitter(serverIdent);
    metaDataUpdate.getCommitBuilder().setAuthor(serverIdent);
    return metaDataUpdate;
  }

  private RevCommit getLatestCommitForGroup(AccountGroup.UUID uuid) throws IOException {
    Ref ref = repository.exactRef(RefNames.refsGroups(uuid));
    assertWithMessage("Precondition: Assumed that ref for group " + uuid + " exists.")
        .that(ref.getObjectId())
        .isNotNull();

    try (RevWalk revWalk = new RevWalk(repository)) {
      return revWalk.parseCommit(ref.getObjectId());
    }
  }

  private static Account createAccount(Account.Id id, String name) {
    Account account = new Account(id, TimeUtil.nowTs());
    account.setFullName(name);
    return account;
  }

  private static GroupDescription.Basic createGroup(AccountGroup.UUID uuid, String name) {
    return new GroupDescription.Basic() {
      @Override
      public AccountGroup.UUID getGroupUUID() {
        return uuid;
      }

      @Override
      public String getName() {
        return name;
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
  }

  private static OptionalSubject<InternalGroupSubject, InternalGroup> assertThatGroup(
      Optional<InternalGroup> loadedGroup) {
    return assertThat(loadedGroup, internalGroups());
  }
}
