package com.google.gerrit.server.git;

import com.google.gerrit.server.permissions.PermissionBackend;
import org.eclipse.jgit.lib.Repository;

/**
 * Wraps and unwraps existing repositories and makes them permission-aware by returning a {@link
 * PermissionAwareReadOnlyRefDatabase}.
 */
public class PermissionAwareRepositoryManager {
  public static Repository wrap(Repository delegate, PermissionBackend.ForProject forProject) {
    if (delegate instanceof PermissionAwareRepository) {
      new PermissionAwareRepository(((PermissionAwareRepository) delegate).unwrap(), forProject);
    }
    return new PermissionAwareRepository(delegate, forProject);
  }

  public static Repository unwrap(Repository repository) {
    if (repository instanceof PermissionAwareRepository) {
      return ((PermissionAwareRepository) repository).unwrap();
    }
    return repository;
  }
}
