// Copyright (C) 2017 The Android Open Source Project
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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;

/** Set submit type to {@link SubmitType#MERGE_IF_NECESSARY} on all projects where it is unset. */
public class Schema_162 extends SchemaVersion {
  private final Provider<PersonIdent> serverIdent;
  private final GitRepositoryManager repoManager;

  @Inject
  Schema_162(
      Provider<Schema_161> prior,
      @GerritPersonIdent Provider<PersonIdent> serverIdent,
      GitRepositoryManager repoManager) {
    super(prior);
    this.serverIdent = serverIdent;
    this.repoManager = repoManager;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    try {
      ProgressMonitor pm = new TextProgressMonitor();
      pm.beginTask("Setting default submit types", ProgressMonitor.UNKNOWN);
      for (Project.NameKey p : repoManager.list()) {
        if (setDefaultSubmitType(p)) {
          pm.update(1);
        }
      }
    } catch (ConfigInvalidException | IOException e) {
      throw new OrmException(e);
    }
  }

  private boolean setDefaultSubmitType(Project.NameKey p)
      throws ConfigInvalidException, IOException {
    try (Repository repo = repoManager.openRepository(p)) {
      MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED, p, repo);
      PersonIdent ident = serverIdent.get();
      md.getCommitBuilder().setAuthor(ident);
      md.getCommitBuilder().setCommitter(ident);
      RawConfig rc = new RawConfig(p);
      rc.load(md);

      if (rc.rawConfig.getString("submit", null, "action") != null) {
        return false;
      }
      // Default is now INHERIT, so setting this non-default value will write it into
      // project.config.
      rc.getProject().setSubmitType(SubmitType.MERGE_IF_NECESSARY);
      rc.commit(md);
      return true;
    }
  }

  @VisibleForTesting
  static class RawConfig extends ProjectConfig {
    Config rawConfig;

    RawConfig(Project.NameKey p) {
      super(p);
    }

    @Override
    protected void onLoad() throws ConfigInvalidException, IOException {
      super.onLoad();
      rawConfig = readConfig(ProjectConfig.PROJECT_CONFIG);
    }
  }
}
