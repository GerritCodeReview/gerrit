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

import static com.google.gerrit.entities.RefNames.REFS_CACHE_AUTOMERGE;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.NoSuchGroupException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.query.change.ChangeData;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jgit.lib.Constants;

@Singleton
class RefVisibilityControl {
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
   * Returns an authoritative answer if the ref and all it's contents are visible to the user. Does
   * not have support for tags and will throw a {@link PermissionBackendException} if asked for tags
   * visibility.
   */
  boolean isVisible(ProjectControl projectControl, String refName)
      throws PermissionBackendException {
    CurrentUser user = projectControl.getUser();
    if (refName.startsWith(Constants.R_TAGS)) {
      throw new PermissionBackendException(
          "can't tag checks through PermissionBackend#ForRef. Use PermissionBackend#filter instead.");
    }
    if (refName.startsWith(REFS_CACHE_AUTOMERGE)) {
      // Internal cache state that is accessible to no one.
      return false;
    }

    // NoteDb sequence visibility
    boolean viewMetadata =
        permissionBackend
            .user(projectControl.getUser())
            .testOrFalse(GlobalPermission.ACCESS_DATABASE);
    if (refName.startsWith(RefNames.REFS_SEQUENCES)) {
      // Sequences are internal database implementation details.
      return viewMetadata;
    }

    // Change and change edit visibility
    Change.Id changeId;
    Project.NameKey project = projectControl.getProject().getNameKey();
    if ((changeId = Change.Id.fromRef(refName)) != null) {
      // Change ref is visible only if the change is visible.
      ChangeData cd = changeDataFactory.create(project, changeId);
      if (RefNames.isRefsEdit(refName)) {
        // Edits are visible only to the owning user, if change is visible.
        return viewMetadata || visibleEdit(refName, projectControl, cd);
      }
      return projectControl.controlFor(cd).isVisible();
    }

    // Account and external ID visibility
    Account.Id currentUserAccountId = user.isIdentifiedUser() ? user.getAccountId() : null;
    Account.Id accountId;
    if ((accountId = Account.Id.fromRef(refName)) != null && currentUserAccountId != null) {
      // Account ref is visible only to the corresponding account.
      if (viewMetadata
          || (accountId.equals(currentUserAccountId)
              && projectControl.controlForRef(refName).hasReadPermissionOnRef())) {
        return true;
      }
      return false;
    }
    if (refName.equals(RefNames.REFS_USERS_SELF)) {
      // viewMetadata allows to see all account refs, hence refs/users/self should be included as
      // well
      return viewMetadata;
    }
    if (projectControl.getProjectState().isAllUsers()
        && (refName.equals(RefNames.REFS_EXTERNAL_IDS)
            || refName.equals(RefNames.REFS_GROUPNAMES))) {
      // The notes branches with the external IDs / group names must not be exposed to normal
      // users.
      return viewMetadata;
    }

    // Group visibility
    AccountGroup.UUID accountGroupUuid;
    if ((accountGroupUuid = AccountGroup.UUID.fromRef(refName)) != null) {
      // Group ref is visible only to the corresponding owner group.
      if (viewMetadata && projectControl.controlForRef(refName).hasReadPermissionOnRef()) {
        try {
          return groupControlFactory.controlFor(user, accountGroupUuid).isOwner();
        } catch (NoSuchGroupException e) {
          // The group is broken, but the ref is still around. Pretend the ref is not visible.
          logger.atWarning().withCause(e).log(
              "Found group ref %s but group isn't parseable", refName);
          return false;
        }
      }
      return false;
    }

    // This is not a special Gerrit ref and not a NoteDb ref. Likely, it's just a ref under
    // refs/heads or another ref the user created. Apply the regular permissions with inheritance.
    return projectControl.controlForRef(refName).hasReadPermissionOnRef();
  }

  private boolean visibleEdit(String refName, ProjectControl projectControl, ChangeData cd)
      throws PermissionBackendException {
    Change.Id id = Change.Id.fromEditRefPart(refName);
    if (id == null) {
      throw new IllegalStateException("unable to parse change id from edit ref " + refName);
    }

    if (projectControl.getUser().isIdentifiedUser()
        && refName.startsWith(
            RefNames.refsEditPrefix(projectControl.getUser().asIdentifiedUser().getAccountId()))) {
      logger.atFinest().log("Own change edit ref is visible: %s", refName);
      return true;
    }

    try {
      // Default to READ_PRIVATE_CHANGES as there is no special permission for reading edits.
      projectControl
          .asForProject()
          .ref(cd.change().getDest().branch())
          .check(RefPermission.READ_PRIVATE_CHANGES);
      logger.atFinest().log("Foreign change edit ref is visible: %s", refName);
      return true;
    } catch (AuthException e) {
      logger.atFinest().log("Foreign change edit ref is not visible: %s", refName);
      return false;
    }
  }
}
