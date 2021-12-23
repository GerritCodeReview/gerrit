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

import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.index.group.GroupIndexCollection;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.Inject;
import com.google.inject.Injector;
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
    final Injector dbInjector;
    final GitRepositoryManager repoManager;
    final AllProjectsName allProjects;
    final AllUsersName allUsers;
    final ProjectConfig.Factory projectConfigFactory;
    final SystemGroupBackend systemGroupBackend;
    final PersonIdent serverUser;
    final GroupIndexCollection groupIndexCollection;

    @Inject
    Arguments(
        Injector dbInjector,
        GitRepositoryManager repoManager,
        AllProjectsName allProjects,
        AllUsersName allUsers,
        ProjectConfig.Factory projectConfigFactory,
        SystemGroupBackend systemGroupBackend,
        @GerritPersonIdent PersonIdent serverUser,
        GroupIndexCollection groupIndexCollection) {
      this.dbInjector = dbInjector;
      this.repoManager = repoManager;
      this.allProjects = allProjects;
      this.allUsers = allUsers;
      this.projectConfigFactory = projectConfigFactory;
      this.systemGroupBackend = systemGroupBackend;
      this.serverUser = serverUser;
      this.groupIndexCollection = groupIndexCollection;
    }
  }

  void upgrade(Arguments args, UpdateUI ui) throws Exception;
}
