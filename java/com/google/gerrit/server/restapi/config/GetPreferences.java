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

package com.google.gerrit.server.restapi.config;

import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.account.Preferences;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class GetPreferences implements RestReadView<ConfigResource> {
  private final GitRepositoryManager gitMgr;
  private final AllUsersName allUsersName;

  @Inject
  public GetPreferences(GitRepositoryManager gitMgr, AllUsersName allUsersName) {
    this.gitMgr = gitMgr;
    this.allUsersName = allUsersName;
  }

  @Override
  public GeneralPreferencesInfo apply(ConfigResource rsrc)
      throws IOException, ConfigInvalidException {
    try (Repository git = gitMgr.openRepository(allUsersName)) {
      return Preferences.readDefaultGeneralPreferences(git);
    }
  }
}
