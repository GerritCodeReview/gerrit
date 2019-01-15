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

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.server.group.testing.InternalGroupSubject.internalGroups;
import static com.google.gerrit.truth.ListSubject.assertThat;
import static com.google.gerrit.truth.OptionalSubject.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.exceptions.NoSuchGroupException;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.group.db.InternalGroupUpdate;
import com.google.gerrit.server.group.testing.InternalGroupSubject;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.gerrit.server.query.group.InternalGroupQuery;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.gerrit.truth.ListSubject;
import com.google.gerrit.truth.OptionalSubject;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
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

  @Test
  public void indexingUpdatesTheIndex() throws Exception {
    AccountGroup.UUID groupUuid = createGroup("users");
    AccountGroup.UUID subgroupUuid = new AccountGroup.UUID("contributors");
    updateGroupWithoutCacheOrIndex(
        groupUuid,
        newGroupUpdate()
            .setSubgroupModification(subgroups -> ImmutableSet.of(subgroupUuid))
            .build());

    groupIndexer.index(groupUuid);

    List<InternalGroup> parentGroups = groupQueryProvider.get().bySubgroup(subgroupUuid);
    assertThatGroups(parentGroups).onlyElement().groupUuid().isEqualTo(groupUuid);
  }

  @Test
  public void indexCannotBeCorruptedByStaleCache() throws Exception {
    AccountGroup.UUID groupUuid = createGroup("verifiers");
    loadGroupToCache(groupUuid);
    AccountGroup.UUID subgroupUuid = new AccountGroup.UUID("contributors");
    updateGroupWithoutCacheOrIndex(
        groupUuid,
        newGroupUpdate()
            .setSubgroupModification(subgroups -> ImmutableSet.of(subgroupUuid))
            .build());

    groupIndexer.index(groupUuid);

    List<InternalGroup> parentGroups = groupQueryProvider.get().bySubgroup(subgroupUuid);
    assertThatGroups(parentGroups).onlyElement().groupUuid().isEqualTo(groupUuid);
  }

  @Test
  public void indexingUpdatesStaleUuidCache() throws Exception {
    AccountGroup.UUID groupUuid = createGroup("verifiers");
    loadGroupToCache(groupUuid);
    updateGroupWithoutCacheOrIndex(groupUuid, newGroupUpdate().setDescription("Modified").build());

    groupIndexer.index(groupUuid);

    Optional<InternalGroup> updatedGroup = groupCache.get(groupUuid);
    assertThatGroup(updatedGroup).value().description().isEqualTo("Modified");
  }

  @Test
  public void reindexingStaleGroupUpdatesTheIndex() throws Exception {
    AccountGroup.UUID groupUuid = createGroup("users");
    AccountGroup.UUID subgroupUuid = new AccountGroup.UUID("contributors");
    updateGroupWithoutCacheOrIndex(
        groupUuid,
        newGroupUpdate()
            .setSubgroupModification(subgroups -> ImmutableSet.of(subgroupUuid))
            .build());

    groupIndexer.reindexIfStale(groupUuid);

    List<InternalGroup> parentGroups = groupQueryProvider.get().bySubgroup(subgroupUuid);
    assertThatGroups(parentGroups).onlyElement().groupUuid().isEqualTo(groupUuid);
  }

  @Test
  public void notStaleGroupIsNotReindexed() throws Exception {
    AccountGroup.UUID groupUuid = createGroup("verifiers");
    updateGroupWithoutCacheOrIndex(groupUuid, newGroupUpdate().setDescription("Modified").build());
    groupIndexer.index(groupUuid);

    boolean reindexed = groupIndexer.reindexIfStale(groupUuid);

    assertWithMessage("Group should not have been reindexed").that(reindexed).isFalse();
  }

  @Test
  public void indexStalenessIsNotDerivedFromCacheStaleness() throws Exception {
    AccountGroup.UUID groupUuid = createGroup("verifiers");
    updateGroupWithoutCacheOrIndex(groupUuid, newGroupUpdate().setDescription("Modified").build());
    reloadGroupToCache(groupUuid);

    boolean reindexed = groupIndexer.reindexIfStale(groupUuid);

    assertWithMessage("Group should have been reindexed").that(reindexed).isTrue();
  }

  private AccountGroup.UUID createGroup(String name) throws RestApiException {
    GroupInfo group = gApi.groups().create(name).get();
    return new AccountGroup.UUID(group.id);
  }

  private void reloadGroupToCache(AccountGroup.UUID groupUuid) {
    groupCache.evict(groupUuid);
    loadGroupToCache(groupUuid);
  }

  private void loadGroupToCache(AccountGroup.UUID groupUuid) {
    groupCache.get(groupUuid);
  }

  private static InternalGroupUpdate.Builder newGroupUpdate() {
    return InternalGroupUpdate.builder();
  }

  private void updateGroupWithoutCacheOrIndex(
      AccountGroup.UUID groupUuid, InternalGroupUpdate groupUpdate)
      throws OrmException, NoSuchGroupException, IOException, ConfigInvalidException {
    groupsUpdate.updateGroupInNoteDb(groupUuid, groupUpdate);
  }

  private static OptionalSubject<InternalGroupSubject, InternalGroup> assertThatGroup(
      Optional<InternalGroup> updatedGroup) {
    return assertThat(updatedGroup, internalGroups());
  }

  private static ListSubject<InternalGroupSubject, InternalGroup> assertThatGroups(
      List<InternalGroup> parentGroups) {
    return assertThat(parentGroups, internalGroups());
  }
}
