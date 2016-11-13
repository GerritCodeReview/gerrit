// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.common.Version;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.RefPattern;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

/** Creates the {@code All-Users} repository. */
public class AllUsersCreator {
  private final GitRepositoryManager mgr;
  private final AllUsersName allUsersName;
  private final PersonIdent serverUser;
  private final GroupReference registered;

  private GroupReference admin;

  @Inject
  AllUsersCreator(
      GitRepositoryManager mgr,
      AllUsersName allUsersName,
      @GerritPersonIdent PersonIdent serverUser) {
    this.mgr = mgr;
    this.allUsersName = allUsersName;
    this.serverUser = serverUser;
    this.registered = SystemGroupBackend.getGroup(REGISTERED_USERS);
  }

  public AllUsersCreator setAdministrators(GroupReference admin) {
    this.admin = admin;
    return this;
  }

  public void create() throws IOException, ConfigInvalidException {
    try (Repository git = mgr.openRepository(allUsersName)) {
      initAllUsers(git);
    } catch (RepositoryNotFoundException notFound) {
      try (Repository git = mgr.createRepository(allUsersName)) {
        initAllUsers(git);
      } catch (RepositoryNotFoundException err) {
        String name = allUsersName.get();
        throw new IOException("Cannot create repository " + name, err);
      }
    }
  }

  private void initAllUsers(Repository git) throws IOException, ConfigInvalidException {
    try (MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED, allUsersName, git)) {
      md.getCommitBuilder().setAuthor(serverUser);
      md.getCommitBuilder().setCommitter(serverUser);
      md.setMessage("Initialized Gerrit Code Review " + Version.getVersion());

      ProjectConfig config = ProjectConfig.read(md);
      Project project = config.getProject();
      project.setDescription("Individual user settings and preferences.");

      AccessSection users =
          config.getAccessSection(
              RefNames.REFS_USERS + "${" + RefPattern.USERID_SHARDED + "}", true);
      LabelType cr = AllProjectsCreator.initCodeReviewLabel(config);
      grant(config, users, Permission.READ, false, true, registered);
      grant(config, users, Permission.PUSH, false, true, registered);
      grant(config, users, Permission.SUBMIT, false, true, registered);
      grant(config, users, cr, -2, 2, registered);

      AccessSection defaults = config.getAccessSection(RefNames.REFS_USERS_DEFAULT, true);
      defaults.getPermission(Permission.READ, true).setExclusiveGroup(true);
      grant(config, defaults, Permission.READ, admin);
      defaults.getPermission(Permission.PUSH, true).setExclusiveGroup(true);
      grant(config, defaults, Permission.PUSH, admin);
      defaults.getPermission(Permission.CREATE, true).setExclusiveGroup(true);
      grant(config, defaults, Permission.CREATE, admin);

      config.commit(md);
    }
  }
}
