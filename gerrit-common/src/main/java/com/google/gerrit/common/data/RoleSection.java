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
import static com.google.gerrit.common.data.PermissionRule.RANGE_PATTERN;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RoleSection {
  private String name;
  private List<RolePermission> rolePermissions;

  public static RolePermission toRolePermission(String rolePermission) {
    return new RolePermission(rolePermission);
  }

  public RoleSection(String name) {
    this.name = name;
    this.rolePermissions = new ArrayList<>(4);
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
    private String rangeString;
    private boolean force;
    private String permissionName;

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
      // A RolePermission can have either force or range.
      if (parts.length == 2) {
        if (parts[1].equals("+force")) {
          force = true;
        } else {
          if (Permission.hasRange(permissionName) && RANGE_PATTERN.matcher(parts[1]).matches()) {
            rangeString = parts[1];
          } else {
            throw new IllegalArgumentException("Invalid option: \"" + parts[1] + "\"");
          }
        }
      }
    }

    public String getPermissionName() {
      return permissionName;
    }

    public boolean getForce() {
      return force;
    }

    public String getRangeString() {
      return rangeString;
    }

    public boolean hasRange() {
      return Permission.hasRange(permissionName);
    }
  }
}
