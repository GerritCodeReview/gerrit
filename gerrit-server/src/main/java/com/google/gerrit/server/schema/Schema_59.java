// Copyright (C) 2011 The Android Open Source Project
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

import com.google.gerrit.common.data.MergeStrategySection;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.NoReplication;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

public class Schema_59 extends SchemaVersion {
  private final LocalDiskRepositoryManager mgr;
  private final PersonIdent serverUser;

  @Inject
  Schema_59(Provider<Schema_57> prior, LocalDiskRepositoryManager mgr,
      @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.mgr = mgr;
    this.serverUser = serverUser;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    for (Project.NameKey projectName : mgr.list()) {
      if (!projectName.get().equals(AllProjectsNameProvider.DEFAULT)) {
        try {
          Repository git = mgr.openRepository(projectName);
          try {
            MetaDataUpdate md =
                new MetaDataUpdate(new NoReplication(), projectName, git);
            md.getCommitBuilder().setAuthor(serverUser);
            md.getCommitBuilder().setCommitter(serverUser);

            final ProjectConfig config = new ProjectConfig(projectName) {
              protected void onSave(CommitBuilder commit) throws IOException,
                  ConfigInvalidException {
                final Config rc = readConfig(PROJECT_CONFIG);
                changeSubmitTypeToMergeStrategy(rc);
                saveConfig(PROJECT_CONFIG, rc);
              }

              private void changeSubmitTypeToMergeStrategy(final Config rc)
                  throws IOException, ConfigInvalidException {
                boolean deleteSubmitSection = true;
                for (final String s : rc.getSections()) {
                  if (s.equals(SUBMIT)) {
                    for (final String n : rc.getNames(SUBMIT)) {
                      if (n.equals(KEY_MERGE_CONTENT)) {
                        deleteSubmitSection = false;
                      } else if (n.equals("action")) {
                        if (!rc.getEnum(SUBMIT, null, "action",
                            defaultMergeStrategy).equals(defaultMergeStrategy)) {
                          final MergeStrategySection section =
                              getMergeStrategySection("refs/*", true);
                          section.setSubmitType(rc.getEnum(SUBMIT, null,
                              "action", defaultMergeStrategy));
                          set(rc, MERGE_STRATEGY, "refs/*", KEY_STRATEGY,
                              section.getSubmitType().name());
                        }
                        rc.unset(SUBMIT, null, "action");
                      }
                    }

                    if (deleteSubmitSection) {
                      rc.unsetSection(SUBMIT, null);
                    }
                  }
                }
              }
            };

            config.load(md);
            md.setMessage("Upgrade to Gerrit Code Review schema 56\n");
            if (!config.commit(md)) {
              throw new OrmException("Cannot update " + projectName);
            }
          } finally {
            git.close();
          }
        } catch (IOException err) {
          throw new OrmException("Cannot update " + projectName, err);
        } catch (ConfigInvalidException err) {
          throw new OrmException("Cannot update " + projectName, err);
        }
      }
    }
  }
}
