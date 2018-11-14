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

package com.google.gerrit.server.notedb.schema;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.util.stream.IntStream;

public class NoteDbSchemaUpdater {
  private final NoteDbSchemaVersionManager versionManager;
  private final NoteDbSchemaVersion.Arguments args;
  private final ImmutableSortedMap<Integer, Class<? extends NoteDbSchemaVersion>> schemaVersions;

  @Inject
  NoteDbSchemaUpdater(
      NoteDbSchemaVersionManager versionManager, NoteDbSchemaVersion.Arguments args) {
    this(versionManager, args, NoteDbSchemaVersions.ALL);
  }

  NoteDbSchemaUpdater(
      NoteDbSchemaVersionManager versionManager,
      NoteDbSchemaVersion.Arguments args,
      ImmutableSortedMap<Integer, Class<? extends NoteDbSchemaVersion>> schemaVersions) {
    this.versionManager = versionManager;
    this.args = args;
    this.schemaVersions = schemaVersions;
  }

  public void update(UpdateUI ui) throws OrmException {
    for (int nextVersion : requiredUpgrades(versionManager.read(), schemaVersions.keySet())) {
      try {
        ui.message(String.format("Upgrading schema to %d ...", nextVersion));
        NoteDbSchemaVersions.get(schemaVersions, nextVersion, args).upgrade(ui);
        versionManager.increment(nextVersion - 1);
      } catch (Exception e) {
        throw new OrmException("Failed to upgrade to schema version " + nextVersion, e);
      }
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
