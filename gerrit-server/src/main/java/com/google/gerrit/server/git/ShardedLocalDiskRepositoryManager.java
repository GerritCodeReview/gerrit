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

import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.RepositoryConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;

import java.io.File;
import java.nio.file.Path;

public class ShardedLocalDiskRepositoryManager extends
    LocalDiskRepositoryManager {

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      bind(GitRepositoryManager.class).to(
          ShardedLocalDiskRepositoryManager.class);
      bind(LocalDiskRepositoryManager.class).to(
          ShardedLocalDiskRepositoryManager.class);
      listener().to(ShardedLocalDiskRepositoryManager.class);
      listener().to(ShardedLocalDiskRepositoryManager.Lifecycle.class);
    }
  }

  private final RepositoryConfig config;

  @Inject
  ShardedLocalDiskRepositoryManager(SitePaths site,
      @GerritServerConfig Config cfg,
      NotesMigration notesMigration,
      RepositoryConfig config) {
    super(site, cfg, notesMigration);
    this.config = config;

    for (String alternateBasePath : config.getAllBasePaths()) {
      if (!new File(alternateBasePath).isAbsolute()) {
        throw new IllegalStateException("repository.<name>.basePath must be "
            + "absolute path, change the following path: " + alternateBasePath);
      }
    }
  }

  @Override
  public Path getBasePath(NameKey name) {
    String alternateBasePath = config.getBasePath(name);
    return alternateBasePath != null
        ? new File(alternateBasePath).toPath()
        : super.getBasePath(name);
  }

  @Override
  protected void scanProjects(ProjectVisitor visitor) {
    super.scanProjects(visitor);
    for (String path : config.getAllBasePaths()) {
      visitor.setStartFolder(new File(path).toPath());
      super.scanProjects(visitor);
    }
  }
}
