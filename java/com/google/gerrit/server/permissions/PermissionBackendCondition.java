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
import com.google.gerrit.extensions.conditions.BooleanCondition;
import com.google.gerrit.extensions.conditions.PrivateInternals_BooleanCondition;
import com.google.gerrit.server.CurrentUser;
import java.util.Objects;

/** {@link BooleanCondition} to evaluate a permission. */
public abstract class PermissionBackendCondition
    extends PrivateInternals_BooleanCondition.SubclassOnlyInCoreServer {
  Boolean value;

  /**
   * Assign a specific {@code testOrFalse} result to this condition.
   *
   * <p>By setting the condition to a specific value the condition will bypass calling {@link
   * PermissionBackend} during {@code value()}, and immediately return the set value instead.
   *
   * @param val value to return from {@code value()}.
   */
  public void set(boolean val) {
    value = val;
  }

  @Override
  public abstract String toString();

  @Override
  public boolean evaluatesTrivially() {
    // PermissionBackendCondition needs to contact PermissionBackend so trivial evaluation is not
    // possible.
    return false;
  }

  @Override
  public BooleanCondition reduce() {
    // No reductions can be made
    return this;
  }

  public static class WithUser extends PermissionBackendCondition {
    private final PermissionBackend.WithUser impl;
    private final GlobalOrPluginPermission perm;
    private final CurrentUser user;

    public WithUser(
        PermissionBackend.WithUser impl, GlobalOrPluginPermission perm, CurrentUser user) {
      this.impl = impl;
      this.perm = perm;
      this.user = user;
    }

    public PermissionBackend.WithUser withUser() {
      return impl;
    }

    public GlobalOrPluginPermission permission() {
      return perm;
    }

    @Override
    public boolean value() {
      return value != null ? value : impl.testOrFalse(perm);
    }

    @Override
    public String toString() {
      return "PermissionBackendCondition.WithUser(" + perm + ")";
    }

    @Override
    public int hashCode() {
      return Objects.hash(perm, hashForUser(user));
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof WithUser)) {
        return false;
      }
      WithUser other = (WithUser) obj;
      return Objects.equals(perm, other.perm) && usersAreEqual(user, other.user);
    }
  }

  public static class ForProject extends PermissionBackendCondition {
    private final PermissionBackend.ForProject impl;
    private final ProjectPermission perm;
    private final CurrentUser user;

    public ForProject(PermissionBackend.ForProject impl, ProjectPermission perm, CurrentUser user) {
      this.impl = impl;
      this.perm = perm;
      this.user = user;
    }

    public PermissionBackend.ForProject project() {
      return impl;
    }

    public ProjectPermission permission() {
      return perm;
    }

    @Override
    public boolean value() {
      return value != null ? value : impl.testOrFalse(perm);
    }

    @Override
    public String toString() {
      return "PermissionBackendCondition.ForProject(" + perm + ")";
    }

    @Override
    public int hashCode() {
      return Objects.hash(perm, impl.resourcePath(), hashForUser(user));
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof ForProject)) {
        return false;
      }
      ForProject other = (ForProject) obj;
      return Objects.equals(perm, other.perm)
          && Objects.equals(impl.resourcePath(), other.impl.resourcePath())
          && usersAreEqual(user, other.user);
    }
  }

  public static class ForRef extends PermissionBackendCondition {
    private final PermissionBackend.ForRef impl;
    private final RefPermission perm;
    private final CurrentUser user;

    public ForRef(PermissionBackend.ForRef impl, RefPermission perm, CurrentUser user) {
      this.impl = impl;
      this.perm = perm;
      this.user = user;
    }

    public PermissionBackend.ForRef ref() {
      return impl;
    }

    public RefPermission permission() {
      return perm;
    }

    @Override
    public boolean value() {
      return value != null ? value : impl.testOrFalse(perm);
    }

    @Override
    public String toString() {
      return "PermissionBackendCondition.ForRef(" + perm + ")";
    }

    @Override
    public int hashCode() {
      return Objects.hash(perm, impl.resourcePath(), hashForUser(user));
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof ForRef)) {
        return false;
      }
      ForRef other = (ForRef) obj;
      return Objects.equals(perm, other.perm)
          && Objects.equals(impl.resourcePath(), other.impl.resourcePath())
          && usersAreEqual(user, other.user);
    }
  }

  public static class ForChange extends PermissionBackendCondition {
    private final PermissionBackend.ForChange impl;
    private final ChangePermissionOrLabel perm;
    private final CurrentUser user;

    public ForChange(
        PermissionBackend.ForChange impl, ChangePermissionOrLabel perm, CurrentUser user) {
      this.impl = impl;
      this.perm = perm;
      this.user = user;
    }

    public PermissionBackend.ForChange change() {
      return impl;
    }

    public ChangePermissionOrLabel permission() {
      return perm;
    }

    @Override
    public boolean value() {
      return value != null ? value : impl.testOrFalse(perm);
    }

    @Override
    public String toString() {
      return "PermissionBackendCondition.ForChange(" + perm + ")";
    }

    @Override
    public int hashCode() {
      return Objects.hash(perm, impl.resourcePath(), hashForUser(user));
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof ForChange)) {
        return false;
      }
      ForChange other = (ForChange) obj;
      return Objects.equals(perm, other.perm)
          && Objects.equals(impl.resourcePath(), other.impl.resourcePath())
          && usersAreEqual(user, other.user);
    }
  }

  private static int hashForUser(CurrentUser user) {
    if (!user.isIdentifiedUser()) {
      return 0;
    }
    return user.getAccountId().get();
  }

  private static boolean usersAreEqual(CurrentUser user1, CurrentUser user2) {
    if (user1.isIdentifiedUser() && user2.isIdentifiedUser()) {
      return user1.getAccountId().equals(user2.getAccountId());
    }
    return false;
  }
}
