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

import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.schema.AclUtil.grant;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.sql.SQLException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

/** Grant read on group branches */
public class Schema_164 extends SchemaVersion {
  private static final String COMMIT_MSG = "Grant read permissions on group branches";

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final SystemGroupBackend systemGroupBackend;
  private final PersonIdent serverUser;

  @Inject
  Schema_164(
      Provider<Schema_163> prior,
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      SystemGroupBackend systemGroupBackend,
      @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.systemGroupBackend = systemGroupBackend;
    this.serverUser = serverUser;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    try (Repository git = repoManager.openRepository(allUsersName);
        MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED, allUsersName, git)) {
      md.getCommitBuilder().setAuthor(serverUser);
      md.getCommitBuilder().setCommitter(serverUser);
      md.setMessage(COMMIT_MSG);

      ProjectConfig config = ProjectConfig.read(md);
      AccessSection groups = config.getAccessSection(RefNames.REFS_GROUPS + "*", true);
      grant(
          config,
          groups,
          Permission.READ,
          false,
          true,
          systemGroupBackend.getGroup(REGISTERED_USERS));
      config.commit(md);
    } catch (IOException | ConfigInvalidException e) {
      throw new OrmException("Failed to grant read permissions on group branches", e);
    }
  }
}
