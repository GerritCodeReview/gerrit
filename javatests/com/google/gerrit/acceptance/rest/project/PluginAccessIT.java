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
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.api.access.AccessSectionInfo;
import com.google.gerrit.extensions.api.access.PermissionInfo;
import com.google.gerrit.extensions.api.access.PermissionRuleInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInput;
import com.google.gerrit.extensions.config.CapabilityDefinition;
import com.google.gerrit.extensions.config.PluginProjectPermissionDefinition;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.permissions.PluginPermissionsUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import java.util.Set;
import org.junit.Test;

public final class PluginAccessIT extends AbstractDaemonTest {
  private static final String TEST_PLUGIN_NAME = "gerrit";
  private static final String TEST_PLUGIN_CAPABILITY = "aPluginCapability";
  private static final String TEST_PLUGIN_PROJECT_PERMISSION = "aPluginProjectPermission";

  @Inject PluginPermissionsUtil pluginPermissionsUtil;

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
  public void setAccessAddPluginCapabilitySucceed() throws Exception {
    String pluginCapability = TEST_PLUGIN_NAME + "-" + TEST_PLUGIN_CAPABILITY;
    ProjectAccessInput accessInput =
        createAccessInput(AccessSection.GLOBAL_CAPABILITIES, pluginCapability);

    ProjectAccessInfo projectAccessInfo =
        gApi.projects().name(allProjects.get()).access(accessInput);

    Set<String> capabilities =
        projectAccessInfo.local.get(AccessSection.GLOBAL_CAPABILITIES).permissions.keySet();
    assertThat(capabilities).contains(pluginCapability);
    // Verifies the plugin defined capability could be listed.
    assertThat(pluginPermissionsUtil.collectPluginCapabilities()).containsKey(pluginCapability);
  }

  @Test
  public void setAccessAddPluginProjectPermissionSucceed() throws Exception {
    String pluginProjectPermission =
        "plugin-" + TEST_PLUGIN_NAME + "-" + TEST_PLUGIN_PROJECT_PERMISSION;
    String accessSection = "refs/heads/plugin-permission";
    ProjectAccessInput accessInput = createAccessInput(accessSection, pluginProjectPermission);

    ProjectAccessInfo projectAccessInfo =
        gApi.projects().name(allProjects.get()).access(accessInput);

    Set<String> permissions = projectAccessInfo.local.get(accessSection).permissions.keySet();
    assertThat(permissions).contains(pluginProjectPermission);
    // Verifies the plugin defined capability could be listed.
    assertThat(pluginPermissionsUtil.collectPluginProjectPermissions())
        .containsKey(pluginProjectPermission);
  }

  private static ProjectAccessInput createAccessInput(String accessSection, String permissionName) {
    ProjectAccessInput accessInput = new ProjectAccessInput();
    PermissionRuleInfo ruleInfo = new PermissionRuleInfo(PermissionRuleInfo.Action.ALLOW, false);
    PermissionInfo email = new PermissionInfo(null, null);
    email.rules = ImmutableMap.of(SystemGroupBackend.REGISTERED_USERS.get(), ruleInfo);
    AccessSectionInfo accessSectionInfo = new AccessSectionInfo();
    accessSectionInfo.permissions = ImmutableMap.of(permissionName, email);
    accessInput.add = ImmutableMap.of(accessSection, accessSectionInfo);

    return accessInput;
  }
}
