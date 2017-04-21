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

package com.google.gerrit.common.data;

import static com.google.gerrit.common.data.PermissionRule.RANGE_REGEX;

import com.google.gerrit.common.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RoleSection {
  private final String name;
  private final List<RolePermission> rolePermissions;

  public static RolePermission toRolePermission(String rolePermission) {
    return new RolePermission(rolePermission);
  }

  public RoleSection(String name) {
    this.name = name;
    this.rolePermissions = new ArrayList<>();
  }

  public void addRolePermission(RolePermission rp) {
    rolePermissions.add(rp);
  }

  public Collection<RolePermission> getRolePermissions() {
    return rolePermissions;
  }

  public String getName() {
    return name;
  }

  public static class RolePermission {
    private final String rangeString;
    private final boolean force;
    private final String permissionName;

    private RolePermission(String rolePermission) throws IllegalArgumentException {
      String[] parts = rolePermission.trim().split(" ");
      if (parts.length > 2) {
        throw new IllegalArgumentException("Invalid role permission: \"" + rolePermission + "\"");
      }
      if (Permission.isPermission(parts[0])) {
        permissionName = parts[0];
      } else {
        throw new IllegalArgumentException("Invalid permission: \"" + parts[0] + "\"");
      }
      if (parts.length == 2) {
        if (parts[1].equals("+force")) {
          force = true;
          rangeString = null;
        } else {
          if (Permission.hasRange(permissionName) && parts[1].matches(RANGE_REGEX)) {
            rangeString = parts[1];
            force = false;
          } else {
            throw new IllegalArgumentException("Invalid option: \"" + parts[1] + "\"");
          }
        }
      } else {
        rangeString = null;
        force = false;
      }
    }

    public String getPermissionName() {
      return permissionName;
    }

    public boolean getForce() {
      return force;
    }

    @Nullable
    public String getRangeString() {
      return rangeString;
    }

    public boolean hasRange() {
      return Permission.hasRange(permissionName);
    }
  }
}
