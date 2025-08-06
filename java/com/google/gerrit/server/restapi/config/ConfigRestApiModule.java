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
import static com.google.gerrit.server.config.ExperimentResource.EXPERIMENT_KIND;
import static com.google.gerrit.server.config.GlobalLabelResource.GLOBAL_LABEL_KIND;
import static com.google.gerrit.server.config.GlobalSubmitRequirementResource.GLOBAL_SUBMIT_REQUIREMENT_KIND;
import static com.google.gerrit.server.config.IndexResource.INDEX_KIND;
import static com.google.gerrit.server.config.IndexVersionResource.INDEX_VERSION_KIND;
import static com.google.gerrit.server.config.TaskResource.TASK_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.config.CapabilityResource;
import com.google.gerrit.server.config.TopMenuResource;

public class ConfigRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), CapabilityResource.CAPABILITY_KIND);
    DynamicMap.mapOf(binder(), CONFIG_KIND);
    DynamicMap.mapOf(binder(), EXPERIMENT_KIND);
    DynamicMap.mapOf(binder(), GLOBAL_LABEL_KIND);
    DynamicMap.mapOf(binder(), GLOBAL_SUBMIT_REQUIREMENT_KIND);
    DynamicMap.mapOf(binder(), TASK_KIND);
    DynamicMap.mapOf(binder(), TopMenuResource.TOP_MENU_KIND);
    DynamicMap.mapOf(binder(), INDEX_KIND);
    DynamicMap.mapOf(binder(), INDEX_VERSION_KIND);

    child(CONFIG_KIND, "capabilities").to(CapabilitiesCollection.class);
    post(CONFIG_KIND, "check.consistency").to(CheckConsistency.class);
    post(CONFIG_KIND, "deactivate.stale.accounts").to(AccountDeactivation.class);
    put(CONFIG_KIND, "email.confirm").to(ConfirmEmail.class);

    child(CONFIG_KIND, "experiments").to(ExperimentsCollection.class);
    get(EXPERIMENT_KIND).to(GetExperiment.class);

    post(CONFIG_KIND, "index.changes").to(IndexChanges.class);

    child(CONFIG_KIND, "indexes").to(IndexCollection.class);
    post(INDEX_KIND, "snapshot").to(SnapshotIndex.class);
    get(INDEX_KIND).to(GetIndex.class);

    get(CONFIG_KIND, "info").to(GetServerInfo.class);

    child(CONFIG_KIND, "labels").to(GlobalLabelsCollection.class);

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

    child(CONFIG_KIND, "tasks").to(TasksCollection.class);
    delete(TASK_KIND).to(DeleteTask.class);
    get(TASK_KIND).to(GetTask.class);

    child(CONFIG_KIND, "top-menus").to(TopMenuCollection.class);
    get(CONFIG_KIND, "version").to(GetVersion.class);

    child(CONFIG_KIND, "submit-requirements").to(GlobalSubmitRequirementsCollection.class);

    child(INDEX_KIND, "versions").to(IndexVersionsCollection.class);
    get(INDEX_VERSION_KIND).to(GetIndexVersion.class);
    post(INDEX_VERSION_KIND, "snapshot").to(SnapshotIndexVersion.class);
    post(INDEX_VERSION_KIND, "reindex").to(ReindexIndexVersion.class);

    post(CONFIG_KIND, "passwords.to.tokens").to(MigratePasswordsToTokens.class);
    // The caches and summary REST endpoints are bound via RestCacheAdminModule.
  }
}
