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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.reviewdb.server.ReviewDbUtil.checkColumns;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;
import static java.util.stream.Collectors.toList;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbWrapper;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.db.AuditLogReader;
import com.google.gerrit.server.group.db.GroupConfig;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;

/**
 * A bundle of all entities rooted at a single {@link AccountGroup} entity.
 *
 * <p>Used primarily during the migration process. Most callers should prefer {@link InternalGroup}
 * instead.
 */
@AutoValue
abstract class GroupBundle {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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

    public static GroupBundle fromReviewDb(ReviewDb db, AccountGroup.UUID groupUuid)
        throws OrmException {
      JdbcSchema jdbcSchema = ReviewDbWrapper.unwrapJbdcSchema(db);
      AccountGroup group = readAccountGroupFromReviewDb(jdbcSchema, groupUuid);
      AccountGroup.Id groupId = group.getId();

      return create(
          Source.REVIEW_DB,
          group,
          readAccountGroupMembersFromReviewDb(jdbcSchema, groupId),
          readAccountGroupMemberAuditsFromReviewDb(jdbcSchema, groupId),
          readAccountGroupSubgroupsFromReviewDb(jdbcSchema, groupId),
          readAccountGroupSubgroupAuditsFromReviewDb(jdbcSchema, groupId));
    }

    private static AccountGroup readAccountGroupFromReviewDb(
        JdbcSchema jdbcSchema, AccountGroup.UUID groupUuid) throws OrmException {
      try (Statement stmt = jdbcSchema.getConnection().createStatement();
          ResultSet rs =
              stmt.executeQuery(
                  "SELECT group_id,"
                      + " name,"
                      + " created_on,"
                      + " description,"
                      + " owner_group_uuid,"
                      + " visible_to_all"
                      + " FROM account_groups"
                      + " WHERE group_uuid = '"
                      + groupUuid.get()
                      + "'")) {
        if (!rs.next()) {
          throw new OrmException(String.format("Group %s not found", groupUuid));
        }

        AccountGroup.Id groupId = new AccountGroup.Id(rs.getInt(1));
        AccountGroup.NameKey groupName = new AccountGroup.NameKey(rs.getString(2));
        Timestamp createdOn = rs.getTimestamp(3);
        String description = rs.getString(4);
        AccountGroup.UUID ownerGroupUuid = new AccountGroup.UUID(rs.getString(5));
        boolean visibleToAll = "Y".equals(rs.getString(6));

        AccountGroup group = new AccountGroup(groupName, groupId, groupUuid, createdOn);
        group.setDescription(description);
        group.setOwnerGroupUUID(ownerGroupUuid);
        group.setVisibleToAll(visibleToAll);

        if (rs.next()) {
          throw new OrmException(String.format("Group UUID %s is ambiguous", groupUuid));
        }

        return group;
      } catch (SQLException e) {
        throw new OrmException(
            String.format("Failed to read account group %s from ReviewDb", groupUuid.get()), e);
      }
    }

    private static List<AccountGroupMember> readAccountGroupMembersFromReviewDb(
        JdbcSchema jdbcSchema, AccountGroup.Id groupId) throws OrmException {
      try (Statement stmt = jdbcSchema.getConnection().createStatement();
          ResultSet rs =
              stmt.executeQuery(
                  "SELECT account_id"
                      + " FROM account_group_members"
                      + " WHERE group_id = '"
                      + groupId.get()
                      + "'")) {
        List<AccountGroupMember> members = new ArrayList<>();
        while (rs.next()) {
          Account.Id accountId = new Account.Id(rs.getInt(1));
          members.add(new AccountGroupMember(new AccountGroupMember.Key(accountId, groupId)));
        }
        return members;
      } catch (SQLException e) {
        throw new OrmException(
            String.format(
                "Failed to read members of account group %s from ReviewDb", groupId.get()),
            e);
      }
    }

    private static List<AccountGroupMemberAudit> readAccountGroupMemberAuditsFromReviewDb(
        JdbcSchema jdbcSchema, AccountGroup.Id groupId) throws OrmException {
      try (Statement stmt = jdbcSchema.getConnection().createStatement();
          ResultSet rs =
              stmt.executeQuery(
                  "SELECT account_id, added_by, added_on, removed_by, removed_on"
                      + " FROM account_group_members_audit"
                      + " WHERE group_id = '"
                      + groupId.get()
                      + "'")) {
        List<AccountGroupMemberAudit> audits = new ArrayList<>();
        while (rs.next()) {
          Account.Id accountId = new Account.Id(rs.getInt(1));

          Account.Id addedBy = new Account.Id(rs.getInt(2));
          Timestamp addedOn = rs.getTimestamp(3);

          Timestamp removedOn = rs.getTimestamp(5);
          Account.Id removedBy = removedOn != null ? new Account.Id(rs.getInt(4)) : null;

          AccountGroupMemberAudit.Key key =
              new AccountGroupMemberAudit.Key(accountId, groupId, addedOn);
          AccountGroupMemberAudit audit = new AccountGroupMemberAudit(key, addedBy);
          audit.removed(removedBy, removedOn);
          audits.add(audit);
        }
        return audits;
      } catch (SQLException e) {
        throw new OrmException(
            String.format(
                "Failed to read member audits of account group %s from ReviewDb", groupId.get()),
            e);
      }
    }

