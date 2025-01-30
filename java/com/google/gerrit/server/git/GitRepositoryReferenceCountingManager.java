package com.google.gerrit.server.git;

import com.google.gerrit.entities.Project;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;

public class GitRepositoryReferenceCountingManager implements GitRepositoryManager {
  private final GitRepositoryManager delegate;
  private final Map<Project.NameKey, RepositoryTracking> trackedRepositories;

  public static class RepositoryTracking {

    private final StackTraceElement[] stackTrace;
    private final Repository repository;
    private final Project.NameKey name;

    RepositoryTracking(Project.NameKey name, Repository repository) {
      this.name = name;
      this.stackTrace = Thread.currentThread().getStackTrace();
      this.repository = repository;
    }

    @Override
    public String toString() {
      return "Project " + name + " created by " + Stream.of(stackTrace).map(stack -> stack.getClassName() + "." + stack.getMethodName() + ":" + stack.getLineNumber()).collect(Collectors.joining("\n"));
    }

    @Override
    public int hashCode() {
      return repository.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (o == this)
        return true;
      if (!(o instanceof RepositoryTracking))
        return false;
      RepositoryTracking other = (RepositoryTracking)o;
      return repository.equals(other.repository);
    }
  }

  public GitRepositoryReferenceCountingManager(GitRepositoryManager delegate) {
    this.delegate = delegate;
    trackedRepositories = new HashMap<>();
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

  public Map<RepositoryTracking, Integer> openRepositoriesWithReferenceCount() {
    return trackedRepositories.entrySet().stream()
            .filter(repositoryTracking -> repositoryTracking.getValue().repository.useCnt.get() > 1)
        .collect(Collectors.toMap(Map.Entry::getValue, repositoryTracking -> repositoryTracking.getValue().repository.useCnt.get()));
  }

  @Override
  public Status getRepositoryStatus(Project.NameKey name) {
    return delegate.getRepositoryStatus(name);
  }

  private Repository trackRepository(Project.NameKey name, Repository repository) {
    trackedRepositories.put(name, new RepositoryTracking(name, repository));
    return repository;
  }
}
