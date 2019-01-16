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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.Sequences;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.stream.IntStream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;

public class NoteDbSchemaUpdater {
  private final Config cfg;
  private final AllUsersName allUsersName;
  private final GitRepositoryManager repoManager;
  private final SchemaCreator schemaCreator;
  private final NoteDbSchemaVersionManager versionManager;
  private final NoteDbSchemaVersion.Arguments args;
  private final ImmutableSortedMap<Integer, Class<? extends NoteDbSchemaVersion>> schemaVersions;

  @Inject
  NoteDbSchemaUpdater(
      @GerritServerConfig Config cfg,
      AllUsersName allUsersName,
      GitRepositoryManager repoManager,
      SchemaCreator schemaCreator,
      NoteDbSchemaVersionManager versionManager,
      NoteDbSchemaVersion.Arguments args) {
    this(
        cfg,
        allUsersName,
        repoManager,
        schemaCreator,
        versionManager,
        args,
        NoteDbSchemaVersions.ALL);
  }

  NoteDbSchemaUpdater(
      Config cfg,
      AllUsersName allUsersName,
      GitRepositoryManager repoManager,
      SchemaCreator schemaCreator,
      NoteDbSchemaVersionManager versionManager,
      NoteDbSchemaVersion.Arguments args,
      ImmutableSortedMap<Integer, Class<? extends NoteDbSchemaVersion>> schemaVersions) {
    this.cfg = cfg;
    this.allUsersName = allUsersName;
    this.repoManager = repoManager;
    this.schemaCreator = schemaCreator;
    this.versionManager = versionManager;
    this.args = args;
    this.schemaVersions = schemaVersions;
  }

  public void update(UpdateUI ui) throws StorageException {
    ensureSchemaCreated();

    int currentVersion = versionManager.read();
    if (currentVersion == 0) {
      // The only valid case where there is no refs/meta/version is when running 3.x init for the
      // first time on a site that previously ran init on 2.16. A freshly created 3.x site will have
      // seeded refs/meta/version during AllProjectsCreator, so it won't hit this block.
      checkNoteDbConfigFor216();
    }

    for (int nextVersion : requiredUpgrades(currentVersion, schemaVersions.keySet())) {
      try {
        ui.message(String.format("Migrating data to schema %d ...", nextVersion));
        NoteDbSchemaVersions.get(schemaVersions, nextVersion).upgrade(args, ui);
        versionManager.increment(nextVersion - 1);
      } catch (Exception e) {
        throw new StorageException(
            String.format("Failed to upgrade to schema version %d", nextVersion), e);
      }
    }
  }

  private void ensureSchemaCreated() throws StorageException {
    try {
      schemaCreator.ensureCreated();
    } catch (IOException | ConfigInvalidException e) {
      throw new StorageException("Cannot initialize Gerrit site");
    }
  }

  // Config#getEnum requires this to be public, so give it an off-putting name.
  public enum PrimaryStorageFor216Compatibility {
    REVIEW_DB,
    NOTE_DB
  }

  private void checkNoteDbConfigFor216() throws StorageException {
    // Check that the NoteDb migration config matches what we expect from a site that both:
    // * Completed the change migration to NoteDB.
    // * Ran schema upgrades from a 2.16 final release.

    if (!cfg.getBoolean("noteDb", "changes", "write", false)
        || !cfg.getBoolean("noteDb", "changes", "read", false)
        || cfg.getEnum(
                "noteDb", "changes", "primaryStorage", PrimaryStorageFor216Compatibility.REVIEW_DB)
            != PrimaryStorageFor216Compatibility.NOTE_DB
        || !cfg.getBoolean("noteDb", "changes", "disableReviewDb", false)) {
      throw new StorageException(
          "You appear to be upgrading from a 2.x site, but the NoteDb change migration was"
              + " not completed. See documentation:\n"
              + "https://gerrit-review.googlesource.com/Documentation/note-db.html#migration");
    }

    // We don't have a direct way to check that 2.16 init was run; the most obvious side effect
    // would be upgrading the *ReviewDb* schema to the latest 2.16 schema version. But in 3.x we can
    // no longer access ReviewDb, so we can't check that directly.
    //
    // Instead, check for a NoteDb-specific side effect of the migration process: the presence of
    // the NoteDb group sequence ref. This is created by the schema 163 migration, which was part of
    // 2.16 and not 2.15.
    //
    // There are a few corner cases where we will proceed even if the schema is not fully up to
    // date:
    //  * If a user happened to run init from master after schema 163 was added but before 2.16
    //    final. We assume that someone savvy enough to do that has followed the documented
    //    requirement of upgrading to 2.16 final before 3.0.
    //  * If a user ran init in 2.16.x and the upgrade to 163 succeeded but a later update failed.
    //    In this case the server literally will not start under 2.16. We assume the user will fix
    //    this and get 2.16 running rather than abandoning 2.16 and jumping to 3.0 at this point.
    try (Repository allUsers = repoManager.openRepository(allUsersName)) {
      if (allUsers.exactRef(RefNames.REFS_SEQUENCES + Sequences.NAME_GROUPS) == null) {
        throw new StorageException(
            "You appear to be upgrading to 3.x from a version prior to 2.16; you must upgrade to"
                + " 2.16.x first");
      }
    } catch (IOException e) {
      throw new StorageException("Failed to check NoteDb migration state", e);
    }
  }

  @VisibleForTesting
  static ImmutableList<Integer> requiredUpgrades(
      int currentVersion, ImmutableSortedSet<Integer> allVersions) throws StorageException {
    int firstVersion = allVersions.first();
    int latestVersion = allVersions.last();
    if (currentVersion == latestVersion) {
      return ImmutableList.of();
    } else if (currentVersion > latestVersion) {
      throw new StorageException(
          String.format(
              "Cannot downgrade NoteDb schema from version %d to %d",
              currentVersion, latestVersion));
    }

    int firstUpgradeVersion;
    if (currentVersion == 0) {
      // Bootstrap NoteDb version to minimum supported schema number.
      firstUpgradeVersion = firstVersion;
    } else {
      if (currentVersion < firstVersion - 1) {
        throw new StorageException(
            String.format(
                "Cannot skip NoteDb schema from version %d to %d", currentVersion, firstVersion));
      }
      firstUpgradeVersion = currentVersion + 1;
    }
    return IntStream.rangeClosed(firstUpgradeVersion, latestVersion)
        .boxed()
        .collect(toImmutableList());
  }
}
