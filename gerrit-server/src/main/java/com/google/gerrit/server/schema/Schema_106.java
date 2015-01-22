// Copyright (C) 2015 The Android Open Source Project
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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.SortedSet;

public class Schema_106 extends SchemaVersion {
  private final GitRepositoryManager repoManager;
  private final PersonIdent serverUser;

  @Inject
  Schema_106(Provider<Schema_105> prior,
      GitRepositoryManager repoManager,
      @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.repoManager = repoManager;
    this.serverUser = serverUser;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    if (!(repoManager instanceof LocalDiskRepositoryManager)) {
      return;
    }

    ui.message("listing all repositories ...");
    SortedSet<Project.NameKey> repoList = repoManager.list();
    ui.message("done");

    ui.message(String.format("creating reflog files for %s branches ...",
        RefNames.REFS_CONFIG));
    for (Project.NameKey project : repoList) {
      try {
        Repository repo = repoManager.openRepository(project);
        try {
          File metaConfigLog =
              new File(repo.getDirectory(), "logs/" + RefNames.REFS_CONFIG);
          if (metaConfigLog.exists()) {
            continue;
          }

          if (!metaConfigLog.getParentFile().mkdirs()
              || !metaConfigLog.createNewFile()) {
            throw new IOException(String.format(
                "Failed to create reflog for %s in repository %s",
                RefNames.REFS_CONFIG, project));
          }

          ObjectId metaConfigId = repo.resolve(RefNames.REFS_CONFIG);
          if (metaConfigId != null) {
            try (PrintWriter writer =
                new PrintWriter(metaConfigLog, UTF_8.name())) {
              writer.print(ObjectId.zeroId().name());
              writer.print(" ");
              writer.print(metaConfigId.name());
              writer.print(" ");
              writer.print(serverUser.toExternalString());
              writer.print("\t");
              writer.print("create reflog");
              writer.println();
            }
          }
        } finally {
          repo.close();
        }
      } catch (IOException e) {
        ui.message(String.format("ERROR: Failed to create reflog file for the"
            + " %s branch in repository %s", RefNames.REFS_CONFIG, project.get()));
      }
    }
    ui.message("done");
  }
}
