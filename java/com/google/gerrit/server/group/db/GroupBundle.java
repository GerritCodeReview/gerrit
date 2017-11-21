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
import java.util.Collection;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A bundle of all entities rooted at a single {@link AccountGroup} entity.
 *
 * <p>Used primarily during the migration process. Most callers should prefer {@link InternalGroup}
 * instead.
 */
@AutoValue
public abstract class GroupBundle {
  private static final Logger log = LoggerFactory.getLogger(GroupBundle.class);

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

  public enum Source {
    REVIEW_DB("ReviewDb"),
    NOTE_DB("NoteDb");

    private final String name;

    private Source(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }
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
          Source.REVIEW_DB,
          group,
          db.accountGroupMembers().byGroup(id).toList(),
          db.accountGroupMembersAudit().byGroup(id).toList(),
          db.accountGroupById().byGroup(id).toList(),
          db.accountGroupByIdAud().byGroup(id).toList());
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
          Source.NOTE_DB,
          accountGroup,
          internalGroup
              .getMembers()
              .stream()
              .map(
                  accountId ->
                      new AccountGroupMember(new AccountGroupMember.Key(accountId, groupId)))
              .collect(toImmutableSet()),
          auditLogReader.getMembersAudit(repo, uuid),
          internalGroup
              .getSubgroups()
              .stream()
              .map(
                  subgroupUuid ->
                      new AccountGroupById(new AccountGroupById.Key(groupId, subgroupUuid)))
              .collect(toImmutableSet()),
          auditLogReader.getSubgroupsAudit(repo, uuid));
    }
  }

  public static GroupBundle create(
      Source source,
      AccountGroup group,
      Collection<AccountGroupMember> members,
      Collection<AccountGroupMemberAudit> memberAudit,
      Collection<AccountGroupById> byId,
      Collection<AccountGroupByIdAud> byIdAudit) {
    AccountGroup.UUID uuid = group.getGroupUUID();
    return new AutoValue_GroupBundle.Builder()
        .group(group)
        .members(logIfNotUnique(source, uuid, members, AccountGroupMember.class))
        .memberAudit(logIfNotUnique(source, uuid, memberAudit, AccountGroupMemberAudit.class))
        .byId(logIfNotUnique(source, uuid, byId, AccountGroupById.class))
        .byIdAudit(logIfNotUnique(source, uuid, byIdAudit, AccountGroupByIdAud.class))
        .build();
  }

  private static <T> ImmutableSet<T> logIfNotUnique(
      Source source, AccountGroup.UUID uuid, Collection<T> collection, Class<T> clazz) {
    ImmutableSet<T> set = ImmutableSet.copyOf(collection);
    if (set.size() != collection.size()) {
      // One way this can happen is that distinct audit entities can compare equal, because
      // AccountGroup{MemberAudit,ByIdAud}.Key does not include the addedOn timestamp in its
      // members() list. However, this particular issue only applies to pure adds, since removedOn
      // *is* included in equality. As a result, if this happens, it means the audit log is already
      // corrupt, and it's not clear if we can programmatically repair it. For migrating to NoteDb,
      // we'll try our best to recreate it, but no guarantees it will match the real sequence of
      // attempted operations, which is in any case lost in the mists of time.
      log.warn(
          "group {} in {} has duplicate {} entities: {}",
          uuid,
          source,
          clazz.getSimpleName(),
          collection);
    }
    return set;
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

  public GroupBundle truncateToSecond() {
    AccountGroup newGroup = new AccountGroup(group());
    if (newGroup.getCreatedOn() != null) {
      newGroup.setCreatedOn(TimeUtil.truncateToSecond(newGroup.getCreatedOn()));
    }
    return toBuilder()
        .group(newGroup)
        .memberAudit(
            memberAudit().stream().map(GroupBundle::truncateToSecond).collect(toImmutableSet()))
        .byIdAudit(
            byIdAudit().stream().map(GroupBundle::truncateToSecond).collect(toImmutableSet()))
        .build();
  }

  private static AccountGroupMemberAudit truncateToSecond(AccountGroupMemberAudit a) {
    AccountGroupMemberAudit result =
        new AccountGroupMemberAudit(
            new AccountGroupMemberAudit.Key(
                a.getKey().getParentKey(),
                a.getKey().getGroupId(),
                TimeUtil.truncateToSecond(a.getKey().getAddedOn())),
            a.getAddedBy());
    if (a.getRemovedOn() != null) {
      result.removed(a.getRemovedBy(), TimeUtil.truncateToSecond(a.getRemovedOn()));
    }
    return result;
  }

  private static AccountGroupByIdAud truncateToSecond(AccountGroupByIdAud a) {
    AccountGroupByIdAud result =
        new AccountGroupByIdAud(
            new AccountGroupByIdAud.Key(
                a.getKey().getParentKey(),
                a.getKey().getIncludeUUID(),
                TimeUtil.truncateToSecond(a.getKey().getAddedOn())),
            a.getAddedBy());
    if (a.getRemovedOn() != null) {
      result.removed(a.getRemovedBy(), TimeUtil.truncateToSecond(a.getRemovedOn()));
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
