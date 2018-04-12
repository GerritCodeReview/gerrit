// Copyright (C) 2017 The Android Open Source Project
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

import com.google.gerrit.extensions.api.access.GlobalOrPluginPermission;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackend.ForChange;
import com.google.gerrit.server.permissions.PermissionBackend.ForProject;
import com.google.gerrit.server.permissions.PermissionBackend.ForRef;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import com.google.gerrit.server.permissions.PermissionBackend.WithUser;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Provider;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/**
 * Helpers for {@link PermissionBackend} that must fail.
 *
 * <p>These helpers are useful to curry failure state identified inside a non-throwing factory
 * method to the throwing {@code check} or {@code test} methods.
 */
public class FailedPermissionBackend {
  public static WithUser user(String message) {
    return new FailedWithUser(message, null);
  }

  public static WithUser user(String message, Throwable cause) {
    return new FailedWithUser(message, cause);
  }

  public static ForProject project(String message) {
    return project(message, null);
  }

  public static ForProject project(String message, Throwable cause) {
    return new FailedProject(message, cause);
  }

  public static ForRef ref(String message) {
    return ref(message, null);
  }

  public static ForRef ref(String message, Throwable cause) {
    return new FailedRef(message, cause);
  }

  public static ForChange change(String message) {
    return change(message, null);
  }

  public static ForChange change(String message, Throwable cause) {
    return new FailedChange(message, cause);
  }

  private FailedPermissionBackend() {}

  private static class FailedWithUser extends WithUser {
    private final String message;
    private final Throwable cause;

    FailedWithUser(String message, Throwable cause) {
      this.message = message;
      this.cause = cause;
    }

    @Override
    public CurrentUser user() {
      throw new UnsupportedOperationException("FailedPermissionBackend is not scoped to user");
    }

    @Override
    public ForProject project(NameKey project) {
      return new FailedProject(message, cause);
    }

    @Override
    public void check(GlobalOrPluginPermission perm) throws PermissionBackendException {
      throw new PermissionBackendException(message, cause);
    }

    @Override
    public <T extends GlobalOrPluginPermission> Set<T> test(Collection<T> permSet)
        throws PermissionBackendException {
      throw new PermissionBackendException(message, cause);
    }
  }

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
    public CurrentUser user() {
      throw new UnsupportedOperationException("FailedPermissionBackend is not scoped to user");
    }

    @Override
    public ForProject user(CurrentUser user) {
      return this;
    }

    @Override
    public String resourcePath() {
      throw new UnsupportedOperationException(
          "FailedPermissionBackend is not scoped to a resource");
    }

    @Override
    public ForRef ref(String ref) {
      return new FailedRef(message, cause);
    }

    @Override
    public void check(ProjectPermission perm) throws PermissionBackendException {
      throw new PermissionBackendException(message, cause);
    }

    @Override
    public Set<ProjectPermission> test(Collection<ProjectPermission> permSet)
        throws PermissionBackendException {
      throw new PermissionBackendException(message, cause);
    }

    @Override
    public Map<String, Ref> filter(Map<String, Ref> refs, Repository repo, RefFilterOptions opts)
        throws PermissionBackendException {
      throw new PermissionBackendException(message, cause);
    }
  }

  private static class FailedRef extends ForRef {
    private final String message;
    private final Throwable cause;

    FailedRef(String message, Throwable cause) {
      this.message = message;
      this.cause = cause;
    }

    @Override
    public ForRef database(Provider<ReviewDb> db) {
      return this;
    }

    @Override
    public CurrentUser user() {
      throw new UnsupportedOperationException("FailedPermissionBackend is not scoped to user");
    }

    @Override
    public ForRef user(CurrentUser user) {
      return this;
    }

    @Override
    public String resourcePath() {
      throw new UnsupportedOperationException(
          "FailedPermissionBackend is not scoped to a resource");
    }

    @Override
    public ForChange change(ChangeData cd) {
      return new FailedChange(message, cause);
    }

    @Override
    public ForChange change(ChangeNotes notes) {
      return new FailedChange(message, cause);
    }

    @Override
    public ForChange indexedChange(ChangeData cd, ChangeNotes notes) {
      return new FailedChange(message, cause);
    }

    @Override
    public void check(RefPermission perm) throws PermissionBackendException {
      throw new PermissionBackendException(message, cause);
    }

    @Override
    public Set<RefPermission> test(Collection<RefPermission> permSet)
        throws PermissionBackendException {
      throw new PermissionBackendException(message, cause);
    }
  }

  private static class FailedChange extends ForChange {
    private final String message;
    private final Throwable cause;

    FailedChange(String message, Throwable cause) {
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
    public String resourcePath() {
      throw new UnsupportedOperationException(
          "FailedPermissionBackend is not scoped to a resource");
    }

    @Override
    public void check(ChangePermissionOrLabel perm) throws PermissionBackendException {
      throw new PermissionBackendException(message, cause);
    }

    @Override
    public <T extends ChangePermissionOrLabel> Set<T> test(Collection<T> permSet)
        throws PermissionBackendException {
      throw new PermissionBackendException(message, cause);
    }

    @Override
    public CurrentUser user() {
      throw new UnsupportedOperationException("FailedPermissionBackend is not scoped to user");
    }
  }
}
