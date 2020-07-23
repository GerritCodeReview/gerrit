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

import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.PermissionRule;
import com.google.gerrit.server.project.ProjectConfig;

/**
 * Contains functions to modify permissions. For all these functions, any of the groups may be null
 * in which case it is ignored.
 */
public class AclUtil {
  public static void grant(
      ProjectConfig config,
      AccessSection.Builder section,
      String permission,
      GroupReference... groupList) {
    grant(config, section, permission, false, groupList);
  }

  public static void grant(
      ProjectConfig config,
      AccessSection.Builder section,
      String permission,
      boolean force,
      GroupReference... groupList) {
    grant(config, section, permission, force, null, groupList);
  }

  public static void grant(
      ProjectConfig config,
      AccessSection.Builder section,
      String permission,
      boolean force,
      Boolean exclusive,
      GroupReference... groupList) {
    Permission.Builder p = section.upsertPermission(permission);
    if (exclusive != null) {
      p.setExclusiveGroup(exclusive);
    }
    for (GroupReference group : groupList) {
      if (group != null) {
        p.add(rule(config, group).setForce(force));
      }
    }
  }

  public static void block(
      ProjectConfig config,
      AccessSection.Builder section,
      String permission,
      GroupReference... groupList) {
    Permission.Builder p = section.upsertPermission(permission);
    for (GroupReference group : groupList) {
      if (group != null) {
        p.add(rule(config, group).setBlock());
      }
    }
  }

  public static void grant(
      ProjectConfig config,
      AccessSection.Builder section,
      LabelType type,
      int min,
      int max,
      GroupReference... groupList) {
    grant(config, section, type, min, max, false, groupList);
  }

  public static void grant(
      ProjectConfig config,
      AccessSection.Builder section,
      LabelType type,
      int min,
      int max,
      boolean exclusive,
      GroupReference... groupList) {
    String name = Permission.LABEL + type.getName();
    Permission.Builder p = section.upsertPermission(name);
    p.setExclusiveGroup(exclusive);
    for (GroupReference group : groupList) {
      if (group != null) {
        p.add(rule(config, group).setRange(min, max));
      }
    }
  }

  public static PermissionRule.Builder rule(ProjectConfig config, GroupReference group) {
    return PermissionRule.builder(config.resolve(group));
  }

  public static void remove(
      ProjectConfig config,
      AccessSection.Builder section,
      String permission,
      GroupReference... groupList) {
    Permission.Builder p = section.upsertPermission(permission);
    for (GroupReference group : groupList) {
      if (group != null) {
        p.remove(rule(config, group).build());
      }
    }
  }
}
