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
import com.google.gerrit.server.restapi.config.capabilities.ConfigCapabilitiesRestApiModule;
import com.google.gerrit.server.restapi.config.experiments.ConfigExperimentsRestApiModule;
import com.google.gerrit.server.restapi.config.indexes.ConfigIndexesRestApiModule;
import com.google.gerrit.server.restapi.config.tasks.ConfigTasksRestApiModule;
import com.google.gerrit.server.restapi.config.topmenus.ConfigTopMenusRestApiModule;

/** Guice module that binds all REST endpoints for {@code /config/server/}. */
public class ConfigRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), CONFIG_KIND);

    /** Run consistency checks {@code POST /config/server/check.consistency}. */
    post(CONFIG_KIND, "check.consistency").to(CheckConsistency.class);

    /** Abandon old changes {@code POST /config/server/cleanup.changes}. */
    post(CONFIG_KIND, "cleanup.changes").to(CleanupChanges.class);
    /** Delete stale draft comments {@code POST /config/server/cleanup.draft.comments}. */
    post(CONFIG_KIND, "cleanup.draft.comments").to(CleanupDraftComments.class);

    /** Deactivate stale accounts {@code POST /config/server/deactivate.stale.accounts}. */
    post(CONFIG_KIND, "deactivate.stale.accounts").to(AccountDeactivation.class);

    /** Confirm email {@code PUT /config/server/email.confirm}. */
    put(CONFIG_KIND, "email.confirm").to(ConfirmEmail.class);

    /** Index a set of changes {@code POST /config/server/index.changes}. */
    post(CONFIG_KIND, "index.changes").to(IndexChanges.class);

    /** Get server info {@code GET /config/server/index.changes}. */
    get(CONFIG_KIND, "info").to(GetServerInfo.class);

    /** Get default user preferences {@code GET /config/server/preferences}. */
    get(CONFIG_KIND, "preferences").to(GetPreferences.class);
    /** Update default user preferences {@code PUT /config/server/preferences}. */
    put(CONFIG_KIND, "preferences").to(SetPreferences.class);
    /** Get default user diff preferences {@code GET /config/server/preferences.diff}. */
    get(CONFIG_KIND, "preferences.diff").to(GetDiffPreferences.class);
    /** Update default user diff preferences {@code PUT /config/server/preferences.diff}. */
    put(CONFIG_KIND, "preferences.diff").to(SetDiffPreferences.class);
    /** Get default user edit preferences {@code GET /config/server/preferences.edit}. */
    get(CONFIG_KIND, "preferences.edit").to(GetEditPreferences.class);
    /** Update default user edit preferences {@code PUT /config/server/preferences.edit}. */
    put(CONFIG_KIND, "preferences.edit").to(SetEditPreferences.class);

    /** Reload config {@code POST /config/server/reload}. */
    post(CONFIG_KIND, "reload").to(ReloadConfig.class);

    /** Create a snapshot of indexes {@code POST /config/server/snapshot.indexes}. */
    post(CONFIG_KIND, "snapshot.indexes").to(SnapshotIndexes.class);

    /** Get server version {@code GET /config/server/version}. */
    get(CONFIG_KIND, "version").to(GetVersion.class);

    // The caches and summary REST endpoints are bound via RestCacheAdminModule.
    /** Module for {@code /config/server/capabilities}. */
    install(new ConfigCapabilitiesRestApiModule());
    /** Module for {@code /config/server/experiments}. */
    install(new ConfigExperimentsRestApiModule());
    /** Module for {@code /config/server/indexes}. */
    install(new ConfigIndexesRestApiModule());
    /** Module for {@code /config/server/tasks}. */
    install(new ConfigTasksRestApiModule());
    /** Module for {@code /config/server/top-menus}. */
    install(new ConfigTopMenusRestApiModule());
  }
}
