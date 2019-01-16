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

package com.google.gerrit.acceptance;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.index.project.ProjectIndexer;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Scopes;

/** Reindex all projects at Gerrit daemon startup. */
public class ReindexProjectsAtStartup implements LifecycleListener {
  private final ProjectIndexer projectIndexer;
  private final GitRepositoryManager repoMgr;

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      listener().to(ReindexProjectsAtStartup.class).in(Scopes.SINGLETON);
    }
  }

  @Inject
  public ReindexProjectsAtStartup(ProjectIndexer projectIndexer, GitRepositoryManager repoMgr) {
    this.projectIndexer = projectIndexer;
    this.repoMgr = repoMgr;
  }

  @Override
  public void start() {
    repoMgr.list().stream().forEach(projectIndexer::index);
  }

  @Override
  public void stop() {}
}
