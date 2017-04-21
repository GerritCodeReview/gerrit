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
import static com.google.gerrit.common.data.PermissionRule.RANGE_REG;

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
    this.rolePermissions = new ArrayList<>(2);
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
    private String permissionName;

    private RolePermission(String rolePermission) throws IllegalArgumentException {
      int ri = rolePermission.indexOf(' ');
      if (ri > 0) {
        permissionName = rolePermission.substring(0, ri);
        rangeString = rolePermission.substring(ri + 1);
        if (!rangeString.matches(RANGE_REG)) {
          throw new IllegalArgumentException("Invalid range: %s" + rangeString);
        }
      } else {
        permissionName = rolePermission;
      }
    }

    public String getPermissionName() {
      return permissionName;
    }

    public String getRangeString() {
      return rangeString;
    }

    public boolean hasRange() {
      return Permission.hasRange(permissionName);
    }
  }
}
