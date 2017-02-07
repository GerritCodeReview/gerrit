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

package com.google.gerrit.server.project;

import static com.google.inject.Scopes.SINGLETON;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.config.AdministrateServerGroups;
import com.google.gerrit.server.config.AdministrateServerGroupsProvider;
import com.google.gerrit.server.config.GitReceivePackGroups;
import com.google.gerrit.server.config.GitReceivePackGroupsProvider;
import com.google.gerrit.server.config.GitUploadPackGroups;
import com.google.gerrit.server.config.GitUploadPackGroupsProvider;
import com.google.inject.TypeLiteral;
import java.util.Set;

public class AccessControlModule extends FactoryModule {
  @Override
  protected void configure() {
    bind(new TypeLiteral<ImmutableSet<GroupReference>>() {})
        .annotatedWith(AdministrateServerGroups.class)
        .toProvider(AdministrateServerGroupsProvider.class)
        .in(SINGLETON);

    bind(new TypeLiteral<Set<AccountGroup.UUID>>() {})
        .annotatedWith(GitUploadPackGroups.class)
        .toProvider(GitUploadPackGroupsProvider.class)
        .in(SINGLETON);

    bind(new TypeLiteral<Set<AccountGroup.UUID>>() {})
        .annotatedWith(GitReceivePackGroups.class)
        .toProvider(GitReceivePackGroupsProvider.class)
        .in(SINGLETON);

    bind(ChangeControl.Factory.class);
    factory(ProjectControl.AssistedFactory.class);
  }
}
