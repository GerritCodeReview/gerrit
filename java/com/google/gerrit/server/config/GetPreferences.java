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

package com.google.gerrit.server.config;

import static com.google.gerrit.server.config.ConfigUtil.loadSection;

import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.account.GeneralPreferencesLoader;
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
public class GetPreferences implements RestReadView<ConfigResource> {
  private final GeneralPreferencesLoader loader;
  private final GitRepositoryManager gitMgr;
  private final AllUsersName allUsersName;

  @Inject
  public GetPreferences(
      GeneralPreferencesLoader loader, GitRepositoryManager gitMgr, AllUsersName allUsersName) {
    this.loader = loader;
    this.gitMgr = gitMgr;
    this.allUsersName = allUsersName;
  }

  @Override
  public GeneralPreferencesInfo apply(ConfigResource rsrc)
      throws IOException, ConfigInvalidException {
    return readFromGit(gitMgr, loader, allUsersName, null);
  }

  static GeneralPreferencesInfo readFromGit(
      GitRepositoryManager gitMgr,
      GeneralPreferencesLoader loader,
      AllUsersName allUsersName,
      GeneralPreferencesInfo in)
      throws IOException, ConfigInvalidException, RepositoryNotFoundException {
    try (Repository git = gitMgr.openRepository(allUsersName)) {
      VersionedAccountPreferences p = VersionedAccountPreferences.forDefault();
      p.load(git);

      GeneralPreferencesInfo r =
          loadSection(
              p.getConfig(),
              UserConfigSections.GENERAL,
              null,
              new GeneralPreferencesInfo(),
              GeneralPreferencesInfo.defaults(),
              in);

      // TODO(davido): Maintain cache of default values in AllUsers repository
      return loader.loadMyMenusAndUrlAliases(r, p, null);
    }
  }
}
