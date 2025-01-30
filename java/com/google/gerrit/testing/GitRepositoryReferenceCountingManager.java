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
import com.google.gerrit.server.git.DelegateRepository;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.RepositoryExistsException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;

public class GitRepositoryReferenceCountingManager implements GitRepositoryManager {
  private final GitRepositoryManager delegate;
  private final ConcurrentHashMap<Project.NameKey, RepositoryTracking> trackedRepositories;

  private static class RepositoryTracking extends DelegateRepository {
    private final AtomicInteger referenceCounter = new AtomicInteger(0);
    private final List<StackTraceElement> incrementCallers;
    private final List<StackTraceElement> decrementCallers;
    private final Repository repository;
    private final Project.NameKey name;

    private RepositoryTracking(Project.NameKey name, Repository repository) {
      super(repository);
      this.name = name;
      this.repository = repository;
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
          + name
          + " has useCount="
          + referenceCounter.get()
          + (incrementCallers.isEmpty() ? "" : "\n  opened by " + formatCallers(incrementCallers))
          + (decrementCallers.isEmpty() ? "" : "\n  closed by " + formatCallers(decrementCallers))
          + "\n";
    }

    String formatCallers(List<StackTraceElement> callers) {
      return String.join(
          "\n            ", callers.stream().map(StackTraceElement::toString).toList());
    }

    @Override
    public int hashCode() {
      return repository.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) return true;
      if (!(o instanceof RepositoryTracking)) return false;
      RepositoryTracking other = (RepositoryTracking) o;
      return repository.equals(other.repository);
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
        incrementCallers.clear();
        decrementCallers.clear();
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

    int getReferenceCounterValue() {
      return referenceCounter.get();
    }
  }

  public GitRepositoryReferenceCountingManager(GitRepositoryManager delegate) {
    this.delegate = delegate;
    trackedRepositories = new ConcurrentHashMap<>();
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
    return trackedRepositories.entrySet().stream()
        .filter(entry -> !entry.getKey().get().contains("All-Users"))
        .filter(entry -> !entry.getKey().get().contains("All-Projects"))
        .map(Map.Entry::getValue)
        .filter(repositoryTracking -> repositoryTracking.getReferenceCounterValue() > 0)
        .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public Status getRepositoryStatus(Project.NameKey name) {
    return delegate.getRepositoryStatus(name);
  }

  private RepositoryTracking trackRepository(Project.NameKey name, Repository repository) {
    RepositoryTracking trackedRepository =
        trackedRepositories.computeIfAbsent(
            name, nameKey -> new RepositoryTracking(nameKey, repository));
    trackedRepository.incrementReferenceCounting();
    return trackedRepository;
  }
}
