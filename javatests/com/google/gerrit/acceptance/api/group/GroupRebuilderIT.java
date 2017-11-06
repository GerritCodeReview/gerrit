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

package com.google.gerrit.acceptance.api.group;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.account.GroupUUID;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.ServerInitiated;
import com.google.gerrit.server.group.db.GroupBundle;
import com.google.gerrit.server.group.db.GroupConfig;
import com.google.gerrit.server.group.db.GroupRebuilder;
import com.google.gerrit.server.group.db.Groups;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.group.db.InternalGroupCreation;
import com.google.gerrit.server.group.db.InternalGroupUpdate;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

@NoHttpd
public class GroupRebuilderIT extends AbstractDaemonTest {
  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config config = new Config();
    // This test is explicitly testing the migration from ReviewDb to NoteDb, and handles reading
    // from NoteDb manually. It should work regardless of the value of writeGroupsToNoteDb, however.
    config.setBoolean("user", null, "readGroupsFromNoteDb", false);
    return config;
  }

  @Inject @ServerInitiated private Provider<GroupsUpdate> groupsUpdate;
  @Inject private GroupRebuilder rebuilder;
  @Inject private Groups groups;
  @Inject private Sequences seq;

  @Test
  public void basicGroupProperties() throws Exception {
    InternalGroup createdGroup =
        groupsUpdate.get().createGroup(db, create(name("group")).build(), noUpdate());
    // Explicitly re-read from ReviewDb.
    InternalGroup reviewDbGroup = groups.getGroup(db, createdGroup.getGroupUUID()).get();
    deleteGroupRefs(reviewDbGroup);

    assertThat(rebuild(reviewDbGroup)).isEqualTo(roundToSecond(reviewDbGroup));
  }

  private InternalGroupCreation.Builder create(String name) throws Exception {
    return InternalGroupCreation.builder()
        .setId(new AccountGroup.Id(seq.nextGroupId()))
        .setGroupUUID(GroupUUID.make(name, serverIdent.get()))
        .setNameKey(new AccountGroup.NameKey(name))
        .setCreatedOn(TimeUtil.nowTs());
  }

  private InternalGroupUpdate noUpdate() {
    return InternalGroupUpdate.builder().build();
  }

  private void deleteGroupRefs(InternalGroup group) throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      String refName = RefNames.refsGroups(group.getGroupUUID());
      RefUpdate ru = repo.updateRef(refName);
      ru.setForceUpdate(true);
      Ref oldRef = repo.exactRef(refName);
      if (oldRef == null) {
        return;
      }
      ru.setExpectedOldObjectId(oldRef.getObjectId());
      ru.setNewObjectId(ObjectId.zeroId());
      assertThat(ru.delete()).isEqualTo(RefUpdate.Result.FORCED);
    }
  }

  private InternalGroup rebuild(InternalGroup group) throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      rebuilder.rebuild(repo, GroupBundle.fromReviewDb(db, group.getId()));
      GroupConfig groupConfig = GroupConfig.loadForGroup(repo, group.getGroupUUID());
      Optional<InternalGroup> result = groupConfig.getLoadedGroup();
      assertThat(result).isPresent();
      return result.get();
    }
  }

  private InternalGroup roundToSecond(InternalGroup g) {
    return InternalGroup.builder()
        .setId(g.getId())
        .setNameKey(g.getNameKey())
        .setDescription(g.getDescription())
        .setOwnerGroupUUID(g.getOwnerGroupUUID())
        .setVisibleToAll(g.isVisibleToAll())
        .setGroupUUID(g.getGroupUUID())
        .setCreatedOn(TimeUtil.roundToSecond(g.getCreatedOn()))
        .setMembers(g.getMembers())
        .setSubgroups(g.getSubgroups())
        .build();
  }
}
