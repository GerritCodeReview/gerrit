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
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import java.io.IOException;

public class ReindexProjectsAtStartup implements LifecycleListener {
  private final ProjectIndexer projectIndexer;
  private final AllProjectsName allProjectsName;
  private final AllUsersName allUsersName;

  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      bind(LifecycleListener.class).to(ReindexProjectsAtStartup.class).in(Scopes.SINGLETON);
    }
  }

  @Inject
  public ReindexProjectsAtStartup(
      ProjectIndexer projectIndexer, AllProjectsName allProjectsName, AllUsersName allUsersName) {
    this.projectIndexer = projectIndexer;
    this.allProjectsName = allProjectsName;
    this.allUsersName = allUsersName;
  }

  @Override
  public void start() {
    try {
      projectIndexer.index(allProjectsName);
      projectIndexer.index(allUsersName);
    } catch (IOException e) {
      throw new IllegalStateException(
          String.format(
              "Unable to index %s and %s repos, tests may fail", allProjectsName, allUsersName),
          e);
    }
  }

  @Override
  public void stop() {}
}
