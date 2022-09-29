package com.google.gerrit.server.git;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class PermissionChecker {
  private final PermissionBackend permissionBackend;

  @Inject
  PermissionChecker(PermissionBackend permissionBackend) {
    this.permissionBackend = permissionBackend;
  }

  /**
   * Checks whether the current user has permissions for creating a change in the given branch.
   *
   * @param project of the new change
   * @param refName of the requested branch. If not provided - check general project permissions.
   * @param forgedAuthor The author that is requested to be listed.
   * @throws ResourceConflictException if the branch doesn't allow writing.
   * @throws ResourceNotFoundException if the branch is not found.
   * @throws AuthException if there are authentication errors.
   * @throws PermissionBackendException if there are permission issues.
   */
  public void checkPermissionsForCreatingChange(
      Project.NameKey project,
      ProjectState projectState,
      @Nullable String refName,
      @Nullable AccountInput forgedAuthor)
      throws ResourceConflictException, ResourceNotFoundException, AuthException,
          PermissionBackendException {
    projectState.checkStatePermitsWrite();
    PermissionBackend.ForRef forRef = permissionBackend.currentUser().project(project).ref(refName);
    if (refName != null && !forRef.test(RefPermission.READ)) {
      throw new ResourceNotFoundException(String.format("ref %s not found", refName));
    }
    forRef.check(RefPermission.CREATE_CHANGE);
    if (forgedAuthor != null) {
      forRef.check(RefPermission.FORGE_AUTHOR);
    }
  }

  public boolean testPermissionsForCreatingChange(
      Project.NameKey project,
      ProjectState projectState,
      @Nullable String refName,
      @Nullable AccountInput forgedAuthor) {
    try {
      checkPermissionsForCreatingChange(project, projectState, refName, forgedAuthor);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
