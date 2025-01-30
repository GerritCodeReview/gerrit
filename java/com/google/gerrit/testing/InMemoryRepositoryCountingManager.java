package com.google.gerrit.testing;

import com.google.gerrit.server.git.GitRepositoryReferenceCountingManager;
import com.google.inject.Inject;

public class InMemoryRepositoryCountingManager extends GitRepositoryReferenceCountingManager {

  @Inject
  public InMemoryRepositoryCountingManager(InMemoryRepositoryManager delegate) {
    super(delegate);
  }
}
