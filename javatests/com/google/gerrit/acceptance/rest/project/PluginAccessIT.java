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
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import org.junit.Test;

public class PluginAccessIT extends AbstractDaemonTest {

  private static final String CORE_PLUGIN_PREFIX = "gerrit-";
  private static final String PLUGIN_CAPABILITY = "printHello";

  @Override
  public Module createModule() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        bind(CapabilityDefinition.class)
            .annotatedWith(Exports.named(PLUGIN_CAPABILITY))
            .toInstance(
                new CapabilityDefinition() {
                  @Override
                  public String getDescription() {
                    return "Print Hello";
                  }
                });
      }
    };
  }

  @Test
  public void addPluginCapability() throws Exception {
    ProjectAccessInput accessInput = new ProjectAccessInput();
    AccessSectionInfo accessSectionInfo = new AccessSectionInfo();
    PermissionInfo email = new PermissionInfo(null, null, null);
    PermissionRuleInfo pri = new PermissionRuleInfo(PermissionRuleInfo.Action.ALLOW, false);

    email.rules = ImmutableMap.of(SystemGroupBackend.REGISTERED_USERS.get(), pri);
    accessSectionInfo.permissions = ImmutableMap.of(CORE_PLUGIN_PREFIX + PLUGIN_CAPABILITY, email);
    accessInput.add = ImmutableMap.of(AccessSection.GLOBAL_CAPABILITIES, accessSectionInfo);

    ProjectAccessInfo updatedAccessSectionInfo =
        gApi.projects().name(allProjects.get()).access(accessInput);
    assertThat(
            updatedAccessSectionInfo
                .local
                .get(AccessSection.GLOBAL_CAPABILITIES)
                .permissions
                .keySet())
        .containsAllIn(accessSectionInfo.permissions.keySet());
  }
}
