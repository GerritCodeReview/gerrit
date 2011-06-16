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
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Access control management for server-wide capabilities. */
public class CapabilityControl {
  public static class Factory {
    private final Project.NameKey wildProject;
    private final ProjectCache projectCache;
    private final Provider<CurrentUser> user;

    @Inject
    Factory(@WildProjectName Project.NameKey wp, ProjectCache pc,
        Provider<CurrentUser> cu) {
      wildProject = wp;
      projectCache = pc;
      user = cu;
    }

    public CapabilityControl controlFor() throws NoSuchProjectException {
      final ProjectState p = projectCache.get(wildProject);
      if (p == null) {
        throw new NoSuchProjectException(wildProject);
      }
      return new CapabilityControl(p, user.get());
    }
  }

  private final ProjectState state;
  private final CurrentUser user;
  private Map<String, List<PermissionRule>> permissions;

  private CapabilityControl(ProjectState p, CurrentUser currentUser) {
    state = p;
    user = currentUser;
  }

  /** Identity of the user the control will compute for. */
  public CurrentUser getCurrentUser() {
    return user;
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
      permissions = new HashMap<String, List<PermissionRule>>();
      AccessSection section = state.getConfig()
        .getAccessSection(AccessSection.GLOBAL_CAPABILITIES);
      if (section != null) {
        for (Permission permission : section.getPermissions()) {
          for (PermissionRule rule : permission.getRules()) {
            if (matchGroup(rule.getGroup().getUUID())) {
              if (!rule.getDeny()) {
                List<PermissionRule> r = permissions.get(permission.getName());
                if (r == null) {
                  r = new ArrayList<PermissionRule>(2);
                  permissions.put(permission.getName(), r);
                }
                r.add(rule);
              }
            }
          }
        }
      }
    }
    return permissions;
  }

  private boolean matchGroup(AccountGroup.UUID uuid) {
    Set<AccountGroup.UUID> userGroups = getCurrentUser().getEffectiveGroups();
    return userGroups.contains(uuid);
  }
}
