// Copyright (C) 2016 The Android Open Source Project
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
package com.google.gerrit.server.config;

import static com.google.gerrit.server.config.ConfigUtil.loadSection;

import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.account.VersionedAccountPreferences;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.UserConfigSections;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class GetDiffPreferences implements RestReadView<ConfigResource> {

  private final AllUsersName allUsersName;
  private final GitRepositoryManager gitManager;

  @Inject
  GetDiffPreferences(GitRepositoryManager gitManager, AllUsersName allUsersName) {
    this.allUsersName = allUsersName;
    this.gitManager = gitManager;
  }

  @Override
  public DiffPreferencesInfo apply(ConfigResource configResource)
      throws BadRequestException, ResourceConflictException, IOException, ConfigInvalidException {
    return readFromGit(gitManager, allUsersName, null);
  }

  static DiffPreferencesInfo readFromGit(
      GitRepositoryManager gitMgr, AllUsersName allUsersName, DiffPreferencesInfo in)
      throws IOException, ConfigInvalidException, RepositoryNotFoundException {
    try (Repository git = gitMgr.openRepository(allUsersName)) {
      // Load all users prefs.
      VersionedAccountPreferences dp = VersionedAccountPreferences.forDefault();
      dp.load(git);
      DiffPreferencesInfo allUserPrefs = new DiffPreferencesInfo();
      loadSection(
          dp.getConfig(),
          UserConfigSections.DIFF,
          null,
          allUserPrefs,
          DiffPreferencesInfo.defaults(),
          in);
      return allUserPrefs;
    }
  }
}
