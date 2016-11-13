// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.git.ProjectConfig;
import java.util.Arrays;

public class Util {
  public static final AccountGroup.UUID ADMIN = new AccountGroup.UUID("test.admin");
  public static final AccountGroup.UUID DEVS = new AccountGroup.UUID("test.devs");

  public static final LabelType codeReview() {
    return category(
        "Code-Review",
        value(2, "Looks good to me, approved"),
        value(1, "Looks good to me, but someone else must approve"),
        value(0, "No score"),
        value(-1, "I would prefer this is not merged as is"),
        value(-2, "This shall not be merged"));
  }

  public static final LabelType verified() {
    return category("Verified", value(1, "Verified"), value(0, "No score"), value(-1, "Fails"));
  }

  public static final LabelType patchSetLock() {
    LabelType label =
        category("Patch-Set-Lock", value(1, "Patch Set Locked"), value(0, "Patch Set Unlocked"));
    label.setFunctionName("PatchSetLock");
    return label;
  }

  public static LabelValue value(int value, String text) {
    return new LabelValue((short) value, text);
  }

  public static LabelType category(String name, LabelValue... values) {
    return new LabelType(name, Arrays.asList(values));
  }

  public static PermissionRule newRule(ProjectConfig project, AccountGroup.UUID groupUUID) {
    GroupReference group = new GroupReference(groupUUID, groupUUID.get());
    group = project.resolve(group);

    return new PermissionRule(group);
  }

  public static PermissionRule allow(
      ProjectConfig project,
      String permissionName,
      int min,
      int max,
      AccountGroup.UUID group,
      String ref) {
    PermissionRule rule = newRule(project, group);
    rule.setMin(min);
    rule.setMax(max);
    return grant(project, permissionName, rule, ref);
  }

  public static PermissionRule block(
      ProjectConfig project,
      String permissionName,
      int min,
      int max,
      AccountGroup.UUID group,
      String ref) {
    PermissionRule rule = newRule(project, group);
    rule.setMin(min);
    rule.setMax(max);
    PermissionRule r = grant(project, permissionName, rule, ref);
    r.setBlock();
    return r;
  }

  public static PermissionRule allow(
      ProjectConfig project, String permissionName, AccountGroup.UUID group, String ref) {
    return grant(project, permissionName, newRule(project, group), ref);
  }

  public static PermissionRule allow(
      ProjectConfig project,
      String permissionName,
      AccountGroup.UUID group,
      String ref,
      boolean exclusive) {
    return grant(project, permissionName, newRule(project, group), ref, exclusive);
  }

  public static PermissionRule allow(
      ProjectConfig project, String capabilityName, AccountGroup.UUID group) {
    PermissionRule rule = newRule(project, group);
    project
        .getAccessSection(AccessSection.GLOBAL_CAPABILITIES, true)
        .getPermission(capabilityName, true)
        .add(rule);
    if (GlobalCapability.hasRange(capabilityName)) {
      PermissionRange.WithDefaults range = GlobalCapability.getRange(capabilityName);
      if (range != null) {
        rule.setRange(range.getDefaultMin(), range.getDefaultMax());
      }
    }
    return rule;
  }

  public static PermissionRule remove(
      ProjectConfig project, String capabilityName, AccountGroup.UUID group) {
    PermissionRule rule = newRule(project, group);
    project
        .getAccessSection(AccessSection.GLOBAL_CAPABILITIES, true)
        .getPermission(capabilityName, true)
        .remove(rule);
    return rule;
  }

  public static PermissionRule remove(
      ProjectConfig project, String permissionName, AccountGroup.UUID group, String ref) {
    PermissionRule rule = newRule(project, group);
    project.getAccessSection(ref, true).getPermission(permissionName, true).remove(rule);
    return rule;
  }

  public static PermissionRule block(
      ProjectConfig project, String capabilityName, AccountGroup.UUID group) {
    PermissionRule rule = newRule(project, group);
    project
        .getAccessSection(AccessSection.GLOBAL_CAPABILITIES, true)
        .getPermission(capabilityName, true)
        .add(rule);
    return rule;
  }

  public static PermissionRule block(
      ProjectConfig project, String permissionName, AccountGroup.UUID group, String ref) {
    PermissionRule r = grant(project, permissionName, newRule(project, group), ref);
    r.setBlock();
    return r;
  }

  public static PermissionRule blockLabel(
      ProjectConfig project, String labelName, AccountGroup.UUID group, String ref) {
    PermissionRule r = grant(project, Permission.LABEL + labelName, newRule(project, group), ref);
    r.setBlock();
    r.setRange(-1, 1);
    return r;
  }

  public static PermissionRule deny(
      ProjectConfig project, String permissionName, AccountGroup.UUID group, String ref) {
    PermissionRule r = grant(project, permissionName, newRule(project, group), ref);
    r.setDeny();
    return r;
  }

  public static void doNotInherit(ProjectConfig project, String permissionName, String ref) {
    project
        .getAccessSection(ref, true) //
        .getPermission(permissionName, true) //
        .setExclusiveGroup(true);
  }

  private static PermissionRule grant(
      ProjectConfig project, String permissionName, PermissionRule rule, String ref) {
    return grant(project, permissionName, rule, ref, false);
  }

  private static PermissionRule grant(
      ProjectConfig project,
      String permissionName,
      PermissionRule rule,
      String ref,
      boolean exclusive) {
    Permission permission = project.getAccessSection(ref, true).getPermission(permissionName, true);
    if (exclusive) {
      permission.setExclusiveGroup(exclusive);
    }
    permission.add(rule);
    return rule;
  }

  private Util() {}
}
