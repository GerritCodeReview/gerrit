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

import static com.google.gerrit.server.account.GetPreferences.loadFromAllUsers;

import com.google.gerrit.extensions.client.AccountGeneralPreferencesInfo;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.account.VersionedAccountPreferences;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

@Singleton
public class GetPreferences implements RestReadView<ConfigResource> {
  private final AllUsersName allUsersName;
  private final GitRepositoryManager gitMgr;

  @Inject
  public GetPreferences(AllUsersName allUsersName,
      GitRepositoryManager gitMgr) {
    this.allUsersName = allUsersName;
    this.gitMgr = gitMgr;
  }

  @Override
  public AccountGeneralPreferencesInfo apply(ConfigResource rsrc)
      throws IOException, ConfigInvalidException {
    try (Repository git = gitMgr.openRepository(allUsersName)) {
      VersionedAccountPreferences p =
          VersionedAccountPreferences.forDefault();
      p.load(git);

      AccountGeneralPreferencesInfo a = new AccountGeneralPreferencesInfo();
      return loadFromAllUsers(a, p, git);
    }
  }
}
