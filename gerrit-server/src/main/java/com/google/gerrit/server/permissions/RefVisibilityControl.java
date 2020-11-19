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

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gwtorm.server.OrmException;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.eclipse.jgit.lib.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is a component that is internal to {@link DefaultPermissionBackend}. It can
 * authoritatively tell if a ref is accessible by a user.
 */
@Singleton
public class RefVisibilityControl {
  private static final Logger logger = LoggerFactory.getLogger(RefVisibilityControl.class);

  private final Provider<ReviewDb> dbProvider;
  private final OneOffRequestContext oneOffRequestContext;
  private final PermissionBackend permissionBackend;
  private final ChangeData.Factory changeDataFactory;

  @Inject
  RefVisibilityControl(
      Provider<ReviewDb> dbProvider,
      OneOffRequestContext oneOffRequestContext,
      PermissionBackend permissionBackend,
      ChangeData.Factory changeDataFactory) {
    this.dbProvider = dbProvider;
    this.oneOffRequestContext = oneOffRequestContext;
    this.permissionBackend = permissionBackend;
    this.changeDataFactory = changeDataFactory;
  }

  /**
   * Returns an authoritative answer if the ref is visible to the user. Does not have support for
   * tags and will throw a {@link PermissionBackendException} if asked for tags visibility.
   */
  public boolean isVisible(ProjectControl projectControl, String refName)
      throws PermissionBackendException {
    if (refName.startsWith(Constants.R_TAGS)) {
      throw new PermissionBackendException(
          "can't check tags through RefVisibilityControl. Use PermissionBackend#filter instead.");
    }
    if (!RefNames.isGerritRef(refName)) {
      // This is not a special Gerrit ref and not a NoteDb ref. Likely, it's just a ref under
      // refs/heads or another ref the user created. Apply the regular permissions with inheritance.
      return projectControl.controlForRef(refName).hasReadPermissionOnRef(false);
    }

    if (refName.startsWith(RefNames.REFS_CACHE_AUTOMERGE)) {
      // Internal cache state that is accessible to no one.
      return false;
    }

    boolean hasAccessDatabase =
        permissionBackend
            .user(projectControl.getUser())
            .testOrFalse(GlobalPermission.ACCESS_DATABASE);
    if (hasAccessDatabase) {
      return true;
    }

    // Change and change edit visibility
    Change.Id changeId;
    if ((changeId = Change.Id.fromRef(refName)) != null) {
      // Change ref is visible only if the change is visible.
      try (CloseableOneTimeReviewDb ignored = new CloseableOneTimeReviewDb()) {
        ChangeData cd;
        try {
          cd =
              changeDataFactory.create(
                  dbProvider.get(), projectControl.getProject().getNameKey(), changeId);
          checkState(cd.change().getId().equals(changeId));
        } catch (OrmException e) {
          if (Throwables.getCausalChain(e).stream()
              .anyMatch(e2 -> e2 instanceof NoSuchChangeException)) {
            // The change was deleted or is otherwise not accessible anymore.
            // If the caller can see all refs and is allowed to see private changes on refs/, allow
            // access. This is an escape hatch for receivers of "ref deleted" events.
            PermissionBackend.ForProject forProject = projectControl.asForProject();
            return forProject.test(ProjectPermission.READ);
          }
          throw new PermissionBackendException(e);
        }
        if (RefNames.isRefsEdit(refName)) {
          // Edits are visible only to the owning user, if change is visible.
          return visibleEdit(refName, projectControl, cd);
        }
        return isVisible(projectControl.controlFor(getNotes(cd)).setChangeData(cd));
      }
    }

    // Account visibility
    CurrentUser user = projectControl.getUser();
    Account.Id currentUserAccountId = user.isIdentifiedUser() ? user.getAccountId() : null;
    Account.Id accountId;
    if ((accountId = Account.Id.fromRef(refName)) != null) {
      // Account ref is visible only to the corresponding account.
      if (accountId.equals(currentUserAccountId)
          && projectControl.controlForRef(refName).hasReadPermissionOnRef(true)) {
        return true;
      }
      return false;
    }

    // We are done checking all cases where we would allow access to Gerrit-managed refs. Deny
    // access in case we got this far.
    logger.debug(
        "Denying access to %s because user doesn't have access to this Gerrit ref", refName);
    return false;
  }

  private boolean visibleEdit(String refName, ProjectControl projectControl, ChangeData cd)
      throws PermissionBackendException {
    Change.Id id = Change.Id.fromEditRefPart(refName);
    if (id == null) {
      throw new IllegalStateException("unable to parse change id from edit ref " + refName);
    }

    if (!isVisible(projectControl.controlFor(getNotes(cd)).setChangeData(cd))) {
      // The user can't see the change so they can't see any edits.
      return false;
    }

    if (projectControl.getUser().isIdentifiedUser()
        && refName.startsWith(
            RefNames.refsEditPrefix(projectControl.getUser().asIdentifiedUser().getAccountId()))) {
      logger.debug("Own change edit ref is visible: %s", refName);
      return true;
    }

    return false;
  }

  private ChangeNotes getNotes(ChangeData cd) throws PermissionBackendException {
    try {
      return cd.notes();
    } catch (OrmException e) {
      throw new PermissionBackendException(e);
    }
  }

  private boolean isVisible(ChangeControl changeControl) throws PermissionBackendException {
    try {
      return changeControl.isVisible(dbProvider.get());
    } catch (OrmException e) {
      throw new PermissionBackendException(e);
    }
  }

  private Optional<ReviewDb> getReviewDb() {
    try {
      return Optional.of(dbProvider.get());
    } catch (Exception e) {
      return Optional.absent();
    }
  }

  /** Helper to establish a database connection. */
  private class CloseableOneTimeReviewDb implements AutoCloseable {
    @Nullable private final ManualRequestContext ctx;

    CloseableOneTimeReviewDb() throws PermissionBackendException {
      if (!getReviewDb().isPresent()) {
        try {
          ctx = oneOffRequestContext.open();
        } catch (OrmException e) {
          throw new PermissionBackendException(e);
        }
      } else {
        ctx = null;
      }
    }

    @Override
    public void close() {
      if (ctx != null) {
        ctx.close();
      }
    }
  }
}
