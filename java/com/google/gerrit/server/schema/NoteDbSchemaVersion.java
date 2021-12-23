// Copyright (C) 2018 The Android Open Source Project
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

import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.index.group.GroupIndexCollection;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * Schema upgrade implementation.
 *
 * <p>Implementations must have a single non-private constructor with no arguments (e.g. the default
 * constructor).
 */
public interface NoteDbSchemaVersion {
  @Singleton
  class Arguments {
    final GitRepositoryManager repoManager;
    final AllProjectsName allProjects;
    final AllUsersName allUsers;
    final ProjectConfig.Factory projectConfigFactory;
    final SystemGroupBackend systemGroupBackend;
    final PersonIdent serverUser;
    final GroupIndexCollection groupIndexCollection;
    final BatchUpdate.Factory updateFactory;
    final Provider<CurrentUser> userProvider;
    final ApprovalsUtil approvalsUtil;
    final ProjectCache projectCache;

    @Inject
    Arguments(
        GitRepositoryManager repoManager,
        AllProjectsName allProjects,
        AllUsersName allUsers,
        ProjectConfig.Factory projectConfigFactory,
        SystemGroupBackend systemGroupBackend,
        @GerritPersonIdent PersonIdent serverUser,
        GroupIndexCollection groupIndexCollection,
        BatchUpdate.Factory updateFactory,
        Provider<CurrentUser> userProvider,
        ApprovalsUtil approvalsUtil,
        ProjectCache projectCache) {
      this.repoManager = repoManager;
      this.allProjects = allProjects;
      this.allUsers = allUsers;
      this.projectConfigFactory = projectConfigFactory;
      this.systemGroupBackend = systemGroupBackend;
      this.serverUser = serverUser;
      this.groupIndexCollection = groupIndexCollection;
      this.updateFactory = updateFactory;
      this.userProvider = userProvider;
      this.approvalsUtil = approvalsUtil;
      this.projectCache = projectCache;
    }
  }

  void upgrade(Arguments args, UpdateUI ui) throws Exception;
}
