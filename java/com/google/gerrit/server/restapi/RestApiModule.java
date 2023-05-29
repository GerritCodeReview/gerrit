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

package com.google.gerrit.server.restapi;

import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.plugins.PluginRestApiModule;
import com.google.gerrit.server.restapi.access.AccessRestApiModule;
import com.google.gerrit.server.restapi.account.AccountRestApiModule;
import com.google.gerrit.server.restapi.change.ChangeRestApiModule;
import com.google.gerrit.server.restapi.config.ConfigRestApiModule;
import com.google.gerrit.server.restapi.config.RestCacheAdminModule;
import com.google.gerrit.server.restapi.group.GroupRestApiModule;
import com.google.gerrit.server.restapi.project.ProjectRestApiModule;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;

public class RestApiModule extends AbstractModule {

  private final boolean deleteGroupEnabled;

  @Inject
  public RestApiModule(@GerritServerConfig Config cfg) {
    deleteGroupEnabled = cfg.getBoolean("groups", "enableDeleteGroup", false);
  }
  @Override
  protected void configure() {
    install(new AccessRestApiModule());
    install(new AccountRestApiModule());
    install(new ChangeRestApiModule());
    install(new ConfigRestApiModule());
    install(new RestCacheAdminModule());
    install(new GroupRestApiModule(deleteGroupEnabled));
    install(new PluginRestApiModule());
    install(new ProjectRestApiModule());
    install(new ProjectRestApiModule.BatchModule());
  }
}
