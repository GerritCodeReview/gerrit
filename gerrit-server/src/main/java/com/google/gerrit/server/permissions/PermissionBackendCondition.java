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

/** {@link BooleanCondition} to evaluate a permission. */
public abstract class PermissionBackendCondition
    extends PrivateInternals_BooleanCondition.SubclassOnlyInCoreServer {

  public static class WithUser extends PermissionBackendCondition {
    private final PermissionBackend.WithUser impl;
    private final GlobalOrPluginPermission perm;

    WithUser(PermissionBackend.WithUser impl, GlobalOrPluginPermission perm) {
      this.impl = impl;
      this.perm = perm;
    }

    public PermissionBackend.WithUser withUser() {
      return impl;
    }

    public GlobalOrPluginPermission permission() {
      return perm;
    }

    @Override
    public boolean value() {
      return impl.testOrFalse(perm);
    }
  }

  public static class ForProject extends PermissionBackendCondition {
    private final PermissionBackend.ForProject impl;
    private final ProjectPermission perm;

    ForProject(PermissionBackend.ForProject impl, ProjectPermission perm) {
      this.impl = impl;
      this.perm = perm;
    }

    public PermissionBackend.ForProject project() {
      return impl;
    }

    public ProjectPermission permission() {
      return perm;
    }

    @Override
    public boolean value() {
      return impl.testOrFalse(perm);
    }
  }

  public static class ForRef extends PermissionBackendCondition {
    private final PermissionBackend.ForRef impl;
    private final RefPermission perm;

    ForRef(PermissionBackend.ForRef impl, RefPermission perm) {
      this.impl = impl;
      this.perm = perm;
    }

    public PermissionBackend.ForRef ref() {
      return impl;
    }

    public RefPermission permission() {
      return perm;
    }

    @Override
    public boolean value() {
      return impl.testOrFalse(perm);
    }
  }

  public static class ForChange extends PermissionBackendCondition {
    private final PermissionBackend.ForChange impl;
    private final ChangePermissionOrLabel perm;

    ForChange(PermissionBackend.ForChange impl, ChangePermissionOrLabel perm) {
      this.impl = impl;
      this.perm = perm;
    }

    public PermissionBackend.ForChange change() {
      return impl;
    }

    public ChangePermissionOrLabel permission() {
      return perm;
    }

    @Override
    public boolean value() {
      return impl.testOrFalse(perm);
    }
  }
}
