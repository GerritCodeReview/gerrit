package com.google.gerrit.server.git;

import org.eclipse.jgit.lib.Repository;

public interface PermissionAwareRepositoryWrapper {
  Repository unwrap();
}
