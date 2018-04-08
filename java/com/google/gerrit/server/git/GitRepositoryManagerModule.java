// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.gerrit.config.RepositoryConfig;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.Inject;

public class GitRepositoryManagerModule extends LifecycleModule {

  private final RepositoryConfig repoConfig;

  @Inject
  public GitRepositoryManagerModule(RepositoryConfig repoConfig) {
    this.repoConfig = repoConfig;
  }

  @Override
  protected void configure() {
    if (repoConfig.getAllBasePaths().isEmpty()) {
      install(new LocalDiskRepositoryManager.Module());
    } else {
      install(new MultiBaseLocalDiskRepositoryManager.Module());
    }
  }
}
