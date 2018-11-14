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

import com.google.gerrit.server.schema.UpdateUI;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

public class NoteDbSchemaUpdater {
  private final NoteDbSchemaVersionManager versionManager;
  private final NoteDbSchemaVersion.Arguments args;

  @Inject
  NoteDbSchemaUpdater(
      NoteDbSchemaVersionManager versionManager, NoteDbSchemaVersion.Arguments args) {
    this.versionManager = versionManager;
    this.args = args;
  }

  public void update(UpdateUI ui) throws OrmException {
    int startVersion = versionManager.read();
    int latestVersion = NoteDbSchemaVersions.LATEST;
    if (startVersion == latestVersion) {
      return;
    } else if (startVersion > latestVersion) {
      throw new OrmException(
          "Cannot downgrade database schema from version "
              + startVersion
              + " to "
              + latestVersion
              + ".");
    }
    for (int nextVersion = startVersion + 1; nextVersion <= latestVersion; nextVersion++) {
      try {
        ui.message(String.format("Upgrading schema to %d ...", nextVersion));
        NoteDbSchemaVersion version = NoteDbSchemaVersions.get(nextVersion, args);
        version.upgrade(ui);
        versionManager.increment(nextVersion - 1);
      } catch (Exception e) {
        throw new OrmException("Failed to upgrade to schema version " + nextVersion);
      }
    }
  }
}
