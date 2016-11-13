//Copyright (C) 2015 The Android Open Source Project
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

package com.google.gerrit.server.git;

import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.RepositoryConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import java.nio.file.Path;
import org.eclipse.jgit.lib.Config;

public class MultiBaseLocalDiskRepositoryManager extends LocalDiskRepositoryManager {

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      bind(GitRepositoryManager.class).to(MultiBaseLocalDiskRepositoryManager.class);
      bind(LocalDiskRepositoryManager.class).to(MultiBaseLocalDiskRepositoryManager.class);
      listener().to(MultiBaseLocalDiskRepositoryManager.class);
      listener().to(MultiBaseLocalDiskRepositoryManager.Lifecycle.class);
    }
  }

  private final RepositoryConfig config;

  @Inject
  MultiBaseLocalDiskRepositoryManager(
      SitePaths site, @GerritServerConfig Config cfg, RepositoryConfig config) {
    super(site, cfg);
    this.config = config;

    for (Path alternateBasePath : config.getAllBasePaths()) {
      checkState(
          alternateBasePath.isAbsolute(),
          "repository.<name>.basePath must be absolute: %s",
          alternateBasePath);
    }
  }

  @Override
  public Path getBasePath(NameKey name) {
    Path alternateBasePath = config.getBasePath(name);
    return alternateBasePath != null ? alternateBasePath : super.getBasePath(name);
  }

  @Override
  protected void scanProjects(ProjectVisitor visitor) {
    super.scanProjects(visitor);
    for (Path path : config.getAllBasePaths()) {
      visitor.setStartFolder(path);
      super.scanProjects(visitor);
    }
  }
}
