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
import static org.hamcrest.CoreMatchers.instanceOf;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.MetaDataUpdate;
import java.util.TimeZone;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class GroupConfigTest {
  @Rule public ExpectedException expectedException = ExpectedException.none();

  private Repository repository;
  private TestRepository<?> testRepository;
  private final AccountGroup.UUID groupUuid = new AccountGroup.UUID("users-XYZ");
  private final AccountGroup.NameKey groupName = new AccountGroup.NameKey("users");
  private final AccountGroup.Id groupId = new AccountGroup.Id(123);

  @Before
  public void setUp() throws Exception {
    repository = new InMemoryRepository(new DfsRepositoryDescription("Test Repository"));
    testRepository = new TestRepository<>(repository);
  }

  @Test
  public void nameOfNewGroupMustNotBeNull() throws Exception {
    InternalGroupCreation groupCreation =
        getPrefilledGroupCreationBuilder().setNameKey(new AccountGroup.NameKey(null)).build();
    GroupConfig groupConfig = GroupConfig.createForNewGroup(repository, groupCreation);

    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      expectedException.expectCause(instanceOf(ConfigInvalidException.class));
      expectedException.expectMessage("Name of the group users-XYZ");
      groupConfig.commit(metaDataUpdate);
    }
  }

  @Test
  public void nameOfNewGroupMustNotBeEmpty() throws Exception {
    InternalGroupCreation groupCreation =
        getPrefilledGroupCreationBuilder().setNameKey(new AccountGroup.NameKey("")).build();
    GroupConfig groupConfig = GroupConfig.createForNewGroup(repository, groupCreation);

    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      expectedException.expectCause(instanceOf(ConfigInvalidException.class));
      expectedException.expectMessage("Name of the group users-XYZ");
      groupConfig.commit(metaDataUpdate);
    }
  }

  @Test
  public void idOfNewGroupMustNotBeNegative() throws Exception {
    InternalGroupCreation groupCreation =
        getPrefilledGroupCreationBuilder().setId(new AccountGroup.Id(-2)).build();
    GroupConfig groupConfig = GroupConfig.createForNewGroup(repository, groupCreation);

    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      expectedException.expectCause(instanceOf(ConfigInvalidException.class));
      expectedException.expectMessage("ID of the group users-XYZ");
      groupConfig.commit(metaDataUpdate);
    }
  }

  @Test
  public void ownerUuidOfNewGroupMustNotBeNull() throws Exception {
    InternalGroupCreation groupCreation = getPrefilledGroupCreationBuilder().build();
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder().setOwnerGroupUUID(new AccountGroup.UUID(null)).build();
    GroupConfig groupConfig = GroupConfig.createForNewGroup(repository, groupCreation);
    groupConfig.setGroupUpdate(groupUpdate, Account.Id::toString, AccountGroup.UUID::get);

    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      expectedException.expectCause(instanceOf(ConfigInvalidException.class));
      expectedException.expectMessage("Owner UUID of the group users-XYZ");
      groupConfig.commit(metaDataUpdate);
    }
  }

  @Test
  public void ownerUuidOfNewGroupMustNotBeEmpty() throws Exception {
    InternalGroupCreation groupCreation = getPrefilledGroupCreationBuilder().build();
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder().setOwnerGroupUUID(new AccountGroup.UUID("")).build();
    GroupConfig groupConfig = GroupConfig.createForNewGroup(repository, groupCreation);
    groupConfig.setGroupUpdate(groupUpdate, Account.Id::toString, AccountGroup.UUID::get);

    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      expectedException.expectCause(instanceOf(ConfigInvalidException.class));
      expectedException.expectMessage("Owner UUID of the group users-XYZ");
      groupConfig.commit(metaDataUpdate);
    }
  }

  @Test
  public void nameInConfigMayBeUndefined() throws Exception {
    populateGroupConfig(groupUuid, "[group]\n\tid = 42\n\townerGroupUuid = owners\n");

    GroupConfig groupConfig = GroupConfig.loadForGroup(repository, groupUuid);
    assertThat(groupConfig.getLoadedGroup().get().getName()).isEmpty();
  }

  @Test
  public void nameInConfigMayBeEmpty() throws Exception {
    populateGroupConfig(groupUuid, "[group]\n\tname=\n\tid = 42\n\townerGroupUuid = owners\n");

    GroupConfig groupConfig = GroupConfig.loadForGroup(repository, groupUuid);
    assertThat(groupConfig.getLoadedGroup().get().getName()).isEmpty();
  }

  @Test
  public void idInConfigMustBeDefined() throws Exception {
    populateGroupConfig(groupUuid, "[group]\n\tname = users\n\townerGroupUuid = owners\n");

    expectedException.expect(ConfigInvalidException.class);
    expectedException.expectMessage("ID of the group users-XYZ");
    GroupConfig.loadForGroup(repository, groupUuid);
  }

  @Test
  public void idInConfigMustNotBeNegative() throws Exception {
    populateGroupConfig(
        groupUuid, "[group]\n\tname = users\n\tid = -5\n\townerGroupUuid = owners\n");

    expectedException.expect(ConfigInvalidException.class);
    expectedException.expectMessage("ID of the group users-XYZ");
    GroupConfig.loadForGroup(repository, groupUuid);
  }

  @Test
  public void ownerUuidInConfigMustBeDefined() throws Exception {
    populateGroupConfig(groupUuid, "[group]\n\tname = users\n\tid = 42\n");

    expectedException.expect(ConfigInvalidException.class);
    expectedException.expectMessage("Owner UUID of the group users-XYZ");
    GroupConfig.loadForGroup(repository, groupUuid);
  }

  @Test
  public void nameCannotBeUpdatedToNull() throws Exception {
    populateGroupConfig(
        groupUuid, "[group]\n\tname = users\n\tid = 42\n\townerGroupUuid = owners\n");

    GroupConfig groupConfig = GroupConfig.loadForGroup(repository, groupUuid);
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder().setName(new AccountGroup.NameKey(null)).build();
    groupConfig.setGroupUpdate(groupUpdate, Account.Id::toString, AccountGroup.UUID::get);

    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      expectedException.expectCause(instanceOf(ConfigInvalidException.class));
      expectedException.expectMessage("Name of the group users-XYZ");
      groupConfig.commit(metaDataUpdate);
    }
  }

  @Test
  public void nameCannotBeUpdatedToEmptyString() throws Exception {
    populateGroupConfig(
        groupUuid, "[group]\n\tname = users\n\tid = 42\n\townerGroupUuid = owners\n");

    GroupConfig groupConfig = GroupConfig.loadForGroup(repository, groupUuid);
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder().setName(new AccountGroup.NameKey("")).build();
    groupConfig.setGroupUpdate(groupUpdate, Account.Id::toString, AccountGroup.UUID::get);

    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      expectedException.expectCause(instanceOf(ConfigInvalidException.class));
      expectedException.expectMessage("Name of the group users-XYZ");
      groupConfig.commit(metaDataUpdate);
    }
  }

  @Test
  public void ownerUuidCannotBeUpdatedToNull() throws Exception {
    populateGroupConfig(
        groupUuid, "[group]\n\tname = users\n\tid = 42\n\townerGroupUuid = owners\n");

    GroupConfig groupConfig = GroupConfig.loadForGroup(repository, groupUuid);
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder().setOwnerGroupUUID(new AccountGroup.UUID(null)).build();
    groupConfig.setGroupUpdate(groupUpdate, Account.Id::toString, AccountGroup.UUID::get);

    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      expectedException.expectCause(instanceOf(ConfigInvalidException.class));
      expectedException.expectMessage("Owner UUID of the group users-XYZ");
      groupConfig.commit(metaDataUpdate);
    }
  }

  @Test
  public void ownerUuidCannotBeUpdatedToEmptyString() throws Exception {
    populateGroupConfig(
        groupUuid, "[group]\n\tname = users\n\tid = 42\n\townerGroupUuid = owners\n");

    GroupConfig groupConfig = GroupConfig.loadForGroup(repository, groupUuid);
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder().setOwnerGroupUUID(new AccountGroup.UUID("")).build();
    groupConfig.setGroupUpdate(groupUpdate, Account.Id::toString, AccountGroup.UUID::get);

    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      expectedException.expectCause(instanceOf(ConfigInvalidException.class));
      expectedException.expectMessage("Owner UUID of the group users-XYZ");
      groupConfig.commit(metaDataUpdate);
    }
  }

  private InternalGroupCreation.Builder getPrefilledGroupCreationBuilder() {
    return InternalGroupCreation.builder()
        .setGroupUUID(groupUuid)
        .setNameKey(groupName)
        .setId(groupId);
  }

  private void populateGroupConfig(AccountGroup.UUID uuid, String fileContent) throws Exception {
    testRepository
        .branch(RefNames.refsGroups(uuid))
        .commit()
        .message("Prepopulate group.config")
        .add(GroupConfig.GROUP_CONFIG_FILE, fileContent)
        .create();
  }

  private MetaDataUpdate createMetaDataUpdate() {
    TimeZone tz = TimeZone.getTimeZone("America/Los_Angeles");
    PersonIdent serverIdent =
        new PersonIdent("Gerrit Server", "noreply@gerritcodereview.com", TimeUtil.nowTs(), tz);

    MetaDataUpdate metaDataUpdate =
        new MetaDataUpdate(
            GitReferenceUpdated.DISABLED, new Project.NameKey("Test Repository"), repository);
    metaDataUpdate.getCommitBuilder().setCommitter(serverIdent);
    metaDataUpdate.getCommitBuilder().setAuthor(serverIdent);
    return metaDataUpdate;
  }
}
