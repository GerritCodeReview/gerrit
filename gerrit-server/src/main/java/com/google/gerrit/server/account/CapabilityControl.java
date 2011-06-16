// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.account;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.WildProjectName;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Access control management for server-wide capabilities. */
public class CapabilityControl {
  public static interface Factory {
    public CapabilityControl create(CurrentUser user);
  }

  private final ProjectState state;
  private final CurrentUser user;
  private Map<String, List<PermissionRule>> permissions;

  @Inject
  CapabilityControl(
      @WildProjectName Project.NameKey wp,
      ProjectCache projectCache,
      @Assisted CurrentUser currentUser) throws NoSuchProjectException {
    state = projectCache.get(wp);
    if (state == null) {
      throw new NoSuchProjectException(wp);
    }
    user = currentUser;
  }

  /** Identity of the user the control will compute for. */
  public CurrentUser getCurrentUser() {
    return user;
  }

  /** @return true if the user can create an account for another user. */
  public boolean canCreateAccount() {
    return canPerform(GlobalCapability.CREATE_ACCOUNT) || user.isAdministrator();
  }

  /** @return true if the user can create a group. */
  public boolean canCreateGroup() {
    return canPerform(GlobalCapability.CREATE_GROUP) || user.isAdministrator();
  }

  /** @return true if the user can kill any running task. */
  public boolean canKillTask() {
    return canPerform(GlobalCapability.KILL_TASK) || user.isAdministrator();
  }

  /** @return true if the user can view the server caches. */
  public boolean canViewCaches() {
    return canPerform(GlobalCapability.VIEW_CACHES) || user.isAdministrator();
  }

  /** @return true if the user can flush the server's caches. */
  public boolean canFlushCaches() {
    return canPerform(GlobalCapability.FLUSH_CACHES) || user.isAdministrator();
  }

  /** @return true if the user can view open connections. */
  public boolean canViewConnections() {
    return canPerform(GlobalCapability.VIEW_CONNECTIONS) || user.isAdministrator();
  }

  /** @return true if the user can view the entire queue. */
  public boolean canViewQueue() {
    return canPerform(GlobalCapability.VIEW_QUEUE) || user.isAdministrator();
  }

  /** @return true if the user can force replication to any configured destination. */
  public boolean canStartReplication() {
    return canPerform(GlobalCapability.START_REPLICATION) || user.isAdministrator();
  }

  /** True if the user has this permission. Works only for non labels. */
  public boolean canPerform(String permissionName) {
    return !access(permissionName).isEmpty();
  }

  /** The range of permitted values associated with a label permission. */
  public PermissionRange getRange(String permission) {
    if (GlobalCapability.hasRange(permission)) {
      return toRange(permission, access(permission));
    }
    return null;
  }

  private static PermissionRange toRange(String permissionName,
      List<PermissionRule> ruleList) {
    int min = 0;
    int max = 0;
    for (PermissionRule rule : ruleList) {
      min = Math.min(min, rule.getMin());
      max = Math.max(max, rule.getMax());
    }
    return new PermissionRange(permissionName, min, max);
  }

  /** Rules for the given permission, or the empty list. */
  private List<PermissionRule> access(String permissionName) {
    List<PermissionRule> r = permissions().get(permissionName);
    return r != null ? r : Collections.<PermissionRule> emptyList();
  }

  /** All rules that pertain to this user. */
  private Map<String, List<PermissionRule>> permissions() {
    if (permissions == null) {
      permissions = indexPermissions();
    }
    return permissions;
  }

  private Map<String, List<PermissionRule>> indexPermissions() {
    Map<String, List<PermissionRule>> res =
        new HashMap<String, List<PermissionRule>>();

    AccessSection section = state.getConfig()
      .getAccessSection(AccessSection.GLOBAL_CAPABILITIES);
    if (section == null) {
      section = new AccessSection(AccessSection.GLOBAL_CAPABILITIES);
    }

    for (Permission permission : section.getPermissions()) {
      for (PermissionRule rule : permission.getRules()) {
        if (matchGroup(rule.getGroup().getUUID())) {
          if (!rule.getDeny()) {
            List<PermissionRule> r = res.get(permission.getName());
            if (r == null) {
              r = new ArrayList<PermissionRule>(2);
              res.put(permission.getName(), r);
            }
            r.add(rule);
          }
        }
      }
    }

    configureDefaults(res, section);
    return res;
  }

  private boolean matchGroup(AccountGroup.UUID uuid) {
    Set<AccountGroup.UUID> userGroups = getCurrentUser().getEffectiveGroups();
    return userGroups.contains(uuid);
  }

  private static final GroupReference anonymous = new GroupReference(
      AccountGroup.ANONYMOUS_USERS,
      "Anonymous Users");

  private static void configureDefaults(
      Map<String, List<PermissionRule>> res,
      AccessSection section) {
    configureDefault(res, section, GlobalCapability.QUERY_LIMIT, anonymous);
  }

  private static void configureDefault(Map<String, List<PermissionRule>> res,
      AccessSection section, String capName, GroupReference group) {
    if (section.getPermission(capName) == null) {
      PermissionRange.WithDefaults range = GlobalCapability.getRange(capName);
      if (range != null) {
        PermissionRule rule = new PermissionRule(group);
        rule.setRange(range.getDefaultMin(), range.getDefaultMax());
        res.put(capName, Collections.singletonList(rule));
      }
    }
  }
}
