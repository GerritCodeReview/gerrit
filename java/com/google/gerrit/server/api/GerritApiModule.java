// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.api;

import com.google.gerrit.common.UsedAt;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.api.accounts.AccountsModule;
import com.google.gerrit.server.api.changes.ChangesModule;
import com.google.gerrit.server.api.config.ConfigModule;
import com.google.gerrit.server.api.groups.GroupsModule;
import com.google.gerrit.server.api.projects.ProjectsModule;
import com.google.gerrit.server.query.project.ProjectQueryBuilder;
import com.google.gerrit.server.query.project.ProjectQueryBuilderImpl;

public class GerritApiModule extends FactoryModule {
  @Override
  protected void configure() {
    bind(GerritApi.class).to(GerritApiImpl.class);
    bindProjectQueryBuilder();

    install(new AccountsModule());
    install(new ChangesModule());
    install(new ConfigModule());
    install(new GroupsModule());
    install(new ProjectsModule());
  }

  @UsedAt(UsedAt.Project.GOOGLE)
  protected void bindProjectQueryBuilder() {
    bind(ProjectQueryBuilder.class).to(ProjectQueryBuilderImpl.class);
  }
}
