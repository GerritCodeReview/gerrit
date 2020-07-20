// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import javax.inject.Inject;
import org.junit.Test;

public class ProjectCacheIT extends AbstractDaemonTest {
  @Inject private PluginConfigFactory pluginConfigFactory;

  @Test
  public void pluginConfig_cachedValueEqualsConfigValue() throws Exception {
    GroupReference group = GroupReference.create(AccountGroup.uuid("uuid"), "local-group-name");
    try (AbstractDaemonTest.ProjectConfigUpdate u = updateProject(project)) {
      PluginConfig.Update cfg = u.getConfig().getPluginConfig("important-plugin");
      cfg.setGroupReference("group-config-name", group);
      cfg.setString("key", "my-plugin-value");
      u.save();
    }

    PluginConfig pluginConfig = projectCache.get(project).get().getPluginConfig("important-plugin");
    assertThat(pluginConfig.getString("key")).isEqualTo("my-plugin-value");

    assertThat(pluginConfig.getGroupReference("group-config-name")).isPresent();
    assertThat(pluginConfig.getGroupReference("group-config-name")).hasValue(group);
  }

  @Test
  public void pluginConfig_inheritedCachedValueEqualsConfigValue() throws Exception {
    GroupReference group = GroupReference.create(AccountGroup.uuid("uuid"), "local-group-name");
    try (AbstractDaemonTest.ProjectConfigUpdate u = updateProject(allProjects)) {
      PluginConfig.Update cfg = u.getConfig().getPluginConfig("important-plugin");
      cfg.setGroupReference("group-config-name", group);
      cfg.setString("key", "my-plugin-value");
      u.save();
    }

    PluginConfig pluginConfig =
        pluginConfigFactory.getFromProjectConfigWithInheritance(project, "important-plugin");
    assertThat(pluginConfig.getString("key")).isEqualTo("my-plugin-value");

    assertThat(pluginConfig.getGroupReference("group-config-name")).isPresent();
    assertThat(pluginConfig.getGroupReference("group-config-name")).hasValue(group);
  }
}
