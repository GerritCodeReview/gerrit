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

package com.google.gerrit.server.schema;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.server.schema.NoteDbSchemaUpdater.requiredUpgrades;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.IntBlob;
import com.google.gerrit.server.notedb.MutableNotesMigration;
import com.google.gerrit.server.notedb.NoteDbSchemaVersionManager;
import com.google.gerrit.server.notedb.NotesMigrationState;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.TestUpdateUI;
import com.google.gwtorm.server.OrmException;
import java.util.Optional;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class NoteDbSchemaUpdaterTest {
  @Test
  public void requiredUpgradesFromNoVersion() throws Exception {
    assertThat(requiredUpgrades(0, versions(10))).containsExactly(10).inOrder();
    assertThat(requiredUpgrades(0, versions(10, 11, 12))).containsExactly(10, 11, 12).inOrder();
  }

  @Test
  public void requiredUpgradesFromExistingVersion() throws Exception {
    ImmutableSortedSet<Integer> versions = versions(10, 11, 12, 13);
    assertThat(requiredUpgrades(10, versions)).containsExactly(11, 12, 13).inOrder();
    assertThat(requiredUpgrades(11, versions)).containsExactly(12, 13).inOrder();
    assertThat(requiredUpgrades(12, versions)).containsExactly(13).inOrder();
    assertThat(requiredUpgrades(13, versions)).isEmpty();
  }

  @Test
  public void downgradeNotSupported() throws Exception {
    try {
      requiredUpgrades(14, versions(10, 11, 12, 13));
      assert_().fail("expected OrmException");
    } catch (OrmException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo("Cannot downgrade NoteDb schema from version 14 to 13");
    }
  }

  @Test
  public void skipToFirstVersionNotSupported() throws Exception {
    ImmutableSortedSet<Integer> versions = versions(10, 11, 12);
    assertThat(requiredUpgrades(9, versions)).containsExactly(10, 11, 12).inOrder();
    try {
      requiredUpgrades(8, versions);
      assert_().fail("expected OrmException");
    } catch (OrmException e) {
      assertThat(e).hasMessageThat().isEqualTo("Cannot skip NoteDb schema from version 8 to 10");
    }
  }

  private static class TestUpdate {
    private final AllProjectsName allProjectsName;
    private final NoteDbSchemaUpdater updater;
    private final GitRepositoryManager repoManager;
    private final NoteDbSchemaVersion.Arguments args;

    TestUpdate(Optional<Integer> initialVersion) throws Exception {
      allProjectsName = new AllProjectsName("The-Projects");
      repoManager = new InMemoryRepositoryManager();
      try (Repository repo = repoManager.createRepository(allProjectsName)) {
        if (initialVersion.isPresent()) {
          TestRepository<?> tr = new TestRepository<>(repo);
          tr.update(RefNames.REFS_VERSION, tr.blob(initialVersion.get().toString()));
        }
      }

      args = new NoteDbSchemaVersion.Arguments(repoManager, allProjectsName);
      NoteDbSchemaVersionManager versionManager =
          new NoteDbSchemaVersionManager(allProjectsName, repoManager);
      MutableNotesMigration notesMigration = MutableNotesMigration.newDisabled();
      notesMigration.setFrom(NotesMigrationState.NOTE_DB);
      updater =
          new NoteDbSchemaUpdater(
              notesMigration,
              versionManager,
              args,
              ImmutableSortedMap.of(10, TestSchema_10.class, 11, TestSchema_11.class));
    }

    ImmutableList<String> update() throws Exception {
      ImmutableList.Builder<String> messages = ImmutableList.builder();
      updater.update(
          new TestUpdateUI() {
            @Override
            public void message(String m) {
              messages.add(m);
            }
          });
      return messages.build();
    }

    Optional<Integer> readVersion() throws Exception {
      try (Repository repo = repoManager.openRepository(allProjectsName)) {
        return IntBlob.parse(repo, RefNames.REFS_VERSION).map(IntBlob::value);
      }
    }

    private static class TestSchema_10 implements NoteDbSchemaVersion {
      TestSchema_10(Arguments args) {
        // Do nothing.
      }

      @Override
      public void upgrade(UpdateUI ui) {
        ui.message("body of 10");
      }
    }

    private static class TestSchema_11 implements NoteDbSchemaVersion {
      TestSchema_11(Arguments args) {
        // Do nothing.
      }

      @Override
      public void upgrade(UpdateUI ui) {
        ui.message("BODY OF 11");
      }
    }
  }

  @Test
  public void bootstrapUpdate() throws Exception {
    TestUpdate u = new TestUpdate(Optional.empty());
    assertThat(u.update())
        .containsExactly(
            "Migrating data to schema 10 ...",
            "body of 10",
            "Migrating data to schema 11 ...",
            "BODY OF 11")
        .inOrder();
    assertThat(u.readVersion()).hasValue(11);
  }

  @Test
  public void updateTwoVersions() throws Exception {
    TestUpdate u = new TestUpdate(Optional.of(9));
    assertThat(u.update())
        .containsExactly(
            "Migrating data to schema 10 ...",
            "body of 10",
            "Migrating data to schema 11 ...",
            "BODY OF 11")
        .inOrder();
    assertThat(u.readVersion()).hasValue(11);
  }

  @Test
  public void updateOneVersion() throws Exception {
    TestUpdate u = new TestUpdate(Optional.of(10));
    assertThat(u.update())
        .containsExactly("Migrating data to schema 11 ...", "BODY OF 11")
        .inOrder();
    assertThat(u.readVersion()).hasValue(11);
  }

  @Test
  public void updateNoOp() throws Exception {
    TestUpdate u = new TestUpdate(Optional.of(11));
    assertThat(u.update()).isEmpty();
    assertThat(u.readVersion()).hasValue(11);
  }

  private static ImmutableSortedSet<Integer> versions(Integer... versions) {
    return ImmutableSortedSet.copyOf(versions);
  }
}
