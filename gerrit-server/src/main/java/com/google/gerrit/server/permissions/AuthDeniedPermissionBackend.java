// Copyright (C) 2018 The Android Open Source Project
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

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackend.ForChange;
import com.google.gerrit.server.permissions.PermissionBackend.ForProject;
import com.google.gerrit.server.permissions.PermissionBackend.ForRef;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Provider;
import java.util.Collection;
import java.util.Set;

/**
 * Helpers for {@link PermissionBackend} that must deny permissions.
 *
 * <p>These helpers are useful to deny permissions to access a resource because of an inner
 * exception that is not within the AuthException hierarchy.
 */
public class AuthDeniedPermissionBackend {

  public static ForProject project(String message, Throwable cause) {
    return new FailedProject(message, cause);
  }

  private AuthDeniedPermissionBackend() {}

  private static class FailedProject extends ForProject {
    private final String message;
    private final Throwable cause;

    FailedProject(String message, Throwable cause) {
      this.message = message;
      this.cause = cause;
    }

    @Override
    public ForProject database(Provider<ReviewDb> db) {
      return this;
    }

    @Override
    public ForProject user(CurrentUser user) {
      return this;
    }

    @Override
    public ForRef ref(String ref) {
      return new AuthDeniedRef(message, cause);
    }

    @Override
    public void check(ProjectPermission perm) throws AuthException {
      throw new AuthException(message, cause);
    }

    @Override
    public Set<ProjectPermission> test(Collection<ProjectPermission> permSet)
        throws PermissionBackendException {
      throw new PermissionBackendException(message, cause);
    }
  }

  private static class AuthDeniedRef extends ForRef {
    private final String message;
    private final Throwable cause;

    AuthDeniedRef(String message, Throwable cause) {
      this.message = message;
      this.cause = cause;
    }

    @Override
    public ForRef database(Provider<ReviewDb> db) {
      return this;
    }

    @Override
    public ForRef user(CurrentUser user) {
      return this;
    }

    @Override
    public ForChange change(ChangeData cd) {
      return new AuthDeniedChange(message, cause);
    }

    @Override
    public ForChange change(ChangeNotes notes) {
      return new AuthDeniedChange(message, cause);
    }

    @Override
    public ForChange indexedChange(ChangeData cd, ChangeNotes notes) {
      return new AuthDeniedChange(message, cause);
    }

    @Override
    public void check(RefPermission perm) throws AuthException {
      throw new AuthException(message, cause);
    }

    @Override
    public Set<RefPermission> test(Collection<RefPermission> permSet)
        throws PermissionBackendException {
      throw new PermissionBackendException(message, cause);
    }
  }

  private static class AuthDeniedChange extends ForChange {
    private final String message;
    private final Throwable cause;

    AuthDeniedChange(String message, Throwable cause) {
      this.message = message;
      this.cause = cause;
    }

    @Override
    public ForChange database(Provider<ReviewDb> db) {
      return this;
    }

    @Override
    public ForChange user(CurrentUser user) {
      return this;
    }

    @Override
    public void check(ChangePermissionOrLabel perm) throws AuthException {
      throw new AuthException(message, cause);
    }

    @Override
    public <T extends ChangePermissionOrLabel> Set<T> test(Collection<T> permSet)
        throws PermissionBackendException {
      throw new PermissionBackendException(message, cause);
    }

    @Override
    public CurrentUser user() {
      throw new UnsupportedOperationException("AuthDeniedPermissionBackend is not scoped to user");
    }
  }
}
