// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.group;

import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.exceptions.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.GroupUUID;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.db.Groups;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.group.db.InternalGroupCreation;
import com.google.gerrit.server.group.db.InternalGroupUpdate;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * The implementation of {@code GroupOperations}.
 *
 * <p>There is only one implementation of {@code GroupOperations}. Nevertheless, we keep the
 * separation between interface and implementation to enhance clarity.
 */
public class GroupOperationsImpl implements GroupOperations {
  private final Groups groups;
  private final GroupsUpdate groupsUpdate;
  private final Sequences seq;
  private final PersonIdent serverIdent;

  @Inject
  public GroupOperationsImpl(
      Groups groups,
      @ServerInitiated GroupsUpdate groupsUpdate,
      Sequences seq,
      @GerritPersonIdent PersonIdent serverIdent) {
    this.groups = groups;
    this.groupsUpdate = groupsUpdate;
    this.seq = seq;
    this.serverIdent = serverIdent;
  }

  @Override
  public PerGroupOperations group(AccountGroup.UUID groupUuid) {
    return new PerGroupOperationsImpl(groupUuid);
  }

  @Override
  public TestGroupCreation.Builder newGroup() {
    return TestGroupCreation.builder(this::createNewGroup);
  }

  private AccountGroup.UUID createNewGroup(TestGroupCreation groupCreation)
      throws ConfigInvalidException, IOException, OrmException {
    InternalGroupCreation internalGroupCreation = toInternalGroupCreation(groupCreation);
    InternalGroupUpdate internalGroupUpdate = toInternalGroupUpdate(groupCreation);
    InternalGroup internalGroup =
        groupsUpdate.createGroup(internalGroupCreation, internalGroupUpdate);
    return internalGroup.getGroupUUID();
  }

  private InternalGroupCreation toInternalGroupCreation(TestGroupCreation groupCreation)
      throws OrmException {
    AccountGroup.Id groupId = new AccountGroup.Id(seq.nextGroupId());
    String groupName = groupCreation.name().orElse("group-with-id-" + groupId.get());
    AccountGroup.UUID groupUuid = GroupUUID.make(groupName, serverIdent);
    AccountGroup.NameKey nameKey = new AccountGroup.NameKey(groupName);
    return InternalGroupCreation.builder()
        .setId(groupId)
        .setGroupUUID(groupUuid)
        .setNameKey(nameKey)
        .build();
  }

  private static InternalGroupUpdate toInternalGroupUpdate(TestGroupCreation groupCreation) {
    InternalGroupUpdate.Builder builder = InternalGroupUpdate.builder();
    groupCreation.description().ifPresent(builder::setDescription);
    groupCreation.ownerGroupUuid().ifPresent(builder::setOwnerGroupUUID);
    groupCreation.visibleToAll().ifPresent(builder::setVisibleToAll);
    builder.setMemberModification(originalMembers -> groupCreation.members());
    builder.setSubgroupModification(originalSubgroups -> groupCreation.subgroups());
    return builder.build();
  }

  private class PerGroupOperationsImpl implements PerGroupOperations {
    private final AccountGroup.UUID groupUuid;

    PerGroupOperationsImpl(AccountGroup.UUID groupUuid) {
      this.groupUuid = groupUuid;
    }

    @Override
    public boolean exists() {
      return getGroup(groupUuid).isPresent();
    }

    @Override
    public TestGroup get() {
      Optional<InternalGroup> group = getGroup(groupUuid);
      checkState(group.isPresent(), "Tried to get non-existing test group");
      return toTestGroup(group.get());
    }

    private Optional<InternalGroup> getGroup(AccountGroup.UUID groupUuid) {
      try {
        return groups.getGroup(groupUuid);
      } catch (IOException | ConfigInvalidException e) {
        throw new IllegalStateException(e);
      }
    }

    private TestGroup toTestGroup(InternalGroup internalGroup) {
      return TestGroup.builder()
          .groupUuid(internalGroup.getGroupUUID())
          .groupId(internalGroup.getId())
          .nameKey(internalGroup.getNameKey())
          .description(Optional.ofNullable(internalGroup.getDescription()))
          .ownerGroupUuid(internalGroup.getOwnerGroupUUID())
          .visibleToAll(internalGroup.isVisibleToAll())
          .createdOn(internalGroup.getCreatedOn())
          .members(internalGroup.getMembers())
          .subgroups(internalGroup.getSubgroups())
          .build();
    }

    @Override
    public TestGroupUpdate.Builder forUpdate() {
      return TestGroupUpdate.builder(this::updateGroup);
    }

    private void updateGroup(TestGroupUpdate groupUpdate)
        throws OrmDuplicateKeyException, NoSuchGroupException, ConfigInvalidException, IOException {
      InternalGroupUpdate internalGroupUpdate = toInternalGroupUpdate(groupUpdate);
      groupsUpdate.updateGroup(groupUuid, internalGroupUpdate);
    }

    private InternalGroupUpdate toInternalGroupUpdate(TestGroupUpdate groupUpdate) {
      InternalGroupUpdate.Builder builder = InternalGroupUpdate.builder();
      groupUpdate.name().map(AccountGroup.NameKey::new).ifPresent(builder::setName);
      groupUpdate.description().ifPresent(builder::setDescription);
      groupUpdate.ownerGroupUuid().ifPresent(builder::setOwnerGroupUUID);
      groupUpdate.visibleToAll().ifPresent(builder::setVisibleToAll);
      builder.setMemberModification(groupUpdate.memberModification()::apply);
      builder.setSubgroupModification(groupUpdate.subgroupModification()::apply);
      return builder.build();
    }
  }
}
