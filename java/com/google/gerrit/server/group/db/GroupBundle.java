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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.reviewdb.server.ReviewDbUtil.checkColumns;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;

/**
 * A bundle of all entities rooted at a single {@link AccountGroup} entity.
 *
 * <p>Used primarily during the migration process. Most callers should prefer {@link InternalGroup}
 * instead.
 */
@AutoValue
public abstract class GroupBundle {
  static {
    // Initialization-time checks that the column set hasn't changed since the
    // last time this file was updated.
    checkColumns(AccountGroup.NameKey.class, 1);
    checkColumns(AccountGroup.UUID.class, 1);
    checkColumns(AccountGroup.Id.class, 1);
    checkColumns(AccountGroup.class, 1, 2, 4, 7, 9, 10, 11);

    checkColumns(AccountGroupById.Key.class, 1, 2);
    checkColumns(AccountGroupById.class, 1);

    checkColumns(AccountGroupByIdAud.Key.class, 1, 2, 3);
    checkColumns(AccountGroupByIdAud.class, 1, 2, 3, 4);

    checkColumns(AccountGroupMember.Key.class, 1, 2);
    checkColumns(AccountGroupMember.class, 1);

    checkColumns(AccountGroupMemberAudit.Key.class, 1, 2, 3);
    checkColumns(AccountGroupMemberAudit.class, 1, 2, 3, 4);
  }

  @Singleton
  public static class Factory {
    private final AuditLogReader auditLogReader;

    @Inject
    Factory(AuditLogReader auditLogReader) {
      this.auditLogReader = auditLogReader;
    }

    public GroupBundle fromReviewDb(ReviewDb db, AccountGroup.Id id) throws OrmException {
      AccountGroup group = db.accountGroups().get(id);
      if (group == null) {
        throw new OrmException("Group " + id + " not found");
      }
      return create(
          group,
          db.accountGroupMembers().byGroup(id),
          db.accountGroupMembersAudit().byGroup(id),
          db.accountGroupById().byGroup(id),
          db.accountGroupByIdAud().byGroup(id));
    }

    public GroupBundle fromNoteDb(Repository repo, AccountGroup.UUID uuid)
        throws ConfigInvalidException, IOException {
      GroupConfig groupConfig = GroupConfig.loadForGroup(repo, uuid);
      InternalGroup internalGroup = groupConfig.getLoadedGroup().get();
      AccountGroup.Id groupId = internalGroup.getId();

      AccountGroup accountGroup =
          new AccountGroup(
              internalGroup.getNameKey(),
              internalGroup.getId(),
              internalGroup.getGroupUUID(),
              internalGroup.getCreatedOn());
      accountGroup.setDescription(internalGroup.getDescription());
      accountGroup.setOwnerGroupUUID(internalGroup.getOwnerGroupUUID());
      accountGroup.setVisibleToAll(internalGroup.isVisibleToAll());

      return create(
          accountGroup,
          internalGroup
              .getMembers()
              .stream()
              .map(
                  accountId ->
                      new AccountGroupMember(new AccountGroupMember.Key(accountId, groupId)))
              .collect(toImmutableSet()),
          auditLogReader.getMembersAudit(uuid),
          internalGroup
              .getSubgroups()
              .stream()
              .map(
                  subgroupUuid ->
                      new AccountGroupById(new AccountGroupById.Key(groupId, subgroupUuid)))
              .collect(toImmutableSet()),
          auditLogReader.getSubgroupsAudit(uuid));
    }
  }

  public static GroupBundle create(
      AccountGroup group,
      Iterable<AccountGroupMember> members,
      Iterable<AccountGroupMemberAudit> memberAudit,
      Iterable<AccountGroupById> byId,
      Iterable<AccountGroupByIdAud> byIdAudit) {
    return new AutoValue_GroupBundle.Builder()
        .group(group)
        .members(members)
        .memberAudit(memberAudit)
        .byId(byId)
        .byIdAudit(byIdAudit)
        .build();
  }

  static Builder builder() {
    return new AutoValue_GroupBundle.Builder().members().memberAudit().byId().byIdAudit();
  }

  public AccountGroup.Id id() {
    return group().getId();
  }

  public AccountGroup.UUID uuid() {
    return group().getGroupUUID();
  }

  public abstract AccountGroup group();

  public abstract ImmutableSet<AccountGroupMember> members();

  public abstract ImmutableSet<AccountGroupMemberAudit> memberAudit();

  public abstract ImmutableSet<AccountGroupById> byId();

  public abstract ImmutableSet<AccountGroupByIdAud> byIdAudit();

  public abstract Builder toBuilder();

  public GroupBundle roundToSecond() {
    AccountGroup newGroup = new AccountGroup(group());
    if (newGroup.getCreatedOn() != null) {
      newGroup.setCreatedOn(TimeUtil.roundToSecond(newGroup.getCreatedOn()));
    }
    return toBuilder()
        .group(newGroup)
        .memberAudit(
            memberAudit().stream().map(GroupBundle::roundToSecond).collect(toImmutableSet()))
        .byIdAudit(byIdAudit().stream().map(GroupBundle::roundToSecond).collect(toImmutableSet()))
        .build();
  }

  private static AccountGroupMemberAudit roundToSecond(AccountGroupMemberAudit a) {
    AccountGroupMemberAudit result =
        new AccountGroupMemberAudit(
            new AccountGroupMemberAudit.Key(
                a.getKey().getParentKey(),
                a.getKey().getGroupId(),
                TimeUtil.roundToSecond(a.getKey().getAddedOn())),
            a.getAddedBy());
    if (a.getRemovedOn() != null) {
      result.removed(a.getRemovedBy(), TimeUtil.roundToSecond(a.getRemovedOn()));
    }
    return result;
  }

  private static AccountGroupByIdAud roundToSecond(AccountGroupByIdAud a) {
    AccountGroupByIdAud result =
        new AccountGroupByIdAud(
            new AccountGroupByIdAud.Key(
                a.getKey().getParentKey(),
                a.getKey().getIncludeUUID(),
                TimeUtil.roundToSecond(a.getKey().getAddedOn())),
            a.getAddedBy());
    if (a.getRemovedOn() != null) {
      result.removed(a.getRemovedBy(), TimeUtil.roundToSecond(a.getRemovedOn()));
    }
    return result;
  }

  public InternalGroup toInternalGroup() {
    return InternalGroup.create(
        group(),
        members().stream().map(AccountGroupMember::getAccountId).collect(toImmutableSet()),
        byId().stream().map(AccountGroupById::getIncludeUUID).collect(toImmutableSet()));
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder group(AccountGroup group);

    abstract Builder members(AccountGroupMember... member);

    abstract Builder members(Iterable<AccountGroupMember> member);

    abstract Builder memberAudit(AccountGroupMemberAudit... audit);

    abstract Builder memberAudit(Iterable<AccountGroupMemberAudit> audit);

    abstract Builder byId(AccountGroupById... byId);

    abstract Builder byId(Iterable<AccountGroupById> byId);

    abstract Builder byIdAudit(AccountGroupByIdAud... audit);

    abstract Builder byIdAudit(Iterable<AccountGroupByIdAud> audit);

    abstract GroupBundle build();
  }
}
