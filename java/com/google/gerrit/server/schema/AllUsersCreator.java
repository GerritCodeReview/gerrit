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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.schema.AclUtil.grant;
import static com.google.gerrit.server.schema.AllProjectsInput.getDefaultCodeReviewLabel;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.common.Version;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectConfig.Factory;
import com.google.gerrit.server.project.RefPattern;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

/** Creates the {@code All-Users} repository. */
public class AllUsersCreator {
  private final GitRepositoryManager mgr;
  private final AllUsersName allUsersName;
  private final PersonIdent serverUser;
  private final ProjectConfig.Factory projectConfigFactory;
  private final GroupReference registered;

  @Nullable private GroupReference admin;
  private LabelType codeReviewLabel;

  @Inject
  AllUsersCreator(
      GitRepositoryManager mgr,
      AllUsersName allUsersName,
      SystemGroupBackend systemGroupBackend,
      @GerritPersonIdent PersonIdent serverUser,
      Factory projectConfigFactory) {
    this.mgr = mgr;
    this.allUsersName = allUsersName;
    this.serverUser = serverUser;
    this.registered = systemGroupBackend.getGroup(REGISTERED_USERS);
    this.projectConfigFactory = projectConfigFactory;
    this.codeReviewLabel = getDefaultCodeReviewLabel();
  }

  /**
   * If setAdministrators() is called, grant the given administrator group permissions on the
   * default user.
   */
  public AllUsersCreator setAdministrators(GroupReference admin) {
    this.admin = admin;
    return this;
  }

  /** If called, the provided "Code-Review" label will be used rather than the default. */
  @UsedAt(UsedAt.Project.GOOGLE)
  public AllUsersCreator setCodeReviewLabel(LabelType labelType) {
    checkArgument(
        labelType.getName().equals("Code-Review"), "label should have 'Code-Review' as its name");
    this.codeReviewLabel = labelType;
    return this;
  }

  public void create() throws IOException, ConfigInvalidException {
    try (Repository git = mgr.openRepository(allUsersName)) {
      initAllUsers(git);
    } catch (RepositoryNotFoundException notFound) {
      try (Repository git = mgr.createRepository(allUsersName)) {
        initAllUsers(git);
        RefUpdate u = git.updateRef(Constants.HEAD);
        u.link(RefNames.REFS_CONFIG);
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

      ProjectConfig config = projectConfigFactory.read(md);
      config.updateProject(p -> p.setDescription("Individual user settings and preferences."));

      config.upsertAccessSection(
          RefNames.REFS_USERS + "${" + RefPattern.USERID_SHARDED + "}",
          users -> {
            grant(config, users, Permission.READ, false, true, registered);
            grant(config, users, Permission.PUSH, false, true, registered);
            grant(config, users, Permission.SUBMIT, false, true, registered);
            grant(config, users, codeReviewLabel, -2, 2, true, registered);
          });

      // Initialize "Code-Review" label.
      config.upsertLabelType(codeReviewLabel);

      if (admin != null) {
        config.upsertAccessSection(
            RefNames.REFS_USERS_DEFAULT,
            defaults -> {
              defaults.upsertPermission(Permission.READ).setExclusiveGroup(true);
              grant(config, defaults, Permission.READ, admin);
              defaults.upsertPermission(Permission.PUSH).setExclusiveGroup(true);
              grant(config, defaults, Permission.PUSH, admin);
              defaults.upsertPermission(Permission.CREATE).setExclusiveGroup(true);
              grant(config, defaults, Permission.CREATE, admin);
            });
      }

      // Grant read permissions on the group branches to all users.
      // This allows group owners to see the group refs. VisibleRefFilter ensures that read
      // permissions for non-group-owners are ignored.
      config.upsertAccessSection(
          RefNames.REFS_GROUPS + "*",
          groups -> {
            grant(config, groups, Permission.READ, false, true, registered);
          });

      config.commit(md);
    }
  }
}
