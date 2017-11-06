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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Comparator.comparing;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.VersionedMetaData.BatchMetaDataUpdate;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

/** Helper for rebuilding an entire group's NoteDb refs. */
@Singleton
public class GroupRebuilder {
  private final AllUsersName allUsers;
  private final IdentifiedUser.GenericFactory userFactory;
  private final MetaDataUpdate.InternalFactory metaDataUpdateFactory;
  private final Provider<PersonIdent> serverIdent;

  // TODO(dborowitz): These probably don't work during init, so we need some other way to pass
  // name/ID lookup methods. I'm also not sure these methods are going to keep their current form;
  // see
  // https://gerrit-review.googlesource.com/c/gerrit/+/136052/7/java/com/google/gerrit/server/group/db/GroupsUpdate.java#398
  private final AccountCache accountCache;
  private final GroupCache groupCache;
  private final String anonymousCowardName;

  @Inject
  GroupRebuilder(
      @AnonymousCowardName String anonymousCowardName,
      @GerritPersonIdent Provider<PersonIdent> serverIdent,
      AccountCache accountCache,
      AllUsersName allUsers,
      GroupCache groupCache,
      IdentifiedUser.GenericFactory userFactory,
      MetaDataUpdate.InternalFactory metaDataUpdateFactory) {
    this.accountCache = accountCache;
    this.allUsers = allUsers;
    this.anonymousCowardName = anonymousCowardName;
    this.groupCache = groupCache;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.serverIdent = serverIdent;
    this.userFactory = userFactory;
  }

  public void rebuild(Repository allUsersRepo, GroupBundle bundle)
      throws IOException, ConfigInvalidException, OrmDuplicateKeyException {

    GroupConfig groupConfig =
        GroupConfig.createForNewGroup(
            allUsersRepo,
            InternalGroupCreation.builder()
                .setId(bundle.id())
                .setNameKey(bundle.group().getNameKey())
                .setGroupUUID(bundle.group().getGroupUUID())
                .setCreatedOn(bundle.group().getCreatedOn())
                .build());

    PersonIdent nowServerIdent = serverIdent.get();

    // Creation is done by the server (unlike audit events).
    MetaDataUpdate md = metaDataUpdateFactory.create(allUsers, allUsersRepo, null);
    PersonIdent created = new PersonIdent(nowServerIdent, bundle.group().getCreatedOn());
    md.getCommitBuilder().setAuthor(created);
    md.getCommitBuilder().setCommitter(created);

    BatchMetaDataUpdate batch = groupConfig.openUpdate(md);
    batch.write(groupConfig, md.getCommitBuilder());

    for (Event e : toEvents(bundle)) {
      PersonIdent currServerIdent = new PersonIdent(nowServerIdent, e.when());
      groupConfig.setGroupUpdate(e.update(), this::getAccountNameEmail, this::getGroupName);
      CommitBuilder cb = new CommitBuilder();
      cb.setAuthor(
          e.accountId().map(id -> newPersonIdent(id, currServerIdent)).orElse(currServerIdent));
      cb.setCommitter(currServerIdent);
      batch.write(groupConfig, cb);
    }

    batch.createRef(groupConfig.getRefName());
  }

  private String getAccountNameEmail(Account.Id accountId) {
    return GroupsUpdate.getAccountNameEmail(accountCache, anonymousCowardName, accountId);
  }

  private PersonIdent newPersonIdent(Account.Id accountId, PersonIdent currServerIdent) {
    return userFactory
        .create(accountId)
        .newCommitterIdent(currServerIdent.getWhen(), currServerIdent.getTimeZone());
  }

  private String getGroupName(AccountGroup.UUID groupUuid) {
    return GroupsUpdate.getGroupName(groupCache, groupUuid);
  }

  private ImmutableList<Event> toEvents(GroupBundle bundle) {
    List<Event> result = new ArrayList<>();

    for (AccountGroupMemberAudit a : bundle.memberAudit()) {
      checkArgument(
          a.getKey().getGroupId().equals(bundle.id()),
          "key %s does not match group %s",
          a.getKey(),
          bundle.id());
      Account.Id accountId = a.getKey().getParentKey();
      result.add(Event.create(a.getAddedBy(), a.getKey().getAddedOn(), addMember(accountId)));
      if (!a.isActive()) {
        result.add(Event.create(a.getRemovedBy(), a.getRemovedOn(), removeMember(accountId)));
      }
    }

    for (AccountGroupByIdAud a : bundle.byIdAudit()) {
      checkArgument(
          a.getKey().getParentKey().equals(bundle.id()),
          "key %s does not match group %s",
          a.getKey(),
          bundle.id());
      AccountGroup.UUID uuid = a.getKey().getIncludeUUID();
      result.add(Event.create(a.getAddedBy(), a.getKey().getAddedOn(), addGroup(uuid)));

      if (!a.isActive()) {
        result.add(Event.create(a.getRemovedBy(), a.getRemovedOn(), removeGroup(uuid)));
      }
    }

    result.add(
        Event.byServer(
            new Timestamp(serverIdent.get().getWhen().getTime()), setCurrentMembership(bundle)));

    // Tiebreaking within a timestamp is arbitrary based on the ordering within the bundle. When a
    // bundle is created from ReviewDb, this at least maintains the arbitrary ordering of the
    // underlying database.
    return result.stream().sorted(comparing(Event::when)).collect(toImmutableList());
  }

  private static InternalGroupUpdate.Builder addMember(Account.Id toAdd) {
    return InternalGroupUpdate.builder()
        .setMemberModification(
            in -> Stream.concat(in.stream(), Stream.of(toAdd)).collect(toImmutableSet()));
  }

  private static InternalGroupUpdate.Builder removeMember(Account.Id toRemove) {
    return InternalGroupUpdate.builder()
        .setMemberModification(
            in -> in.stream().filter(id -> !id.equals(toRemove)).collect(toImmutableSet()));
  }

  private static InternalGroupUpdate.Builder addGroup(AccountGroup.UUID toAdd) {
    return InternalGroupUpdate.builder()
        .setSubgroupModification(
            in -> Stream.concat(in.stream(), Stream.of(toAdd)).collect(toImmutableSet()));
  }

  private static InternalGroupUpdate.Builder removeGroup(AccountGroup.UUID toRemove) {
    return InternalGroupUpdate.builder()
        .setSubgroupModification(
            in -> in.stream().filter(id -> !id.equals(toRemove)).collect(toImmutableSet()));
  }

  private static InternalGroupUpdate.Builder setCurrentMembership(GroupBundle bundle) {
    // Overwrite members and subgroups with the current values. The storage layer will do the
    // set differences to compute the appropriate delta.
    return InternalGroupUpdate.builder()
        .setMemberModification(
            in -> bundle.members().stream().map(m -> m.getAccountId()).collect(toImmutableSet()))
        .setSubgroupModification(
            in -> bundle.byId().stream().map(m -> m.getIncludeUUID()).collect(toImmutableSet()));
  }

  @AutoValue
  abstract static class Event {
    static Event byServer(Timestamp when, InternalGroupUpdate.Builder update) {
      return new AutoValue_GroupRebuilder_Event(Optional.empty(), when, update.build());
    }

    static Event create(Account.Id accountId, Timestamp when, InternalGroupUpdate.Builder update) {
      return new AutoValue_GroupRebuilder_Event(Optional.of(accountId), when, update.build());
    }

    abstract Optional<Account.Id> accountId();

    abstract Timestamp when();

    abstract InternalGroupUpdate update();
  }
}