    private static List<AccountGroupById> readAccountGroupSubgroupsFromReviewDb(
        JdbcSchema jdbcSchema, AccountGroup.Id groupId) throws OrmException {
      try (Statement stmt = jdbcSchema.getConnection().createStatement();
          ResultSet rs =
              stmt.executeQuery(
                  "SELECT include_uuid"
                      + " FROM account_group_by_id"
                      + " WHERE group_id = '"
                      + groupId.get()
                      + "'")) {
        List<AccountGroupById> subgroups = new ArrayList<>();
        while (rs.next()) {
          AccountGroup.UUID includedGroupUuid = new AccountGroup.UUID(rs.getString(1));
          subgroups.add(new AccountGroupById(new AccountGroupById.Key(groupId, includedGroupUuid)));
        }
        return subgroups;
      } catch (SQLException e) {
        throw new OrmException(
            String.format(
                "Failed to read subgroups of account group %s from ReviewDb", groupId.get()),
            e);
      }
    }

    private static List<AccountGroupByIdAud> readAccountGroupSubgroupAuditsFromReviewDb(
        JdbcSchema jdbcSchema, AccountGroup.Id groupId) throws OrmException {
      try (Statement stmt = jdbcSchema.getConnection().createStatement();
          ResultSet rs =
              stmt.executeQuery(
                  "SELECT include_uuid, added_by, added_on, removed_by, removed_on"
                      + " FROM account_group_by_id_aud"
                      + " WHERE group_id = '"
                      + groupId.get()
                      + "'")) {
        List<AccountGroupByIdAud> audits = new ArrayList<>();
        while (rs.next()) {
          AccountGroup.UUID includedGroupUuid = new AccountGroup.UUID(rs.getString(1));

          Account.Id addedBy = new Account.Id(rs.getInt(2));
          Timestamp addedOn = rs.getTimestamp(3);

          Timestamp removedOn = rs.getTimestamp(5);
          Account.Id removedBy = removedOn != null ? new Account.Id(rs.getInt(4)) : null;

          AccountGroupByIdAud.Key key =
              new AccountGroupByIdAud.Key(groupId, includedGroupUuid, addedOn);
          AccountGroupByIdAud audit = new AccountGroupByIdAud(key, addedBy);
          audit.removed(removedBy, removedOn);
          audits.add(audit);
        }
        return audits;
      } catch (SQLException e) {
        throw new OrmException(
            String.format(
                "Failed to read subgroup audits of account group %s from ReviewDb", groupId.get()),
            e);
      }
    }
  }

  private static final Comparator<AccountGroupMember> ACCOUNT_GROUP_MEMBER_COMPARATOR =
      Comparator.comparingInt((AccountGroupMember m) -> m.getAccountGroupId().get())
          .thenComparingInt(m -> m.getAccountId().get());

  private static final Comparator<AccountGroupMemberAudit> ACCOUNT_GROUP_MEMBER_AUDIT_COMPARATOR =
      Comparator.comparingInt((AccountGroupMemberAudit a) -> a.getGroupId().get())
          .thenComparing(AccountGroupMemberAudit::getAddedOn)
          .thenComparingInt(a -> a.getAddedBy().get())
          .thenComparingInt(a -> a.getMemberId().get())
          .thenComparing(
              a -> a.getRemovedBy() != null ? a.getRemovedBy().get() : null,
              nullsLast(naturalOrder()))
          .thenComparing(AccountGroupMemberAudit::getRemovedOn, nullsLast(naturalOrder()));

  private static final Comparator<AccountGroupById> ACCOUNT_GROUP_BY_ID_COMPARATOR =
      Comparator.comparingInt((AccountGroupById m) -> m.getGroupId().get())
          .thenComparing(AccountGroupById::getIncludeUUID);

  private static final Comparator<AccountGroupByIdAud> ACCOUNT_GROUP_BY_ID_AUD_COMPARATOR =
      Comparator.comparingInt((AccountGroupByIdAud a) -> a.getGroupId().get())
          .thenComparing(AccountGroupByIdAud::getAddedOn)
          .thenComparingInt(a -> a.getAddedBy().get())
          .thenComparing(AccountGroupByIdAud::getIncludeUUID)
          .thenComparing(
              a -> a.getRemovedBy() != null ? a.getRemovedBy().get() : null,
              nullsLast(naturalOrder()))
          .thenComparing(AccountGroupByIdAud::getRemovedOn, nullsLast(naturalOrder()));

  private static final Comparator<AuditEntry> AUDIT_ENTRY_COMPARATOR =
      Comparator.comparing(AuditEntry::getTimestamp)
          .thenComparing(AuditEntry::getAction, Comparator.comparingInt(Action::getOrder));

  public static GroupBundle create(
      Source source,
      AccountGroup group,
      Iterable<AccountGroupMember> members,
      Iterable<AccountGroupMemberAudit> memberAudit,
      Iterable<AccountGroupById> byId,
      Iterable<AccountGroupByIdAud> byIdAudit) {
    AccountGroup.UUID uuid = group.getGroupUUID();
    return new AutoValue_GroupBundle.Builder()
        .source(source)
        .group(group)
        .members(
            logIfNotUnique(
                source, uuid, members, ACCOUNT_GROUP_MEMBER_COMPARATOR, AccountGroupMember.class))
        .memberAudit(
            logIfNotUnique(
                source,
                uuid,
                memberAudit,
                ACCOUNT_GROUP_MEMBER_AUDIT_COMPARATOR,
                AccountGroupMemberAudit.class))
        .byId(
            logIfNotUnique(
                source, uuid, byId, ACCOUNT_GROUP_BY_ID_COMPARATOR, AccountGroupById.class))
        .byIdAudit(
            logIfNotUnique(
                source,
                uuid,
                byIdAudit,
                ACCOUNT_GROUP_BY_ID_AUD_COMPARATOR,
                AccountGroupByIdAud.class))
        .build();
  }

  private static <T> ImmutableSet<T> logIfNotUnique(
      Source source,
      AccountGroup.UUID uuid,
      Iterable<T> iterable,
      Comparator<T> comparator,
      Class<T> clazz) {
    List<T> list = Streams.stream(iterable).sorted(comparator).collect(toList());
    ImmutableSet<T> set = ImmutableSet.copyOf(list);
    if (set.size() != list.size()) {
      // One way this can happen is that distinct audit entities can compare equal, because
      // AccountGroup{MemberAudit,ByIdAud}.Key does not include the addedOn timestamp in its
      // members() list. However, this particular issue only applies to pure adds, since removedOn
      // *is* included in equality. As a result, if this happens, it means the audit log is already
      // corrupt, and it's not clear if we can programmatically repair it. For migrating to NoteDb,
      // we'll try our best to recreate it, but no guarantees it will match the real sequence of
      // attempted operations, which is in any case lost in the mists of time.
      logger.atWarning().log(
          "group %s in %s has duplicate %s entities: %s",
          uuid, source, clazz.getSimpleName(), iterable);
    }
    return set;
  }

  static Builder builder() {
    return new AutoValue_GroupBundle.Builder().members().memberAudit().byId().byIdAudit();
  }

  public static ImmutableList<String> compareWithAudits(
      GroupBundle reviewDbBundle, GroupBundle noteDbBundle) {
    return compare(reviewDbBundle, noteDbBundle, true);
  }

  public static ImmutableList<String> compareWithoutAudits(
      GroupBundle reviewDbBundle, GroupBundle noteDbBundle) {
    return compare(reviewDbBundle, noteDbBundle, false);
  }

  private static ImmutableList<String> compare(
      GroupBundle reviewDbBundle, GroupBundle noteDbBundle, boolean compareAudits) {
    // Normalize the ReviewDb bundle to what we expect in NoteDb. This means that values in error
    // messages will not reflect the actual data in ReviewDb, but it will make it easier for humans
    // to see the difference.
    reviewDbBundle = reviewDbBundle.truncateToSecond();
    AccountGroup reviewDbGroup = new AccountGroup(reviewDbBundle.group());
    reviewDbGroup.setDescription(Strings.emptyToNull(reviewDbGroup.getDescription()));
    reviewDbBundle = reviewDbBundle.toBuilder().group(reviewDbGroup).build();

    checkArgument(
        reviewDbBundle.source() == Source.REVIEW_DB,
        "first bundle's source must be %s: %s",
        Source.REVIEW_DB,
        reviewDbBundle);
    checkArgument(
        noteDbBundle.source() == Source.NOTE_DB,
        "second bundle's source must be %s: %s",
        Source.NOTE_DB,
        noteDbBundle);

    ImmutableList.Builder<String> result = ImmutableList.builder();
    if (!reviewDbBundle.group().equals(noteDbBundle.group())) {
      result.add(
          "AccountGroups differ\n"
              + ("ReviewDb: " + reviewDbBundle.group() + "\n")
              + ("NoteDb  : " + noteDbBundle.group()));
    }
    if (!reviewDbBundle.members().equals(noteDbBundle.members())) {
      result.add(
          "AccountGroupMembers differ\n"
              + ("ReviewDb: " + reviewDbBundle.members() + "\n")
              + ("NoteDb  : " + noteDbBundle.members()));
    }
    if (compareAudits
        && !areMemberAuditsConsideredEqual(
            reviewDbBundle.memberAudit(), noteDbBundle.memberAudit())) {
      result.add(
          "AccountGroupMemberAudits differ\n"
              + ("ReviewDb: " + reviewDbBundle.memberAudit() + "\n")
              + ("NoteDb  : " + noteDbBundle.memberAudit()));
    }
    if (!reviewDbBundle.byId().equals(noteDbBundle.byId())) {
      result.add(
          "AccountGroupByIds differ\n"
              + ("ReviewDb: " + reviewDbBundle.byId() + "\n")
              + ("NoteDb  : " + noteDbBundle.byId()));
    }
    if (compareAudits
        && !areByIdAuditsConsideredEqual(reviewDbBundle.byIdAudit(), noteDbBundle.byIdAudit())) {
      result.add(
          "AccountGroupByIdAudits differ\n"
              + ("ReviewDb: " + reviewDbBundle.byIdAudit() + "\n")
              + ("NoteDb  : " + noteDbBundle.byIdAudit()));
    }
    return result.build();
  }

  private static boolean areMemberAuditsConsideredEqual(
      ImmutableSet<AccountGroupMemberAudit> reviewDbMemberAudits,
      ImmutableSet<AccountGroupMemberAudit> noteDbMemberAudits) {
    ListMultimap<String, AuditEntry> reviewDbMemberAuditsByMemberId =
        toMemberAuditEntriesByMemberId(reviewDbMemberAudits);
    ListMultimap<String, AuditEntry> noteDbMemberAuditsByMemberId =
        toMemberAuditEntriesByMemberId(noteDbMemberAudits);

    return areConsideredEqual(reviewDbMemberAuditsByMemberId, noteDbMemberAuditsByMemberId);
  }

  private static boolean areByIdAuditsConsideredEqual(
      ImmutableSet<AccountGroupByIdAud> reviewDbByIdAudits,
      ImmutableSet<AccountGroupByIdAud> noteDbByIdAudits) {
    ListMultimap<String, AuditEntry> reviewDbByIdAuditsById =
        toByIdAuditEntriesById(reviewDbByIdAudits);
    ListMultimap<String, AuditEntry> noteDbByIdAuditsById =
        toByIdAuditEntriesById(noteDbByIdAudits);

    return areConsideredEqual(reviewDbByIdAuditsById, noteDbByIdAuditsById);
  }

  private static ListMultimap<String, AuditEntry> toMemberAuditEntriesByMemberId(
      ImmutableSet<AccountGroupMemberAudit> memberAudits) {
    return memberAudits
        .stream()
        .flatMap(GroupBundle::toAuditEntries)
        .collect(
            Multimaps.toMultimap(
                AuditEntry::getTarget,
                Function.identity(),
                MultimapBuilder.hashKeys().arrayListValues()::build));
  }

  private static Stream<AuditEntry> toAuditEntries(AccountGroupMemberAudit memberAudit) {
    AuditEntry additionAuditEntry =
        AuditEntry.create(
            Action.ADD,
            memberAudit.getAddedBy(),
            memberAudit.getMemberId(),
            memberAudit.getAddedOn());
    if (memberAudit.isActive()) {
      return Stream.of(additionAuditEntry);
    }

    AuditEntry removalAuditEntry =
        AuditEntry.create(
            Action.REMOVE,
            memberAudit.getRemovedBy(),
            memberAudit.getMemberId(),
            memberAudit.getRemovedOn());
    return Stream.of(additionAuditEntry, removalAuditEntry);
  }

  private static ListMultimap<String, AuditEntry> toByIdAuditEntriesById(
      ImmutableSet<AccountGroupByIdAud> byIdAudits) {
    return byIdAudits
        .stream()
        .flatMap(GroupBundle::toAuditEntries)
        .collect(
            Multimaps.toMultimap(
                AuditEntry::getTarget,
                Function.identity(),
                MultimapBuilder.hashKeys().arrayListValues()::build));
  }

  private static Stream<AuditEntry> toAuditEntries(AccountGroupByIdAud byIdAudit) {
    AuditEntry additionAuditEntry =
        AuditEntry.create(
            Action.ADD, byIdAudit.getAddedBy(), byIdAudit.getIncludeUUID(), byIdAudit.getAddedOn());
    if (byIdAudit.isActive()) {
      return Stream.of(additionAuditEntry);
    }

    AuditEntry removalAuditEntry =
        AuditEntry.create(
            Action.REMOVE,
            byIdAudit.getRemovedBy(),
            byIdAudit.getIncludeUUID(),
            byIdAudit.getRemovedOn());
    return Stream.of(additionAuditEntry, removalAuditEntry);
  }

  /**
   * Determines whether the audit log entries are equal except for redundant entries. Entries of the
   * same type (addition/removal) which follow directly on each other according to their timestamp
   * are considered redundant.
   */
  private static boolean areConsideredEqual(
      ListMultimap<String, AuditEntry> reviewDbMemberAuditsByTarget,
      ListMultimap<String, AuditEntry> noteDbMemberAuditsByTarget) {
    for (String target : reviewDbMemberAuditsByTarget.keySet()) {
      ImmutableList<AuditEntry> reviewDbAuditEntries =
          reviewDbMemberAuditsByTarget
              .get(target)
              .stream()
              .sorted(AUDIT_ENTRY_COMPARATOR)
              .collect(toImmutableList());
      ImmutableSet<AuditEntry> noteDbAuditEntries =
          noteDbMemberAuditsByTarget
              .get(target)
              .stream()
              .sorted(AUDIT_ENTRY_COMPARATOR)
              .collect(toImmutableSet());

      int reviewDbIndex = 0;
      for (AuditEntry noteDbAuditEntry : noteDbAuditEntries) {
        Set<AuditEntry> redundantReviewDbAuditEntries = new HashSet<>();
        while (reviewDbIndex < reviewDbAuditEntries.size()) {
          AuditEntry reviewDbAuditEntry = reviewDbAuditEntries.get(reviewDbIndex);
          if (!reviewDbAuditEntry.getAction().equals(noteDbAuditEntry.getAction())) {
            break;
          }
          redundantReviewDbAuditEntries.add(reviewDbAuditEntry);
          reviewDbIndex++;
        }

        // The order of the entries is not perfect as ReviewDb included milliseconds for timestamps
        // and we cut off everything below seconds due to NoteDb/git. Consequently, we don't have a
        // way to know in this method in which exact order additions/removals within the same second
        // happened. The best we can do is to group all additions within the same second as
        // redundant entries and the removals afterward. To compensate that we possibly group
        // non-redundant additions/removals, we also accept NoteDb audit entries which just occur
        // anywhere as ReviewDb audit entries.
        if (!redundantReviewDbAuditEntries.contains(noteDbAuditEntry)
            && !reviewDbAuditEntries.contains(noteDbAuditEntry)) {
          return false;
        }
      }

      if (reviewDbIndex < reviewDbAuditEntries.size()) {
        // Some of the ReviewDb audit log entries aren't matched by NoteDb audit log entries.
        return false;
      }
    }
    return true;
  }

  public AccountGroup.Id id() {
    return group().getId();
  }

  public AccountGroup.UUID uuid() {
    return group().getGroupUUID();
  }

  public abstract Source source();

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

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException(
        "hashCode is not supported because equals is not supported");
  }

  @Override
  public boolean equals(Object o) {
    throw new UnsupportedOperationException("Use GroupBundle.compare(a, b) instead of equals");
  }

  @AutoValue
  abstract static class AuditEntry {
    private static AuditEntry create(
        Action action, Account.Id userId, Account.Id memberId, Timestamp timestamp) {
      return new AutoValue_GroupBundle_AuditEntry(
          action, userId, String.valueOf(memberId.get()), timestamp);
    }

    private static AuditEntry create(
        Action action, Account.Id userId, AccountGroup.UUID subgroupId, Timestamp timestamp) {
      return new AutoValue_GroupBundle_AuditEntry(action, userId, subgroupId.get(), timestamp);
    }

    abstract Action getAction();

    abstract Account.Id getUserId();

    abstract String getTarget();

    abstract Timestamp getTimestamp();
  }

  enum Action {
    ADD(1),
    REMOVE(2);

    private final int order;

    Action(int order) {
      this.order = order;
    }

    public int getOrder() {
      return order;
    }
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder source(Source source);

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
