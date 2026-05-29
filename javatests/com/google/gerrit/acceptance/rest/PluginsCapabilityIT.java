// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest;

import static com.google.gerrit.acceptance.rest.TestPluginModule.PLUGIN_CAPABILITY;
import static com.google.gerrit.acceptance.rest.TestPluginModule.PLUGIN_COLLECTION;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.rest.CreateTestPlugin.Input;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.extensions.api.access.AccessSectionInfo;
import com.google.gerrit.extensions.api.access.PermissionInfo;
import com.google.gerrit.extensions.api.access.PermissionRuleInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInput;
import com.google.gerrit.server.group.SystemGroupBackend;
import org.junit.Before;
import org.junit.Test;

@TestPlugin(
    name = PluginsCapabilityIT.PLUGIN_NAME,
    sysModule = "com.google.gerrit.acceptance.rest.TestPluginModule")
public class PluginsCapabilityIT extends LightweightPluginDaemonTest {

  public static final String PLUGIN_NAME = "test";

  public String restEndpoint;

  @Override
  @Before
  public void setUpTestPlugin() throws Exception {
    super.setUpTestPlugin();
    this.setUpPluginPermission();

    this.restEndpoint = "/config/server/" + PLUGIN_NAME + "~" + PLUGIN_COLLECTION;
  }

  @Test
  public void testGet() throws Exception {
    adminRestSession.get(this.restEndpoint).assertOK();
    userRestSession.get(this.restEndpoint).assertOK();
  }

  @Test
  public void testCreate() throws Exception {
    Input input = new Input();
    input.input = "test";

    adminRestSession.post(this.restEndpoint + "/notexisting", input).assertCreated();
    userRestSession.post(this.restEndpoint + "/notexisting", input).assertCreated();
  }

  private void setUpPluginPermission() throws Exception {
    ProjectAccessInput accessInput = new ProjectAccessInput();
    AccessSectionInfo accessSectionInfo = new AccessSectionInfo();
    PermissionInfo email = new PermissionInfo(null, null);
    PermissionRuleInfo pri = new PermissionRuleInfo(PermissionRuleInfo.Action.ALLOW, false);
    email.rules = ImmutableMap.of(SystemGroupBackend.REGISTERED_USERS.get(), pri);
    accessSectionInfo.permissions = ImmutableMap.of(PLUGIN_NAME + "-" + PLUGIN_CAPABILITY, email);
    accessInput.add = ImmutableMap.of(AccessSection.GLOBAL_CAPABILITIES, accessSectionInfo);
    gApi.projects().name(allProjects.get()).access(accessInput);
  }
}
