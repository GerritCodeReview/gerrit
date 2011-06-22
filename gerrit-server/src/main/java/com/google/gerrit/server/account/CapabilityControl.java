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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PeerDaemonUser;
import com.google.gerrit.server.git.QueueProvider;
import com.google.gerrit.server.project.ProjectCache;
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

  private final CapabilityCollection capabilities;
  private final CurrentUser user;
  private final Map<String, List<PermissionRule>> effective;

  private Boolean canAdministrateServer;

  @Inject
  CapabilityControl(ProjectCache projectCache, @Assisted CurrentUser currentUser) {
    capabilities = projectCache.getAllProjects().getCapabilityCollection();
    user = currentUser;
    effective = new HashMap<String, List<PermissionRule>>();
  }

  /** Identity of the user the control will compute for. */
  public CurrentUser getCurrentUser() {
    return user;
  }

  /** @return true if the user can administer this server. */
  public boolean canAdministrateServer() {
    if (canAdministrateServer == null) {
      canAdministrateServer = user instanceof PeerDaemonUser
          || matchAny(capabilities.administrateServer);
    }
    return canAdministrateServer;
  }

  /** @return true if the user can create an account for another user. */
  public boolean canCreateAccount() {
    return canPerform(GlobalCapability.CREATE_ACCOUNT)
      || canAdministrateServer();
  }

  /** @return true if the user can create a group. */
  public boolean canCreateGroup() {
    return canPerform(GlobalCapability.CREATE_GROUP)
      || canAdministrateServer();
  }

  /** @return true if the user can create a group. */
  public boolean canCreateProject() {
    return canPerform(GlobalCapability.CREATE_PROJECT)
      || canAdministrateServer();
  }

  /** @return true if the user can kill any running task. */
  public boolean canKillTask() {
    return canPerform(GlobalCapability.KILL_TASK)
      || canAdministrateServer();
  }

  /** @return true if the user can view the server caches. */
  public boolean canViewCaches() {
    return canPerform(GlobalCapability.VIEW_CACHES)
      || canAdministrateServer();
  }

  /** @return true if the user can flush the server's caches. */
  public boolean canFlushCaches() {
    return canPerform(GlobalCapability.FLUSH_CACHES)
      || canAdministrateServer();
  }

  /** @return true if the user can view open connections. */
  public boolean canViewConnections() {
    return canPerform(GlobalCapability.VIEW_CONNECTIONS)
        || canAdministrateServer();
  }

  /** @return true if the user can view the entire queue. */
  public boolean canViewQueue() {
    return canPerform(GlobalCapability.VIEW_QUEUE)
      || canAdministrateServer();
  }

  /** @return true if the user can force replication to any configured destination. */
  public boolean canStartReplication() {
    return canPerform(GlobalCapability.START_REPLICATION)
        || canAdministrateServer();
  }

  /** @return which priority queue the user's tasks should be submitted to. */
  public QueueProvider.QueueType getQueueType() {
    // If a non-generic group (that is not Anonymous Users or Registered Users)
    // grants us INTERACTIVE permission, use the INTERACTIVE queue even if
    // BATCH was otherwise granted. This allows site administrators to grant
    // INTERACTIVE to Registered Users, and BATCH to 'CI Servers' and have
    // the 'CI Servers' actually use the BATCH queue while everyone else gets
    // to use the INTERACTIVE queue without additional grants.
    //
    Set<AccountGroup.UUID> groups = user.getEffectiveGroups();
    boolean batch = false;
    for (PermissionRule r : capabilities.priority) {
      if (match(groups, r)) {
        switch (r.getAction()) {
          case INTERACTIVE:
            if (!isGenericGroup(r.getGroup())) {
              return QueueProvider.QueueType.INTERACTIVE;
            }
            break;

          case BATCH:
            batch = true;
            break;
        }
      }
    }

    if (batch) {
      // If any of our groups matched to the BATCH queue, use it.
      return QueueProvider.QueueType.BATCH;
    } else {
      return QueueProvider.QueueType.INTERACTIVE;
    }
  }

  private static boolean isGenericGroup(GroupReference group) {
    return AccountGroup.ANONYMOUS_USERS.equals(group.getUUID())
        || AccountGroup.REGISTERED_USERS.equals(group.getUUID());
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
    List<PermissionRule> rules = effective.get(permissionName);
    if (rules != null) {
      return rules;
    }

    rules = capabilities.getPermission(permissionName);

    if (rules.isEmpty()) {
      effective.put(permissionName, rules);
      return rules;
    }

    Set<AccountGroup.UUID> groups = user.getEffectiveGroups();
    if (rules.size() == 1) {
      if (!match(groups, rules.get(0))) {
        rules = Collections.emptyList();
      }
      effective.put(permissionName, rules);
      return rules;
    }

    List<PermissionRule> mine = new ArrayList<PermissionRule>(rules.size());
    for (PermissionRule rule : rules) {
      if (match(groups, rule)) {
        mine.add(rule);
      }
    }

    if (mine.isEmpty()) {
      mine = Collections.emptyList();
    }
    effective.put(permissionName, mine);
    return mine;
  }

  private boolean matchAny(List<PermissionRule> rules) {
    Set<AccountGroup.UUID> groups = user.getEffectiveGroups();
    for (PermissionRule rule : rules) {
      if (match(groups, rule)) {
        return true;
      }
    }
    return false;
  }

  private static boolean match(Set<AccountGroup.UUID> groups,
      PermissionRule rule) {
    return groups.contains(rule.getGroup().getUUID());
  }
}
