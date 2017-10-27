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
import static com.google.gerrit.server.config.TaskResource.TASK_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.config.CapabilityResource;
import com.google.gerrit.server.config.TopMenuResource;

public class ConfigRestModule extends RestApiModule {
  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), CapabilityResource.CAPABILITY_KIND);
    DynamicMap.mapOf(binder(), CONFIG_KIND);
    DynamicMap.mapOf(binder(), TASK_KIND);
    DynamicMap.mapOf(binder(), TopMenuResource.TOP_MENU_KIND);
    child(CONFIG_KIND, "capabilities").to(CapabilitiesCollection.class);
    child(CONFIG_KIND, "tasks").to(TasksCollection.class);
    get(TASK_KIND).to(GetTask.class);
    delete(TASK_KIND).to(DeleteTask.class);
    child(CONFIG_KIND, "top-menus").to(TopMenuCollection.class);
    get(CONFIG_KIND, "version").to(GetVersion.class);
    get(CONFIG_KIND, "info").to(GetServerInfo.class);
    post(CONFIG_KIND, "check.consistency").to(CheckConsistency.class);
    get(CONFIG_KIND, "preferences").to(GetPreferences.class);
    put(CONFIG_KIND, "preferences").to(SetPreferences.class);
    get(CONFIG_KIND, "preferences.diff").to(GetDiffPreferences.class);
    put(CONFIG_KIND, "preferences.diff").to(SetDiffPreferences.class);
    get(CONFIG_KIND, "preferences.edit").to(GetEditPreferences.class);
    put(CONFIG_KIND, "preferences.edit").to(SetEditPreferences.class);

    configureDeprecated();
  }

  @SuppressWarnings("deprecation")
  private void configureDeprecated() {
    put(CONFIG_KIND, "email.confirm").to(ConfirmEmail.class);
  }
}
