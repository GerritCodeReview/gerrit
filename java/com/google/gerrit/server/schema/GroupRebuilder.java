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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.git.meta.VersionedMetaData.BatchMetaDataUpdate;
import com.google.gerrit.server.group.db.AuditLogFormatter;
import com.google.gerrit.server.group.db.GroupConfig;
import com.google.gerrit.server.group.db.InternalGroupCreation;
import com.google.gerrit.server.group.db.InternalGroupUpdate;
import com.google.gerrit.server.group.db.InternalGroupUpdate.MemberModification;
import com.google.gerrit.server.group.db.InternalGroupUpdate.SubgroupModification;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

/** Helper for rebuilding an entire group's NoteDb refs. */
class GroupRebuilder {
  private final PersonIdent serverIdent;
  private final AllUsersName allUsers;
  private final AuditLogFormatter auditLogFormatter;

  public GroupRebuilder(
      PersonIdent serverIdent, AllUsersName allUsers, AuditLogFormatter auditLogFormatter) {
    this.serverIdent = serverIdent;
    this.allUsers = allUsers;
    this.auditLogFormatter = auditLogFormatter;
  }

  public void rebuild(Repository allUsersRepo, GroupBundle bundle, @Nullable BatchRefUpdate bru)
      throws IOException, ConfigInvalidException, OrmDuplicateKeyException {
    AccountGroup group = bundle.group();
    InternalGroupCreation groupCreation =
        InternalGroupCreation.builder()
            .setId(bundle.id())
            .setNameKey(group.getNameKey())
            .setGroupUUID(group.getGroupUUID())
            .build();
    GroupConfig groupConfig = GroupConfig.createForNewGroup(allUsersRepo, groupCreation);
    groupConfig.setAllowSaveEmptyName();

    InternalGroupUpdate.Builder updateBuilder =
        InternalGroupUpdate.builder()
            .setOwnerGroupUUID(group.getOwnerGroupUUID())
            .setVisibleToAll(group.isVisibleToAll())
            .setUpdatedOn(group.getCreatedOn());
    if (bundle.group().getDescription() != null) {
      updateBuilder.setDescription(group.getDescription());
    }
    groupConfig.setGroupUpdate(updateBuilder.build(), auditLogFormatter);

    Map<Key, Collection<Event>> events = toEvents(bundle).asMap();
    PersonIdent nowServerIdent = getServerIdent(events);

    MetaDataUpdate md = createMetaDataUpdate(allUsers, allUsersRepo, bru);

    // Creation is done by the server (unlike later audit events).
    PersonIdent created = new PersonIdent(nowServerIdent, group.getCreatedOn());
    md.getCommitBuilder().setAuthor(created);
    md.getCommitBuilder().setCommitter(created);

    // Rebuild group ref.
    try (BatchMetaDataUpdate batch = groupConfig.openUpdate(md)) {
      batch.write(groupConfig, md.getCommitBuilder());

      for (Map.Entry<Key, Collection<Event>> e : events.entrySet()) {
        InternalGroupUpdate.Builder ub = InternalGroupUpdate.builder();
        e.getValue().forEach(event -> event.update().accept(ub));
        ub.setUpdatedOn(e.getKey().when());
        groupConfig.setGroupUpdate(ub.build(), auditLogFormatter);

        PersonIdent currServerIdent = new PersonIdent(nowServerIdent, e.getKey().when());
        CommitBuilder cb = new CommitBuilder();
        cb.setAuthor(
            e.getKey()
                .accountId()
                .map(id -> auditLogFormatter.getParsableAuthorIdent(id, currServerIdent))
                .orElse(currServerIdent));
        cb.setCommitter(currServerIdent);
        batch.write(groupConfig, cb);
      }

      batch.createRef(groupConfig.getRefName());
    }
  }

  private ListMultimap<Key, Event> toEvents(GroupBundle bundle) {
    ListMultimap<Key, Event> result =
        MultimapBuilder.treeKeys(Key.COMPARATOR).arrayListValues(1).build();
    Event e;

    for (AccountGroupMemberAudit a : bundle.memberAudit()) {
      checkArgument(
          a.getKey().getGroupId().equals(bundle.id()),
          "key %s does not match group %s",
          a.getKey(),
          bundle.id());
      Account.Id accountId = a.getKey().getParentKey();
      e = event(Type.ADD_MEMBER, a.getAddedBy(), a.getKey().getAddedOn(), addMember(accountId));
      result.put(e.key(), e);
      if (!a.isActive()) {
        e = event(Type.REMOVE_MEMBER, a.getRemovedBy(), a.getRemovedOn(), removeMember(accountId));
        result.put(e.key(), e);
      }
    }

    for (AccountGroupByIdAud a : bundle.byIdAudit()) {
      checkArgument(
          a.getKey().getParentKey().equals(bundle.id()),
          "key %s does not match group %s",
          a.getKey(),
          bundle.id());
      AccountGroup.UUID uuid = a.getKey().getIncludeUUID();
      e = event(Type.ADD_GROUP, a.getAddedBy(), a.getKey().getAddedOn(), addGroup(uuid));
      result.put(e.key(), e);
      if (!a.isActive()) {
        e = event(Type.REMOVE_GROUP, a.getRemovedBy(), a.getRemovedOn(), removeGroup(uuid));
        result.put(e.key(), e);
      }
    }

    // Due to clock skew, audit events may be in the future relative to this machine. Ensure the
    // fixup event happens after any other events, both for the purposes of sorting Keys correctly
    // and to avoid non-monotonic timestamps in the commit history.
    Timestamp maxTs =
        Stream.concat(result.keySet().stream().map(Key::when), Stream.of(TimeUtil.nowTs()))
            .max(Comparator.naturalOrder())
            .get();
    Timestamp fixupTs = new Timestamp(maxTs.getTime() + 1);
    e = serverEvent(Type.FIXUP, fixupTs, setCurrentMembership(bundle));
    result.put(e.key(), e);

    return result;
  }

