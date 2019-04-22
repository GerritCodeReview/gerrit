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
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.IntBlob;
import com.google.gerrit.server.notedb.RepoSequence;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.testing.GerritBaseTests;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.TestUpdateUI;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class NoteDbSchemaUpdaterTest extends GerritBaseTests {
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
      assert_().fail("expected StorageException");
    } catch (StorageException e) {
      assertThat(e)
          .hasMessageThat()
          .contains("Cannot downgrade NoteDb schema from version 14 to 13");
    }
  }

  @Test
  public void skipToFirstVersionNotSupported() throws Exception {
    ImmutableSortedSet<Integer> versions = versions(10, 11, 12);
    assertThat(requiredUpgrades(9, versions)).containsExactly(10, 11, 12).inOrder();
    try {
      requiredUpgrades(8, versions);
      assert_().fail("expected StorageException");
    } catch (StorageException e) {
      assertThat(e).hasMessageThat().contains("Cannot skip NoteDb schema from version 8 to 10");
    }
  }

  private static class TestUpdate {
    protected final Config cfg;
    protected final AllProjectsName allProjectsName;
    protected final AllUsersName allUsersName;
    protected final NoteDbSchemaUpdater updater;
    protected final GitRepositoryManager repoManager;
    protected final NoteDbSchemaVersion.Arguments args;
    private final List<String> messages;

    TestUpdate(Optional<Integer> initialVersion) {
      cfg = new Config();
      allProjectsName = new AllProjectsName("The-Projects");
      allUsersName = new AllUsersName("The-Users");
      repoManager = new InMemoryRepositoryManager();

      args = new NoteDbSchemaVersion.Arguments(repoManager, allProjectsName, allUsersName);
      NoteDbSchemaVersionManager versionManager =
          new NoteDbSchemaVersionManager(allProjectsName, repoManager);
      updater =
          new NoteDbSchemaUpdater(
              cfg,
              allUsersName,
              repoManager,
              new TestSchemaCreator(initialVersion),
              versionManager,
              args,
              ImmutableSortedMap.of(10, TestSchema_10.class, 11, TestSchema_11.class));
      messages = new ArrayList<>();
    }

    private class TestSchemaCreator implements SchemaCreator {
      private final Optional<Integer> initialVersion;

      TestSchemaCreator(Optional<Integer> initialVersion) {
        this.initialVersion = initialVersion;
      }

      @Override
      public void create() throws IOException {
        try (Repository repo = repoManager.createRepository(allProjectsName)) {
          if (initialVersion.isPresent()) {
            TestRepository<?> tr = new TestRepository<>(repo);
            tr.update(RefNames.REFS_VERSION, tr.blob(initialVersion.get().toString()));
          }
        } catch (Exception e) {
          throw new StorageException(e);
        }
        repoManager.createRepository(allUsersName).close();
        setUp();
      }

      @Override
      public void ensureCreated() throws IOException {
        try {
          repoManager.openRepository(allProjectsName).close();
        } catch (RepositoryNotFoundException e) {
          create();
        }
      }
    }

    protected void setNotesMigrationConfig() {
      cfg.setString("noteDb", "changes", "write", "true");
      cfg.setString("noteDb", "changes", "read", "true");
      cfg.setString("noteDb", "changes", "primaryStorage", "NOTE_DB");
      cfg.setString("noteDb", "changes", "disableReviewDb", "true");
    }

    protected void seedGroupSequenceRef() {
      new RepoSequence(
              repoManager,
              GitReferenceUpdated.DISABLED,
              allUsersName,
              Sequences.NAME_GROUPS,
              () -> 1,
              1)
          .next();
    }

    /** Test-specific setup. */
    protected void setUp() {}

    ImmutableList<String> update() throws Exception {
      updater.update(
          new TestUpdateUI() {
            @Override
            public void message(String m) {
              messages.add(m);
            }
          });
      return getMessages();
    }

    ImmutableList<String> getMessages() {
      return ImmutableList.copyOf(messages);
    }

    Optional<Integer> readVersion() throws Exception {
      try (Repository repo = repoManager.openRepository(allProjectsName)) {
        return IntBlob.parse(repo, RefNames.REFS_VERSION).map(IntBlob::value);
      }
    }

    static class TestSchema_10 implements NoteDbSchemaVersion {
      @Override
      public void upgrade(Arguments args, UpdateUI ui) {
        ui.message("body of 10");
      }
    }

    static class TestSchema_11 implements NoteDbSchemaVersion {
      @Override
      public void upgrade(Arguments args, UpdateUI ui) {
        ui.message("BODY OF 11");
      }
    }
  }

  @Test
  public void bootstrapUpdateWith216Prerequisites() throws Exception {
    TestUpdate u =
        new TestUpdate(Optional.empty()) {
          @Override
          public void setUp() {
            setNotesMigrationConfig();
            seedGroupSequenceRef();
          }
        };
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
  public void bootstrapUpdateFailsWithoutNotesMigrationConfig() throws Exception {
    TestUpdate u =
        new TestUpdate(Optional.empty()) {
          @Override
          public void setUp() {
            seedGroupSequenceRef();
          }
        };
    try {
      u.update();
      assert_().fail("expected StorageException");
    } catch (StorageException e) {
      assertThat(e).hasMessageThat().contains("NoteDb change migration was not completed");
    }
    assertThat(u.getMessages()).isEmpty();
    assertThat(u.readVersion()).isEmpty();
  }

  @Test
  public void bootstrapUpdateFailsWithoutGroupSequenceRef() throws Exception {
    TestUpdate u =
        new TestUpdate(Optional.empty()) {
          @Override
          public void setUp() {
            setNotesMigrationConfig();
          }
        };
    try {
      u.update();
      assert_().fail("expected StorageException");
    } catch (StorageException e) {
      assertThat(e).hasMessageThat().contains("upgrade to 2.16.x first");
    }
    assertThat(u.getMessages()).isEmpty();
    assertThat(u.readVersion()).isEmpty();
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
    // This test covers the state when running the updater after initializing a new 3.x site, which
    // seeds the schema version ref with the latest version.
    TestUpdate u = new TestUpdate(Optional.of(11));
    assertThat(u.update()).isEmpty();
    assertThat(u.readVersion()).hasValue(11);
  }

  private static ImmutableSortedSet<Integer> versions(Integer... versions) {
    return ImmutableSortedSet.copyOf(versions);
  }
}
