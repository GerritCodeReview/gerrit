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
import com.google.gerrit.extensions.api.access.GlobalOrPluginPermission;
import com.google.gerrit.extensions.api.access.PluginPermission;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PeerDaemonUser;
import com.google.gerrit.server.git.QueueProvider;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Access control management for server-wide capabilities. */
public class CapabilityControl {
  private static final CurrentUser.PropertyKey<CapabilityControl> SELF =
      CurrentUser.PropertyKey.create();

  @Singleton
  public static class Factory {
    private final ProjectCache projectCache;

    @Inject
    Factory(ProjectCache projectCache) {
      this.projectCache = projectCache;
    }

    public CapabilityControl create(CurrentUser user) {
      CapabilityControl ctl = user.get(SELF);
      if (ctl == null) {
        ctl = new CapabilityControl(projectCache, user);
        user.put(SELF, ctl);
      }
      return ctl;
    }
  }

  private final CapabilityCollection capabilities;
  private final CurrentUser user;
  private final Map<String, List<PermissionRule>> effective;
  private Boolean canAdministrateServer;

  private CapabilityControl(ProjectCache projectCache, CurrentUser currentUser) {
    capabilities = projectCache.getAllProjects().getCapabilityCollection();
    user = currentUser;
    effective = new HashMap<>();
  }

  private boolean isAdmin() {
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

  /** @return true if the user can email reviewers. */
  private boolean canEmailReviewers() {
    return matchAny(capabilities.emailReviewers, ALLOWED_RULE)
        || !matchAny(capabilities.emailReviewers, not(ALLOWED_RULE));
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

  /** @return true if the user has this permission. */
  private boolean canPerform(String permissionName) {
    return !access(permissionName).isEmpty();
  }

  /** @return true if the user has a permission rule specifying the range. */
  public boolean hasExplicitRange(String permission) {
    return GlobalCapability.hasRange(permission) && !access(permission).isEmpty();
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

  /** Do not use unless inside DefaultPermissionBackend. */
  public boolean doCanForDefaultPermissionBackend(GlobalOrPluginPermission perm)
      throws PermissionBackendException {
    if (perm instanceof GlobalPermission) {
      return can((GlobalPermission) perm);
    } else if (perm instanceof PluginPermission) {
      PluginPermission pluginPermission = (PluginPermission) perm;
      return canPerform(pluginPermission.permissionName())
          || (pluginPermission.fallBackToAdmin() && isAdmin());
    }
    throw new PermissionBackendException(perm + " unsupported");
  }

  private boolean can(GlobalPermission perm) throws PermissionBackendException {
    switch (perm) {
      case ADMINISTRATE_SERVER:
        return isAdmin();
      case EMAIL_REVIEWERS:
        return canEmailReviewers();

      case FLUSH_CACHES:
      case KILL_TASK:
      case RUN_GC:
      case VIEW_CACHES:
      case VIEW_QUEUE:
        return canPerform(perm.permissionName())
            || canPerform(GlobalCapability.MAINTAIN_SERVER)
            || isAdmin();

      case CREATE_ACCOUNT:
      case CREATE_GROUP:
      case CREATE_PROJECT:
      case MAINTAIN_SERVER:
      case MODIFY_ACCOUNT:
      case STREAM_EVENTS:
      case VIEW_ALL_ACCOUNTS:
      case VIEW_CONNECTIONS:
      case VIEW_PLUGINS:
        return canPerform(perm.permissionName()) || isAdmin();

      case ACCESS_DATABASE:
      case RUN_AS:
        return canPerform(perm.permissionName());
    }
    throw new PermissionBackendException(perm + " unsupported");
  }
}
