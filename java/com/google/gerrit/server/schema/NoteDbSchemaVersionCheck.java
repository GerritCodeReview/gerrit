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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.ProvisionException;
import org.eclipse.jgit.lib.Config;

public class NoteDbSchemaVersionCheck implements LifecycleListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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
  private Config gerritConfig;

  @Inject
  NoteDbSchemaVersionCheck(
      NoteDbSchemaVersionManager versionManager,
      SitePaths sitePaths,
      @GerritServerConfig Config gerritConfig) {
    this.versionManager = versionManager;
    this.sitePaths = sitePaths;
    this.gerritConfig = gerritConfig;
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

      if (current > expected
          && gerritConfig.getBoolean("gerrit", "experimentalRollingUpgrade", false)) {
        logger.atWarning().log(
            "Gerrit has detected refs/meta/version %d different than the expected %d.Bear in mind"
                + " that this is supported ONLY for rolling upgrades to immediate next Gerrit"
                + " version (e.g. v3.1 to v3.2). If this is not expected, remove"
                + " gerrit.experimentalRollingUpgrade from $GERRIT_SITE/etc/gerrit.config and"
                + " restart Gerrit.Please note that gerrit.experimentalRollingUpgrade is intended"
                + " to be used for the rolling upgrade phase only and should be disabled"
                + " afterwards.",
            current, expected);
      } else if (current != expected) {
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
