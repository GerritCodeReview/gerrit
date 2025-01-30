package com.google.gerrit.acceptance;

import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.inject.Inject;

public class LocalDiskRepositoryCountingManager extends GitRepositoryReferenceCountingManager {

  @Inject
  public LocalDiskRepositoryCountingManager(LocalDiskRepositoryManager delegate) {
    super(delegate);
  }
}
