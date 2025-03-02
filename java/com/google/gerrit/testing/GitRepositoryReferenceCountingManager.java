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

import static org.junit.Assert.fail;

import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.google.common.base.MoreObjects;
import com.google.common.base.Predicates;
import com.google.common.collect.Sets;
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
import java.util.Collections;
import java.util.List;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.junit.runner.Description;

public class GitRepositoryReferenceCountingManager implements GitRepositoryManager {
  private static final int TIMEOUT_WAITING_FOR_CLOSED_REPOSITORIES_SEC = 30;
  private final GitRepositoryManager delegate;
  private Set<RepositoryTracking> openRepositories;
  private final AllUsersName allUsersName;
  private final AllProjectsName allProjectsName;

  private static class RepositoryTracking extends DelegateRepository {
    private final AtomicInteger referenceCounter = new AtomicInteger(1);
    private final List<StackTraceElement> openCallerStack;
    private List<List<StackTraceElement>> incrementCallersStacks;
    private List<List<StackTraceElement>> decrementCallersStacks;
    private final String repoName;

    private RepositoryTracking(String repoName, Repository repository) {
      super(repository);
      this.repoName = repoName;
      openCallerStack = getCallers();
      incrementCallersStacks = new ArrayList<>();
      decrementCallersStacks = new ArrayList<>();
    }

    @Nullable
    private static List<StackTraceElement> getCallers() {
      StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
      return Arrays.stream(stackTrace)
          .filter(
              stackTraceElement ->
                  !stackTraceElement
                      .getClassName()
                      .startsWith(GitRepositoryReferenceCountingManager.class.getName()))
          .filter(stackTraceElement -> !stackTraceElement.getClassName().contains("java.lang"))
          .filter(stackTraceElement -> !stackTraceElement.getClassName().contains("jdk.internal"))
          .filter(stackTraceElement -> !stackTraceElement.getClassName().contains("org.junit"))
          .limit(5)
          .toList();
    }

    @Override
    public String toString() {
      return "JGit Repository object "
          + repoName
          + "\nwas opened "
          + (incrementCallersStacks.size() + 1)
          + " time(s) but closed only "
          + decrementCallersStacks.size()
          + " time(s), leaving a reference counting of "
          + referenceCounter.get()
          + " instance leaked\n"
          + "------------\n"
          + "  opened from "
          + formatCallStack(openCallerStack)
          + (incrementCallersStacks.isEmpty()
              ? ""
              : "\n  incrementOpen from " + formatCallers(incrementCallersStacks))
          + (decrementCallersStacks.isEmpty()
              ? ""
              : "\n  closed from " + formatCallers(decrementCallersStacks))
          + "\n\n";
    }

    static String formatCallers(List<List<StackTraceElement>> callers) {
      return String.join(
          "\n            ", callers.stream().map(RepositoryTracking::formatCallStack).toList());
    }

    static String formatCallStack(List<StackTraceElement> stackTraceElements) {
      return String.join(
          "\n              called by ",
          stackTraceElements.stream().map(StackTraceElement::toString).toList());
    }

    @Override
    public void incrementOpen() {
      super.incrementOpen();
      incrementReferenceCounting();
    }

    @Override
    public synchronized void close() {
      super.close();
      if (decrementCallersStacks == null) {
        return;
      }
      decrementCallersStacks.add(getCallers());
      int unused = referenceCounter.decrementAndGet();
    }

    synchronized void incrementReferenceCounting() {
      if (incrementCallersStacks == null) {
        return;
      }
      incrementCallersStacks.add(getCallers());
      int unused = referenceCounter.incrementAndGet();
    }

    synchronized void clear() {
      incrementCallersStacks.clear();
      decrementCallersStacks.clear();
      incrementCallersStacks = null;
      decrementCallersStacks = null;
    }

    private synchronized Optional<String> reportIfOpen() {
      return referenceCounter.get() > 0 ? Optional.of(toString()) : Optional.empty();
    }
  }

  GitRepositoryReferenceCountingManager(
      GitRepositoryManager delegate, AllUsersName allUsersName, AllProjectsName allProjectsName) {
    this.delegate = delegate;
    this.allUsersName = allUsersName;
    this.allProjectsName = allProjectsName;
  }

  public void clear() {
    if (openRepositories != null) {
      openRepositories.forEach(RepositoryTracking::clear);
      openRepositories.clear();
      openRepositories = null;
    }
  }

  public void init(Description testDescription) {
    if (openRepositories != null) {
      clear();
    }

    if (isDisabled(testDescription)) {
      openRepositories = null;
      return;
    }

    openRepositories = Sets.newConcurrentHashSet();
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

  public void assertThatAllRepositoriesAreClosed(String testName) {
    List<String> repositoriesToReport = waitUntilAllRepositoriesAreClosed();
    if (!repositoriesToReport.isEmpty()) {
      fail(
          "All repositories were expected to be closed at the end of the following test:\n"
              + testName
              + "\n\n"
              + "P.S. Hints to resolve the issue:\n"
              + "     See below the tracking information of when the Repository was created,\n"
              + "     referenced and closed throughout the test. Look carefully at the\n"
              + "     open/close or incrementOpen/close pairs for all the AutoCloseable \n"
              + "     objects (either Repository or one of its wrappers, like\n"
              + "     RepoView or RepoRefCache) that is not managed properly inside a\n"
              + "     try-with-resource enclosure.\n"
              + "\n"
              + "See below more details about the Repository objects created / opened and not"
              + " closed.\n"
              + "------------\n"
              + String.join("\n------------\n", repositoriesToReport));
    }
  }

  private List<String> waitUntilAllRepositoriesAreClosed() {
    try {
      return RetryerBuilder.<List<String>>newBuilder()
          .retryIfResult(Predicates.not(List::isEmpty))
          .withStopStrategy(
              StopStrategies.stopAfterDelay(
                  TIMEOUT_WAITING_FOR_CLOSED_REPOSITORIES_SEC, TimeUnit.SECONDS))
          .build()
          .call(this::getOpenRepositoriesToReport);
    } catch (ExecutionException | RetryException e) {
      return getOpenRepositoriesToReport();
    }
  }

  private List<String> getOpenRepositoriesToReport() {
    return MoreObjects.<Set<RepositoryTracking>>firstNonNull(
            openRepositories, Collections.emptySet())
        .stream()
        .map(RepositoryTracking::reportIfOpen)
        .flatMap(Optional::stream)
        .toList();
  }

  @Override
  public Status getRepositoryStatus(Project.NameKey name) {
    return delegate.getRepositoryStatus(name);
  }

  private Repository trackRepository(Project.NameKey name, Repository repository) {
    if (openRepositories == null || name.equals(allUsersName) || name.equals(allProjectsName)) {
      return repository;
    }

    RepositoryTracking trackedRepository = new RepositoryTracking(name.get(), repository);
    openRepositories.add(trackedRepository);
    return trackedRepository;
  }

  private static boolean isDisabled(Description testDescription) {
    if (testDescription.getAnnotation(NoGitRepositoryCheckIfClosed.class) != null) {
      return true;
    }

    for (Class<?> clazz = testDescription.getTestClass();
        clazz != null;
        clazz = clazz.getSuperclass()) {
      if (clazz.getAnnotation(NoGitRepositoryCheckIfClosed.class) != null) {
        return true;
      }
    }

    return false;
  }
}
