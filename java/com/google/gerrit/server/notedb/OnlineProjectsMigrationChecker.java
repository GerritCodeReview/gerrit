// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import static com.google.gerrit.server.notedb.NotesMigration.SECTION_NOTE_DB;
import static com.google.gerrit.server.notedb.rebuild.OnlineNoteDbMigrator.ONLINE_MIGRATION_PROJECTS;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.GerritServerConfigProvider;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.Config;

@Singleton
public class OnlineProjectsMigrationChecker {
  private Set<String> migrationProjects;
  private final GerritServerConfigProvider configProvider;
  private NotesMigration notesMigration;

  @Inject
  public OnlineProjectsMigrationChecker(
      GerritServerConfigProvider configProvider, NotesMigration notesMigration) {
    this.configProvider = configProvider;
    this.notesMigration = notesMigration;
    reloadProjectsFromConfig();
  }

  /**
   * Check whether the NoteDB migration is enabled for the provided project and writes for NoteDB
   * should be committed.
   *
   * @param projectNameKey the name of the project to check
   * @return true when the migration is enabled for the project
   */
  public final boolean commitChangeWritesForProject(Project.NameKey projectNameKey) {
    return notesMigration.commitChangeWrites() && shouldMigrateProject(projectNameKey);
  }

  @VisibleForTesting
  public void reloadProjectsFromConfig(Config cfg) {
    this.migrationProjects = readProjects(cfg);
  }

  @VisibleForTesting
  public void reloadProjectsFromConfig() {
    this.migrationProjects = readProjects(configProvider.loadConfig());
  }

  private boolean shouldMigrateProject(Project.NameKey projectNameKey) {
    return migrationProjects.isEmpty() || migrationProjects.contains(projectNameKey.get());
  }

  private static Set<String> readProjects(Config cfg) {
    return Arrays.stream(cfg.getStringList(SECTION_NOTE_DB, null, ONLINE_MIGRATION_PROJECTS))
        .collect(Collectors.toSet());
  }
}
