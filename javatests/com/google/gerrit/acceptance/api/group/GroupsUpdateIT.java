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

import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.server.notedb.NoteDbTable.GROUPS;
import static com.google.gerrit.server.notedb.NotesMigration.DISABLE_REVIEW_DB;
import static com.google.gerrit.server.notedb.NotesMigration.READ;
import static com.google.gerrit.server.notedb.NotesMigration.SECTION_NOTE_DB;
import static com.google.gerrit.server.notedb.NotesMigration.WRITE;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.group.ServerInitiated;
import com.google.gerrit.server.group.db.Groups;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.group.db.InternalGroupCreation;
import com.google.gerrit.server.group.db.InternalGroupUpdate;
import com.google.gerrit.testing.SchemaUpgradeTestEnvironment;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.junit.Rule;
import org.junit.Test;

public class GroupsUpdateIT {

  private static Config createPureNoteDbConfig() {
    Config config = new Config();
    config.setBoolean(SECTION_NOTE_DB, GROUPS.key(), WRITE, true);
    config.setBoolean(SECTION_NOTE_DB, GROUPS.key(), READ, true);
    config.setBoolean(SECTION_NOTE_DB, GROUPS.key(), DISABLE_REVIEW_DB, true);
    return config;
  }

  // TODO(aliceks): Use a more general name for SchemaUpgradeTestEnvironment.
  @Rule
  public SchemaUpgradeTestEnvironment testEnvironment =
      new SchemaUpgradeTestEnvironment(GroupsUpdateIT::createPureNoteDbConfig);

  @Inject @ServerInitiated private Provider<GroupsUpdate> groupsUpdateProvider;
  @Inject private Groups groups;
  @Inject private ReviewDb reviewDb;

  @Test
  public void groupCreationIsRetriedWhenFailedDueToConcurrentNameModification() throws Exception {
    InternalGroupCreation groupCreation = getGroupCreation("users", "users-UUID");
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setMemberModification(
                new CreateAnotherGroupOnceAsSideEffectOfMemberModification("verifiers"))
            .build();
    createGroup(groupCreation, groupUpdate);

    Stream<String> allGroupNames = getAllGroupNames();
    assertThat(allGroupNames).containsAllOf("users", "verifiers");
  }

  @Test
  public void groupRenameIsRetriedWhenFailedDueToConcurrentNameModification() throws Exception {
    createGroup("users", "users-UUID");

    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setName(new AccountGroup.NameKey("contributors"))
            .setMemberModification(
                new CreateAnotherGroupOnceAsSideEffectOfMemberModification("verifiers"))
            .build();
    updateGroup(new AccountGroup.UUID("users-UUID"), groupUpdate);

    Stream<String> allGroupNames = getAllGroupNames();
    assertThat(allGroupNames).containsAllOf("contributors", "verifiers");
  }

  private void createGroup(String groupName, String groupUuid) throws Exception {
    InternalGroupCreation groupCreation = getGroupCreation(groupName, groupUuid);
    InternalGroupUpdate groupUpdate = InternalGroupUpdate.builder().build();

    createGroup(groupCreation, groupUpdate);
  }

  private void createGroup(InternalGroupCreation groupCreation, InternalGroupUpdate groupUpdate)
      throws OrmException, IOException, ConfigInvalidException {
    groupsUpdateProvider.get().createGroup(reviewDb, groupCreation, groupUpdate);
  }

  private void updateGroup(AccountGroup.UUID groupUuid, InternalGroupUpdate groupUpdate)
      throws Exception {
    groupsUpdateProvider.get().updateGroup(reviewDb, groupUuid, groupUpdate);
  }

  private Stream<String> getAllGroupNames()
      throws OrmException, IOException, ConfigInvalidException {
    return groups.getAllGroupReferences(reviewDb).map(GroupReference::getName);
  }

  private static InternalGroupCreation getGroupCreation(String groupName, String groupUuid) {
    return InternalGroupCreation.builder()
        .setGroupUUID(new AccountGroup.UUID(groupUuid))
        .setNameKey(new AccountGroup.NameKey(groupName))
        .setId(new AccountGroup.Id(Math.abs(groupName.hashCode())))
        .build();
  }

  private class CreateAnotherGroupOnceAsSideEffectOfMemberModification
      implements InternalGroupUpdate.MemberModification {

    private boolean groupCreated = false;
    private String groupName;

    public CreateAnotherGroupOnceAsSideEffectOfMemberModification(String groupName) {
      this.groupName = groupName;
    }

    @Override
    public Set<Account.Id> apply(ImmutableSet<Account.Id> members) {
      if (!groupCreated) {
        createGroup();
        groupCreated = true;
      }

      return members;
    }

    private void createGroup() {
      InternalGroupCreation groupCreation = getGroupCreation(groupName, groupName + "-UUID");
      InternalGroupUpdate groupUpdate = InternalGroupUpdate.builder().build();
      try {
        groupsUpdateProvider.get().createGroup(reviewDb, groupCreation, groupUpdate);
      } catch (OrmException | IOException | ConfigInvalidException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
