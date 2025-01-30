package com.google.gerrit.server.git;

import com.google.gerrit.acceptance.GitRepositoryReferenceCountingManager;
import com.google.inject.Inject;

public class LocalDiskRepositoryCountingManager extends GitRepositoryReferenceCountingManager {

  @Inject
  public LocalDiskRepositoryCountingManager(LocalDiskRepositoryManager delegate) {
    super(delegate);
  }
}
