// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.git.VersionedMetaData;
import com.google.gerrit.server.project.ProjectCache;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.lib.Repository;

public class ProjectConfigResetter implements AutoCloseable {
  private final GitRepositoryManager repoManager;
  private final MetaDataUpdate.Server metaDataUpdateFactory;
  private final ProjectCache projectCache;
  private final Map<Project.NameKey, ProjectConfig> configs;
  private final Field revisionField;

  public ProjectConfigResetter(
      GitRepositoryManager repoManager,
      MetaDataUpdate.Server metaDataUpdateFactory,
      ProjectCache projectCache,
      Project.NameKey... projects)
      throws Exception {
    this.repoManager = repoManager;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.projectCache = projectCache;
    this.configs = getConfigs(projects);
    this.revisionField = VersionedMetaData.class.getDeclaredField("revision");
    revisionField.setAccessible(true);
  }

  private Map<Project.NameKey, ProjectConfig> getConfigs(Project.NameKey... projects)
      throws Exception {
    Map<Project.NameKey, ProjectConfig> configs = new HashMap<>();
    for (Project.NameKey project : projects) {
      try (Repository repo = repoManager.openRepository(project)) {
        ProjectConfig config = new ProjectConfig(project);
        config.load(repo);
        configs.put(project, config);
      }
    }
    return configs;
  }

  @Override
  public void close() throws Exception {
    for (Map.Entry<Project.NameKey, ProjectConfig> e : configs.entrySet()) {
      try (MetaDataUpdate md = metaDataUpdateFactory.create(e.getKey())) {
        md.setMessage("Reset config");
        ProjectConfig config = e.getValue();
        revisionField.set(config, revisionField.get(ProjectConfig.read(md)));
        config.commit(md);
        projectCache.evict(config.getProject());
      }
    }
  }
}
