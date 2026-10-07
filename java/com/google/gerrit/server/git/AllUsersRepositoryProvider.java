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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.AllUsersName;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
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
  private final AtomicReference<SharedRepository> repository = new AtomicReference<>();
  private volatile boolean stopped;

  @Inject
  AllUsersRepositoryProvider(GitRepositoryManager repoManager, AllUsersName allUsersName) {
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
  }

  @Override
  public Repository get() {
    checkState(!stopped, "All-Users repository provider has stopped");
    SharedRepository repo = repository.get();
    if (repo != null) {
      return repo;
    }
    try {
      SharedRepository opened = new SharedRepository(repoManager.openRepository(allUsersName));
      repo = repository.compareAndExchange(/*expectedValue*/ null, opened);
      if (repo == null) {
        repo = opened;
      } else {
        opened.delegate().close();
      }
      return repo;
    } catch (IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void start() {}

  @Override
  public void stop() {
    stopped = true;
    SharedRepository repo = repository.getAndSet(null);
    if (repo != null) {
      repo.delegate().close();
    }
  }

  /** Only the provider manages the lifetime of the underlying repository. */
  private static class SharedRepository extends DelegateRepository {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    SharedRepository(Repository repository) {
      super(repository);
    }

    @Override
    public void incrementOpen() {}

    @Override
    public void close() {
      logger.atWarning()
          .withCause(new IllegalStateException("Unexpected close of shared All-Users repository"))
          .log("All-Users repository must only be closed by its provider");
    }
  }
}
