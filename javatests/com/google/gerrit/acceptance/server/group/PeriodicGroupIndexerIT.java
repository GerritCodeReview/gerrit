// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.group;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.query.FieldBundle;
import com.google.gerrit.server.group.PeriodicGroupIndexer;
import com.google.gerrit.server.index.group.GroupField;
import com.google.gerrit.server.index.group.GroupIndex;
import com.google.gerrit.server.index.group.GroupIndexCollection;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

@UseLocalDisk
public class PeriodicGroupIndexerIT extends AbstractDaemonTest {

  @Inject private GroupIndexCollection indexes;
  @Inject private IndexConfig indexConfig;

  @Inject private PeriodicGroupIndexer periodicIndexer;

  private static final ImmutableSet<String> FIELDS =
      ImmutableSet.of(GroupField.NAME_SPEC.getName());

  @Test
  public void removesNonExistingGroupsFromIndex() throws Exception {
    ObjectId groupNamesObjectId = getGroupNamesRefObjectId();

    GroupInfo info = gApi.groups().create("foo").get();
    AccountGroup.UUID uuid = AccountGroup.uuid(info.id);
    System.out.println(">>> uuid = " + uuid.get());
    GroupIndex i = indexes.getSearchIndex();
    Optional<FieldBundle> result = i.getRaw(uuid, QueryOptions.create(indexConfig, 0, 1, FIELDS));
    assertThat(result).isPresent();

    // Delete the group by directly updating the All-Users repository.
    // Thus, Gerrit will not notice the deletion and will not remove the group
    // from the index
    deleteGroupRef(uuid);
    forceUpdateGroupNamesRef(groupNamesObjectId);
    groupCache.evict(uuid);

    result = i.getRaw(uuid, QueryOptions.create(indexConfig, 0, 1, FIELDS));
    assertThat(result).isPresent();

    periodicIndexer.run();

    result = i.getRaw(uuid, QueryOptions.create(indexConfig, 0, 1, FIELDS));
    assertThat(result).isEmpty();
  }

  private ObjectId getGroupNamesRefObjectId() throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      return repo.getRefDatabase().exactRef("refs/meta/group-names").getObjectId();
    }
  }

  private void deleteGroupRef(AccountGroup.UUID uuid) throws IOException {
    String groupRef = String.format("refs/groups/%s/%s", uuid.get().substring(0, 2), uuid.get());
    try (Repository repo = repoManager.openRepository(allUsers)) {
      RefUpdate ru = repo.updateRef(groupRef);
      ru.setForceUpdate(true);
      ru.delete();
    }
  }

  private void forceUpdateGroupNamesRef(ObjectId oid) throws IOException {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      RefUpdate refUpdate = repo.updateRef("refs/meta/group-names");
      refUpdate.setNewObjectId(oid);
      refUpdate.forceUpdate();
    }
  }
}
