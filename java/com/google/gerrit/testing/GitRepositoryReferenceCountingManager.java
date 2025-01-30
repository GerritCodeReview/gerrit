// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.testing;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.DelegateRepository;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.RepositoryExistsException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;

public class GitRepositoryReferenceCountingManager implements GitRepositoryManager {
  private final GitRepositoryManager delegate;
  private final Set<RepositoryTracking> openRepositories;
  private final AllUsersName allUsersName;
  private final AllProjectsName allProjectsName;

  private class RepositoryTracking extends DelegateRepository {
    private final AtomicInteger referenceCounter = new AtomicInteger(1);
    private final StackTraceElement openCaller;
    private final List<StackTraceElement> incrementCallers;
    private final List<StackTraceElement> decrementCallers;
    private final String repoName;

    private RepositoryTracking(String repoName, Repository repository) {
      super(repository);
      this.repoName = repoName;
      openCaller = getCaller();
      incrementCallers = new ArrayList<>();
      decrementCallers = new ArrayList<>();
    }

    @Nullable
    private static StackTraceElement getCaller() {
      StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
      return Arrays.stream(stackTrace)
          .filter(
              stackTraceElement ->
                  !stackTraceElement
                      .getClassName()
                      .contains("GitRepositoryReferenceCountingManager"))
          .filter(stackTraceElement -> !stackTraceElement.getClassName().contains("java.lang"))
          .findFirst()
          .orElse(null);
    }

    @Override
    public String toString() {
      return "Project "
          + repoName
          + " has useCount="
          + referenceCounter.get()
          + "\n  opened from "
          + openCaller
          + (incrementCallers.isEmpty()
              ? ""
              : "\n  incrementOpen from " + formatCallers(incrementCallers))
          + (decrementCallers.isEmpty() ? "" : "\n  closed from " + formatCallers(decrementCallers))
          + "\n";
    }

    String formatCallers(List<StackTraceElement> callers) {
      return String.join(
          "\n            ", callers.stream().map(StackTraceElement::toString).toList());
    }

    @Override
    public void incrementOpen() {
      super.incrementOpen();
      incrementReferenceCounting();
    }

    @Override
    public synchronized void close() {
      StackTraceElement decrementCaller = getCaller();
      super.close();
      decrementCallers.add(decrementCaller);
      int counter = referenceCounter.decrementAndGet();

      if (counter == 0) {
        openRepositories.remove(this);
      } else {
        Optional<StackTraceElement> callerFound =
            incrementCallers.stream()
                .filter(
                    stackTraceElement ->
                        stackTraceElement.getClassName().equals(decrementCaller.getClassName()))
                .findFirst();

        callerFound.ifPresent(
            (incrementCaller) -> {
              incrementCallers.remove(incrementCaller);
              decrementCallers.remove(decrementCaller);
            });
      }
    }

    synchronized void incrementReferenceCounting() {
      incrementCallers.add(getCaller());
      int unused = referenceCounter.incrementAndGet();
    }
  }

  public GitRepositoryReferenceCountingManager(
      GitRepositoryManager delegate, AllUsersName allUsersName, AllProjectsName allProjectsName) {
    this.delegate = delegate;
    this.allUsersName = allUsersName;
    this.allProjectsName = allProjectsName;
    openRepositories = new HashSet<>();
  }

  public void clear() {
    openRepositories.clear();
  }

  @Override
  public Repository openRepository(Project.NameKey name)
      throws RepositoryNotFoundException, IOException {
    return trackRepository(name, delegate.openRepository(name));
  }

  @Override
  public Repository createRepository(Project.NameKey name)
      throws RepositoryNotFoundException, RepositoryExistsException, IOException {
    return trackRepository(name, delegate.createRepository(name));
  }

  @Override
  public NavigableSet<Project.NameKey> list() {
    return delegate.list();
  }

  @Override
  public Boolean canPerformGC() {
    return delegate.canPerformGC();
  }

  public Set<RepositoryTracking> openRepositories() {
    return openRepositories;
  }

  @Override
  public Status getRepositoryStatus(Project.NameKey name) {
    return delegate.getRepositoryStatus(name);
  }

  private Repository trackRepository(Project.NameKey name, Repository repository) {
    if (name.equals(allUsersName) || name.equals(allProjectsName)) {
      return repository;
    }

    RepositoryTracking trackedRepository = new RepositoryTracking(name.get(), repository);
    openRepositories.add(trackedRepository);
    return trackedRepository;
  }
}
