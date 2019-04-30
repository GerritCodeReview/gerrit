package com.google.gerrit.server.git;

import com.google.gerrit.server.permissions.PermissionBackend;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.lib.Repository;

/**
 * Wraps and unwraps existing repositories and makes them permission-aware by returning a {@link
 * PermissionAwareRefDatabase}.
 */
public class PermissionAwareRepositoryManager {
  public static Repository wrap(Repository delegate, PermissionBackend.ForProject forProject) {
    if (delegate instanceof PermissionAwareRepositoryWrapper) {
      return wrap(((PermissionAwareRepositoryWrapper) delegate).unwrap(), forProject);
    }

    if (delegate instanceof DfsRepository) {
      return new PermissionAwareDfsRepository((DfsRepository) delegate, forProject);
    }

    return new PermissionAwareRepository(delegate, forProject);
  }
}
