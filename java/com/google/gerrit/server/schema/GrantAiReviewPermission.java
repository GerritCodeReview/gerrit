// Copyright (C) 2026 The Android Open Source Project
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
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

/**
 * Adds an explicit {@code allow aiReview group Registered Users} rule on {@code refs/heads/*} to
 * {@code All-Projects} so existing sites preserve their previous default-allow behavior after
 * {@code aiReview} switches to standard default-deny semantics.
 *
 * <p>Skipped when an {@code aiReview} permission already exists on {@code refs/heads/*} so admin
 * customizations are not overwritten.
 */
public class GrantAiReviewPermission {

  private final GitRepositoryManager repoManager;
  private final ProjectConfig.Factory projectConfigFactory;
  private final SystemGroupBackend systemGroupBackend;
  private final PersonIdent serverUser;

  @Inject
  public GrantAiReviewPermission(
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

      boolean alreadyHasAiReview =
          projectConfig.getAccessSection(AccessSection.HEADS) != null
              && projectConfig
                      .getAccessSection(AccessSection.HEADS)
                      .getPermission(Permission.AI_REVIEW)
                  != null;
      if (alreadyHasAiReview) {
        return;
      }

      projectConfig.upsertAccessSection(
          AccessSection.HEADS,
          heads -> grant(projectConfig, heads, Permission.AI_REVIEW, registeredUsers));

      md.getCommitBuilder().setAuthor(serverUser);
      md.getCommitBuilder().setCommitter(serverUser);
      md.setMessage("Add AI Review permission for all registered users\n");

      projectConfig.commit(md);
    }
  }
}
