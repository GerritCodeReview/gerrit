// Copyright (C) 2025 The Android Open Source Project
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

import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

/**
 * This class adds the "postReviewComment" permission to all hosts that call this method with the
 * relevant projectName. This class should be called with AllProjects as the project, by all hosts
 * before enabling the "postReviewComment" permission.
 */
public class GrantPostReviewCommentPermission {

  private final GitRepositoryManager repoManager;
  private final ProjectConfig.Factory projectConfigFactory;
  private final SystemGroupBackend systemGroupBackend;
  private final PersonIdent serverUser;

  @Inject
  public GrantPostReviewCommentPermission(
      GitRepositoryManager repoManager,
      ProjectConfig.Factory projectConfigFactory,
      SystemGroupBackend systemGroupBackend,
      @GerritPersonIdent PersonIdent serverUser) {
    this.repoManager = repoManager;
    this.projectConfigFactory = projectConfigFactory;
    this.systemGroupBackend = systemGroupBackend;
    this.serverUser = serverUser;
  }

  public void execute(Project.NameKey projectName) throws IOException, ConfigInvalidException {
    GroupReference registeredUsers = systemGroupBackend.getGroup(REGISTERED_USERS);
    try (Repository repo = repoManager.openRepository(projectName)) {
      MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED, projectName, repo);
      ProjectConfig projectConfig = projectConfigFactory.read(md);

      AtomicBoolean shouldExit = new AtomicBoolean(false);
      projectConfig.upsertAccessSection(
          AccessSection.ALL,
          all -> {
            Permission permissionOnRefsStar =
                all.build().getPermission(Permission.POST_REVIEW_COMMENT);
            if (permissionOnRefsStar != null) {
              if (permissionOnRefsStar.getRule(registeredUsers) == null) {
                // If admins already changed the permission, don't do anything.
                shouldExit.set(true);
                return;
              }
              // permission already exists on refs/* for Registered Users, don't do anything.
              return;
            }
            // If the permission doesn't exist on refs/* for Registered Users, grant it.
            grant(projectConfig, all, Permission.POST_REVIEW_COMMENT, registeredUsers);
          });

      if (shouldExit.get()) {
        return;
      }

      md.getCommitBuilder().setAuthor(serverUser);
      md.getCommitBuilder().setCommitter(serverUser);
      md.setMessage("Add Post Review Comment permission for all registered users\n");

      projectConfig.commit(md);
    }
  }
}
