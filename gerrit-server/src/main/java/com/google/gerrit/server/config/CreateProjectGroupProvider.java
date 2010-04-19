// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.SystemConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.eclipse.jgit.lib.Config;

public class CreateProjectGroupProvider implements Provider<AccountGroup.Id> {
  private final AccountGroup.Id groupId;

  @Inject
  CreateProjectGroupProvider(@GerritServerConfig final Config config, final SystemConfig systemConfig) {
    final String idAsString = config.getString("gerrit", null, "createProjectGroup");
    if (idAsString == null || idAsString.equals("")){
      groupId = systemConfig.adminGroupId;
    }else{
      groupId = AccountGroup.Id.parse(idAsString);
    }
  }

  public AccountGroup.Id get() {
    return groupId;
  }
}