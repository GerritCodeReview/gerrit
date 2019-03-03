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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.api.access.AccessSectionInfo;
import com.google.gerrit.extensions.api.access.PermissionInfo;
import com.google.gerrit.extensions.api.access.PermissionRuleInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInput;
import com.google.gerrit.extensions.config.CapabilityDefinition;
import com.google.gerrit.extensions.config.PluginProjectPermissionDefinition;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import org.junit.Test;

public class PluginAccessIT extends AbstractDaemonTest {
  private static final String TEST_PLUGIN_NAME = "gerrit";
  private static final String TEST_PLUGIN_CAPABILITY = "aPluginCapability";
  private static final String TEST_PLUGIN_PROJECT_PERMISSION = "aPluginProjectPermission";

  @Override
  public Module createModule() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        bind(CapabilityDefinition.class)
            .annotatedWith(Exports.named(TEST_PLUGIN_CAPABILITY))
            .toInstance(
                new CapabilityDefinition() {
                  @Override
                  public String getDescription() {
                    return "A Plugin Capability";
                  }
                });
        bind(PluginProjectPermissionDefinition.class)
            .annotatedWith(Exports.named(TEST_PLUGIN_PROJECT_PERMISSION))
            .toInstance(
                new PluginProjectPermissionDefinition() {
                  @Override
                  public String getDescription() {
                    return "A Plugin Project Permission";
                  }
                });
      }
    };
  }

  @Test
  public void addPluginCapability() throws Exception {
    addPluginPermission(AccessSection.GLOBAL_CAPABILITIES, TEST_PLUGIN_CAPABILITY);
  }

  @Test
  public void addPluginProjectPermission() throws Exception {
    addPluginPermission("refs/heads/plugin-permission", TEST_PLUGIN_PROJECT_PERMISSION);
  }

  private void addPluginPermission(String accessSection, String permission) throws Exception {
    ProjectAccessInput accessInput = new ProjectAccessInput();
    PermissionRuleInfo ruleInfo = new PermissionRuleInfo(PermissionRuleInfo.Action.ALLOW, false);
    PermissionInfo email = new PermissionInfo(null, null);
    email.rules = ImmutableMap.of(SystemGroupBackend.REGISTERED_USERS.get(), ruleInfo);
    String permissionConfigName = TEST_PLUGIN_NAME + "-" + permission;
    if (!accessSection.equals(AccessSection.GLOBAL_CAPABILITIES)) {
      permissionConfigName = "plugin-" + permissionConfigName;
    }
    AccessSectionInfo accessSectionInfo = new AccessSectionInfo();
    accessSectionInfo.permissions = ImmutableMap.of(permissionConfigName, email);
    accessInput.add = ImmutableMap.of(accessSection, accessSectionInfo);

    ProjectAccessInfo updatedAccessSectionInfo =
        gApi.projects().name(allProjects.get()).access(accessInput);

    assertThat(updatedAccessSectionInfo.local.get(accessSection).permissions.keySet())
        .containsAllIn(accessSectionInfo.permissions.keySet());
  }
}
