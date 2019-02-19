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

import com.google.gerrit.server.plugins.PluginRestApiModule;
import com.google.gerrit.server.restapi.config.RestCacheAdminModule;
import com.google.inject.AbstractModule;

public class RestApiModule extends AbstractModule {
  @Override
  protected void configure() {
    install(new com.google.gerrit.server.restapi.access.Module());
    install(new com.google.gerrit.server.restapi.account.Module());
    install(new com.google.gerrit.server.restapi.change.Module());
    install(new com.google.gerrit.server.restapi.config.Module());
    install(new RestCacheAdminModule());
    install(new com.google.gerrit.server.restapi.group.Module());
    install(new PluginRestApiModule());
    install(new com.google.gerrit.server.restapi.project.Module());
  }
}
