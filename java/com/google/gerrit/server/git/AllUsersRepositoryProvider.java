// Copyright (C) 2026 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.AllUsersName;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class AllUsersRepositoryProvider implements Provider<Repository>, LifecycleListener {
  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      bind(Repository.class)
          .annotatedWith(AllUsersRepository.class)
          .toProvider(AllUsersRepositoryProvider.class);
      listener().to(AllUsersRepositoryProvider.class);
    }
  }

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  @Nullable private volatile Repository repository;
  private volatile boolean stopped;

  @Inject
  AllUsersRepositoryProvider(GitRepositoryManager repoManager, AllUsersName allUsersName) {
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
  }

  @Override
  public Repository get() {
    checkState(!stopped, "All-Users repository provider has stopped");
    Repository result = repository;
    return result != null ? result : open();
  }

  private synchronized Repository open() {
    checkState(!stopped, "All-Users repository provider has stopped");
    if (repository == null) {
      try {
        repository = repoManager.openRepository(allUsersName);
      } catch (IOException e) {
        throw new StorageException(e);
      }
    }
    return repository;
  }

  @Override
  public void start() {}

  @Override
  public synchronized void stop() {
    stopped = true;
    if (repository != null) {
      repository.close();
    }
  }
}
