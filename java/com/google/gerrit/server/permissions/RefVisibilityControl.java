// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.permissions;

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.entities.RefNames.REFS_CACHE_AUTOMERGE;

import com.google.common.base.Throwables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.NoSuchGroupException;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jgit.lib.Constants;

/**
 * This class is a component that is internal to {@link DefaultPermissionBackend}. It can
 * authoritatively tell if a ref is accessible by a user.
 */
@Singleton
public class RefVisibilityControl {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PermissionBackend permissionBackend;
  private final GroupControl.GenericFactory groupControlFactory;
  private final ChangeData.Factory changeDataFactory;

  @Inject
  RefVisibilityControl(
      PermissionBackend permissionBackend,
      GroupControl.GenericFactory groupControlFactory,
      ChangeData.Factory changeDataFactory) {
    this.permissionBackend = permissionBackend;
    this.groupControlFactory = groupControlFactory;
    this.changeDataFactory = changeDataFactory;
  }

  /**
   * Returns an authoritative answer if the ref is visible to the user. Does not have support for
   * tags and will throw a {@link PermissionBackendException} if asked for tags visibility.
   */
  boolean isVisible(ProjectControl projectControl, String refName)
      throws PermissionBackendException {
    if (refName.startsWith(Constants.R_TAGS)) {
      throw new PermissionBackendException(
          "can't check tags through RefVisibilityControl. Use PermissionBackend#filter instead.");
    }
    if (!RefNames.isGerritRef(refName)) {
      // This is not a special Gerrit ref and not a NoteDb ref. Likely, it's just a ref under
      // refs/heads or another ref the user created. Apply the regular permissions with inheritance.

      // Admins should be able to read all branches (including 'refs/meta/config').
      if (projectControl.isAdmin()) {
        return true;
      }
      return projectControl.controlForRef(refName).hasReadPermissionOnRef(false);
    }

    if (refName.startsWith(REFS_CACHE_AUTOMERGE)) {
      // Internal cache state that is accessible to no one.
      return false;
    }

    boolean hasAccessDatabase =
        permissionBackend
            .exactUser(projectControl.getUser())
            .testOrFalse(GlobalPermission.ACCESS_DATABASE);
    if (hasAccessDatabase) {
      return true;
    }

    // Change and change edit visibility
    Change.Id changeId;
    if ((changeId = Change.Id.fromRef(refName)) != null) {
      // Change ref is visible only if the change is visible.
      ChangeData cd;
      try {
        cd = changeDataFactory.create(projectControl.getProject().getNameKey(), changeId);
        checkState(cd.change().getId().equals(changeId));
      } catch (StorageException e) {
        if (Throwables.getCausalChain(e).stream()
            .anyMatch(e2 -> e2 instanceof NoSuchChangeException)) {
          // The change was deleted or is otherwise not accessible anymore.
          // If the caller can see all refs and is allowed to see private changes on refs/, allow
          // access. This is an escape hatch for receivers of "ref deleted" events.
          PermissionBackend.ForProject forProject = projectControl.asForProject();
          return forProject.test(ProjectPermission.READ)
              && forProject.ref("refs/").test(RefPermission.READ_PRIVATE_CHANGES);
        }
        throw new PermissionBackendException(e);
      }
      if (RefNames.isRefsEdit(refName)) {
        // Edits are visible only to the owning user, if change is visible.
        return visibleEdit(refName, projectControl, cd);
      }
      return projectControl.controlFor(cd).isVisible();
    }

    // Account visibility
    CurrentUser user = projectControl.getUser();
    Account.Id currentUserAccountId = user.isIdentifiedUser() ? user.getAccountId() : null;
    Account.Id accountId;
    if ((accountId = Account.Id.fromRef(refName)) != null) {
      // Account ref is visible only to the corresponding account.
      if (accountId.equals(currentUserAccountId)) {
        // Always allow visibility to refs/draft-comments and refs/starred-changes. For all other
        // refs, check if the user has read permissions.
        if (RefNames.isRefsDraftsComments(refName)
            || RefNames.isRefsStarredChanges(refName)
            || projectControl.controlForRef(refName).hasReadPermissionOnRef(true)) {
          return true;
        }
      }
      return false;
    }

    // Group visibility
    AccountGroup.UUID accountGroupUuid;
    if ((accountGroupUuid = AccountGroup.UUID.fromRef(refName)) != null) {
      // Group ref is visible only to the corresponding owner group.
      try {
        return projectControl.controlForRef(refName).hasReadPermissionOnRef(true)
            && groupControlFactory.controlFor(user, accountGroupUuid).isOwner();
      } catch (NoSuchGroupException e) {
        // The group is broken, but the ref is still around. Pretend the ref is not visible.
        logger.atWarning().withCause(e).log("Found group ref %s but group isn't parsable", refName);
        return false;
      }
    }

    // We are done checking all cases where we would allow access to Gerrit-managed refs. Deny
    // access in case we got this far.
    logger.atFine().log(
        "Denying access to %s because user doesn't have access to this Gerrit ref", refName);
    return false;
  }

  private boolean visibleEdit(String refName, ProjectControl projectControl, ChangeData cd)
      throws PermissionBackendException {
    Change.Id id = Change.Id.fromEditRefPart(refName);
    if (id == null) {
      throw new IllegalStateException("unable to parse change id from edit ref " + refName);
    }

    if (!projectControl.controlFor(cd).isVisible()) {
      // The user can't see the change so they can't see any edits.
      return false;
    }

    if (projectControl.getUser().isIdentifiedUser()
        && refName.startsWith(
            RefNames.refsEditPrefix(projectControl.getUser().asIdentifiedUser().getAccountId()))) {
      logger.atFinest().log("Own change edit ref is visible: %s", refName);
      return true;
    }

    // Default to READ_PRIVATE_CHANGES as there is no special permission for reading edits.
    boolean canRead =
        projectControl
            .asForProject()
            .ref(cd.change().getDest().branch())
            .test(RefPermission.READ_PRIVATE_CHANGES);
    logger.atFinest().log(
        "Foreign change edit ref is " + (canRead ? "visible" : "invisible") + ": %s", refName);
    return canRead;
  }
}
