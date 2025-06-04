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

package com.google.gerrit.server.restapi.config;

import static com.google.gerrit.server.config.ConfigResource.CONFIG_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.config.TopMenuResource;
import com.google.gerrit.server.restapi.config.capabilities.ConfigCapabilitiesRestApiModule;
import com.google.gerrit.server.restapi.config.experiments.ConfigExperimentsRestApiModule;
import com.google.gerrit.server.restapi.config.indexes.ConfigIndexesRestApiModule;
import com.google.gerrit.server.restapi.config.tasks.ConfigTasksRestApiModule;

public class ConfigRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), CONFIG_KIND);
    DynamicMap.mapOf(binder(), TopMenuResource.TOP_MENU_KIND);

    post(CONFIG_KIND, "check.consistency").to(CheckConsistency.class);
    post(CONFIG_KIND, "deactivate.stale.accounts").to(AccountDeactivation.class);
    put(CONFIG_KIND, "email.confirm").to(ConfirmEmail.class);

    post(CONFIG_KIND, "index.changes").to(IndexChanges.class);
    get(CONFIG_KIND, "info").to(GetServerInfo.class);
    get(CONFIG_KIND, "preferences").to(GetPreferences.class);
    put(CONFIG_KIND, "preferences").to(SetPreferences.class);
    get(CONFIG_KIND, "preferences.diff").to(GetDiffPreferences.class);
    put(CONFIG_KIND, "preferences.diff").to(SetDiffPreferences.class);
    get(CONFIG_KIND, "preferences.edit").to(GetEditPreferences.class);
    put(CONFIG_KIND, "preferences.edit").to(SetEditPreferences.class);
    post(CONFIG_KIND, "reload").to(ReloadConfig.class);
    post(CONFIG_KIND, "snapshot.indexes").to(SnapshotIndexes.class);
    post(CONFIG_KIND, "cleanup.changes").to(CleanupChanges.class);
    post(CONFIG_KIND, "cleanup.draft.comments").to(CleanupDraftComments.class);

    child(CONFIG_KIND, "top-menus").to(TopMenuCollection.class);
    get(CONFIG_KIND, "version").to(GetVersion.class);

    // The caches and summary REST endpoints are bound via RestCacheAdminModule.
    install(new ConfigCapabilitiesRestApiModule());
    install(new ConfigExperimentsRestApiModule());
    install(new ConfigIndexesRestApiModule());
    install(new ConfigTasksRestApiModule());
  }
}
