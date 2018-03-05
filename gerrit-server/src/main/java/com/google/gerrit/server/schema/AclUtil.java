// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.server.git.ProjectConfig;

public class AclUtil {
  public static void grant(
      ProjectConfig config, AccessSection section, String permission, GroupReference... groupList) {
    grant(config, section, permission, false, groupList);
  }

  public static void grant(
      ProjectConfig config,
      AccessSection section,
      String permission,
      boolean force,
      GroupReference... groupList) {
    grant(config, section, permission, force, null, groupList);
  }

  public static void grant(
      ProjectConfig config,
      AccessSection section,
      String permission,
      boolean force,
      Boolean exclusive,
      GroupReference... groupList) {
    Permission p = section.getPermission(permission, true);
    if (exclusive != null) {
      p.setExclusiveGroup(exclusive);
    }
    for (GroupReference group : groupList) {
      if (group != null) {
        PermissionRule r = rule(config, group);
        r.setForce(force);
        p.add(r);
      }
    }
  }

  public static void grant(
      ProjectConfig config,
      AccessSection section,
      LabelType type,
      int min,
      int max,
      GroupReference... groupList) {
    String name = Permission.LABEL + type.getName();
    Permission p = section.getPermission(name, true);
    for (GroupReference group : groupList) {
      if (group != null) {
        PermissionRule r = rule(config, group);
        r.setRange(min, max);
        p.add(r);
      }
    }
  }

  public static PermissionRule rule(ProjectConfig config, GroupReference group) {
    return new PermissionRule(config.resolve(group));
  }
}
