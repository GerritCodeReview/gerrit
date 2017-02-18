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

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Checks authorization to perform an action on project, ref, or change.
 *
 * <p>{@code PermissionBackend} should be a singleton for the server, acting as a factory for
 * lightweight request instances.
 *
 * <p>Example use:
 *
 * <pre>
 *   private final PermissionBackend permissions;
 *   private final Provider<CurrentUser> user;
 *
 *   @Inject
 *   Foo(PermissionBackend permissions, Provider<CurrentUser> user) {
 *     this.permissions = permissions;
 *     this.user = user;
 *   }
 *
 *   void apply(...) {
 *     permissions.user(user).change(cd).check(ChangePermission.SUBMIT);
 *   }
 * </pre>
 *
 * <p>{@code check} methods should be used during action handlers to verify the user is allowed to
 * exercise the specified permission. For convenience in implementation {@code check} methods throw
 * {@link AuthException} if the permission is denied.
 *
 * <p>{@code test} methods should be used when constructing replies to the client and the result
 * object needs to include a true/false hint indicating the user's ability to exercise the
 * permission. This is suitable for configuring UI button state, but should not be relied upon to
 * guard handlers before making state changes.
 */
public abstract class PermissionBackend {
  /** @return lightweight factory scoped to answer for the specified user. */
  public abstract WithUser user(CurrentUser user);

  /** @return lightweight factory scoped to answer for the specified user. */
  public WithUser user(Provider<CurrentUser> user) {
    return user(user.get());
  }

  /** PermissionBackend scoped to a specific user. */
  public abstract static class WithUser {
    /** @return instance scoped for the specified project. */
    public abstract ForProject project(Project.NameKey project);

    /** @return instance scoped for the {@code ref}, and its parent project. */
    public ForRef ref(Branch.NameKey ref) {
      return project(ref.getParentKey()).ref(ref.get());
    }

    /** @return instance scoped for the change, and its destination ref and project. */
    public ForChange change(ChangeData cd) throws OrmException {
      return ref(cd.change().getDest()).change(cd);
    }

    /** @return instance scoped for the change, and its destination ref and project. */
    public ForChange change(ChangeNotes notes) {
      return ref(notes.getChange().getDest()).change(notes);
    }
  }

  /** PermissionBackend scoped to a user and project. */
  public abstract static class ForProject {
    /** @return new instance rescoped to same project, but different {@code user}. */
    public abstract ForProject user(CurrentUser user);

    /** @return instance scoped for {@code ref} in this project. */
    public abstract ForRef ref(String ref);

    /** Verify scoped user can {@code perm}, throwing if denied. */
    public abstract void check(ProjectPermission perm)
        throws AuthException, PermissionBackendException;

    /** Filter {@code permSet} to permissions scoped user might be able to perform. */
    public abstract Set<ProjectPermission> test(Collection<ProjectPermission> permSet)
        throws PermissionBackendException;

    public boolean test(ProjectPermission perm) throws PermissionBackendException {
      return test(EnumSet.of(perm)).contains(perm);
    }
  }

  /** PermissionBackend scoped to a user, project and reference. */
  public abstract static class ForRef {
    /** @return new instance rescoped to same reference, but different {@code user}. */
    public abstract ForRef user(CurrentUser user);

    /** @return instance scoped to change. */
    public abstract ForChange change(ChangeData cd);

    /** @return instance scoped to change. */
    public abstract ForChange change(ChangeNotes notes);

    /** Verify scoped user can {@code perm}, throwing if denied. */
    public abstract void check(RefPermission perm) throws AuthException, PermissionBackendException;

    /** Filter {@code permSet} to permissions scoped user might be able to perform. */
    public abstract Set<RefPermission> test(Collection<RefPermission> permSet)
        throws PermissionBackendException;

    public boolean test(RefPermission perm) throws PermissionBackendException {
      return test(EnumSet.of(perm)).contains(perm);
    }
  }

  /** PermissionBackend scoped to a user, project, reference and change. */
  public abstract static class ForChange {
    /** @return new instance rescoped to same change, but different {@code user}. */
    public abstract ForChange user(CurrentUser user);

    /** Verify scoped user can {@code perm}, throwing if denied. */
    public abstract void check(ChangePermissionOrLabel perm)
        throws AuthException, PermissionBackendException;

    /** Filter {@code permSet} to permissions scoped user might be able to perform. */
    public abstract <T extends ChangePermissionOrLabel> Set<T> test(Collection<T> permSet)
        throws PermissionBackendException;

    public boolean test(ChangePermissionOrLabel perm) throws PermissionBackendException {
      return test(Collections.singleton(perm)).contains(perm);
    }

    public boolean testOrFalse(ChangePermissionOrLabel perm) {
      try {
        return test(perm);
      } catch (PermissionBackendException e) {
        return false;
      }
    }
  }
}
