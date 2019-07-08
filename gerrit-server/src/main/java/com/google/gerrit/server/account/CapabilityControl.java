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

import static com.google.common.base.Predicates.not;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.data.PermissionRule.Action;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PeerDaemonUser;
import com.google.gerrit.server.git.QueueProvider;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Access control management for server-wide capabilities. */
public class CapabilityControl {
  public interface Factory {
    CapabilityControl create(CurrentUser user);
  }

  private final CapabilityCollection capabilities;
  private final CurrentUser user;
  private final Map<String, List<PermissionRule>> effective;

  private Boolean canAdministrateServer;
  private Boolean canEmailReviewers;

  @Inject
  CapabilityControl(ProjectCache projectCache, @Assisted CurrentUser currentUser) {
    capabilities = projectCache.getAllProjects().getCapabilityCollection();
    user = currentUser;
    effective = new HashMap<>();
  }

  /** Identity of the user the control will compute for. */
  public CurrentUser getUser() {
    return user;
  }

  /** @return true if the user can administer this server. */
  public boolean canAdministrateServer() {
    if (canAdministrateServer == null) {
      if (user.getRealUser() != user) {
        canAdministrateServer = false;
      } else {
        canAdministrateServer =
            user instanceof PeerDaemonUser
                || matchAny(capabilities.administrateServer, ALLOWED_RULE);
      }
    }
    return canAdministrateServer;
  }

  /** @return true if the user can create an account for another user. */
  public boolean canCreateAccount() {
    return canPerform(GlobalCapability.CREATE_ACCOUNT) || canAdministrateServer();
  }

  /** @return true if the user can create a group. */
  public boolean canCreateGroup() {
    return canPerform(GlobalCapability.CREATE_GROUP) || canAdministrateServer();
  }

  /** @return true if the user can create a project. */
  public boolean canCreateProject() {
    return canPerform(GlobalCapability.CREATE_PROJECT) || canAdministrateServer();
  }

  /** @return true if the user can email reviewers. */
  public boolean canEmailReviewers() {
    if (canEmailReviewers == null) {
      canEmailReviewers =
          matchAny(capabilities.emailReviewers, ALLOWED_RULE)
              || !matchAny(capabilities.emailReviewers, not(ALLOWED_RULE));
    }
    return canEmailReviewers;
  }

  /** @return true if the user can kill any running task. */
  public boolean canKillTask() {
    return canPerform(GlobalCapability.KILL_TASK) || canMaintainServer();
  }

  /** @return true if the user can modify an account for another user. */
  public boolean canModifyAccount() {
    return canPerform(GlobalCapability.MODIFY_ACCOUNT) || canAdministrateServer();
  }

  /** @return true if the user can view all accounts. */
  public boolean canViewAllAccounts() {
    return canPerform(GlobalCapability.VIEW_ALL_ACCOUNTS) || canAdministrateServer();
  }

  /** @return true if the user can view the server caches. */
  public boolean canViewCaches() {
    return canPerform(GlobalCapability.VIEW_CACHES) || canMaintainServer();
  }

  /** @return true if the user can flush the server's caches. */
  public boolean canFlushCaches() {
    return canPerform(GlobalCapability.FLUSH_CACHES) || canMaintainServer();
  }

  /** @return true if the user can perform basic server maintenance. */
  public boolean canMaintainServer() {
    return canPerform(GlobalCapability.MAINTAIN_SERVER) || canAdministrateServer();
  }

  /** @return true if the user can view open connections. */
  public boolean canViewConnections() {
    return canPerform(GlobalCapability.VIEW_CONNECTIONS) || canAdministrateServer();
  }

  /** @return true if the user can view the installed plugins. */
  public boolean canViewPlugins() {
    return canPerform(GlobalCapability.VIEW_PLUGINS) || canAdministrateServer();
  }

  /** @return true if the user can view the entire queue. */
  public boolean canViewQueue() {
    return canPerform(GlobalCapability.VIEW_QUEUE) || canMaintainServer();
  }

  /** @return true if the user can access the database (with gsql). */
  public boolean canAccessDatabase() {
    return canPerform(GlobalCapability.ACCESS_DATABASE);
  }

  /** @return true if the user can stream Gerrit events. */
  public boolean canStreamEvents() {
    return canPerform(GlobalCapability.STREAM_EVENTS) || canAdministrateServer();
  }

  /** @return true if the user can run the Git garbage collection. */
  public boolean canRunGC() {
    return canPerform(GlobalCapability.RUN_GC) || canMaintainServer();
  }

  /** @return true if the user can impersonate another user. */
  public boolean canRunAs() {
    return canPerform(GlobalCapability.RUN_AS);
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
    GroupMembership groups = user.getEffectiveGroups();
    boolean batch = false;
    for (PermissionRule r : capabilities.priority) {
      if (match(groups, r)) {
        switch (r.getAction()) {
          case INTERACTIVE:
            if (!SystemGroupBackend.isAnonymousOrRegistered(r.getGroup())) {
              return QueueProvider.QueueType.INTERACTIVE;
            }
            break;

          case BATCH:
            batch = true;
            break;

          case ALLOW:
          case BLOCK:
          case DENY:
            break;
        }
      }
    }

    if (batch) {
      // If any of our groups matched to the BATCH queue, use it.
      return QueueProvider.QueueType.BATCH;
    }
    return QueueProvider.QueueType.INTERACTIVE;
  }

  /** True if the user has this permission. Works only for non labels. */
  public boolean canPerform(String permissionName) {
    if (GlobalCapability.ADMINISTRATE_SERVER.equals(permissionName)) {
      return canAdministrateServer();
    }
    return !access(permissionName).isEmpty();
  }

  /** The range of permitted values associated with a label permission. */
  public PermissionRange getRange(String permission) {
    if (GlobalCapability.hasRange(permission)) {
      return toRange(permission, access(permission));
    }
    return null;
  }

  private static PermissionRange toRange(String permissionName, List<PermissionRule> ruleList) {
    int min = 0;
    int max = 0;
    if (ruleList.isEmpty()) {
      PermissionRange.WithDefaults defaultRange = GlobalCapability.getRange(permissionName);
      if (defaultRange != null) {
        min = defaultRange.getDefaultMin();
        max = defaultRange.getDefaultMax();
      }
    } else {
      for (PermissionRule rule : ruleList) {
        min = Math.min(min, rule.getMin());
        max = Math.max(max, rule.getMax());
      }
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
    GroupMembership groups = user.getEffectiveGroups();

    List<PermissionRule> mine = new ArrayList<>(rules.size());
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

  private static final Predicate<PermissionRule> ALLOWED_RULE = r -> r.getAction() == Action.ALLOW;

  private boolean matchAny(Collection<PermissionRule> rules, Predicate<PermissionRule> predicate) {
    return user.getEffectiveGroups()
        .containsAnyOf(
            FluentIterable.from(rules).filter(predicate).transform(r -> r.getGroup().getUUID()));
  }

  private static boolean match(GroupMembership groups, PermissionRule rule) {
    return groups.contains(rule.getGroup().getUUID());
  }
}
