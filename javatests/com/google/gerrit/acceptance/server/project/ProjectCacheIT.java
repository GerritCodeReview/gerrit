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

import com.google.common.cache.LoadingCache;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.CachedProjectConfig;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.project.ProjectCacheImpl;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.name.Named;
import java.util.Optional;
import javax.inject.Inject;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.Test;

public class ProjectCacheIT extends AbstractDaemonTest {
  @Inject private PluginConfigFactory pluginConfigFactory;

  @Inject
  @Named(ProjectCacheImpl.CACHE_NAME)
  private LoadingCache<Project.NameKey, Optional<CachedProjectConfig>> inMemoryProjectCache;

  @Test
  public void pluginConfig_cachedValueEqualsConfigValue() throws Exception {
    GroupReference group = GroupReference.create(AccountGroup.uuid("uuid"), "local-group-name");
    try (AbstractDaemonTest.ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updatePluginConfig(
              "important-plugin",
              cfg -> {
                cfg.setGroupReference("group-config-name", group);
                cfg.setString("key", "my-plugin-value");
              });
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
      u.getConfig()
          .updatePluginConfig(
              "important-plugin",
              cfg -> {
                cfg.setGroupReference("group-config-name", group);
                cfg.setString("key", "my-plugin-value");
              });
      u.save();
    }

    PluginConfig pluginConfig =
        pluginConfigFactory.getFromProjectConfigWithInheritance(project, "important-plugin");
    assertThat(pluginConfig.getString("key")).isEqualTo("my-plugin-value");

    assertThat(pluginConfig.getGroupReference("group-config-name")).isPresent();
    assertThat(pluginConfig.getGroupReference("group-config-name")).hasValue(group);
  }

  @Test
  public void pluginConfig_inheritanceCanOverrideValuesAndKeepsRest() throws Exception {
    try (AbstractDaemonTest.ProjectConfigUpdate u = updateProject(allProjects)) {
      u.getConfig()
          .updatePluginConfig(
              "important-plugin2",
              cfg -> {
                cfg.setString("key", "kept");
                cfg.setString("key2", "my-plugin-value2");
              });
      u.save();
    }

    try (AbstractDaemonTest.ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updatePluginConfig(
              "important-plugin2",
              cfg -> {
                cfg.setString("key2", "overridden");
              });
      u.save();
    }

    PluginConfig pluginConfig =
        pluginConfigFactory.getFromProjectConfigWithInheritance(project, "important-plugin2");
    assertThat(pluginConfig.getString("key")).isEqualTo("kept");
    assertThat(pluginConfig.getString("key2")).isEqualTo("overridden");
  }

  @Test
  public void allProjectsProjectsConfig_ChangeInFileInvalidatesPersistedCache() throws Exception {
    assertThat(projectCache.getAllProjects().getConfig().getCheckReceivedObjects()).isTrue();
    // Change etc/All-Projects-project.config
    FileBasedConfig fileBasedConfig =
        new FileBasedConfig(
            sitePaths
                .etc_dir
                .resolve(allProjects.get())
                .resolve(ProjectConfig.PROJECT_CONFIG)
                .toFile(),
            FS.DETECTED);
    fileBasedConfig.setString("receive", null, "checkReceivedObjects", "false");
    fileBasedConfig.save();
    // Invalidate only the in-memory cache
    inMemoryProjectCache.invalidate(allProjects);
    assertThat(projectCache.getAllProjects().getConfig().getCheckReceivedObjects()).isFalse();
  }

  @Test
  public void cachesNegativeLookup() throws Exception {
    long initialNumMisses = inMemoryProjectCache.stats().missCount();
    assertThat(inMemoryProjectCache.get(Project.nameKey("foo"))).isEmpty();
    assertThat(inMemoryProjectCache.stats().missCount()).isEqualTo(initialNumMisses + 1);
    inMemoryProjectCache.get(Project.nameKey("foo")); // Another invocation
    assertThat(inMemoryProjectCache.stats().missCount()).isEqualTo(initialNumMisses + 1);
  }

  @Test
  public void invalidatesNegativeCachingAfterProjectCreation() throws Exception {
    long initialNumMisses = inMemoryProjectCache.stats().missCount();
    assertThat(inMemoryProjectCache.get(Project.nameKey(name("foo")))).isEmpty();
    assertThat(inMemoryProjectCache.stats().missCount())
        .isEqualTo(initialNumMisses + 1); // Negative voting cached
    Project.NameKey newProjectName =
        createProjectOverAPI("foo", allProjects, true, /* submitType= */ null);
    assertThat(inMemoryProjectCache.get(newProjectName)).isPresent(); // Another invocation
    assertThat(inMemoryProjectCache.stats().missCount())
        .isEqualTo(initialNumMisses + 3); // Two eviction happened during the project creation
  }
}
