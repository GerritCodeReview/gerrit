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
import static com.google.gerrit.server.notedb.NoteDbTable.CHANGES;
import static com.google.gerrit.server.notedb.NotesMigration.DISABLE_REVIEW_DB;
import static com.google.gerrit.server.notedb.NotesMigration.PRIMARY_STORAGE;
import static com.google.gerrit.server.notedb.NotesMigration.READ;
import static com.google.gerrit.server.notedb.NotesMigration.SECTION_NOTE_DB;
import static com.google.gerrit.server.notedb.NotesMigration.WRITE;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.notedb.NoteDbSchemaVersionManager;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.stream.IntStream;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;

public class NoteDbSchemaUpdater {
  private final Config cfg;
  private final AllUsersName allUsersName;
  private final GitRepositoryManager repoManager;
  private final NotesMigration notesMigration;
  private final NoteDbSchemaVersionManager versionManager;
  private final NoteDbSchemaVersion.Arguments args;
  private final ImmutableSortedMap<Integer, Class<? extends NoteDbSchemaVersion>> schemaVersions;

  @Inject
  NoteDbSchemaUpdater(
      @GerritServerConfig Config cfg,
      NotesMigration notesMigration,
      NoteDbSchemaVersionManager versionManager,
      NoteDbSchemaVersion.Arguments args,
      GitRepositoryManager repoManager,
      AllUsersName allUsersName) {
    this(
        cfg,
        allUsersName,
        repoManager,
        notesMigration,
        versionManager,
        args,
        NoteDbSchemaVersions.ALL);
  }

  NoteDbSchemaUpdater(
      Config cfg,
      AllUsersName allUsersName,
      GitRepositoryManager repoManager,
      NotesMigration notesMigration,
      NoteDbSchemaVersionManager versionManager,
      NoteDbSchemaVersion.Arguments args,
      ImmutableSortedMap<Integer, Class<? extends NoteDbSchemaVersion>> schemaVersions) {
    this.cfg = cfg;
    this.allUsersName = allUsersName;
    this.repoManager = repoManager;
    this.notesMigration = notesMigration;
    this.versionManager = versionManager;
    this.args = args;
    this.schemaVersions = schemaVersions;
  }

  public void update(UpdateUI ui) throws OrmException {
    if (!notesMigration.commitChangeWrites()) {
      // TODO(dborowitz): Only necessary to make migration tests pass; remove when NoteDb is the
      // only option.
      return;
    }
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
        NoteDbSchemaVersions.get(schemaVersions, nextVersion, args).upgrade(ui);
        versionManager.increment(nextVersion - 1);
      } catch (Exception e) {
        throw new OrmException("Failed to upgrade to schema version " + nextVersion, e);
      }
    }
  }

  private void checkNoteDbConfigFor216() throws OrmException {
    // Check that the NoteDb migration config matches what we expect from a site that both:
    // * Completed the change migration to NoteDB.
    // * Ran schema upgrades from a 2.16 final release.

    if (!cfg.getBoolean(SECTION_NOTE_DB, CHANGES.key(), WRITE, false)
        || !cfg.getBoolean(SECTION_NOTE_DB, CHANGES.key(), READ, false)
        || cfg.getEnum(SECTION_NOTE_DB, CHANGES.key(), PRIMARY_STORAGE, PrimaryStorage.REVIEW_DB)
            != PrimaryStorage.NOTE_DB
        || !cfg.getBoolean(SECTION_NOTE_DB, CHANGES.key(), DISABLE_REVIEW_DB, false)) {
      // TODO(dborowitz): Include more details?
      throw new OrmException(
          "You appear to be upgrading from a 2.x site, but the NoteDb change migration was"
              + " not completed");
    }

    // We don't have a straightforward way to check that 2.16 init was run; the most obvious side
    // effect would be upgrading the *ReviewDb* schema to the latest 2.16 schema version. But in 3.x
    // we can no longer access ReviewDb, so we can't check that directly.
    //
    // Instead, check for a NoteDb-specific side effect of the migration process: the presence of
    // the NoteDb group sequence ref. This is created by the schema 163 migration, which was part of
    // 2.16 and not 2.15.
    //
    // There is one corner case where we will proceed even if the schema is not fully up to date: if
    // a user happened to run init from master after schema 163 was added but before 2.16 final. We
    // assume that someone savvy enough to do that has followed the documented requirement of
    // upgrading to 2.16 final before 3.0.
    try (Repository allUsers = repoManager.openRepository(allUsersName)) {
      if (allUsers.exactRef(RefNames.REFS_SEQUENCES + Sequences.NAME_GROUPS) == null) {
        throw new OrmException(
            "You appear to be upgrading to 3.x from a version prior to 2.16; you must upgrade to"
                + " 2.16.x first");
      }
    } catch (IOException e) {
      throw new OrmException("Failed to check NoteDb migration state", e);
    }
  }

  @VisibleForTesting
  static ImmutableList<Integer> requiredUpgrades(
      int currentVersion, ImmutableSortedSet<Integer> allVersions) throws OrmException {
    int firstVersion = allVersions.first();
    int latestVersion = allVersions.last();
    if (currentVersion == latestVersion) {
      return ImmutableList.of();
    } else if (currentVersion > latestVersion) {
      throw new OrmException(
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
        throw new OrmException(
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
