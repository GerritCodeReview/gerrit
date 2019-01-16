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

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.ProvisionException;

public class NoteDbSchemaVersionCheck implements LifecycleListener {
  public static Module module() {
    return new LifecycleModule() {
      @Override
      protected void configure() {
        listener().to(NoteDbSchemaVersionCheck.class);
      }
    };
  }

  private final NoteDbSchemaVersionManager versionManager;
  private final SitePaths sitePaths;

  @Inject
  NoteDbSchemaVersionCheck(NoteDbSchemaVersionManager versionManager, SitePaths sitePaths) {
    this.versionManager = versionManager;
    this.sitePaths = sitePaths;
  }

  @Override
  public void start() {
    try {
      int current = versionManager.read();
      if (current == 0) {
        throw new ProvisionException(
            String.format(
                "Schema not yet initialized. Run init to initialize the schema:\n"
                    + "$ java -jar gerrit.war init -d %s",
                sitePaths.site_path.toAbsolutePath()));
      }
      int expected = NoteDbSchemaVersions.LATEST;
      if (current != expected) {
        String advice =
            current > expected
                ? "Downgrade is not supported"
                : String.format(
                    "Run init to upgrade:\n$ java -jar %s init -d %s",
                    sitePaths.gerrit_war.toAbsolutePath(), sitePaths.site_path.toAbsolutePath());
        throw new ProvisionException(
            String.format(
                "Unsupported schema version %d; expected schema version %d. %s",
                current, expected, advice));
      }
    } catch (StorageException e) {
      throw new ProvisionException("Failed to read NoteDb schema version", e);
    }
  }

  @Override
  public void stop() {
    // Do nothing.
  }
}
