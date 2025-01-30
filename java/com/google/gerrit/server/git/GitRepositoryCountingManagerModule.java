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

import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.ModuleImpl;
import com.google.inject.Scopes;

/**
 * Module to install {@link MultiBaseLocalDiskRepositoryManager} rather than {@link
 * LocalDiskRepositoryManager} if needed.
 */
@ModuleImpl(name = GitRepositoryManagerModule.MANAGER_MODULE)
public class GitRepositoryCountingManagerModule extends LifecycleModule {

  @Override
  protected void configure() {
    bind(LocalDiskRepositoryManager.class).in(Scopes.SINGLETON);
    bind(LocalDiskRepositoryCountingManager.class).in(Scopes.SINGLETON);
    bind(GitRepositoryManager.class).to(LocalDiskRepositoryCountingManager.class);
  }
}
