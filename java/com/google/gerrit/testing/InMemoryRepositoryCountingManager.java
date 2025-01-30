package com.google.gerrit.testing;

import com.google.inject.Inject;
import org.eclipse.jgit.lib.GitRepositoryReferenceCountingManager;

public class InMemoryRepositoryCountingManager extends GitRepositoryReferenceCountingManager {

  @Inject
  public InMemoryRepositoryCountingManager(InMemoryRepositoryManager delegate) {
    super(delegate);
  }
}
