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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Comparator.comparing;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.data.GroupReference;
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
import com.google.gerrit.server.config.GerritServerId;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.VersionedMetaData.BatchMetaDataUpdate;
import com.google.gerrit.server.group.db.GroupConfig.UpdateOwnerPermissionsStrategy;
import com.google.gerrit.server.update.RefUpdateUtil;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/** Helper for rebuilding an entire group's NoteDb refs. */
@Singleton
public class GroupRebuilder {
  private final Provider<PersonIdent> serverIdent;
  private final AllUsersName allUsers;
  private final MetaDataUpdate.InternalFactory metaDataUpdateFactory;

  private final BiFunction<Account.Id, PersonIdent, PersonIdent> newPersonIdentFunc;
  private final Function<Account.Id, String> getAccountNameEmailFunc;
  private final Function<AccountGroup.UUID, String> getGroupNameFunc;

  @Inject
  GroupRebuilder(
      @AnonymousCowardName String anonymousCowardName,
      @GerritPersonIdent Provider<PersonIdent> serverIdent,
      @GerritServerId String serverId,
      AllUsersName allUsers,
      MetaDataUpdate.InternalFactory metaDataUpdateFactory,
      IdentifiedUser.GenericFactory userFactory,
      AccountCache accountCache,
      GroupCache groupCache) {
    this(
        serverIdent,
        allUsers,
        metaDataUpdateFactory,

        // TODO(dborowitz): These probably won't work during init.
        (id, ident) ->
            userFactory.create(id).newCommitterIdent(ident.getWhen(), ident.getTimeZone()),
        id -> GroupsUpdate.getAccountNameEmail(accountCache, anonymousCowardName, id, serverId),
        uuid -> GroupsUpdate.getGroupName(groupCache, uuid));
  }

  @VisibleForTesting
  GroupRebuilder(
      Provider<PersonIdent> serverIdent,
      AllUsersName allUsers,
      MetaDataUpdate.InternalFactory metaDataUpdateFactory,
      BiFunction<Account.Id, PersonIdent, PersonIdent> newPersonIdentFunc,
      Function<Account.Id, String> getAccountNameEmailFunc,
      Function<AccountGroup.UUID, String> getGroupNameFunc) {
    this.serverIdent = serverIdent;
    this.allUsers = allUsers;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.newPersonIdentFunc = newPersonIdentFunc;
    this.getAccountNameEmailFunc = getAccountNameEmailFunc;
    this.getGroupNameFunc = getGroupNameFunc;
  }

  public void rebuild(Repository allUsersRepo, GroupBundle bundle)
      throws IOException, ConfigInvalidException, OrmDuplicateKeyException {
    GroupConfig groupConfig =
        GroupConfig.loadForGroupNoOwnerUpdate(allUsers, allUsersRepo, bundle.uuid());
    groupConfig.setGroupCreation(
        InternalGroupCreation.builder()
            .setId(bundle.id())
            .setNameKey(bundle.group().getNameKey())
            .setGroupUUID(bundle.group().getGroupUUID())
            .setCreatedOn(bundle.group().getCreatedOn())
            .build());
    groupConfig.setUpdateOwnerPermissionsStrategy(UpdateOwnerPermissionsStrategy.SKIP);

    // Don't set owner in the InternalGroupUpdate; this triggers a non-atomic update of
    // refs/meta/config within GroupConfig#onSave. The owner update is handled specially in this
    // method using a single BatchRefUpdate.
    InternalGroupUpdate.Builder updateBuilder =
        InternalGroupUpdate.builder().setVisibleToAll(bundle.group().isVisibleToAll());
    if (bundle.group().getDescription() != null) {
      updateBuilder.setDescription(bundle.group().getDescription());
    }
    groupConfig.setGroupUpdate(updateBuilder.build(), getAccountNameEmailFunc, getGroupNameFunc);

    BatchRefUpdate bru = allUsersRepo.getRefDatabase().newBatchUpdate();
    MetaDataUpdate md = metaDataUpdateFactory.create(allUsers, allUsersRepo, bru);

    // Creation is done by the server (unlike later audit events).
    PersonIdent nowServerIdent = serverIdent.get();
    PersonIdent created = new PersonIdent(nowServerIdent, bundle.group().getCreatedOn());
    md.getCommitBuilder().setAuthor(created);
    md.getCommitBuilder().setCommitter(created);

    // Rebuild group ref.
    try (BatchMetaDataUpdate batch = groupConfig.openUpdate(md)) {
      batch.write(groupConfig, md.getCommitBuilder());

      for (Event e : toEvents(bundle, nowServerIdent)) {
        PersonIdent currServerIdent = new PersonIdent(nowServerIdent, e.when());
        groupConfig.setGroupUpdate(e.update(), getAccountNameEmailFunc, getGroupNameFunc);
        CommitBuilder cb = new CommitBuilder();
        cb.setAuthor(
            e.accountId()
                .map(id -> newPersonIdentFunc.apply(id, currServerIdent))
                .orElse(currServerIdent));
        cb.setCommitter(currServerIdent);
        batch.write(groupConfig, cb);
      }

      batch.createRef(groupConfig.getRefName());
    }

    // Update refs/meta/config in same batch.
    GroupOwnerPermissions ownerPerm =
        new GroupOwnerPermissions(
            allUsers,
            allUsersRepo,
            project -> {
              checkArgument(project.equals(allUsers));
              MetaDataUpdate result = metaDataUpdateFactory.create(project, allUsersRepo, bru);
              result.getCommitBuilder().setAuthor(nowServerIdent);
              result.getCommitBuilder().setCommitter(nowServerIdent);
              return result;
            });
    AccountGroup.UUID ownerUuid = bundle.group().getOwnerGroupUUID();
    ownerPerm.updateOwnerPermissions(
        bundle.uuid(), null, new GroupReference(ownerUuid, getGroupNameFunc.apply(ownerUuid)));

    checkState(bru.getCommands().size() == 2, "expected 2 commands, got: %s", bru);
    try (RevWalk rw = new RevWalk(allUsersRepo)) {
      RefUpdateUtil.executeChecked(bru, rw);
    }
  }

  private ImmutableList<Event> toEvents(GroupBundle bundle, PersonIdent nowServerIdent) {
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
            new Timestamp(nowServerIdent.getWhen().getTime()), setCurrentMembership(bundle)));

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