  private PersonIdent getServerIdent(Map<Key, Collection<Event>> events) {
    // Created with MultimapBuilder.treeKeys, so the keySet is navigable.
    Key lastKey = ((NavigableSet<Key>) events.keySet()).last();
    checkState(lastKey.type() == Type.FIXUP);
    return new PersonIdent(
        serverIdent.getName(),
        serverIdent.getEmailAddress(),
        Iterables.getOnlyElement(events.get(lastKey)).when(),
        serverIdent.getTimeZone());
  }

  private static MetaDataUpdate createMetaDataUpdate(
      Project.NameKey projectName, Repository repository, @Nullable BatchRefUpdate batchRefUpdate) {
    return new MetaDataUpdate(
        GitReferenceUpdated.DISABLED, projectName, repository, batchRefUpdate);
  }

  private static Consumer<InternalGroupUpdate.Builder> addMember(Account.Id toAdd) {
    return b -> {
      MemberModification prev = b.getMemberModification();
      b.setMemberModification(in -> Sets.union(prev.apply(in), ImmutableSet.of(toAdd)));
    };
  }

  private static Consumer<InternalGroupUpdate.Builder> removeMember(Account.Id toRemove) {
    return b -> {
      MemberModification prev = b.getMemberModification();
      b.setMemberModification(in -> Sets.difference(prev.apply(in), ImmutableSet.of(toRemove)));
    };
  }

  private static Consumer<InternalGroupUpdate.Builder> addGroup(AccountGroup.UUID toAdd) {
    return b -> {
      SubgroupModification prev = b.getSubgroupModification();
      b.setSubgroupModification(in -> Sets.union(prev.apply(in), ImmutableSet.of(toAdd)));
    };
  }

  private static Consumer<InternalGroupUpdate.Builder> removeGroup(AccountGroup.UUID toRemove) {
    return b -> {
      SubgroupModification prev = b.getSubgroupModification();
      b.setSubgroupModification(in -> Sets.difference(prev.apply(in), ImmutableSet.of(toRemove)));
    };
  }

  private static Consumer<InternalGroupUpdate.Builder> setCurrentMembership(GroupBundle bundle) {
    // Overwrite members and subgroups with the current values. The storage layer will do the
    // set differences to compute the appropriate delta, if any.
    return b ->
        b.setMemberModification(
                in ->
                    bundle.members().stream().map(m -> m.getAccountId()).collect(toImmutableSet()))
            .setSubgroupModification(
                in ->
                    bundle.byId().stream().map(m -> m.getIncludeUUID()).collect(toImmutableSet()));
  }

  private static Event event(
      Type type,
      Account.Id accountId,
      Timestamp when,
      Consumer<InternalGroupUpdate.Builder> update) {
    return new AutoValue_GroupRebuilder_Event(type, Optional.of(accountId), when, update);
  }

  private static Event serverEvent(
      Type type, Timestamp when, Consumer<InternalGroupUpdate.Builder> update) {
    return new AutoValue_GroupRebuilder_Event(type, Optional.empty(), when, update);
  }

  @AutoValue
  abstract static class Event {
    abstract Type type();

    abstract Optional<Account.Id> accountId();

    abstract Timestamp when();

    abstract Consumer<InternalGroupUpdate.Builder> update();

    Key key() {
      return new AutoValue_GroupRebuilder_Key(accountId(), when(), type());
    }
  }

  /**
   * Distinct event types.
   *
   * <p>Events at the same time by the same user are batched together by type. The types should
   * correspond to the possible batch operations supported by {@link
   * com.google.gerrit.server.audit.AuditService}.
   */
  enum Type {
    ADD_MEMBER,
    REMOVE_MEMBER,
    ADD_GROUP,
    REMOVE_GROUP,
    FIXUP;
  }

  @AutoValue
  abstract static class Key {
    static final Comparator<Key> COMPARATOR =
        Comparator.comparing(Key::when)
            .thenComparing(
                k -> k.accountId().map(Account.Id::get).orElse(null),
                Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(Key::type);

    abstract Optional<Account.Id> accountId();

    abstract Timestamp when();

    abstract Type type();
  }
}
