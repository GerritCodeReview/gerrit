package com.google.gerrit.server.git;

import com.google.gerrit.entities.Project;
import java.io.IOException;
import java.util.SortedSet;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;

public class GitRepositoryReferenceCountingManager implements GitRepositoryManager {
  private final GitRepositoryManager delegate;

  public GitRepositoryReferenceCountingManager(GitRepositoryManager delegate) {
    this.delegate = delegate;
  }

  @Override
  public Repository openRepository(Project.NameKey name)
      throws RepositoryNotFoundException, IOException {
    return delegate.openRepository(name);
  }

  @Override
  public Repository createRepository(Project.NameKey name)
      throws RepositoryCaseMismatchException, RepositoryNotFoundException, IOException {
    return delegate.createRepository(name);
  }

  @Override
  public SortedSet<Project.NameKey> list() {
    return delegate.list();
  }
}
