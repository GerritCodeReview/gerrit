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

package com.google.gerrit.acceptance.api.group;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.server.group.testing.InternalGroupSubject.internalGroups;
import static com.google.gerrit.truth.OptionalSubject.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.exceptions.NoSuchGroupException;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.group.db.GroupDelta;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.group.testing.InternalGroupSubject;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.gerrit.server.query.group.InternalGroupQuery;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.gerrit.truth.OptionalSubject;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.junit.Rule;
import org.junit.Test;

public class GroupIndexerIT {
  @Rule public InMemoryTestEnvironment testEnvironment = new InMemoryTestEnvironment();

  @Inject private GroupIndexer groupIndexer;
  @Inject private GerritApi gApi;
  @Inject private GroupCache groupCache;
  @Inject @ServerInitiated private GroupsUpdate groupsUpdate;
  @Inject private Provider<InternalGroupQuery> groupQueryProvider;
  @Inject private GroupOperations groupOperations;

  @Test
  public void indexingUpdatesTheIndex() throws Exception {
    AccountGroup.UUID groupUuid = createGroup("users");
    AccountGroup.UUID subgroupUuid = AccountGroup.uuid("contributors");
    updateGroupWithoutCacheOrIndex(
        groupUuid,
        newGroupDelta()
            .setSubgroupModification(subgroups -> ImmutableSet.of(subgroupUuid))
            .build());

    groupIndexer.index(groupUuid);

    Set<AccountGroup.UUID> parentGroups =
        groupQueryProvider.get().bySubgroups(ImmutableSet.of(subgroupUuid)).get(subgroupUuid);
    assertThat(parentGroups).hasSize(1);
    assertThat(parentGroups).containsExactly(groupUuid);
  }

  @Test
  public void indexCannotBeCorruptedByStaleCache() throws Exception {
    AccountGroup.UUID groupUuid = createGroup("verifiers");
    loadGroupToCache(groupUuid);
    AccountGroup.UUID subgroupUuid = AccountGroup.uuid("contributors");
    updateGroupWithoutCacheOrIndex(
        groupUuid,
        newGroupDelta()
            .setSubgroupModification(subgroups -> ImmutableSet.of(subgroupUuid))
            .build());

    groupIndexer.index(groupUuid);

    Set<AccountGroup.UUID> parentGroups =
        groupQueryProvider.get().bySubgroups(ImmutableSet.of(subgroupUuid)).get(subgroupUuid);
    assertThat(parentGroups).hasSize(1);
    assertThat(parentGroups).containsExactly(groupUuid);
  }

  @Test
  public void indexingUpdatesStaleUuidCache() throws Exception {
    AccountGroup.UUID groupUuid = createGroup("verifiers");
    loadGroupToCache(groupUuid);
    updateGroupWithoutCacheOrIndex(groupUuid, newGroupDelta().setDescription("Modified").build());

    groupIndexer.index(groupUuid);

    Optional<InternalGroup> updatedGroup = groupCache.get(groupUuid);
    assertThatGroup(updatedGroup).value().description().isEqualTo("Modified");
  }

  @Test
  public void reindexingStaleGroupUpdatesTheIndex() throws Exception {
    AccountGroup.UUID groupUuid = createGroup("users");
    AccountGroup.UUID subgroupUuid = AccountGroup.uuid("contributors");
    updateGroupWithoutCacheOrIndex(
        groupUuid,
        newGroupDelta()
            .setSubgroupModification(subgroups -> ImmutableSet.of(subgroupUuid))
            .build());

    groupIndexer.reindexIfStale(groupUuid);

    Set<AccountGroup.UUID> parentGroups =
        groupQueryProvider.get().bySubgroups(ImmutableSet.of(subgroupUuid)).get(subgroupUuid);
    assertThat(parentGroups).hasSize(1);
    assertThat(parentGroups).containsExactly(groupUuid);
  }

  @Test
  public void notStaleGroupIsNotReindexed() throws Exception {
    AccountGroup.UUID groupUuid = createGroup("verifiers");
    updateGroupWithoutCacheOrIndex(groupUuid, newGroupDelta().setDescription("Modified").build());
    groupIndexer.index(groupUuid);

    boolean reindexed = groupIndexer.reindexIfStale(groupUuid);

    assertWithMessage("Group should not have been reindexed").that(reindexed).isFalse();
  }

  @Test
  public void indexStalenessIsNotDerivedFromCacheStaleness() throws Exception {
    AccountGroup.UUID groupUuid = createGroup("verifiers");
    updateGroupWithoutCacheOrIndex(groupUuid, newGroupDelta().setDescription("Modified").build());
    reloadGroupToCache(groupUuid);

    boolean reindexed = groupIndexer.reindexIfStale(groupUuid);

    assertWithMessage("Group should have been reindexed").that(reindexed).isTrue();
  }

  @Test
  public void getMultipleParents() throws Exception {
    AccountGroup.UUID sub1 = groupOperations.newGroup().create();
    AccountGroup.UUID sub2 = groupOperations.newGroup().create();
    AccountGroup.UUID parent1 = groupOperations.newGroup().addSubgroup(sub1).create();
    AccountGroup.UUID parent2 = groupOperations.newGroup().addSubgroup(sub2).create();
    AccountGroup.UUID parent3 = groupOperations.newGroup().addSubgroup(sub2).create();

    assertThat(groupQueryProvider.get().bySubgroups(ImmutableSet.of(sub1, sub2)))
        .containsExactlyEntriesIn(
            ImmutableMap.of(
                sub1, ImmutableSet.of(parent1), sub2, ImmutableSet.of(parent2, parent3)));
  }

  private AccountGroup.UUID createGroup(String name) throws RestApiException {
    GroupInfo group = gApi.groups().create(name).get();
    return AccountGroup.uuid(group.id);
  }

  private void reloadGroupToCache(AccountGroup.UUID groupUuid) {
    groupCache.evict(groupUuid);
    loadGroupToCache(groupUuid);
  }

  private void loadGroupToCache(AccountGroup.UUID groupUuid) {
    @SuppressWarnings("unused")
    var unused = groupCache.get(groupUuid);
  }

  private static GroupDelta.Builder newGroupDelta() {
    return GroupDelta.builder();
  }

  private void updateGroupWithoutCacheOrIndex(AccountGroup.UUID groupUuid, GroupDelta groupDelta)
      throws NoSuchGroupException, IOException, ConfigInvalidException {
    groupsUpdate.updateGroupInNoteDb(groupUuid, groupDelta);
  }

  private static OptionalSubject<InternalGroupSubject, InternalGroup> assertThatGroup(
      Optional<InternalGroup> updatedGroup) {
    return assertThat(updatedGroup, internalGroups());
  }
}
