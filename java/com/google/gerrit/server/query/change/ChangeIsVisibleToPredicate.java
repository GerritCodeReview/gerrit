// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Change;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.index.query.IsVisibleToPredicate;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.util.Optional;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

public class ChangeIsVisibleToPredicate extends IsVisibleToPredicate<ChangeData> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    ChangeIsVisibleToPredicate forUser(CurrentUser user);
  }

  protected final ChangeNotes.Factory notesFactory;
  protected final CurrentUser user;
  protected final PermissionBackend permissionBackend;
  protected final ProjectCache projectCache;
  private final Provider<AnonymousUser> anonymousUserProvider;

  @Inject
  public ChangeIsVisibleToPredicate(
      ChangeNotes.Factory notesFactory,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      Provider<AnonymousUser> anonymousUserProvider,
      @Assisted CurrentUser user) {
    super(ChangeQueryBuilder.FIELD_VISIBLETO, IndexUtils.describe(user));
    this.notesFactory = notesFactory;
    this.user = user;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
    this.anonymousUserProvider = anonymousUserProvider;
  }

  @Override
  public boolean match(ChangeData cd) {
    if (cd.fastIsVisibleTo(user)) {
      return true;
    }
    Change change = cd.change();
    if (change == null) {
      return false;
    }

    Optional<ProjectState> projectState = projectCache.get(cd.project());
    if (!projectState.isPresent()) {
      logger.atFine().log("Filter out change %s of non-existing project %s", cd, cd.project());
      return false;
    }
    if (!projectState.get().statePermitsRead()) {
      logger.atFine().log("Filter out change %s of non-reabable project %s", cd, cd.project());
      return false;
    }

    PermissionBackend.WithUser withUser =
        user.isIdentifiedUser()
            ? permissionBackend.absentUser(user.getAccountId())
            : permissionBackend.user(
                Optional.of(user)
                    .filter(u -> u instanceof GroupBackedUser || u instanceof InternalUser)
                    .orElseGet(anonymousUserProvider::get));
    try {
      withUser.change(cd).check(ChangePermission.READ);
    } catch (PermissionBackendException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RepositoryNotFoundException) {
        logger.atWarning().withCause(e).log(
            "Filter out change %s because the corresponding repository %s was not found",
            cd, cd.project());
        return false;
      }
      throw new StorageException("unable to check permissions on change " + cd.getId(), e);
    } catch (AuthException e) {
      logger.atFine().log("Filter out non-visisble change: %s", cd);
      return false;
    }

    cd.cacheVisibleTo(user);
    return true;
  }

  @Override
  public int getCost() {
    return 1;
  }
}
