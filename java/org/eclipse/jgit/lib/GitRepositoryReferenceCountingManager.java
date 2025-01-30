package org.eclipse.jgit.lib;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.RepositoryExistsException;
import java.io.IOException;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

public class GitRepositoryReferenceCountingManager implements GitRepositoryManager {
  private final GitRepositoryManager delegate;
  private Set<Repository> trackedRepositories;

  public GitRepositoryReferenceCountingManager(GitRepositoryManager delegate) {
    this.delegate = delegate;
  }

  @Override
  public Repository openRepository(Project.NameKey name)
      throws RepositoryNotFoundException, IOException {
    return trackRepository(delegate.openRepository(name));
  }

  @Override
  public Repository createRepository(Project.NameKey name)
      throws RepositoryNotFoundException, RepositoryExistsException, IOException {
    return trackRepository(delegate.createRepository(name));
  }

  @Override
  public NavigableSet<Project.NameKey> list() {
    return delegate.list();
  }

  @Override
  public Boolean canPerformGC() {
    return delegate.canPerformGC();
  }

  public Map<Repository, Integer> openRepositoriesWithReferenceCount() {
    return trackedRepositories.stream()
        .collect(Collectors.toMap(Function.identity(), repository -> repository.useCnt.get()));
  }

  @Override
  public Status getRepositoryStatus(Project.NameKey name) {
    return delegate.getRepositoryStatus(name);
  }

  private Repository trackRepository(Repository repository) {
    trackedRepositories.add(repository);
    return repository;
  }
}
