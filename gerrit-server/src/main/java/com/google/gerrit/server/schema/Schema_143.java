//Copyright (C) 2016 The Android Open Source Project
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

package com.google.gerrit.server.schema;

import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.schema.AclUtil.block;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

public class Schema_143 extends SchemaVersion {
  private static final String COMMIT_MSG =
      "Block read access to " + RefNames.REFS_EXTERNAL_IDS + " for all users";

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final SystemGroupBackend systemGroupBackend;
  private final PersonIdent serverUser;

  @Inject
  Schema_143(
      Provider<Schema_142> prior,
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
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    try (Repository git = repoManager.openRepository(allUsersName);
        MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED, allUsersName, git)) {
      ProjectConfig config = ProjectConfig.read(md);

      AccessSection externalIds = config.getAccessSection(RefNames.REFS_EXTERNAL_IDS, true);
      block(config, externalIds, Permission.READ, systemGroupBackend.getGroup(ANONYMOUS_USERS));

      md.getCommitBuilder().setAuthor(serverUser);
      md.getCommitBuilder().setCommitter(serverUser);
      md.setMessage(COMMIT_MSG);
      config.commit(md);
    } catch (ConfigInvalidException | IOException ex) {
      throw new OrmException(ex);
    }
  }
}
